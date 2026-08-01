package app.sillage.features.settings

data class AIProfileDiagnosticsContext(
    val appMode: String,
    val clientContextGeneration: Long,
    val anotherOperationInProgress: Boolean,
)

data class AIProfileTestRequest(
    val requestId: Long,
    val profileKey: String,
    val profile: AIProfileDraft,
    val appMode: String,
    val clientContextGeneration: Long,
)

data class AIProfileModelsRequest(
    val requestId: Long,
    val profileKey: String,
    val profile: AIProfileDraft,
    val appMode: String,
    val clientContextGeneration: Long,
)

/** Owns profile diagnostics progress, results, and stale-callback rejection. */
data class AIProfileDiagnosticsStateHolder(
    val testingProfileKey: String = "",
    val loadingModelsProfileKey: String = "",
    val testResults: Map<String, String> = emptyMap(),
    val modelResults: Map<String, List<String>> = emptyMap(),
    val testRequestId: Long = 0,
    val modelsRequestId: Long = 0,
) {
    val busy: Boolean get() = testingProfileKey.isNotBlank() || loadingModelsProfileKey.isNotBlank()

    fun nextTestRequest(
        profile: AIProfileDraft,
        index: Int,
        context: AIProfileDiagnosticsContext,
    ): AIProfileTestRequest? {
        if (busy || context.anotherOperationInProgress) {
            return null
        }
        return AIProfileTestRequest(
            requestId = testRequestId + 1,
            profileKey = profile.editorKey(index),
            profile = profile,
            appMode = context.appMode,
            clientContextGeneration = context.clientContextGeneration,
        )
    }

    fun beginTest(
        request: AIProfileTestRequest,
        profiles: List<AIProfileDraft>,
        context: AIProfileDiagnosticsContext,
    ): AIProfileDiagnosticsStateHolder? {
        val profileIndex = profiles.indexOfKey(request.profileKey)
        val currentProfile = profiles.getOrNull(profileIndex)
        if (currentProfile == null || nextTestRequest(currentProfile, profileIndex, context) != request) {
            return null
        }
        return copy(testingProfileKey = request.profileKey, testRequestId = request.requestId)
    }

    fun canApplyTest(
        request: AIProfileTestRequest,
        profiles: List<AIProfileDraft>,
        context: AIProfileDiagnosticsContext,
    ): Boolean {
        return testingProfileKey == request.profileKey &&
            testRequestId == request.requestId &&
            profiles.profileForKey(request.profileKey) == request.profile &&
            context.appMode == request.appMode &&
            context.clientContextGeneration == request.clientContextGeneration
    }

    fun completeTest(
        request: AIProfileTestRequest,
        message: String,
        profiles: List<AIProfileDraft>,
        context: AIProfileDiagnosticsContext,
    ): AIProfileDiagnosticsStateHolder? {
        if (!canApplyTest(request, profiles, context)) {
            return null
        }
        return copy(
            testingProfileKey = "",
            testResults = testResults + (request.profileKey to message),
        )
    }

    fun nextModelsRequest(
        profile: AIProfileDraft,
        index: Int,
        context: AIProfileDiagnosticsContext,
    ): AIProfileModelsRequest? {
        if (busy || context.anotherOperationInProgress) {
            return null
        }
        return AIProfileModelsRequest(
            requestId = modelsRequestId + 1,
            profileKey = profile.editorKey(index),
            profile = profile,
            appMode = context.appMode,
            clientContextGeneration = context.clientContextGeneration,
        )
    }

    fun beginModels(
        request: AIProfileModelsRequest,
        profiles: List<AIProfileDraft>,
        context: AIProfileDiagnosticsContext,
    ): AIProfileDiagnosticsStateHolder? {
        val profileIndex = profiles.indexOfKey(request.profileKey)
        val currentProfile = profiles.getOrNull(profileIndex)
        if (currentProfile == null || nextModelsRequest(currentProfile, profileIndex, context) != request) {
            return null
        }
        return copy(loadingModelsProfileKey = request.profileKey, modelsRequestId = request.requestId)
    }

    fun canApplyModels(
        request: AIProfileModelsRequest,
        profiles: List<AIProfileDraft>,
        context: AIProfileDiagnosticsContext,
    ): Boolean {
        return loadingModelsProfileKey == request.profileKey &&
            modelsRequestId == request.requestId &&
            profiles.profileForKey(request.profileKey) == request.profile &&
            context.appMode == request.appMode &&
            context.clientContextGeneration == request.clientContextGeneration
    }

    fun completeModels(
        request: AIProfileModelsRequest,
        models: List<String>,
        message: String,
        profiles: List<AIProfileDraft>,
        context: AIProfileDiagnosticsContext,
    ): AIProfileDiagnosticsStateHolder? {
        if (!canApplyModels(request, profiles, context)) {
            return null
        }
        return copy(
            loadingModelsProfileKey = "",
            modelResults = modelResults + (request.profileKey to models.toList()),
            testResults = testResults + (request.profileKey to message),
        )
    }

    fun failModels(
        request: AIProfileModelsRequest,
        message: String,
        profiles: List<AIProfileDraft>,
        context: AIProfileDiagnosticsContext,
    ): AIProfileDiagnosticsStateHolder? {
        if (!canApplyModels(request, profiles, context)) {
            return null
        }
        return copy(
            loadingModelsProfileKey = "",
            testResults = testResults + (request.profileKey to message),
        )
    }

    fun recordFeedback(profileKey: String, message: String): AIProfileDiagnosticsStateHolder {
        return copy(testResults = testResults + (profileKey to message))
    }

    fun clearResults(): AIProfileDiagnosticsStateHolder = copy(
        testResults = emptyMap(),
        modelResults = emptyMap(),
    )

    fun reset(): AIProfileDiagnosticsStateHolder = copy(
        testingProfileKey = "",
        loadingModelsProfileKey = "",
        testResults = emptyMap(),
        modelResults = emptyMap(),
        testRequestId = testRequestId + 1,
        modelsRequestId = modelsRequestId + 1,
    )
}

private fun List<AIProfileDraft>.indexOfKey(profileKey: String): Int {
    return indices.firstOrNull { get(it).editorKey(it) == profileKey } ?: -1
}

private fun List<AIProfileDraft>.profileForKey(profileKey: String): AIProfileDraft? {
    return getOrNull(indexOfKey(profileKey))
}
