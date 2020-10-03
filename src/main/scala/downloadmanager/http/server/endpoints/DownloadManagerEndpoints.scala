package downloadmanager.http.server.endpoints

import java.time.Instant

import cats.syntax.option._
import downloadmanager.http.server.model._
import downloadmanager.streams.model.StreamState
import endpoints4s.{Invalid, algebra, generic}

trait DownloadManagerEndpoints
    extends algebra.Endpoints
    with algebra.Responses
    with algebra.StatusCodes
    with algebra.JsonEntitiesFromSchemas
    with generic.JsonSchemas {

  private val basePath = path / "streams"

  val addStream: Endpoint[(String, AddStreamReq), Either[Invalid, Unit]] = endpoint(
    post(basePath / segment[String]("domain") / "add", jsonRequest[AddStreamReq]),
    badRequest().orElse(ok(emptyResponse)),
    EndpointDocs().withSummary("Add a stream".some)
  )

  val removeStream: Endpoint[String, Either[Invalid, Unit]] = endpoint(
    delete(basePath / segment[String]("domain")),
    badRequest().orElse(ok(emptyResponse)),
    EndpointDocs().withSummary("Remove a stream".some)
  )

  val startStream: Endpoint[String, Either[Invalid, Unit]] = endpoint(
    put(basePath / segment[String]("domain") / "start", emptyRequest),
    badRequest().orElse(ok(emptyResponse)),
    EndpointDocs().withSummary("Start a stream".some)
  )

  val stopStream: Endpoint[String, Either[Invalid, Unit]] = endpoint(
    put(basePath / segment[String]("domain") / "stop", emptyRequest),
    badRequest().orElse(ok(emptyResponse)),
    EndpointDocs().withSummary("Stop a stream".some)
  )

  val listStreams: Endpoint[Unit, List[StreamState]] = endpoint(
    get(basePath),
    ok(jsonResponse[List[StreamState]]),
    EndpointDocs().withSummary("List streams".some)
  )

  implicit lazy val instantSchema: JsonSchema[Instant] =
    longJsonSchema.xmap(Instant.ofEpochSecond)(_.getEpochSecond)

  implicit lazy val addStreamReqSchema: JsonSchema[AddStreamReq] = genericJsonSchema

  implicit lazy val streamStateSchema: JsonSchema[StreamState] = genericJsonSchema

}
