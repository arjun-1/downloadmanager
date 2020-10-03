package downloadmanager.streams.repo

import downloadmanager.streams.model.{StreamState, _}
import zio.macros.accessible
import zio.{Has, IO, Ref, UIO, ZIO, ZLayer}

@accessible
object StreamStateRepo {
  type StreamStateRepo = Has[Service]

  trait Service {
    def get(domain: String): IO[StreamStateRepoError, StreamState]
    def add(streamState: StreamState): IO[StreamStateRepoError, StreamState]
    def remove(domain: String): IO[StreamStateRepoError, Unit]

    def update(
        domain: String,
        newStreamState: StreamState => StreamState
    ): IO[StreamStateRepoError, StreamState]
    // def start(domain: String): IO[StreamStateRepoError, StreamState]
    // def stop(domain: String): IO[StreamStateRepoError, StreamState]
    // def incNrOfSeen(domain: String): IO[StreamStateRepoError, StreamState]
    // def updateCursor(domain: String, cursor: String): IO[StreamStateRepoError, StreamState]
    def list: UIO[List[StreamState]]
  }

  val live = ZLayer.fromEffect(
    Ref
      .make(Map[String, StreamState]())
      .map(ref =>
        new Service {

          def get(domain: String) =
            ZIO.require(StreamStateRepoError.NotFound(domain))(ref.get.map(_.get(domain)))

          def add(streamState: StreamState) = {

            val stream = ref.get.map(_.get(streamState.domain))

            val guard =
              ZIO.whenM(stream.map(_.isDefined))(
                IO.fail(StreamStateRepoError.AlreadyExists(streamState.domain))
              )

            for {
              _        <- guard
              newState <- ref.updateAndGet(_ + (streamState.domain -> streamState))
            } yield streamState
          }

          def remove(domain: String) = {
            val guard = get(domain)

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
              newState = newStreamState(s)
              _ <- ref.update(_ + (domain -> newState))
            } yield newState

          }

          // def start(domain: String) = update(domain, _.copy(isPaused = false))

          // def stop(domain: String) = update(domain, _.copy(isPaused = true))

          // def incNrOfSeen(domain: String) =
          //   update(domain, s => s.copy(nrOfTicketsSeen = s.nrOfTicketsSeen + 1))

          def list = ref.get.map(_.values.toList)

        }
      )
  )

}
