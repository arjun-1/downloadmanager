package downloadmanager.streams.model

sealed trait StreamStateRepoError

object StreamStateRepoError {
  final case class NotFound(domain: String)      extends StreamStateRepoError
  final case class AlreadyExists(domain: String) extends StreamStateRepoError

}
