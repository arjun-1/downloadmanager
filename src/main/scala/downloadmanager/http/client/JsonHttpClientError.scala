package downloadmanager.http.client

import io.circe.Error
import sttp.model.StatusCode

sealed trait JsonHttpClientError

object JsonHttpClientError {
  final case class NetworkError(err: Throwable) extends JsonHttpClientError
  final case class DecodeError(err: Error)      extends JsonHttpClientError

  final case class UnexpectedStatusCode(statusCode: StatusCode, body: String)
      extends JsonHttpClientError

}
