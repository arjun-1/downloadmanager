package downloadmanager.streams

import java.time.Instant

import downloadmanager.streams.model._
import downloadmanager.zendesk.ZendeskClient
import downloadmanager.zendesk.model.{CursorPage, ZendeskClientError}
import zio.stream.ZStream
import zio.{IO, Ref, UIO, ZIO, ZLayer}

object StreamApi {

  trait Service {

    def startFromTime(
        domain: String,
        startTime: Instant,
        token: String
    ): UIO[ZStream[Any, ZendeskClientError, CursorPage]]

    def startFromCursor(
        domain: String,
        cursor: String,
        token: String
    ): UIO[ZStream[Any, ZendeskClientError, CursorPage]]

    def stop(domain: String): IO[StreamApiError, Unit]
  }

  val live = ZLayer.fromServiceM((client: ZendeskClient.Service) =>
    Ref
      .make(Map[String, Boolean]())
      .map(shouldStopRef =>
        new Service {

          def stop(domain: String) = {
            val guard =
              ZIO.require(StreamApiError.AlreadyStopped(domain))(
                shouldStopRef.get.map(_.get(domain))
              )

            for {
              _ <- guard
              _ <- shouldStopRef.update(_ + (domain -> true))
            } yield ()

          }

          def shouldStopSignal(domain: String) =
            shouldStopRef.get.map(_.get(domain).getOrElse(false))

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

            val stream =
              ZStream.unfoldM(initialRequest)(s =>
                shouldStopSignal(domain).flatMap(shouldStop =>
                  if (shouldStop)
                    UIO(None)
                  else
                    processRequestState(s)
                )
              )

            shouldStopRef.update(_ + (domain -> false)) *>
              UIO(stream.ensuring(shouldStopRef.update(_ - domain)))

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

            val stream =
              ZStream.unfoldM(initialRequest)(s =>
                shouldStopSignal(domain).flatMap(shouldStop =>
                  if (shouldStop)
                    UIO(None)
                  else
                    processRequestState(s)
                )
              )

            // clean up internal shouldStop state when the stream finishes.
            // Prevents memory leak.
            shouldStopRef.update(_ + (domain -> false)) *>
              UIO(stream.ensuring(shouldStopRef.update(_ - domain)))

          }
        }
      )
  )
}
