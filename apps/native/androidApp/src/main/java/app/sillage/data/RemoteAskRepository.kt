package app.sillage.data

import app.sillage.core.application.ask.AskRepository
import app.sillage.core.domain.ask.AskConversation
import app.sillage.core.domain.ask.AskMessage

class RemoteAskRepository(
    private val api: SillageApi,
) : AskRepository {
    override suspend fun listConversations(): List<AskConversation> {
        return api.listAskConversations()
    }

    override suspend fun listMessages(conversationId: String): List<AskMessage> {
        return api.listAskMessages(conversationId)
    }

    override suspend fun createConversation(contextScope: String): AskConversation {
        return api.createAskConversation(contextScope)
    }

    override suspend fun setHead(conversationId: String, messageId: String) {
        api.setAskHead(conversationId, messageId)
    }
}
