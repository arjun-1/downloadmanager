package downloadmanager.http.server.endpoints

import endpoints4s.http4s.server
import zio.RIO
import zio.interop.catz._

class Http4sEndpointsRIO[R] extends server.Endpoints[RIO[R, *]]
