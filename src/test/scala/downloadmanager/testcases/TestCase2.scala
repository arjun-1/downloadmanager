package downloadmanager.testcases

import java.time.Instant

import downloadmanager.mocks._
import downloadmanager.publish.PublishApi.PublishApi
import downloadmanager.streams.StreamApi.StreamApi
import downloadmanager.streams.model.StreamState
import downloadmanager.zendesk.model.{CursorPage, Ticket}
import zio.ULayer
import zio.stream.ZStream
import zio.test.Assertion._
import zio.test.mock.Expectation._

object TestCase2 {
  // test case describes starting a stream from cursor

  val domain  = "domain"
  val instant = Instant.MIN
  val token   = "token"

  val ticket1 = Ticket(0, "created_at", "updated_at")

  val cursor = "cursor"

  val page1  = CursorPage(Some(cursor), false, List(ticket1))
  val stream = ZStream(page1)

  val publishApi: ULayer[PublishApi] = (PublishApiMock.Publish(equalTo((domain, ticket1)), unit))

  val streamApi: ULayer[StreamApi] =
    (StreamApiMock.StartFromCursor(equalTo((domain, cursor, token)), value(stream)))

  val streamState1 = StreamState(domain, token, instant, Some(cursor), false, 0)

  val streamState2 = StreamState(domain, token, instant, Some(cursor), false, 1)
}
