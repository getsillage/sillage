package app.sillage.core.application.ask

import app.sillage.core.domain.ask.AskConversation
import app.sillage.core.domain.ask.AskMessage
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AskRepositoryTest {
    @Test
    fun useCasesDelegateToOneRepositoryBoundary() = runSuspend {
        val repository = CapturingRepository()

        assertEquals(repository.conversations, ListAskConversationsUseCase(repository)())
        assertEquals(repository.messages, ListAskMessagesUseCase(repository)("ask-1"))
        assertEquals(repository.conversations.single(), CreateAskConversationUseCase(repository)("recent"))
        SetAskHeadUseCase(repository)("ask-1", "message-1")

        assertEquals(
            listOf("list-conversations", "list-messages:ask-1", "create:recent", "head:ask-1:message-1"),
            repository.operations,
        )
    }

    @Test
    fun identifierAndScopeValidationStayPlatformIndependent() {
        val repository = CapturingRepository()

        assertFailsWith<IllegalArgumentException> {
            runSuspend { ListAskMessagesUseCase(repository)(" ") }
        }
        assertFailsWith<IllegalArgumentException> {
            runSuspend { CreateAskConversationUseCase(repository)("") }
        }
        assertFailsWith<IllegalArgumentException> {
            runSuspend { SetAskHeadUseCase(repository)("ask-1", "") }
        }
        assertEquals(emptyList(), repository.operations)
    }

    private class CapturingRepository : AskRepository {
        val operations = mutableListOf<String>()
        val conversations = listOf(conversation())
        val messages = listOf(message())

        override suspend fun listConversations(): List<AskConversation> {
            operations += "list-conversations"
            return conversations
        }

        override suspend fun listMessages(conversationId: String): List<AskMessage> {
            operations += "list-messages:$conversationId"
            return messages
        }

        override suspend fun createConversation(contextScope: String): AskConversation {
            operations += "create:$contextScope"
            return conversations.single()
        }

        override suspend fun setHead(conversationId: String, messageId: String) {
            operations += "head:$conversationId:$messageId"
        }
    }

    private companion object {
        fun conversation() = AskConversation(
            id = "ask-1",
            title = "Question",
            status = "active",
            contextScope = "recent",
            headMessageId = "message-1",
            pinnedAt = null,
            archivedAt = null,
            createdAt = "2026-08-01T00:00:00Z",
            updatedAt = "2026-08-01T00:00:00Z",
            deletedAt = null,
        )

        fun message() = AskMessage(
            id = "message-1",
            conversationId = "ask-1",
            role = "assistant",
            content = "Answer",
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

    private fun <T> runSuspend(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(
            object : Continuation<T> {
                override val context = EmptyCoroutineContext

                override fun resumeWith(result: Result<T>) {
                    outcome = result
                }
            },
        )
        return checkNotNull(outcome) { "Test coroutine did not complete synchronously" }.getOrThrow()
    }
}
