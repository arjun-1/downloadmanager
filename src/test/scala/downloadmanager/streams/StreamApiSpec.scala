package downloadmanager.streams

import java.time.Instant

import downloadmanager.AppConfig.{Config, ThrottleConfig}
import downloadmanager.http.client.JsonHttpClient
import downloadmanager.http.client.TaskBackend.TaskBackend
import downloadmanager.streams.model.StreamApiError
import downloadmanager.zendesk.ZendeskClient
import sttp.client.impl.zio.RIOMonadAsyncError
import sttp.client.testing.SttpBackendStub
import zio.test.Assertion._
import zio.test.environment.TestClock
import zio.test.{DefaultRunnableSpec, _}
import zio.{Task, ZLayer}

object StreamApiSpec extends DefaultRunnableSpec {

  def config = {
    import scala.concurrent.duration._
    ZLayer.succeed(Config("localhost", 0, ThrottleConfig(10, 1.seconds)))
  }

  val throttleConfig = config >>> ZLayer.fromService(_.throttle)

  // zio does not yet allow for mocks with no expectations
  // https://github.com/zio/zio/issues/3115
  // The first couple of tests have no expectations, so we resort to
  // creating a `StreamApi` instance built on the sttp stub

  type SttpStub = SttpBackendStub[Task, Nothing, Nothing]

  def streamApiStubbed(sttpBackendStubbing: SttpStub => SttpStub) = {

    val backend: TaskBackend = sttpBackendStubbing(SttpBackendStub(new RIOMonadAsyncError[Any]))
    val httpClient           = ZLayer.succeed(backend) >>> JsonHttpClient.live
    val zendeskClient        = httpClient ++ config >>> ZendeskClient.live

    zendeskClient ++ throttleConfig >>> StreamApi.live
  }

  val domain     = "domain"
  val token      = "token"
  val cursorPage = """{
    "end_of_stream": false,
    "tickets": []
  }"""

  def spec =
    suite("StreamApi")(
      testM("no race conditions should be in start - enforces max 1 stream per domain") {

        val start1 = StreamApi.startFromTime(domain, Instant.ofEpochSecond(0), token)
        val start2 = StreamApi.startFromTime(domain, Instant.ofEpochSecond(1), token)

        assertM(start1.zipPar(start2).run)(fails(equalTo(StreamApiError.AlreadyStarted(domain))))
      }.provideLayer(streamApiStubbed(identity)) @@ TestAspect.nonFlaky,
      //
      testM("no race conditions should be in stop") {
        val streamApi = streamApiStubbed(_.whenAnyRequest.thenRespond(cursorPage))

        val start =
          for {
            stream <- StreamApi.startFromTime(domain, Instant.ofEpochSecond(0), token)
            // a stream must be started to be consumed, before stop can complete
            _ <- stream.runDrain.fork
          } yield ()
        val stop1 = StreamApi.stop(domain)
        val stop2 = StreamApi.stop(domain)

        val result = (start *> stop1.zipPar(stop2)).provideLayer(streamApi ++ TestClock.default)

        assertM(result.run)(fails(equalTo(StreamApiError.AlreadyStopped(domain))))
      } @@ TestAspect.nonFlaky,
      //
      testM("stop should allow starting a stream again") {
        val streamApi = streamApiStubbed(_.whenAnyRequest.thenRespond(cursorPage))

        val domain = "domain"
        val start1 =
          for {
            stream <- StreamApi.startFromTime(domain, Instant.ofEpochSecond(0), token)
            // a stream must be started to be consumed, before stop can complete
            _ <- stream.runDrain.fork
          } yield ()
        val stop   = StreamApi.stop(domain)
        val start2 = StreamApi.startFromTime(domain, Instant.ofEpochSecond(1), token)

        val result = (start1 *> stop *> start2).provideLayer(streamApi ++ TestClock.default)

        result.map(_ => assertCompletes)
      } @@ TestAspect.nonFlaky
    )

}
