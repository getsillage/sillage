package app.sillage.features.settings

data class AIProfilesMutationContext(
    val appMode: String,
    val clientContextGeneration: Long,
    val anotherOperationInProgress: Boolean,
)

data class AIProfilesMutationRequest(
    val requestId: Long,
    val appMode: String,
    val clientContextGeneration: Long,
    val previousProfiles: List<AIProfileDraft>,
    val pendingProfiles: List<AIProfileDraft>,
    val submittedProfiles: List<AIProfileDraft>,
)

/** Owns editable AI profiles and their optimistic save request lifecycle. */
data class AIProfilesMutationStateHolder(
    val profiles: List<AIProfileDraft> = emptyList(),
    val saving: Boolean = false,
    val requestId: Long = 0,
) {
    fun nextRequest(
        pendingProfiles: List<AIProfileDraft>,
        context: AIProfilesMutationContext,
        submittedProfiles: List<AIProfileDraft> = pendingProfiles,
    ): AIProfilesMutationRequest? {
        if (context.anotherOperationInProgress || saving) {
            return null
        }
        return AIProfilesMutationRequest(
            requestId = requestId + 1,
            appMode = context.appMode,
            clientContextGeneration = context.clientContextGeneration,
            previousProfiles = profiles.toList(),
            pendingProfiles = pendingProfiles.toList(),
            submittedProfiles = submittedProfiles.toList(),
        )
    }

    fun begin(
        request: AIProfilesMutationRequest,
        context: AIProfilesMutationContext,
    ): AIProfilesMutationStateHolder? {
        if (
            nextRequest(
                pendingProfiles = request.pendingProfiles,
                context = context,
                submittedProfiles = request.submittedProfiles,
            ) != request
        ) {
            return null
        }
        return copy(
            profiles = request.pendingProfiles,
            saving = true,
            requestId = request.requestId,
        )
    }

    fun canApply(
        request: AIProfilesMutationRequest,
        context: AIProfilesMutationContext,
    ): Boolean {
        return saving &&
            requestId == request.requestId &&
            context.appMode == request.appMode &&
            context.clientContextGeneration == request.clientContextGeneration
    }

    fun complete(
        request: AIProfilesMutationRequest,
        savedProfiles: List<AIProfileDraft>,
        context: AIProfilesMutationContext,
    ): AIProfilesMutationStateHolder? {
        if (!canApply(request, context)) {
            return null
        }
        return copy(
            profiles = if (profiles == request.pendingProfiles) savedProfiles.toList() else profiles,
            saving = false,
        )
    }

    fun fail(
        request: AIProfilesMutationRequest,
        context: AIProfilesMutationContext,
    ): AIProfilesMutationStateHolder? {
        if (!canApply(request, context)) {
            return null
        }
        return copy(
            profiles = if (profiles == request.pendingProfiles) request.previousProfiles else profiles,
            saving = false,
        )
    }

    /** Replace editor contents without changing request ownership. */
    fun replace(profiles: List<AIProfileDraft>): AIProfilesMutationStateHolder {
        return copy(profiles = profiles.toList())
    }

    /** Cancel callbacks and optionally establish a new authoritative snapshot. */
    fun invalidate(profiles: List<AIProfileDraft> = this.profiles): AIProfilesMutationStateHolder {
        return copy(
            profiles = profiles.toList(),
            saving = false,
            requestId = requestId + 1,
        )
    }
}
