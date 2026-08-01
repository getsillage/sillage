package app.sillage.features.ask

import app.sillage.core.domain.ask.AskConversation
import app.sillage.core.domain.ask.AskMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class AskConversationStateHolderTest {
    @Test
    fun selectionRejectsMessagesFromAnotherConversation() {
        assertFailsWith<IllegalArgumentException> {
            AskConversationStateHolder().select(
                conversationId = "ask-1",
                headMessageId = null,
                messages = listOf(message("message-2", "ask-2")),
            )
        }
    }

    @Test
    fun lateSnapshotCannotReplaceAnotherSelection() {
        val selected = AskConversationStateHolder().select(
            conversationId = "ask-2",
            headMessageId = "message-2",
            messages = listOf(message("message-2", "ask-2")),
        )

        val updated = selected.replaceSnapshot(
            conversationId = "ask-1",
            conversations = listOf(conversation("ask-1", "message-1")),
            headMessageId = "message-1",
            messages = listOf(message("message-1", "ask-1")),
        )

        assertSame(selected, updated)
    }

    @Test
    fun movingHeadUpdatesSelectionAndConversationMetadata() {
        val state = AskConversationStateHolder(
            conversations = listOf(conversation("ask-1", "message-1")),
            activeConversationId = "ask-1",
            headMessageId = "message-1",
            messages = listOf(
                message("message-1", "ask-1"),
                message("message-2", "ask-1"),
            ),
        )

        val updated = state.moveHead("ask-1", "message-2")

        assertEquals("message-2", updated.headMessageId)
        assertEquals("message-2", updated.conversations.single().headMessageId)
    }

    @Test
    fun activatingConversationClearsMessagesFromPreviousSelection() {
        val state = AskConversationStateHolder(
            conversations = listOf(conversation("ask-1", "message-1")),
            activeConversationId = "ask-1",
            headMessageId = "message-1",
            messages = listOf(message("message-1", "ask-1")),
        )

        val updated = state.activate(conversation("ask-2", null))

        assertEquals("ask-2", updated.activeConversationId)
        assertEquals(emptyList(), updated.messages)
        assertEquals(listOf("ask-2", "ask-1"), updated.conversations.map { it.id })
    }

    private fun conversation(id: String, headMessageId: String?) = AskConversation(
        id = id,
        title = id,
        status = "active",
        contextScope = "recent_30_days",
        headMessageId = headMessageId,
        pinnedAt = null,
        archivedAt = null,
        createdAt = "2026-08-01T00:00:00Z",
        updatedAt = "2026-08-01T00:00:00Z",
        deletedAt = null,
    )

    private fun message(id: String, conversationId: String) = AskMessage(
        id = id,
        conversationId = conversationId,
        role = "assistant",
        content = id,
        parentId = null,
        forkOfId = null,
        status = "complete",
        sourceRefs = emptyList(),
        model = "model",
        promptVersion = "v1",
        createdAt = "2026-08-01T00:00:00Z",
        updatedAt = "2026-08-01T00:00:00Z",
        deletedAt = null,
    )
}
