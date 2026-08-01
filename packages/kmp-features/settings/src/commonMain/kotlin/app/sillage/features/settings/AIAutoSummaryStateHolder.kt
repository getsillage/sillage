package app.sillage.features.settings

data class AIAutoSummaryContext(
    val appMode: String,
    val clientContextGeneration: Long,
    val anotherMutationInProgress: Boolean,
)

data class AIAutoSummaryRequest(
    val requestId: Long,
    val previousValue: Boolean,
    val targetValue: Boolean,
    val appMode: String,
    val clientContextGeneration: Long,
)

/** Owns optimistic automatic-summary preference mutation and rollback. */
data class AIAutoSummaryStateHolder(
    val enabled: Boolean = false,
    val saving: Boolean = false,
    val requestId: Long = 0,
) {
    fun nextRequest(
        targetValue: Boolean,
        context: AIAutoSummaryContext,
    ): AIAutoSummaryRequest? {
        if (context.anotherMutationInProgress || saving || targetValue == enabled) {
            return null
        }
        return AIAutoSummaryRequest(
            requestId = requestId + 1,
            previousValue = enabled,
            targetValue = targetValue,
            appMode = context.appMode,
            clientContextGeneration = context.clientContextGeneration,
        )
    }

    fun begin(
        request: AIAutoSummaryRequest,
        context: AIAutoSummaryContext,
    ): AIAutoSummaryStateHolder? {
        if (nextRequest(request.targetValue, context) != request) {
            return null
        }
        return copy(
            enabled = request.targetValue,
            saving = true,
            requestId = request.requestId,
        )
    }

    fun canApply(
        request: AIAutoSummaryRequest,
        context: AIAutoSummaryContext,
    ): Boolean {
        return saving &&
            requestId == request.requestId &&
            context.appMode == request.appMode &&
            context.clientContextGeneration == request.clientContextGeneration
    }

    fun complete(
        request: AIAutoSummaryRequest,
        savedValue: Boolean,
        context: AIAutoSummaryContext,
    ): AIAutoSummaryStateHolder? {
        if (!canApply(request, context)) {
            return null
        }
        return copy(enabled = savedValue, saving = false)
    }

    fun fail(
        request: AIAutoSummaryRequest,
        context: AIAutoSummaryContext,
    ): AIAutoSummaryStateHolder? {
        if (!canApply(request, context)) {
            return null
        }
        return copy(enabled = request.previousValue, saving = false)
    }

    fun replace(enabled: Boolean): AIAutoSummaryStateHolder = copy(
        enabled = enabled,
        saving = false,
    )

    fun invalidate(): AIAutoSummaryStateHolder = copy(
        saving = false,
        requestId = requestId + 1,
    )
}
