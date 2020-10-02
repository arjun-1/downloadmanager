package downloadmanager.zendesk.model

import downloadmanager.http.client.JsonHttpClientError

sealed trait ZendeskClientError

object ZendeskClientError {
  final case class InvalidUrl(url: String, err: String)  extends ZendeskClientError
  final case class ClientError(err: JsonHttpClientError) extends ZendeskClientError

}
