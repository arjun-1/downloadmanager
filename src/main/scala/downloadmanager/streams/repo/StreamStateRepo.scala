package downloadmanager.streams.repo

import downloadmanager.streams.model.{StreamState, _}
import zio.macros.accessible
import zio.{IO, Ref, UIO, ZIO, ZLayer}

@accessible
object StreamStateRepo {

  trait Service {
    def add(streamState: StreamState): IO[StreamStateRepoError, Unit]
    def remove(domain: String): IO[StreamStateRepoError, Unit]
    def start(domain: String): IO[StreamStateRepoError, Unit]
    def stop(domain: String): IO[StreamStateRepoError, Unit]
    def incNrOfSeen(domain: String): IO[StreamStateRepoError, Unit]
    def list: UIO[List[StreamState]]
  }

  val live = ZLayer.fromEffect(
    Ref
      .make(Map[String, StreamState]())
      .map(ref =>
        new Service {

          def add(streamState: StreamState) = {

            val db     = ref.get
            val stream = db.map(_.get(streamState.domain))

            val guard =
              stream.filterOrFail(_.isEmpty)(StreamStateRepoError.AlreadyExists(streamState.domain))

            for {
              _ <- guard
              _ <- db.map(_ + (streamState.domain -> streamState))
            } yield ()
          }

          def remove(domain: String) = {
            val guard =
              ZIO.require(StreamStateRepoError.NotFound(domain))(ref.get.map(_.get(domain)))

            for {
              _ <- guard
              _ <- ref.update(_ - domain)
            } yield ()

          }

          def update(domain: String, newStreamState: StreamState => StreamState) = {
            val streamState =
              ZIO.require(StreamStateRepoError.NotFound(domain))(ref.get.map(_.get(domain)))

            for {
              s <- streamState
              _ <- ref.update(_ + (domain -> newStreamState(s)))
            } yield ()

          }

          def start(domain: String) = update(domain, _.copy(isPaused = false))

          def stop(domain: String) = update(domain, _.copy(isPaused = true))

          def incNrOfSeen(domain: String) =
            update(domain, s => s.copy(nrOfTicketsSeen = s.nrOfTicketsSeen + 1))

          def list = ref.get.map(_.values.toList)

        }
      )
  )

}
