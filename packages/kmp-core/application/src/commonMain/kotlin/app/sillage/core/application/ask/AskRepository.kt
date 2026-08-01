package app.sillage.core.application.ask

import app.sillage.core.domain.ask.AskConversation
import app.sillage.core.domain.ask.AskMessage

interface AskRepository {
    suspend fun listConversations(): List<AskConversation>

    suspend fun listMessages(conversationId: String): List<AskMessage>

    suspend fun createConversation(contextScope: String): AskConversation

    suspend fun setHead(conversationId: String, messageId: String)
}

class ListAskConversationsUseCase(
    private val repository: AskRepository,
) {
    suspend operator fun invoke(): List<AskConversation> = repository.listConversations()
}

class ListAskMessagesUseCase(
    private val repository: AskRepository,
) {
    suspend operator fun invoke(conversationId: String): List<AskMessage> {
        require(conversationId.isNotBlank()) { "Conversation id must not be blank" }
        return repository.listMessages(conversationId)
    }
}

class CreateAskConversationUseCase(
    private val repository: AskRepository,
) {
    suspend operator fun invoke(contextScope: String): AskConversation {
        require(contextScope.isNotBlank()) { "Ask context scope must not be blank" }
        return repository.createConversation(contextScope)
    }
}

class SetAskHeadUseCase(
    private val repository: AskRepository,
) {
    suspend operator fun invoke(conversationId: String, messageId: String) {
        require(conversationId.isNotBlank()) { "Conversation id must not be blank" }
        require(messageId.isNotBlank()) { "Ask head message id must not be blank" }
        repository.setHead(conversationId, messageId)
    }
}
