package downloadmanager.http.server.model

import java.time.Instant

final case class AddStreamReq(startTime: Instant, token: String)
