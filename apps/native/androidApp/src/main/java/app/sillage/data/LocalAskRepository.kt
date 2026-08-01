package app.sillage.data

import app.sillage.core.application.ask.AskRepository
import app.sillage.core.domain.ask.AskConversation
import app.sillage.core.domain.ask.AskMessage

class LocalAskRepository(
    private val localDataStore: LocalDataStore,
) : AskRepository {
    override suspend fun listConversations(): List<AskConversation> {
        return localDataStore.listAskConversations()
    }

    override suspend fun listMessages(conversationId: String): List<AskMessage> {
        return localDataStore.listAskMessages(conversationId)
    }

    override suspend fun createConversation(contextScope: String): AskConversation {
        return localDataStore.createAskConversation(contextScope)
    }

    override suspend fun setHead(conversationId: String, messageId: String) {
        localDataStore.setAskHead(conversationId, messageId)
    }
}
