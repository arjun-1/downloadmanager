package downloadmanager.streams

import java.time.Instant

import downloadmanager.streams.model._
import downloadmanager.zendesk.ZendeskClient
import downloadmanager.zendesk.model.{CursorPage, ZendeskClientError}
import zio.clock.Clock
import zio.duration._
import zio.macros.accessible
import zio.stm.{TMap, TPromise, ZSTM}
import zio.stream.ZStream
import zio.{Has, IO, UIO, ZLayer}

@accessible
object StreamApi {

  type StreamApi = Has[Service]

  trait Service {

    def startFromTime(
        domain: String,
        startTime: Instant,
        token: String
    ): IO[StreamApiError, ZStream[Clock, ZendeskClientError, CursorPage]]

    def startFromCursor(
        domain: String,
        cursor: String,
        token: String
    ): IO[StreamApiError, ZStream[Clock, ZendeskClientError, CursorPage]]

    def stop(domain: String): IO[StreamApiError, Unit]
  }

  val live = ZLayer.fromServiceM((client: ZendeskClient.Service) =>
    // TMap is a software transactional map, having key domain and value `shouldStop`
    // When streams are created, their value in the map is set to 'false'
    // When streams are stopped, their value in the map is set to 'true', signalling termination to the stream.
    // Subsequently the entry of the stream in the map is removed to prevent a memory leak.

    // Software Transactional Memory (STM) is used, so that no race conditions can occur
    // when 2 simultaneous request to create a stream are made.
    TMap
      .empty[String, (Boolean, TPromise[Nothing, Unit])]
      .commit
      .map(runningStreams =>
        new Service {

          def stop(domain: String) =
            ZSTM.atomically {
              for {
                (_, promise) <-
                  ZSTM.require(StreamApiError.AlreadyStopped(domain))(runningStreams.get(domain))
                _ <- runningStreams.put(domain, (true, promise))
                _ <- promise.await

              } yield ()
            }

          def shouldStopSignal(domain: String) =
            runningStreams.get(domain).map(_.map(_._1).getOrElse(false)).commit

          def unfoldStream[S, E, A](
              domain: String,
              initialRequest: S,
              processRequest: S => IO[E, Option[(A, S)]]
          ) = {
            def alreadyStartedGuard =
              ZSTM.whenM(runningStreams.contains(domain))(
                ZSTM.fail(StreamApiError.AlreadyStarted(domain))
              )
            // ZSTM.whenM(
            //   shouldStop.get(domain).map(_.map(x => !x._1).getOrElse(false))
            // )(ZSTM.fail(StreamApiError.AlreadyStarted(domain)))

            val stream =
              ZStream.unfoldM(initialRequest)(s =>
                shouldStopSignal(domain).flatMap(shouldStop =>
                  if (shouldStop)
                    UIO(None)
                  else
                    processRequest(s)
                )
              )

            // Ensure no 2 streams for the same domain are started in parallel through race conditions,
            // by running the guard (if statement), and updating the internal map in 1 transaction.
            // creates the promise to signal stopping of the stream to caller of stop

            val before =
              (
                alreadyStartedGuard *>
                  TPromise
                    .make[Nothing, Unit]
                    .flatMap(promise => runningStreams.put(domain, (false, promise)))
              ).commit

            // Clean up internal shouldStop state when the stream finishes, prevents memory leak.
            val after =
              (
                runningStreams.get(domain).map(_.map(_._2.succeed(()))) *>
                  runningStreams.delete(domain)
              ).commit
            before *> UIO(stream.ensuring(after).throttleShape(1, 10.seconds)(_.size.toLong))
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
