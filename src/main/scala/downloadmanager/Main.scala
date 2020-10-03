package downloadmanager

import java.time.Instant

import cats.syntax.show._
import downloadmanager.AppConfig.Config
import downloadmanager.http.client.{JsonHttpClient, TaskBackend}
import downloadmanager.publish.PublishApi
import downloadmanager.streams.StreamApi
import downloadmanager.streams.repo.StreamStateRepo
import downloadmanager.zendesk.ZendeskClient
import zio.blocking.Blocking
import zio.clock.{Clock, _}
import zio.console._
import zio.duration._
import zio.stream.ZStream
import zio.{Has, UIO, ZLayer}

object Main extends zio.App {

  val domain    = "d3v-kaizo"
  
  val startTime = Instant.ofEpochSecond(1585699200)

  val config = ZLayer.succeed(Config("zendesk.com/api/v2/incremental/tickets/cursor.json"))

  val httpBackend = Blocking.live >>> ZLayer.fromManaged(TaskBackend.live)

  val httpClient = httpBackend >>> JsonHttpClient.live

  val zendeskClient = httpClient ++ config >>> ZendeskClient.live

  val publishApi = Console.live >>> PublishApi.live
  val streamApi  = zendeskClient >>> StreamApi.live
  val streamRepo = StreamStateRepo.live

  val downloadManager: ZLayer[Any, Throwable, Has[DownloadManagerApi.Service]] =
    streamApi ++ streamRepo ++ publishApi ++ Clock.live ++ Console.live >>> DownloadManagerApi.live

  val program =
    (
      DownloadManagerApi.addStream(domain, startTime, token) *> sleep(1.seconds) *>
        UIO(println("stopping..")) *> DownloadManagerApi.stopStream(domain) *>
        UIO(println("stopped..")) *> sleep(5.seconds) *> UIO(println("huh1")) *>
        StreamStateRepo.list.map(println) *> DownloadManagerApi.startStream(domain) *>
        ZStream(1).forever.runDrain
    ).provideLayer(downloadManager ++ streamRepo ++ Clock.live)

  def run(args: List[String]) =
    program.flatMapError(e => putStrLn(show"Application failed to start: $e")).exitCode

}
