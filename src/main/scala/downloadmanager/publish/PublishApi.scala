package downloadmanager.publish

import cats.instances.int._
import cats.instances.string._
import cats.syntax.show._
import downloadmanager.zendesk.model.Ticket
import zio.console.Console
import zio.{Has, UIO, ZLayer}

object PublishApi {
  type PublishApi = Has[Service]

  trait Service {
    def publish(domain: String, ticket: Ticket): UIO[Unit]
  }

  val live = ZLayer.fromService((console: Console.Service) =>
    new Service {

      def publish(domain: String, ticket: Ticket) = {
        val message =
          show"domain: $domain, ticket_id: ${ticket.id}, created_at: ${ticket
            .created_at}, updated_at: ${ticket.updated_at}"

        console.putStrLn(message)
      }
    }
  )
}
