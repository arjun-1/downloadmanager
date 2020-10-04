package downloadmanager.http.server

import cats.Show
import downloadmanager.DownloadManagerError

package object routes {

  implicit val showDownloadManagerError: Show[DownloadManagerError] = Show
    .fromToString[DownloadManagerError]
}
