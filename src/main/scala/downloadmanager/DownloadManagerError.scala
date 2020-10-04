package downloadmanager

import downloadmanager.streams.model.{StreamApiError, StreamStateRepoError}
import downloadmanager.zendesk.model.ZendeskClientError

sealed trait DownloadManagerError

object DownloadManagerError {
  final case class RepoError(err: StreamStateRepoError) extends DownloadManagerError
  final case class StreamError(err: StreamApiError)     extends DownloadManagerError
  final case class ClientError(err: ZendeskClientError) extends DownloadManagerError
}
