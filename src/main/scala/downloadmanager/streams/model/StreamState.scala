package downloadmanager.streams.model

import java.time.Instant

final case class StreamState private (
    domain: String,
    token: String,
    startTime: Instant,
    cursor: Option[String],
    isPaused: Boolean,
    nrOfTicketsSeen: Int
)

object StreamState {

  def apply2(domain: String, startTime: Instant, token: String): StreamState =
    StreamState(domain, token, startTime, None, false, 0)
}
