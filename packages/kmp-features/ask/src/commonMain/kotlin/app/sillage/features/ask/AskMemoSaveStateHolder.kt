package app.sillage.features.ask

import app.sillage.core.domain.ask.AskMessage

data class AskMemoSaveContext(
    val destinationAvailable: Boolean,
    val anotherRequestInProgress: Boolean,
    val screenSessionId: Long,
    val conversationId: String,
    val headMessageId: String?,
    val messages: List<AskMessage>,
    val appMode: String,
    val clientContextGeneration: Long,
)

data class AskMemoSaveRequest(
    val requestId: Long,
    val screenSessionId: Long,
    val conversationId: String,
    val headMessageId: String?,
    val messageId: String,
    val sourceMessageContent: String,
    val memoContent: String,
    val appMode: String,
    val clientContextGeneration: Long,
)

/** Owns the single-flight request that saves an Ask answer as a record. */
data class AskMemoSaveStateHolder(
    val requestId: Long = 0,
    val savingMessageId: String = "",
) {
    fun nextRequest(
        message: AskMessage,
        memoContent: String,
        context: AskMemoSaveContext,
    ): AskMemoSaveRequest? {
        val currentMessage = context.messages.find { it.id == message.id }
        if (
            !context.destinationAvailable ||
            context.anotherRequestInProgress ||
            savingMessageId.isNotBlank() ||
            context.conversationId.isBlank() ||
            message.role != "assistant" ||
            message.conversationId != context.conversationId ||
            currentMessage?.content != message.content ||
            memoContent.isBlank()
        ) {
            return null
        }
        return AskMemoSaveRequest(
            requestId = requestId + 1,
            screenSessionId = context.screenSessionId,
            conversationId = context.conversationId,
            headMessageId = context.headMessageId,
            messageId = message.id,
            sourceMessageContent = message.content,
            memoContent = memoContent,
            appMode = context.appMode,
            clientContextGeneration = context.clientContextGeneration,
        )
    }

    fun begin(
        request: AskMemoSaveRequest,
        context: AskMemoSaveContext,
    ): AskMemoSaveStateHolder? {
        val message = context.messages.find { it.id == request.messageId } ?: return null
        if (nextRequest(message, request.memoContent, context) != request) {
            return null
        }
        return copy(
            requestId = request.requestId,
            savingMessageId = request.messageId,
        )
    }

    fun canApply(
        request: AskMemoSaveRequest,
        context: AskMemoSaveContext,
    ): Boolean {
        return context.destinationAvailable &&
            requestId == request.requestId &&
            savingMessageId == request.messageId &&
            context.screenSessionId == request.screenSessionId &&
            context.conversationId == request.conversationId &&
            context.headMessageId == request.headMessageId &&
            context.appMode == request.appMode &&
            context.clientContextGeneration == request.clientContextGeneration &&
            context.messages.any {
                it.id == request.messageId && it.content == request.sourceMessageContent
            }
    }

    fun finish(request: AskMemoSaveRequest): AskMemoSaveStateHolder? {
        if (requestId != request.requestId || savingMessageId != request.messageId) {
            return null
        }
        return copy(savingMessageId = "")
    }

    fun invalidate(): AskMemoSaveStateHolder = copy(
        requestId = requestId + 1,
        savingMessageId = "",
    )
}
