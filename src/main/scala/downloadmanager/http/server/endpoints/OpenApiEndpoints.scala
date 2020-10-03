package downloadmanager.http.server.endpoints

import endpoints4s.openapi
import endpoints4s.openapi.model.{Info, OpenApi}

object OpenApiEndpoints
    extends DownloadManagerEndpoints
    with openapi.Endpoints
    with openapi.JsonEntitiesFromSchemas
    with openapi.StatusCodes {
  val pack: Package = getClass.getPackage

  val api: OpenApi =
    openApi(Info(title = pack.getImplementationTitle, version = pack.getImplementationVersion))(
      addStream,
      removeStream,
      startStream,
      stopStream,
      listStreams
    )

  val json: String = OpenApi.stringEncoder.encode(api)

}
