package app.sillage.features.ask

import app.sillage.core.domain.ask.AskMessage
import app.sillage.core.domain.ask.AskSourceRef
import kotlin.test.Test
import kotlin.test.assertEquals

class AskPresentationTest {
    @Test
    fun askAnswerMemoContentUsesAssistantBodiesOnly() {
        assertEquals(
            "回答",
            askAnswerMemoContent(message(role = "assistant", content = " 回答 ")),
        )
        assertEquals(
            "",
            askAnswerMemoContent(message(role = "user", content = "问题")),
        )
    }

    @Test
    fun askSourceLabelJoinsDateAndExcerpt() {
        assertEquals(
            "2026-08-01 · 摘要",
            askSourceLabel(
                AskSourceRef(
                    memoId = "m1",
                    entryDate = "2026-08-01",
                    excerpt = "摘要",
                    rank = 1,
                ),
            ),
        )
    }

    @Test
    fun activeAskMessagesDropsDeletedRows() {
        val active = message(id = "a", deletedAt = null)
        val deleted = message(id = "b", deletedAt = "2026-08-01T00:00:00Z")
        assertEquals(listOf(active), activeAskMessages(listOf(active, deleted)))
    }

    private fun message(
        id: String = "m1",
        role: String = "assistant",
        content: String = id,
        deletedAt: String? = null,
    ): AskMessage {
        return AskMessage(
            id = id,
            conversationId = "c1",
            role = role,
            content = content,
            parentId = null,
            forkOfId = null,
            status = "complete",
            sourceRefs = emptyList(),
            model = "model",
            promptVersion = "v1",
            createdAt = "2026-08-01T00:00:00Z",
            updatedAt = "2026-08-01T00:00:00Z",
            deletedAt = deletedAt,
        )
    }
}
