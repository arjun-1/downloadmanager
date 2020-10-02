package downloadmanager

import java.time.Instant

import cats.Show

package object zendesk {

  implicit val showInstant: Show[Instant] = Show.show(_.getEpochSecond.toString)
}
