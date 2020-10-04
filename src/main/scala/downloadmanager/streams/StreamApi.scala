package downloadmanager.streams

import java.time.Instant

import scala.jdk.DurationConverters._

import downloadmanager.AppConfig
import downloadmanager.streams.model._
import downloadmanager.zendesk.ZendeskClient
import downloadmanager.zendesk.model.{CursorPage, ZendeskClientError}
import zio.clock.Clock
import zio.macros.accessible
import zio.stm.{TMap, ZSTM}
import zio.stream.{Stream, ZStream}
import zio.{Has, IO, Promise, ZIO, ZLayer}

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
    ): IO[StreamApiError, Stream[ZendeskClientError, CursorPage]]

    /**
      * Starts a stream, using a starting cursor.
      * Yields an error when a stream is already running for the domain
      */
    def startFromCursor(
        domain: String,
        cursor: String,
        token: String
    ): IO[StreamApiError, Stream[ZendeskClientError, CursorPage]]

    /**
      * Stops a running stream.
      * Completes when the stream is actually stopped. Note that this means
      * the stream must be started to be consumed, before the returned effect completes.
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

  type ShouldStop = Promise[ZendeskClientError, Unit]
  type DidStop    = Promise[ZendeskClientError, Unit]

  val live = ZLayer.fromServicesM(
    (client: ZendeskClient.Service, config: AppConfig.ThrottleConfig, clock: Clock.Service) =>
      // Implementation detail:
      // TMap is a Transactional Map, which we use to represent running streams.
      // We create a TMap which is keyed by domain, and value Tuple of Promises shouldStop, didStop.
      // `shouldStop` is used to signal to the stream to stop processing.
      // Since the actual stopping of the stream happens not immediately,
      // we use `didStop` to signal back to the caller of stop that the stream actually stopped.

      // Software Transactional Memory (STM) is used, so that no race conditions can occur
      // when 2 simultaneous request to create a stream are made. I.e. we are guaranteed to have
      // a single stream per domain.
      TMap
        .empty[String, (ShouldStop, DidStop)]
        .commit
        .map[Service](runningStreams =>
          new Service {

            def stop(domain: String) = {
              val promises = ZSTM.atomically {
                for {
                  (shouldStop, didStop) <-
                    ZSTM.require(StreamApiError.AlreadyStopped(domain))(runningStreams.get(domain))
                  _ <- runningStreams.delete(domain)
                } yield (shouldStop, didStop)
              }

              promises.flatMap {
                case (shouldStop, didStop) =>
                  shouldStop.succeed(()) *> didStop.await.ignore
              }

            }

            // unfold a stream, using an initial request `intialRequest`,
            // and creation of next request through `processRequest`
            private def unfoldStream[S, A](
                domain: String,
                initialRequest: S,
                processRequest: S => IO[ZendeskClientError, Option[(A, S)]]
            ): IO[StreamApiError, Stream[ZendeskClientError, A]] = {
              def alreadyStartedGuard =
                ZSTM.whenM(runningStreams.contains(domain))(
                  ZSTM.fail(StreamApiError.AlreadyStarted(domain))
                )

              val stream = ZStream.unfoldM(initialRequest)(processRequest)

              // Ensure no 2 streams for the same domain are started in parallel through race conditions,
              // by running the guard (predicate), and updating the internal map in 1 transaction.
              val before = ZIO
                .mapN(
                  Promise.make[ZendeskClientError, Unit],
                  Promise.make[ZendeskClientError, Unit]
                )((shouldStop, didStop) => shouldStop -> didStop)
                .tap(promises =>
                  ZSTM.atomically(alreadyStartedGuard *> runningStreams.put(domain, promises))
                )

              // Clean up internal runningStreams state when the stream finishes, prevents memory leak.
              before.map {
                case (shouldStop, didStop) =>
                  stream
                    .ensuring(didStop.succeed(()) *> runningStreams.delete(domain).commit)
                    .haltWhen(shouldStop)
                    .throttleShape(config.count, config.duration.toJava)(_.size.toLong)
                    .provide(Has(clock))
              }

            }

            def startFromCursor(domain: String, cursor: String, token: String) = {
              final case class RequestState(domain: String, cursor: String, token: String)

              def processRequestState(requestState: RequestState) =
                client
                  .getTicketsFromCursor(
                    requestState.domain,
                    requestState.cursor,
                    requestState.token
                  )
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
