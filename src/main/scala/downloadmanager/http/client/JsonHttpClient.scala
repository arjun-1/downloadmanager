package downloadmanager.http.client

import io.circe.{Decoder, Error}
import sttp.client._
import sttp.client.circe._
import sttp.model.Uri
import zio.macros.accessible
import zio.{Has, IO, UIO, ZLayer}

import TaskBackend.TaskBackend

@accessible
object JsonHttpClient {

  type JsonHttpClient = Has[Service]

  trait Service {
    def get[T: Decoder](url: Uri, token: String): IO[JsonHttpClientError, T]
  }

  val live = ZLayer.fromService { implicit b: TaskBackend =>
    def transformResponse[T](
        response: Response[Either[ResponseError[Error], T]]
    ): IO[JsonHttpClientError, T] =
      response.body match {
        case Left(HttpError(body, statusCode)) =>
          IO.fail(JsonHttpClientError.UnexpectedStatusCode(statusCode, body))
        case Left(DeserializationError(_, error)) =>
          IO.fail(JsonHttpClientError.DecodeError(error))
        case Right(value) =>
          IO.succeed(value)
      }

    def send[T: Decoder](request: Request[Either[String, String], Nothing]) =
      request
        .response(asJson)
        .send()
        .mapError(JsonHttpClientError.NetworkError)
        .flatMap(transformResponse)

    new Service {
      def get[T: Decoder](url: Uri, token: String): IO[JsonHttpClientError, T] =
        UIO(println(url)) *> send(basicRequest.get(url).auth.bearer(token))
    }
  }

}
