package downloadmanager.http.server.routes

import cats.data.{Kleisli, OptionT}
import cats.effect.Blocker
import cats.syntax.semigroupk._
import downloadmanager.http.server.endpoints.OpenApiEndpoints
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`Content-Type`
import org.http4s.{Charset, HttpRoutes, MediaType, Request, StaticFile}
import zio.blocking.Blocking
import zio.interop.catz._
import zio.{RIO, URIO, ZIO}

object OpenApiRoutes {

  def staticResource[R <: Blocking](
      requestToResource: PartialFunction[Request[RIO[R, *]], String]
  ): URIO[R, HttpRoutes[RIO[R, *]]] =
    ZIO
      .runtime[R]
      .map { runtime =>
        val blocker = Blocker.liftExecutionContext(runtime.environment.get.blockingExecutor.asEC)

        Kleisli(requestToResource.lift)
          .mapF(OptionT.fromOption[RIO[R, *]](_))
          .tapWithF((request, resource) =>
            StaticFile.fromResource(resource, blocker, Some(request))
          )
      }

  def make[R <: Blocking]: URIO[R, HttpRoutes[RIO[R, *]]] = {
    val dsl = Http4sDsl[RIO[R, *]]
    import dsl._

    val openapiRoute: HttpRoutes[RIO[R, *]] = HttpRoutes.of({
      case GET -> Root / "openapi" =>
        Ok(OpenApiEndpoints.json, `Content-Type`(MediaType.application.json, Charset.`UTF-8`))
    })

    staticResource[R] {
      case GET -> Root =>
        "index.html"
    }.map(indexRoute => indexRoute <+> openapiRoute)
  }

}
