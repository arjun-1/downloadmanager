package downloadmanager

import downloadmanager.streams.model.StreamState
import downloadmanager.streams.repo.StreamStateRepo
import downloadmanager.testcases._
import zio.duration._
import zio.test.Assertion._
import zio.test.environment.TestClock
import zio.test.{DefaultRunnableSpec, _}

object DownloadManagerApiSpec extends DefaultRunnableSpec {

  val streamRepo = StreamStateRepo.live

  def spec =
    suite("DownloadManagerApi")(
      testM("elements of added stream should be published") {
        import TestCase1._
        val downloadManagerApi = streamRepo ++ streamApi ++ publishApi ++ TestClock.default >>>
          DownloadManagerApi.live

        val result = DownloadManagerApi
          .addStream(domain, instant, token)
          .provideLayer(downloadManagerApi)

        result.map(_ => assertCompletes)
      },
      //
      testM("stream should persist its state") {
        import TestCase1._
        val downloadManagerApi = streamRepo ++ streamApi ++ publishApi ++ TestClock.default >>>
          DownloadManagerApi.live

        val result =
          for {
            _     <- DownloadManagerApi.addStream(domain, instant, token).ignore
            _     <- TestClock.adjust(1.seconds)
            state <- StreamStateRepo.get(domain)
          } yield state

        assertM(result.provideLayer(downloadManagerApi ++ streamRepo ++ TestClock.default))(
          equalTo(StreamState(domain, token, instant, None, false, 1))
        )
      },
      //
      testM("a stopped stream should be resumed from its latest offset - time") {
        import TestCase1._
        val downloadManagerApi = streamRepo ++ streamApi ++ publishApi ++ TestClock.default >>>
          DownloadManagerApi.live

        val result =
          for {
            _ <- StreamStateRepo.add(streamState1).ignore
            _ <- DownloadManagerApi.startStream(domain)
          } yield ()

        result.provideLayer(downloadManagerApi ++ streamRepo).map(_ => assertCompletes)

      },
      //
      testM("a stopped stream should be resumed from its latest offset - cursor") {
        import TestCase2._
        val downloadManagerApi = streamRepo ++ streamApi ++ publishApi ++ TestClock.default >>>
          DownloadManagerApi.live

        val result =
          for {
            _ <- StreamStateRepo.add(streamState1).ignore
            _ <- DownloadManagerApi.startStream(domain)
          } yield ()

        result.provideLayer(downloadManagerApi ++ streamRepo).map(_ => assertCompletes)

      }
    )

}
