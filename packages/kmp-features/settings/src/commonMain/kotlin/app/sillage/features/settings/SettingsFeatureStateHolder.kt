package app.sillage.features.settings

/**
 * Aggregated immutable ownership for the settings feature.
 *
 * Individual holders remain the unit of request identity and late-response
 * validation. This type owns cross-holder transitions that must stay consistent
 * when a workspace ends or a loaded/imported settings snapshot is applied.
 */
data class SettingsFeatureStateHolder(
    val profilesMutation: AIProfilesMutationStateHolder = AIProfilesMutationStateHolder(),
    val autoSummary: AIAutoSummaryStateHolder = AIAutoSummaryStateHolder(),
    val load: AISettingsLoadStateHolder = AISettingsLoadStateHolder(),
    val diagnostics: AIProfileDiagnosticsStateHolder = AIProfileDiagnosticsStateHolder(),
) {
    val profiles: List<AIProfileDraft> get() = profilesMutation.profiles
    val profilesSaving: Boolean get() = profilesMutation.saving
    val profilesRequestId: Long get() = profilesMutation.requestId
    val autoSummaryEnabled: Boolean get() = autoSummary.enabled
    val autoSummarySaving: Boolean get() = autoSummary.saving
    val autoSummaryRequestId: Long get() = autoSummary.requestId
    val loading: Boolean get() = load.loading
    val loadErrorMessage: String? get() = load.errorMessage
    val testingProfileKey: String get() = diagnostics.testingProfileKey
    val loadingModelsProfileKey: String get() = diagnostics.loadingModelsProfileKey
    val testResults: Map<String, String> get() = diagnostics.testResults
    val modelResults: Map<String, List<String>> get() = diagnostics.modelResults
    val diagnosticsBusy: Boolean get() = diagnostics.busy

    /** Replaces editable profile drafts without exposing the nested holder to hosts. */
    fun replaceProfiles(profiles: List<AIProfileDraft>): SettingsFeatureStateHolder {
        return copy(profilesMutation = profilesMutation.replace(profiles))
    }

    /**
     * Clears editable settings ownership for a workspace or client-context change.
     * [profiles] and [autoSummaryEnabled] seed the post-clear snapshot (empty/false
     * for online reconnect, local values for offline entry).
     */
    fun clearWorkspace(
        profiles: List<AIProfileDraft> = emptyList(),
        autoSummaryEnabled: Boolean = false,
    ): SettingsFeatureStateHolder {
        return copy(
            profilesMutation = profilesMutation.invalidate(profiles),
            autoSummary = autoSummary.invalidate().replace(autoSummaryEnabled),
            load = load.cancel(),
            diagnostics = diagnostics.reset(),
        )
    }

    /**
     * Applies a freshly loaded editable settings snapshot and drops stale
     * diagnostic results bound to previous profile drafts.
     */
    fun applyLoadedSnapshot(
        profiles: List<AIProfileDraft>,
        autoSummaryEnabled: Boolean,
    ): SettingsFeatureStateHolder {
        return copy(
            profilesMutation = profilesMutation.replace(profiles),
            autoSummary = autoSummary.replace(autoSummaryEnabled),
            diagnostics = diagnostics.clearResults(),
        )
    }

    /**
     * Applies imported profile/auto-summary preferences without cancelling an
     * unrelated in-flight settings load identity.
     */
    fun applyImportedPreferences(
        profiles: List<AIProfileDraft>,
        autoSummaryEnabled: Boolean,
    ): SettingsFeatureStateHolder {
        return copy(
            profilesMutation = profilesMutation.invalidate(profiles),
            autoSummary = autoSummary.replace(autoSummaryEnabled),
        )
    }
}
