package downloadmanager

import java.time.Instant

import downloadmanager.DownloadManagerError
import downloadmanager.publish.PublishApi
import downloadmanager.streams.StreamApi
import downloadmanager.streams.model.StreamState
import downloadmanager.streams.repo.StreamStateRepo
import downloadmanager.zendesk.model.CursorPage
import zio.clock.Clock
import zio.macros.accessible
import zio.{Has, IO, ZIO, ZLayer}

@accessible
object DownloadManagerApi {

  trait Service {
    def addStream(domain: String, startTime: Instant, token: String): IO[DownloadManagerError, Unit]
    def removeStream(domain: String): IO[DownloadManagerError, Unit]
    def startStream(domain: String): IO[DownloadManagerError, Unit]
    def stopStream(domain: String): IO[DownloadManagerError, Unit]
  }

  val live = ZLayer.fromServices(
    (
        streamApi: StreamApi.Service,
        streamRepo: StreamStateRepo.Service,
        publishApi: PublishApi.Service,
        clock: Clock.Service
    ) =>
      new Service {

        def pageAction(domain: String, page: CursorPage) =
          ZIO.foreach(page.tickets)(publishApi.publish(domain, _)) *>
            streamRepo.update(
              domain,
              s =>
                s.copy(
                  cursor = page.after_cursor,
                  nrOfTicketsSeen = s.nrOfTicketsSeen + page.tickets.length,
                  isPaused = false
                )
            )

        def addStream(domain: String, startTime: Instant, token: String) = {
          val initialStreamState = StreamState.apply2(domain, startTime, token)

          for {
            _ <- streamRepo.add(initialStreamState).mapError(DownloadManagerError.RepoError)
            stream <-
              streamApi
                .startFromTime(domain, startTime, token)
                .mapError(DownloadManagerError.StreamError)
            _ <- stream.map(pageAction(domain, _)).runDrain.provide(Has(clock)).fork
          } yield ()
        }

        def removeStream(domain: String) =
          streamApi.stop(domain).mapError[DownloadManagerError](DownloadManagerError.StreamError) *>
            streamRepo.remove(domain).mapError(DownloadManagerError.RepoError)

        def startStream(domain: String) =
          for {

            state <- streamRepo.get(domain).mapError(DownloadManagerError.RepoError)
            stream <- (
                state match {
                  case StreamState(_, token, _, Some(cursor), _, _) =>
                    streamApi.startFromCursor(domain, cursor, token)
                  case StreamState(domain, token, startTime, _, _, _) =>
                    streamApi.startFromTime(domain, startTime, token)
                }
            ).mapError[DownloadManagerError](DownloadManagerError.StreamError)
            _ <- stream.map(pageAction(domain, _)).runDrain.provide(Has(clock)).fork
          } yield ()

        def stopStream(domain: String) =
          streamApi.stop(domain).mapError[DownloadManagerError](DownloadManagerError.StreamError) *>
            streamRepo
              .update(domain, _.copy(isPaused = true))
              .mapError(DownloadManagerError.RepoError)
              .unit

      }
  )
}
