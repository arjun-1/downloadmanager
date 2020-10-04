package downloadmanager.streams

import java.time.Instant

import scala.jdk.DurationConverters._

import downloadmanager.AppConfig
import downloadmanager.streams.model._
import downloadmanager.zendesk.ZendeskClient
import downloadmanager.zendesk.model.{CursorPage, ZendeskClientError}
import zio.clock.Clock
import zio.macros.accessible
import zio.stm.{STM, TMap, TPromise, ZSTM}
import zio.stream.ZStream
import zio.{Has, IO, UIO, ZLayer}

@accessible
object StreamApi {

  type StreamApi = Has[Service]

  trait Service {

    /**
      * Starts a stream, using a starting time (but uses cursor when available).
      * Yields an error when a stream is already running for the domain
      */

    def startFromTime(
        domain: String,
        startTime: Instant,
        token: String
    ): IO[StreamApiError, ZStream[Clock, ZendeskClientError, CursorPage]]

    /**
      * Starts a stream, using a starting cursor.
      * Yields an error when a stream is already running for the domain
      */
    def startFromCursor(
        domain: String,
        cursor: String,
        token: String
    ): IO[StreamApiError, ZStream[Clock, ZendeskClientError, CursorPage]]

    /**
      * Stops a running stream.
      * Completes when the stream is actually stopped
      */
    def stop(domain: String): IO[StreamApiError, Unit]

    /**
      * We could also have had a function which idempotently starts a stream,
      * i.e. if there is a running stream stop it and start a new one,
      * or start a new one if no stream was running.
      * Not implemented due to time constraints.
      */
    // def restartFromTime(domain: String, startTIme: Instant, token: String)
  }

  type ShouldStop = Boolean

  val live = ZLayer.fromServicesM((client: ZendeskClient.Service, config: AppConfig.Config) =>
    // Implementation detail:
    // TMap is a Transactional Map, which we use to represent running streams.
    // We create a TMap which is keyed by domain, and value tuple of (shouldStop, promise).
    // `shouldStop` is a boolean flag, signalling the stream to stop.
    // `promise` is used to let the stop function be completed, exactly when the stream is stopped.

    // Software Transactional Memory (STM) is used, so that no race conditions can occur
    // when 2 simultaneous request to create a stream are made. I.e. we are guaranteed to have
    // a single stream per domain.
    TMap
      .empty[String, (ShouldStop, TPromise[Nothing, Unit])]
      .commit
      .map[Service](runningStreams =>
        new Service {

          def stop(domain: String) = {
            val promise = ZSTM.atomically {
              for {
                (_, promise) <-
                  ZSTM.require(StreamApiError.AlreadyStopped(domain))(runningStreams.get(domain))
                _ <- runningStreams.put(domain, (true, promise))
              } yield promise
            }

            promise.flatMap(_.await.commit)

          }

          // unfold a stream, using an initial request `intialRequest`,
          // and creation of next request through `processRequest`
          private def unfoldStream[S, E, A](
              domain: String,
              initialRequest: S,
              processRequest: S => IO[E, Option[(A, S)]]
          ): IO[StreamApiError.AlreadyStarted, ZStream[Clock, E, A]] = {
            def alreadyStartedGuard =
              ZSTM.whenM(runningStreams.contains(domain))(
                ZSTM.fail(StreamApiError.AlreadyStarted(domain))
              )

            def shouldStopSignal = runningStreams.get(domain).map(_.exists(_._1)).commit

            val stream =
              ZStream.unfoldM(initialRequest)(s =>
                shouldStopSignal.flatMap(shouldStop =>
                  if (shouldStop)
                    UIO(None)
                  else
                    processRequest(s)
                )
              )

            // 1. Ensure no 2 streams for the same domain are started in parallel through race conditions,
            // by running the guard (predicate), and updating the internal map in 1 transaction.
            // 2. Creates the promise to signal stopping of the stream to caller of `stop` function
            val before = ZSTM.atomically(
              alreadyStartedGuard *>
                TPromise
                  .make[Nothing, Unit]
                  .flatMap(promise => runningStreams.put(domain, (false, promise)))
            )

            // 1. signal completion to caller of `stop` function
            // 2. Clean up internal runningStreams state when the stream finishes, prevents memory leak.
            val after = ZSTM.atomically(
              runningStreams
                .get(domain)
                .flatMap {
                  case None =>
                    STM.unit
                  case Some((_, promise)) =>
                    promise.succeed(()).unit
                } *> runningStreams.delete(domain)
            )

            before *>
              UIO(
                stream
                  .ensuring(after)
                  .throttleShape(config.throttle.count, config.throttle.duration.toJava)(
                    _.size.toLong
                  )
              )
          }

          def startFromCursor(domain: String, cursor: String, token: String) = {
            final case class RequestState(domain: String, cursor: String, token: String)

            def processRequestState(requestState: RequestState) =
              client
                .getTicketsFromCursor(requestState.domain, requestState.cursor, requestState.token)
                .map { page =>
                  // If we have reached the final page, keep using the latest known next cursor ptr for new requests.
                  val newCursor       = page.after_cursor.getOrElse(requestState.cursor)
                  val newRequestState = requestState.copy(cursor = newCursor)

                  Some(page -> newRequestState)
                }

            val initialRequest = RequestState(domain, cursor, token)

            unfoldStream(domain, initialRequest, processRequestState)
          }

          def startFromTime(domain: String, startTime: Instant, token: String) = {
            final case class RequestState(
                domain: String,
                startTime: Instant,
                cursor: Option[String],
                token: String
            )

            def processRequestState(requestState: RequestState) =
              requestState match {
                // previous request succesfully yielded a next cursor ptr
                case rs @ RequestState(domain, _, Some(cursor), token) =>
                  client
                    .getTicketsFromCursor(domain, cursor, token)
                    .map { page =>
                      // If we have reached the final page, keep using the latest known next cursor ptr for new requests.
                      val newCursor       = page.after_cursor.getOrElse(cursor)
                      val newRequestState = rs.copy(cursor = Some(newCursor))

                      Some(page -> newRequestState)
                    }
                // previous request did not yield a next cursor ptr
                case rs @ RequestState(domain, startTime, None, token) =>
                  client
                    .getTicketsFromStartTime(domain, startTime, token)
                    .map { page =>
                      // If we found a next cursor ptr, use that for the next request. Otherwise keep using start time.
                      val newCursor       = page.after_cursor
                      val newRequestState = rs.copy(cursor = newCursor)

                      Some(page -> newRequestState)
                    }
              }

            val initialRequest = RequestState(domain, startTime, None, token)

            unfoldStream(domain, initialRequest, processRequestState)
          }
        }
      )
  )
}
