package downloadmanager.streams.repo

import downloadmanager.streams.model.{StreamState, _}
import zio.macros.accessible
import zio.{Has, IO, Ref, UIO, ULayer, ZIO, ZLayer}

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
    def list: UIO[List[StreamState]]
  }

  // Repository, implemented in memory using a Map as underlying data structure.
  // Could have been equally well a database repo.
  val live: ULayer[StreamStateRepo] = ZLayer.fromEffect(
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
              _ <- guard
              _ <- ref.updateAndGet(_ + (streamState.domain -> streamState))
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

          def list = ref.get.map(_.values.toList)

        }
      )
  )

}
