package downloadmanager.streams.model

import java.time.Instant

final case class StreamState(
    domain: String,
    token: String,
    startTime: Instant,
    cursor: Option[String],
    isPaused: Boolean,
    nrOfTicketsSeen: Int
)
