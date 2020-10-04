package downloadmanager

import cats.syntax.show._
import downloadmanager.http.client.{JsonHttpClient, TaskBackend}
import downloadmanager.http.server.Server
import downloadmanager.publish.PublishApi
import downloadmanager.streams.StreamApi
import downloadmanager.streams.repo.StreamStateRepo
import downloadmanager.zendesk.ZendeskClient
import zio.ZLayer
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console._

object Main extends zio.App {

  val config         = AppConfig.live
  val throttleConfig = config >>> ZLayer.fromService(_.throttle)

  val httpBackend   = Blocking.live >>> ZLayer.fromManaged(TaskBackend.live)
  val httpClient    = httpBackend >>> JsonHttpClient.live
  val zendeskClient = httpClient ++ config >>> ZendeskClient.live

  val publishApi = Console.live >>> PublishApi.live

  val streamApi  = zendeskClient ++ throttleConfig >>> StreamApi.live
  val streamRepo = StreamStateRepo.live

  val downloadManager = streamApi ++ streamRepo ++ publishApi ++ Clock.live ++ Console.live >>>
    DownloadManagerApi.live

  val program = Server.serve.provideCustomLayer(downloadManager ++ config ++ Blocking.live)

  def run(args: List[String]) =
    program.flatMapError(e => putStrLn(show"Application failed to start: $e")).exitCode

}
