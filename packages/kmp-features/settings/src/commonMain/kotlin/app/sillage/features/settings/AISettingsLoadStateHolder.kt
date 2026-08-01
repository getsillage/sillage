package app.sillage.features.settings

data class AISettingsLoadContext(
    val appMode: String,
    val clientContextGeneration: Long,
    val anotherOperationInProgress: Boolean,
)

data class AISettingsLoadRequest(
    val requestId: Long,
    val appMode: String,
    val clientContextGeneration: Long,
)

/** Owns AI-settings load, retry failure, and stale-response rejection. */
data class AISettingsLoadStateHolder(
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val requestId: Long = 0,
) {
    fun nextRequest(context: AISettingsLoadContext): AISettingsLoadRequest? {
        if (loading || context.anotherOperationInProgress) {
            return null
        }
        return AISettingsLoadRequest(
            requestId = requestId + 1,
            appMode = context.appMode,
            clientContextGeneration = context.clientContextGeneration,
        )
    }

    fun begin(
        request: AISettingsLoadRequest,
        context: AISettingsLoadContext,
    ): AISettingsLoadStateHolder? {
        if (nextRequest(context) != request) {
            return null
        }
        return copy(loading = true, errorMessage = null, requestId = request.requestId)
    }

    fun canApply(
        request: AISettingsLoadRequest,
        context: AISettingsLoadContext,
    ): Boolean {
        return loading &&
            requestId == request.requestId &&
            context.appMode == request.appMode &&
            context.clientContextGeneration == request.clientContextGeneration
    }

    fun complete(
        request: AISettingsLoadRequest,
        context: AISettingsLoadContext,
    ): AISettingsLoadStateHolder? {
        if (!canApply(request, context)) {
            return null
        }
        return copy(loading = false, errorMessage = null)
    }

    fun fail(
        request: AISettingsLoadRequest,
        message: String,
        context: AISettingsLoadContext,
    ): AISettingsLoadStateHolder? {
        if (!canApply(request, context)) {
            return null
        }
        return copy(loading = false, errorMessage = message)
    }

    fun cancel(): AISettingsLoadStateHolder = copy(
        loading = false,
        errorMessage = null,
        requestId = requestId + 1,
    )
}
