package downloadmanager

import java.time.Instant

import downloadmanager.DownloadManagerError
import downloadmanager.publish.PublishApi
import downloadmanager.streams.model.StreamState
import downloadmanager.streams.{StreamApi, StreamStateRepo}
import downloadmanager.zendesk.model.{CursorPage, ZendeskClientError}
import zio.macros.accessible
import zio.stream.Stream
import zio.{Has, IO, UIO, ZIO, ZLayer}

@accessible
object DownloadManagerApi {

  type DownloadManagerApi = Has[Service]

  trait Service {
    def addStream(domain: String, startTime: Instant, token: String): IO[DownloadManagerError, Unit]
    def removeStream(domain: String): IO[DownloadManagerError, Unit]
    def startStream(domain: String): IO[DownloadManagerError, Unit]
    def stopStream(domain: String): IO[DownloadManagerError, Unit]
    def listStreams: UIO[List[StreamState]]
  }

  val live = ZLayer.fromServices(
    (
        streamApi: StreamApi.Service,
        streamRepo: StreamStateRepo.Service,
        publishApi: PublishApi.Service
    ) =>
      new Service {

        private def pageAction(domain: String, page: CursorPage) =
          ZIO.foreach(page.tickets)(publishApi.publish(domain, _)) *>
            streamRepo.update(
              domain,
              s =>
                s.copy(
                  cursor = page.after_cursor.orElse(s.cursor),
                  nrOfTicketsSeen = s.nrOfTicketsSeen + page.tickets.length,
                  isPaused = false
                )
            )

        private def runStream(domain: String, stream: Stream[ZendeskClientError, CursorPage]) =
          stream.tap(pageAction(domain, _)).runDrain.fork

        def addStream(domain: String, startTime: Instant, token: String) = {
          val initialStreamState = StreamState.initial(domain, startTime, token)

          for {
            _ <- streamRepo.add(initialStreamState).mapError(DownloadManagerError.RepoError)
            stream <-
              streamApi
                .startFromTime(domain, startTime, token)
                .mapError(DownloadManagerError.StreamError)
            _ <- runStream(domain, stream)
          } yield ()
        }

        def removeStream(domain: String) =
          // a stream might have been stopped before. If so, ignore the error of already being stopped
          streamApi.stop(domain).ignore *>
            streamRepo.remove(domain).mapError[DownloadManagerError](DownloadManagerError.RepoError)

        def startStream(domain: String) =
          for {
            state <- streamRepo.get(domain).mapError(DownloadManagerError.RepoError)
            stream <- (
                state match {
                  case StreamState(_, token, _, Some(cursor), _, _) =>
                    streamApi.startFromCursor(domain, cursor, token)
                  case StreamState(domain, token, startTime, None, _, _) =>
                    streamApi.startFromTime(domain, startTime, token)
                }
            ).mapError[DownloadManagerError](DownloadManagerError.StreamError)
            _ <- runStream(domain, stream)
          } yield ()

        def stopStream(domain: String) =
          streamApi.stop(domain).mapError[DownloadManagerError](DownloadManagerError.StreamError) *>
            streamRepo
              .update(domain, _.copy(isPaused = true))
              .mapError(DownloadManagerError.RepoError)
              .unit

        def listStreams = streamRepo.list

      }
  )
}
