package app.sillage.features.ask

data class AskSourceNavigationContext(
    val destinationKey: String,
    val destinationAvailable: Boolean,
    val historyKeys: List<String>,
    val anotherRequestInProgress: Boolean,
    val screenSessionId: Long,
    val conversationId: String,
    val appMode: String,
    val clientContextGeneration: Long,
)

data class AskSourceNavigationRequest(
    val requestId: Long,
    val screenSessionId: Long,
    val conversationId: String,
    val memoId: String,
    val appMode: String,
    val clientContextGeneration: Long,
    val originDestinationKey: String,
    val originHistoryKeys: List<String>,
) {
    fun destinationHistoryKeys(): List<String> = originHistoryKeys + originDestinationKey
}

/** Owns single-flight identity for opening an Ask source record. */
data class AskSourceNavigationStateHolder(
    val requestId: Long = 0,
    val loading: Boolean = false,
) {
    fun nextRequest(
        memoId: String,
        context: AskSourceNavigationContext,
    ): AskSourceNavigationRequest? {
        if (
            !context.destinationAvailable ||
            memoId.isBlank() ||
            context.anotherRequestInProgress ||
            loading
        ) {
            return null
        }
        return AskSourceNavigationRequest(
            requestId = requestId + 1,
            screenSessionId = context.screenSessionId,
            conversationId = context.conversationId,
            memoId = memoId,
            appMode = context.appMode,
            clientContextGeneration = context.clientContextGeneration,
            originDestinationKey = context.destinationKey,
            originHistoryKeys = context.historyKeys.toList(),
        )
    }

    fun begin(
        request: AskSourceNavigationRequest,
        context: AskSourceNavigationContext,
    ): AskSourceNavigationStateHolder? {
        if (nextRequest(request.memoId, context) != request) {
            return null
        }
        return copy(requestId = request.requestId, loading = true)
    }

    fun canApply(
        request: AskSourceNavigationRequest,
        context: AskSourceNavigationContext,
    ): Boolean {
        return loading &&
            requestId == request.requestId &&
            context.screenSessionId == request.screenSessionId &&
            context.appMode == request.appMode &&
            context.clientContextGeneration == request.clientContextGeneration &&
            context.destinationKey == request.originDestinationKey &&
            context.historyKeys == request.originHistoryKeys &&
            context.conversationId == request.conversationId
    }

    fun finish(request: AskSourceNavigationRequest): AskSourceNavigationStateHolder? {
        if (requestId != request.requestId) {
            return null
        }
        return copy(loading = false)
    }

    fun invalidate(): AskSourceNavigationStateHolder = copy(
        requestId = requestId + 1,
        loading = false,
    )
}
