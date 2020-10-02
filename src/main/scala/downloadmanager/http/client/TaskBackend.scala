package downloadmanager.http.client

import cats.effect.Blocker
import org.http4s.client.blaze.BlazeClientBuilder
import sttp.client.http4s.Http4sBackend
import sttp.client.{NothingT, SttpBackend}
import zio.blocking.Blocking
import zio.interop.catz._
import zio.{RManaged, Runtime, Task, ZIO}

object TaskBackend {
  type TaskBackend = SttpBackend[Task, Nothing, NothingT]

  val live: RManaged[Blocking, TaskBackend] = ZIO
    .runtime[Blocking]
    .toManaged_
    .flatMap { implicit r =>
      val defaultEC  = Runtime.default.platform.executor.asEC
      val blockingEC = r.environment.get.blockingExecutor.asEC
      Http4sBackend
        .usingClientBuilder[Task](
          BlazeClientBuilder(defaultEC),
          Blocker.liftExecutionContext(blockingEC)
        )
        .toManaged
    }

}
