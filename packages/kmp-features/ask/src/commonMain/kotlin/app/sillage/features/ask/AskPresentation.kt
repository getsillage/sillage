package app.sillage.features.ask

import app.sillage.core.domain.ask.AskMessage
import app.sillage.core.domain.ask.AskSourceRef

/** Body used when saving an assistant answer as a record. */
fun askAnswerMemoContent(message: AskMessage): String {
    return if (message.role == "assistant") message.content.trim() else ""
}

/** Compact source chip label for Ask citations. */
fun askSourceLabel(source: AskSourceRef): String {
    return "${source.entryDate} · ${source.excerpt}"
}

/** Active (non-deleted) messages in conversation order as provided. */
fun activeAskMessages(messages: List<AskMessage>): List<AskMessage> {
    return messages.filter { it.deletedAt == null }
}
