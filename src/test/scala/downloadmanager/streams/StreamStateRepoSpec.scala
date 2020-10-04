package downloadmanager.streams

import downloadmanager.streams.model.StreamStateRepoError
import downloadmanager.streams.repo.StreamStateRepo
import downloadmanager.testcases._
import zio.test.Assertion._
import zio.test._

object StreamStateRepoSpec extends DefaultRunnableSpec {

  val streamRepo = StreamStateRepo.live

  def spec =
    suite("StreamStateRepo")(
      testM("should add and get") {
        import TestCase1._

        val result =
          for {
            _           <- StreamStateRepo.add(streamState1)
            streamState <- StreamStateRepo.get(streamState1.domain)
          } yield streamState

        assertM(result)(equalTo(streamState1))
      },
      //
      testM("should remove") {
        import TestCase1._

        val result =
          for {
            _           <- StreamStateRepo.add(streamState1)
            _           <- StreamStateRepo.remove(streamState1.domain)
            streamState <- StreamStateRepo.get(streamState1.domain)
          } yield streamState

        assertM(result.run)(fails(equalTo(StreamStateRepoError.NotFound(streamState1.domain))))
      },
      //
      testM("should list") {
        import TestCase1._

        val result =
          for {
            _    <- StreamStateRepo.add(streamState1)
            list <- StreamStateRepo.list
          } yield list

        assertM(result)(equalTo(List(streamState1)))
      }
    ).provideLayer(streamRepo)

}
