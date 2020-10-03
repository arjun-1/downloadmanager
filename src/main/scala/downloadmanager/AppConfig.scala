package downloadmanager

import scala.concurrent.duration._

import pureconfig.ConfigSource
import pureconfig.generic.auto._
import zio.{Has, Task, URIO, ZIO, ZLayer}

object AppConfig {

  type AppConfig = Has[Config]

  final case class ThrottleConfig(count: Long, duration: FiniteDuration)
  final case class Config(zendeskCursorUrl: String, serverPort: Int, throttle: ThrottleConfig)

  val live                         = ZLayer.fromEffect(Task(ConfigSource.default.loadOrThrow[Config]))
  def get: URIO[AppConfig, Config] = ZIO.access(_.get)

}
