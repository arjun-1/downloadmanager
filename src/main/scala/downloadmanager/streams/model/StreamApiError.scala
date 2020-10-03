package downloadmanager.streams.model

sealed trait StreamApiError

object StreamApiError {
  final case class AlreadyStopped(domain: String) extends StreamApiError
}
