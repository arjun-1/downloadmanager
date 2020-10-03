package downloadmanager.streams.model

sealed trait StreamApiError

object StreamApiError {
  final case class AlreadyStopped(domain: String) extends StreamApiError
  final case class AlreadyStarted(domain: String) extends StreamApiError
}
