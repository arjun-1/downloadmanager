package downloadmanager

import zio.Has

object AppConfig {

  type AppConfig = Has[Config]

  final case class Config(zendeskCursorUrl: String)

}
