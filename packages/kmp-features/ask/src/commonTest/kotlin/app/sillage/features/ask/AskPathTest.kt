package app.sillage.features.ask

import app.sillage.core.domain.ask.AskMessage
import kotlin.test.Test
import kotlin.test.assertEquals

class AskPathTest {
    @Test
    fun activePathFollowsSelectedHeadAndExposesAnswerVariants() {
        val user = message(id = "u1", role = "user", createdAt = "2026-01-01T00:00:00Z")
        val first = message(
            id = "a1",
            role = "assistant",
            parentId = "u1",
            createdAt = "2026-01-01T00:00:01Z",
        )
        val second = message(
            id = "a2",
            role = "assistant",
            parentId = "u1",
            forkOfId = "a1",
            createdAt = "2026-01-01T00:00:02Z",
        )

        val path = buildAskActivePath(listOf(user, first, second), "a1")

        assertEquals(listOf("u1", "a1"), path.map { it.message.id })
        assertEquals(listOf("a1", "a2"), path.last().variants.map { it.id })
        assertEquals(0, path.last().index)
    }

    @Test
    fun activePathIgnoresDeletedMessagesWhenChoosingFallbackHead() {
        val user = message(id = "u1", role = "user", createdAt = "2026-01-01T00:00:00Z")
        val active = message(
            id = "a1",
            role = "assistant",
            parentId = "u1",
            createdAt = "2026-01-01T00:00:01Z",
        )
        val deleted = message(
            id = "a2",
            role = "assistant",
            parentId = "u1",
            createdAt = "2026-01-01T00:00:02Z",
            deletedAt = "2026-01-01T00:00:03Z",
        )

        val path = buildAskActivePath(listOf(user, active, deleted), headId = null)

        assertEquals(listOf("u1", "a1"), path.map { it.message.id })
        assertEquals(listOf("a1"), path.last().variants.map { it.id })
    }

    @Test
    fun branchLeafDescendsThroughNewestChildren() {
        val user = message(id = "u1", role = "user", createdAt = "2026-01-01T00:00:00Z")
        val answer = message(
            id = "a1",
            role = "assistant",
            parentId = "u1",
            createdAt = "2026-01-01T00:00:01Z",
        )
        val followUp = message(
            id = "u2",
            role = "user",
            parentId = "a1",
            createdAt = "2026-01-01T00:00:02Z",
        )

        assertEquals(
            "u2",
            askBranchLeafId(listOf(user, answer, followUp), "a1"),
        )
    }

    @Test
    fun lastAssistantIdSkipsTrailingUserMessage() {
        val user = message(id = "u1", role = "user", createdAt = "2026-01-01T00:00:00Z")
        val answer = message(
            id = "a1",
            role = "assistant",
            parentId = "u1",
            createdAt = "2026-01-01T00:00:01Z",
        )
        val followUp = message(
            id = "u2",
            role = "user",
            parentId = "a1",
            createdAt = "2026-01-01T00:00:02Z",
        )

        val path = buildAskActivePath(listOf(user, answer, followUp), "u2")

        assertEquals("a1", lastAssistantMessageId(path))
    }

    private fun message(
        id: String,
        role: String,
        createdAt: String,
        parentId: String? = null,
        forkOfId: String? = null,
        deletedAt: String? = null,
    ): AskMessage = AskMessage(
        id = id,
        conversationId = "conversation-id",
        role = role,
        content = id,
        parentId = parentId,
        forkOfId = forkOfId,
        status = "complete",
        sourceRefs = emptyList(),
        model = "model",
        promptVersion = "v1",
        createdAt = createdAt,
        updatedAt = createdAt,
        deletedAt = deletedAt,
    )
}
