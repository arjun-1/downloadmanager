package downloadmanager.zendesk

import java.time.Instant

import cats.instances.string._
import cats.syntax.show._
import downloadmanager.AppConfig.Config
import downloadmanager.http.client.JsonHttpClient
import io.circe.generic.auto._
import sttp.model.Uri
import zio.macros.accessible
import zio.{Has, IO, ZIO, ZLayer}

import model._

@accessible
object ZendeskClient {

  type ZendeskClient = Has[Service]

  trait Service {

    def getTicketsFromStartTime(
        domain: String,
        startTime: Instant,
        token: String
    ): IO[ZendeskClientError, CursorPage]

    def getTicketsFromCursor(
        domain: String,
        cursor: String,
        token: String
    ): IO[ZendeskClientError, CursorPage]
  }

  val live = ZLayer.fromServices[JsonHttpClient.Service, Config, Service]((client, config) =>
    new Service {

      def getTicketsFromCursor(domain: String, cursor: String, token: String) = {
        val url = show"https://$domain.${config.zendeskCursorUrl}"
        val uri = Uri.parse(url).map(_.param("cursor", cursor))

        for {
          uri <-
            ZIO.fromEither(uri).mapError[ZendeskClientError](ZendeskClientError.InvalidUrl(url, _))
          page <- client.get[CursorPage](uri, token).mapError(ZendeskClientError.ClientError)
        } yield page

      }

      def getTicketsFromStartTime(domain: String, startTime: Instant, token: String) = {
        val url = show"https://$domain.${config.zendeskCursorUrl}"
        val uri = Uri.parse(url).map(_.param("start_time", startTime.getEpochSecond.toString))

        for {
          uri <-
            ZIO.fromEither(uri).mapError[ZendeskClientError](ZendeskClientError.InvalidUrl(url, _))

          page <- client.get[CursorPage](uri, token).mapError(ZendeskClientError.ClientError)
        } yield page
      }
    }
  )
}
