package app.sillage.features.ask

import app.sillage.core.domain.ask.AskConversation
import app.sillage.core.domain.ask.AskMessage

/** Immutable Ask conversation collection and the currently selected branch. */
data class AskConversationStateHolder(
    val conversations: List<AskConversation> = emptyList(),
    val activeConversationId: String = "",
    val headMessageId: String? = null,
    val messages: List<AskMessage> = emptyList(),
) {
    fun clear(): AskConversationStateHolder = AskConversationStateHolder()

    fun deselect(): AskConversationStateHolder = copy(
        activeConversationId = "",
        headMessageId = null,
        messages = emptyList(),
    )

    fun replaceConversations(
        conversations: List<AskConversation>,
    ): AskConversationStateHolder = copy(conversations = conversations)

    fun select(
        conversationId: String,
        headMessageId: String?,
        messages: List<AskMessage>,
    ): AskConversationStateHolder {
        require(conversationId.isNotBlank()) { "Conversation id must not be blank" }
        require(messages.all { it.conversationId == conversationId }) {
            "Ask messages must belong to the selected conversation"
        }
        return copy(
            activeConversationId = conversationId,
            headMessageId = headMessageId,
            messages = messages,
        )
    }

    fun activate(conversation: AskConversation): AskConversationStateHolder {
        require(conversation.id.isNotBlank()) { "Conversation id must not be blank" }
        return copy(
            conversations = listOf(conversation) +
                conversations.filterNot { it.id == conversation.id },
            activeConversationId = conversation.id,
            headMessageId = conversation.headMessageId,
            messages = emptyList(),
        )
    }

    fun replaceSnapshot(
        conversationId: String,
        conversations: List<AskConversation>,
        headMessageId: String?,
        messages: List<AskMessage>,
    ): AskConversationStateHolder {
        if (activeConversationId != conversationId) {
            return this
        }
        require(messages.all { it.conversationId == conversationId }) {
            "Ask messages must belong to the selected conversation"
        }
        return copy(
            conversations = conversations,
            headMessageId = headMessageId,
            messages = messages,
        )
    }

    fun moveHead(
        conversationId: String,
        headMessageId: String?,
    ): AskConversationStateHolder {
        if (activeConversationId != conversationId) {
            return this
        }
        return copy(
            conversations = conversations.map { conversation ->
                if (conversation.id == conversationId) {
                    conversation.copy(headMessageId = headMessageId)
                } else {
                    conversation
                }
            },
            headMessageId = headMessageId,
        )
    }
}
