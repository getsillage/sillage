package app.sillage.features.ask

data class AskVariantContext(
    val destinationAvailable: Boolean,
    val screenSessionId: Long,
    val conversationId: String,
    val appMode: String,
    val clientContextGeneration: Long,
    val anotherRequestInProgress: Boolean,
)

data class AskVariantRequest(
    val requestId: Long,
    val screenSessionId: Long,
    val conversationId: String,
    val appMode: String,
    val clientContextGeneration: Long,
)

/** Owns single-flight identity for selecting an Ask branch variant. */
data class AskVariantStateHolder(
    val requestId: Long = 0,
    val loading: Boolean = false,
) {
    fun nextRequest(context: AskVariantContext): AskVariantRequest? {
        if (
            !context.destinationAvailable ||
            context.conversationId.isBlank() ||
            context.anotherRequestInProgress ||
            loading
        ) {
            return null
        }
        return AskVariantRequest(
            requestId = requestId + 1,
            screenSessionId = context.screenSessionId,
            conversationId = context.conversationId,
            appMode = context.appMode,
            clientContextGeneration = context.clientContextGeneration,
        )
    }

    fun begin(
        request: AskVariantRequest,
        context: AskVariantContext,
    ): AskVariantStateHolder? {
        if (nextRequest(context) != request) {
            return null
        }
        return copy(requestId = request.requestId, loading = true)
    }

    fun canApply(
        request: AskVariantRequest,
        context: AskVariantContext,
    ): Boolean {
        return context.destinationAvailable &&
            loading &&
            requestId == request.requestId &&
            context.screenSessionId == request.screenSessionId &&
            context.conversationId == request.conversationId &&
            context.appMode == request.appMode &&
            context.clientContextGeneration == request.clientContextGeneration
    }

    fun finish(
        request: AskVariantRequest,
        context: AskVariantContext,
    ): AskVariantStateHolder? {
        if (!canApply(request, context)) {
            return null
        }
        return copy(loading = false)
    }

    fun invalidate(): AskVariantStateHolder = copy(
        requestId = requestId + 1,
        loading = false,
    )
}
