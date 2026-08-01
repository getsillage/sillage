package app.sillage.features.ask

import app.sillage.core.domain.ask.AskMessage

data class AskStreamContext(
    val screenSessionId: Long,
    val conversationId: String,
    val appMode: String,
    val clientContextGeneration: Long,
    val anotherRequestInProgress: Boolean,
)

data class AskStreamRequest(
    val requestId: Long,
    val screenSessionId: Long,
    val conversationId: String,
    val appMode: String,
    val clientContextGeneration: Long,
)

/** Owns Ask answer-generation request identity and transient stream presentation. */
data class AskStreamStateHolder(
    val sending: Boolean = false,
    val streaming: Boolean = false,
    val requestId: Long = 0,
    val completionEventId: Long = 0,
    val regeneratingMessageId: String = "",
    val liveUser: AskMessage? = null,
    val liveAnswer: String = "",
) {
    fun nextRequest(context: AskStreamContext): AskStreamRequest? {
        if (context.anotherRequestInProgress || sending) {
            return null
        }
        return AskStreamRequest(
            requestId = requestId + 1,
            screenSessionId = context.screenSessionId,
            conversationId = context.conversationId,
            appMode = context.appMode,
            clientContextGeneration = context.clientContextGeneration,
        )
    }

    fun begin(
        request: AskStreamRequest,
        context: AskStreamContext,
        regeneratingMessageId: String,
    ): AskStreamStateHolder? {
        if (nextRequest(context) != request) {
            return null
        }
        return copy(
            sending = true,
            streaming = false,
            requestId = request.requestId,
            regeneratingMessageId = regeneratingMessageId,
            liveUser = null,
            liveAnswer = "",
        )
    }

    fun canApply(
        request: AskStreamRequest,
        context: AskStreamContext,
    ): Boolean {
        return sending &&
            requestId == request.requestId &&
            context.screenSessionId == request.screenSessionId &&
            context.conversationId == request.conversationId &&
            context.appMode == request.appMode &&
            context.clientContextGeneration == request.clientContextGeneration
    }

    fun startStreaming(userMessage: AskMessage?): AskStreamStateHolder = copy(
        streaming = true,
        liveUser = userMessage,
        liveAnswer = "",
    )

    fun appendDelta(text: String): AskStreamStateHolder = copy(liveAnswer = liveAnswer + text)

    fun finish(answerCompleted: Boolean): AskStreamStateHolder = copy(
        sending = false,
        streaming = false,
        completionEventId = if (answerCompleted) completionEventId + 1 else completionEventId,
        regeneratingMessageId = "",
        liveUser = null,
        liveAnswer = "",
    )

    fun invalidate(): AskStreamStateHolder = copy(
        sending = false,
        streaming = false,
        requestId = requestId + 1,
        regeneratingMessageId = "",
        liveUser = null,
        liveAnswer = "",
    )

    fun clearPresentation(): AskStreamStateHolder = copy(
        streaming = false,
        regeneratingMessageId = "",
        liveUser = null,
        liveAnswer = "",
    )
}
