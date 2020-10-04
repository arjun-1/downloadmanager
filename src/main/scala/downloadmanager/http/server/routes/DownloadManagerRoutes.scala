package downloadmanager.http.server.routes

import scala.util.control.NoStackTrace

import cats.syntax.show._
import downloadmanager.DownloadManagerApi.DownloadManagerApi
import downloadmanager.http.server.endpoints.{DownloadManagerEndpoints, Http4sEndpointsRIO}
import downloadmanager.{DownloadManagerApi, DownloadManagerError}
import endpoints4s.Invalid
import endpoints4s.http4s.server
import org.http4s.HttpRoutes
import zio.{RIO, UIO}

object DownloadManagerRoutes {
  final case class InternalError(message: String) extends Exception(message) with NoStackTrace

  private class Routes[R <: DownloadManagerApi]
      extends Http4sEndpointsRIO[R]
      with DownloadManagerEndpoints
      with server.JsonEntitiesFromSchemas
      with server.StatusCodes {

    def refineClientError(err: DownloadManagerError) =
      err match {
        // due to time constraints, we consider all errors to be user errors (4xx)
        case _ =>
          UIO(Left(Invalid(show"error: $err")))
      }

    val addStreamEndpoint = addStream.implementedByEffect {
      case (domain, req) =>
        DownloadManagerApi
          .addStream(domain, req.startTime, req.token)
          .foldM(refineClientError, x => UIO(Right(x)))
    }

    val removeStreamEndpoint = removeStream.implementedByEffect(
      DownloadManagerApi.removeStream(_).foldM(refineClientError, x => UIO(Right(x)))
    )

    val startStreamEndpoint = startStream.implementedByEffect(
      DownloadManagerApi.startStream(_).foldM(refineClientError, x => UIO(Right(x)))
    )

    val stopStreamEndpoint = stopStream.implementedByEffect(
      DownloadManagerApi.stopStream(_).foldM(refineClientError, x => UIO(Right(x)))
    )

    val listStreamsEndpoint = listStreams.implementedByEffect(_ => DownloadManagerApi.listStreams)

    val routes: HttpRoutes[RIO[R, *]] = HttpRoutes.of(
      routesFromEndpoints(
        addStreamEndpoint,
        removeStreamEndpoint,
        startStreamEndpoint,
        stopStreamEndpoint,
        listStreamsEndpoint
      )
    )

  }

  def apply[R <: DownloadManagerApi]: HttpRoutes[RIO[R, *]] = new Routes[R].routes

}
