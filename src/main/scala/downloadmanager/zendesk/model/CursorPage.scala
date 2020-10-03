package downloadmanager.zendesk.model

final case class CursorPage(
    after_cursor: Option[String],
    end_of_stream: Boolean,
    tickets: List[Ticket]
)
