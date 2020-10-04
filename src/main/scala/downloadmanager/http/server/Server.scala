package downloadmanager.http.server

import cats.effect.Timer
import cats.syntax.semigroupk._
import downloadmanager.AppConfig.{AppConfig, Config}
import downloadmanager.DownloadManagerApi.DownloadManagerApi
import downloadmanager.http.server.routes.{DownloadManagerRoutes, OpenApiRoutes}
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import zio.blocking.Blocking
import zio.interop.catz._
import zio.{RIO, ZEnv, ZIO}

object Server {

  type ServerEnv = Blocking with DownloadManagerApi with AppConfig

  def httpApp[R <: ServerEnv] =
    OpenApiRoutes
      .make[R]
      .map(openApiRoutes =>
        Logger.httpApp[RIO[R, *]](logHeaders = false, logBody = false)(
          Router("/" -> (openApiRoutes <+> DownloadManagerRoutes[R])).orNotFound
        )
      )

  def serve[R <: ServerEnv with ZEnv] =
    for {
      app     <- httpApp[R]
      runtime <- ZIO.runtime[R]
      executionContext = runtime.platform.executor.asEC
      concurrentEffect = taskEffectInstance(runtime)
      timer            = implicitly[Timer[RIO[R, *]]]
      port             = runtime.environment.get[Config].serverPort
      _ <-
        BlazeServerBuilder(executionContext)(concurrentEffect, timer)
          .bindHttp(port, "0.0.0.0")
          .withHttpApp(app)
          .serve
          .compile
          .drain
    } yield ()

}
