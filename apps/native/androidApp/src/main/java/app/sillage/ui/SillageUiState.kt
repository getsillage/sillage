package app.sillage.ui

import app.sillage.core.domain.auth.Account
import app.sillage.core.domain.ask.AskMessage
import app.sillage.features.ask.AskFeatureStateHolder
import app.sillage.features.ask.AskMemoSaveContext
import app.sillage.features.ask.AskMemoSaveRequest
import app.sillage.features.ask.AskSourceNavigationContext
import app.sillage.features.ask.AskSourceNavigationRequest
import app.sillage.features.ask.AskStreamContext
import app.sillage.features.ask.AskStreamRequest
import app.sillage.features.ask.AskVariantContext
import app.sillage.features.ask.AskVariantRequest
import app.sillage.features.auth.AuthFeatureStateHolder
import app.sillage.features.auth.PasswordChangeContext
import app.sillage.features.auth.PasswordChangeRequest
import app.sillage.features.sync.MemoSyncConflictItem
import app.sillage.features.sync.SyncFeatureStateHolder
import app.sillage.core.domain.records.Memo
import app.sillage.core.application.records.RecordDetail
import app.sillage.core.domain.records.MemoAI
import app.sillage.features.records.MemoListFilter
import app.sillage.features.records.RecordsPageContext
import app.sillage.features.records.RecordsPageRequest
import app.sillage.features.records.RecordsRefreshContext
import app.sillage.features.records.RecordsRefreshRequest
import app.sillage.features.records.CompletedRecordsSearch
import app.sillage.features.records.RecordsSearchContext
import app.sillage.features.records.RecordsSearchRequest
import app.sillage.features.records.RecordsSummaryContext
import app.sillage.features.records.RecordsSummaryRequest
import app.sillage.features.records.RecordsDetailContext
import app.sillage.features.records.RecordsDetailRequest
import app.sillage.features.records.RecordsDetailResponseDisposition
import app.sillage.features.records.RecordsAttachmentOpenRequest
import app.sillage.features.records.RecordsBrowseStateHolder
import app.sillage.features.records.RecordsEditorStateHolder
import app.sillage.features.records.RecordsEditorActionContext
import app.sillage.features.records.RecordsEditorBusyReason
import app.sillage.features.records.RecordsFeatureStateHolder
import app.sillage.features.records.canRunEditorAction
import app.sillage.features.records.editorBusyReason
import app.sillage.features.records.MemoViewMode
import app.sillage.data.SessionStore
import app.sillage.features.settings.AIAutoSummaryContext
import app.sillage.features.settings.AIAutoSummaryRequest
import app.sillage.features.settings.AIProfileDraft
import app.sillage.features.settings.AIProfilesMutationContext
import app.sillage.features.settings.AIProfilesMutationRequest
import app.sillage.features.settings.AISettingsLoadContext
import app.sillage.features.settings.AISettingsLoadRequest
import app.sillage.features.settings.AIProfileDiagnosticsContext
import app.sillage.features.settings.AIProfileModelsRequest
import app.sillage.features.settings.AIProfileTestRequest
import app.sillage.features.settings.SettingsFeatureStateHolder
import app.sillage.ui.appshell.AppAppearanceStateHolder
import app.sillage.ui.appshell.AppClientContextStateHolder
import app.sillage.ui.appshell.AppDestination
import app.sillage.ui.appshell.AppNavigationPolicy
import app.sillage.ui.appshell.AppWorkspaceStateHolder
import java.time.LocalDate

internal fun defaultRecordsFeatureState(
    today: LocalDate = LocalDate.now(),
): RecordsFeatureStateHolder {
    return RecordsFeatureStateHolder(
        editor = RecordsEditorStateHolder(
            draftEntryDate = today.toString(),
            initialDraftEntryDate = today.toString(),
        ),
        browse = RecordsBrowseStateHolder(
            calendarYear = today.year,
            calendarMonth = today.monthValue,
        ),
    )
}

data class SillageUiState(
    val baseUrl: String,
    val clientContext: AppClientContextStateHolder = AppClientContextStateHolder(),
    val appearance: AppAppearanceStateHolder = AppAppearanceStateHolder(),
    val initialized: Boolean? = null,
    val serverVersion: String = "",
    val serverRevision: String = "",
    val apiVersion: String = "",
    val minimumAndroidVersionCode: Int = 0,
    val androidUpdateRequired: Boolean = false,
    val account: Account? = null,
    val workspace: AppWorkspaceStateHolder = AppWorkspaceStateHolder(
        records = defaultRecordsFeatureState(),
    ),
    val auth: AuthFeatureStateHolder = AuthFeatureStateHolder(),
    val sync: SyncFeatureStateHolder = SyncFeatureStateHolder(),
    val loading: Boolean = false,
    val authError: String? = null,
    val authErrorResourceId: Int? = null,
    val error: String? = null,
    val notice: String? = null,
)

/** Applies a pure application-shell transition without touching host fields. */
internal inline fun SillageUiState.withClientContext(
    transform: (AppClientContextStateHolder) -> AppClientContextStateHolder,
): SillageUiState = copy(clientContext = transform(clientContext))

/** Applies a pure records-feature transition without touching host-only fields. */
internal inline fun SillageUiState.withRecords(
    transform: (RecordsFeatureStateHolder) -> RecordsFeatureStateHolder,
): SillageUiState = copy(workspace = workspace.updateRecords(transform))

/** Updates records search presentation through the aggregate holder. */
internal fun SillageUiState.withMemoSearchQuery(value: String): SillageUiState {
    return withRecords { it.updateSearchQuery(value) }
}

/** Clears records search presentation through the aggregate holder. */
internal fun SillageUiState.clearMemoSearchState(): SillageUiState {
    return withRecords { it.clearSearch() }
}

/** Applies a restored records view preference through its aggregate policy. */
internal fun SillageUiState.applyRestoredMemoViewMode(mode: MemoViewMode): SillageUiState {
    return withRecords { it.applyRestoredViewMode(mode) }
}

/** Applies a pure Ask-feature transition without touching host-only fields. */
internal inline fun SillageUiState.withAsk(
    transform: (AskFeatureStateHolder) -> AskFeatureStateHolder,
): SillageUiState = copy(workspace = workspace.updateAsk(transform))

/** Updates the Ask composer draft through the aggregate holder. */
internal fun SillageUiState.withAskQuestion(value: String): SillageUiState {
    return withAsk { it.updateQuestion(value) }
}

/** Updates the Ask retrieval scope through the aggregate holder. */
internal fun SillageUiState.withAskContextScope(value: String): SillageUiState {
    return withAsk { it.updateContextScope(value) }
}

/** Updates the Ask source kind through the aggregate holder. */
internal fun SillageUiState.withAskSourceKind(value: String): SillageUiState {
    return withAsk { it.updateSourceKind(value) }
}

/** Applies a pure settings-feature transition without touching host-only fields. */
internal inline fun SillageUiState.withSettings(
    transform: (SettingsFeatureStateHolder) -> SettingsFeatureStateHolder,
): SillageUiState = copy(workspace = workspace.updateSettings(transform))

/** Replaces editable AI profile drafts through the settings aggregate. */
internal fun SillageUiState.withAIProfiles(
    profiles: List<AIProfileDraft>,
): SillageUiState {
    return withSettings { it.replaceProfiles(profiles) }
}

/** Clears AI profile diagnostic results through the settings aggregate. */
internal fun SillageUiState.clearAIProfileDiagnosticsResults(): SillageUiState {
    return withSettings { it.clearDiagnosticsResults() }
}

/** Records AI profile diagnostic feedback through the settings aggregate. */
internal fun SillageUiState.recordAIProfileDiagnosticsFeedback(
    profileKey: String,
    message: String,
): SillageUiState {
    return withSettings { it.recordDiagnosticsFeedback(profileKey, message) }
}

/** Applies a pure sync-feature transition without touching host-only fields. */
internal inline fun SillageUiState.withSync(
    transform: (SyncFeatureStateHolder) -> SyncFeatureStateHolder,
): SillageUiState = copy(sync = transform(sync))

internal fun SillageUiState.applySyncPushConflicts(
    items: List<MemoSyncConflictItem>,
): SillageUiState {
    return withSync { it.applyPushConflicts(items) }
}

internal fun SillageUiState.removeSyncConflict(resourceId: String): SillageUiState {
    return withSync { it.removeConflict(resourceId) }
}

internal fun SillageUiState.replaceSyncConflicts(
    items: List<MemoSyncConflictItem>,
): SillageUiState {
    return withSync { it.replaceConflicts(items) }
}

/** Applies a pure auth-feature transition without touching host-only fields. */
internal inline fun SillageUiState.withAuth(
    transform: (AuthFeatureStateHolder) -> AuthFeatureStateHolder,
): SillageUiState = copy(auth = transform(auth))

internal fun SillageUiState.withAuthUsername(value: String): SillageUiState {
    return withAuth { it.updateUsername(value) }
}

internal fun SillageUiState.withAuthDisplayName(value: String): SillageUiState {
    return withAuth { it.updateDisplayName(value) }
}

internal fun SillageUiState.withAuthPassword(value: String): SillageUiState {
    return withAuth { it.updatePassword(value) }
}

internal fun SillageUiState.withAuthCurrentPassword(value: String): SillageUiState {
    return withAuth { it.updateCurrentPassword(value) }
}

internal fun SillageUiState.withAuthNewPassword(value: String): SillageUiState {
    return withAuth { it.updateNewPassword(value) }
}

internal fun SillageUiState.withAuthConfirmPassword(value: String): SillageUiState {
    return withAuth { it.updateConfirmPassword(value) }
}

internal fun SillageUiState.clearAuthPrimaryCredentials(
    clearDisplayName: Boolean,
): SillageUiState {
    return withAuth { it.clearPrimaryCredentials(clearDisplayName) }
}

/**
 * Clears records/settings/ask interactive ownership for a client-context or
 * workspace change. Does not touch host-only fields such as auth, theme, or the
 * root loading gate.
 */
internal fun SillageUiState.clearClientWorkspace(
    settingsProfiles: List<AIProfileDraft> = emptyList(),
    settingsAutoSummaryEnabled: Boolean = false,
    askInvalidateStream: Boolean = false,
    askInvalidateVariant: Boolean = false,
): SillageUiState {
    return copy(
        workspace = workspace.clearClientWorkspace(
            settingsProfiles = settingsProfiles,
            settingsAutoSummaryEnabled = settingsAutoSummaryEnabled,
            askInvalidateStream = askInvalidateStream,
            askInvalidateVariant = askInvalidateVariant,
        ),
    )
}

/**
 * Offline entry: clear interactive ownership, seed settings from local values,
 * and replace the visible records snapshot.
 */
internal fun SillageUiState.enterOfflineClientWorkspace(
    memos: List<Memo>,
    settingsProfiles: List<AIProfileDraft>,
    settingsAutoSummaryEnabled: Boolean,
): SillageUiState {
    return copy(
        workspace = workspace.enterOfflineClientWorkspace(
            memos = memos,
            settingsProfiles = settingsProfiles,
            settingsAutoSummaryEnabled = settingsAutoSummaryEnabled,
        ),
    )
}

/**
 * UI model for one push conflict: local pending content plus the server resource.
 */
typealias SyncConflictItem = MemoSyncConflictItem

internal typealias MemoEditorBusyReason = RecordsEditorBusyReason

internal fun SillageUiState.memoEditorBusyReason(): MemoEditorBusyReason? {
    return workspace.records.editorBusyReason(memoEditorActionContext())
}

internal fun SillageUiState.withMemoEditorBackBlockedNotice(
    attachmentUploadNotice: String,
    operationNotice: String,
): SillageUiState {
    val blockedNotice = when (memoEditorBusyReason()) {
        MemoEditorBusyReason.AttachmentUpload -> attachmentUploadNotice
        MemoEditorBusyReason.Operation -> operationNotice
        null -> return this
    }
    return copy(error = null, notice = blockedNotice)
}

internal fun SillageUiState.canRunMemoEditorAction(): Boolean {
    return workspace.records.canRunEditorAction(memoEditorActionContext())
}

internal fun SillageUiState.memoEditorActionContext(): RecordsEditorActionContext {
    return RecordsEditorActionContext(
        destinationAvailable = clientContext.screen == Screen.Editor,
        hostOperationInProgress = loading,
    )
}

internal fun SillageUiState.isMemoMutationInProgress(memoId: String): Boolean {
    return workspace.records.mutation.isActive(memoId)
}

internal fun SillageUiState.beginMemoMutation(memoId: String?): SillageUiState {
    return withRecords { it.beginMemoMutation(memoId) }
}

internal fun SillageUiState.finishMemoMutation(memoId: String?): SillageUiState {
    return withRecords { it.finishMemoMutation(memoId) }
}

internal fun SillageUiState.hasClientContextOperationInProgress(): Boolean {
    return loading ||
        workspace.records.summary.loading ||
        workspace.records.mutation.active ||
        workspace.ask.memoSave.savingMessageId.isNotBlank() ||
        workspace.settings.profilesSaving ||
        workspace.settings.autoSummarySaving ||
        workspace.settings.testingProfileKey.isNotBlank() ||
        workspace.settings.loadingModelsProfileKey.isNotBlank() ||
        auth.passwordChanging
}

internal fun SillageUiState.nextPasswordChangeRequest(): PasswordChangeRequest? {
    return auth.authentication.nextPasswordChangeRequest(passwordChangeContext())
}

internal fun SillageUiState.startPasswordChange(request: PasswordChangeRequest): SillageUiState {
    val started = auth.authentication.beginPasswordChange(request, passwordChangeContext())
        ?: return this
    return withAuth { it.copy(authentication = started) }
}

internal fun SillageUiState.canApplyPasswordChange(request: PasswordChangeRequest): Boolean {
    return auth.authentication.canApplyPasswordChange(request, passwordChangeContext())
}

internal fun SillageUiState.completePasswordChange(request: PasswordChangeRequest): SillageUiState {
    val completed = auth.authentication.completePasswordChange(
        request,
        passwordChangeContext(),
    ) ?: return this
    return withAuth { it.copy(authentication = completed) }
}

internal fun SillageUiState.failPasswordChange(request: PasswordChangeRequest): SillageUiState {
    val failed = auth.authentication.failPasswordChange(request, passwordChangeContext())
        ?: return this
    return withAuth { it.copy(authentication = failed) }
}

private fun SillageUiState.passwordChangeContext(): PasswordChangeContext {
    return PasswordChangeContext(
        appMode = clientContext.appMode,
        clientContextGeneration = clientContext.generation,
        online = clientContext.online,
        anotherOperationInProgress = loading ||
            workspace.records.summary.loading ||
            workspace.records.mutation.active ||
            workspace.ask.memoSave.savingMessageId.isNotBlank() ||
            workspace.settings.profilesSaving ||
            workspace.settings.autoSummarySaving ||
            workspace.settings.diagnostics.busy,
    )
}

internal fun SillageUiState.canApplyAttachmentUpload(sessionId: Long): Boolean {
    return clientContext.screen == Screen.Editor &&
        workspace.records.editor.canApplyAttachmentUpload(sessionId)
}

internal fun SillageUiState.canHandleAttachmentOpen(requestId: Long): Boolean {
    return workspace.records.attachmentOpen.owns(requestId)
}

internal fun SillageUiState.nextAttachmentOpenRequest(
    path: String,
): RecordsAttachmentOpenRequest? {
    return workspace.records.nextAttachmentOpenRequest(path)
}

internal fun SillageUiState.beginAttachmentOpenRequest(
    request: RecordsAttachmentOpenRequest,
): SillageUiState? {
    val nextRecords = workspace.records.beginAttachmentOpen(request) ?: return null
    return copy(workspace = workspace.copy(records = nextRecords))
}

internal fun SillageUiState.completeAttachmentOpenRequest(
    requestId: Long,
): SillageUiState {
    val nextRecords = workspace.records.completeAttachmentOpen(requestId)
    return if (nextRecords === workspace.records) this else copy(
        workspace = workspace.copy(records = nextRecords),
    )
}

internal fun SillageUiState.invalidateAttachmentOpenRequest(): SillageUiState {
    val nextRecords = workspace.records.invalidateAttachmentOpen()
    return if (nextRecords === workspace.records) this else copy(
        workspace = workspace.copy(records = nextRecords),
    )
}

internal fun SillageUiState.withAskStreamingStoppedNotice(message: String): SillageUiState {
    if (!workspace.ask.stream.sending) {
        return this
    }
    return copy(error = null, notice = message)
}

private fun SillageUiState.recordsDetailContext(): RecordsDetailContext {
    val detailAvailable =
        clientContext.screen == Screen.MemoDetail || clientContext.screen == Screen.Editor
    return RecordsDetailContext(
        sourceKey = clientContext.appMode,
        clientContextGeneration = clientContext.generation,
        destinationKey = clientContext.screen.name,
        destinationGeneration =
            if (clientContext.screen == Screen.Editor) workspace.records.editor.sessionId else 0,
        cacheGeneration = workspace.records.collection.cacheGeneration,
        detailAvailable = detailAvailable,
    )
}

internal fun SillageUiState.nextMemoDetailRequest(memoId: String): RecordsDetailRequest? {
    return workspace.records.selection.nextDetailRequest(memoId, recordsDetailContext())
}

internal fun SillageUiState.startMemoDetailRequest(request: RecordsDetailRequest): SillageUiState {
    val selection = workspace.records.selection.beginDetailRequest(
        request,
        recordsDetailContext(),
    ) ?: return this
    return withRecords {
        it.acceptDetailRequest(
            selection = selection,
            loadSummary = request.sourceKey != SessionStore.MODE_OFFLINE,
        )
    }
}

internal fun SillageUiState.completeMemoDetailRequest(
    request: RecordsDetailRequest,
    detail: RecordDetail,
): SillageUiState {
    return when (
        workspace.records.selection.detailResponseDisposition(
            request,
            recordsDetailContext(),
            detail.memo,
        )
    ) {
        RecordsDetailResponseDisposition.Ignore -> this
        RecordsDetailResponseDisposition.Superseded -> withRecords { it.finishDetailSummary() }
        RecordsDetailResponseDisposition.Apply -> withRecords {
            it.completePresentedDetail(detail.memo, detail.ai)
        }
    }
}

internal fun SillageUiState.failMemoDetailRequest(
    request: RecordsDetailRequest,
    message: String,
): SillageUiState {
    return when (
        workspace.records.selection.detailFailureDisposition(request, recordsDetailContext())
    ) {
        RecordsDetailResponseDisposition.Ignore -> this
        RecordsDetailResponseDisposition.Superseded -> withRecords { it.finishDetailSummary() }
        RecordsDetailResponseDisposition.Apply -> withRecords { it.finishDetailSummary() }
            .copy(error = message)
    }
}

internal typealias MemoSummaryRequest = RecordsSummaryRequest

private fun SillageUiState.recordsSummaryContext(): RecordsSummaryContext {
    return RecordsSummaryContext(
        sourceKey = clientContext.appMode,
        clientContextGeneration = clientContext.generation,
        destinationKey = clientContext.screen.name,
        destinationGeneration =
            if (clientContext.screen == Screen.Editor) workspace.records.editor.sessionId else 0,
        detailRequestId = workspace.records.selection.detailRequestId,
        summaryAvailable =
            clientContext.screen == Screen.MemoDetail || clientContext.screen == Screen.Editor,
    )
}

internal fun SillageUiState.nextMemoSummaryRequest(): MemoSummaryRequest? {
    return workspace.records.summary.nextRequest(
        workspace.records.selection.selectedMemo,
        recordsSummaryContext(),
    )
}

internal fun SillageUiState.startMemoSummaryRequest(request: MemoSummaryRequest): SillageUiState {
    val summaryState = workspace.records.summary.begin(
        request,
        workspace.records.selection.selectedMemo,
        recordsSummaryContext(),
    )
        ?: return this
    return withRecords { it.copy(summary = summaryState) }.copy(
        error = null,
        notice = null,
    )
}

private fun SillageUiState.ownsMemoSummaryRequest(request: MemoSummaryRequest): Boolean {
    return workspace.records.summary.owns(request)
}

internal fun SillageUiState.canApplyMemoSummaryRequest(request: MemoSummaryRequest): Boolean {
    return workspace.records.summary.canApply(
        request,
        workspace.records.selection.selectedMemo,
        recordsSummaryContext(),
    )
}

internal fun SillageUiState.completeMemoSummaryRequest(
    request: MemoSummaryRequest,
    summary: MemoAI,
    message: String,
): SillageUiState {
    if (!canApplyMemoSummaryRequest(request) || summary.memoId != request.memoId) {
        return this
    }
    return withRecords {
        it.copy(
            summary = it.summary.complete(
                request,
                workspace.records.selection.selectedMemo,
                recordsSummaryContext(),
                summary,
            ),
        )
    }.copy(
        error = null,
        notice = message,
    )
}

internal fun SillageUiState.failMemoSummaryRequest(
    request: MemoSummaryRequest,
    message: String,
): SillageUiState {
    if (!canApplyMemoSummaryRequest(request)) {
        return this
    }
    return withRecords {
        it.copy(
            summary =
                it.summary.fail(
                    request,
                    workspace.records.selection.selectedMemo,
                    recordsSummaryContext(),
                ),
        )
    }.copy(error = message)
}

internal fun SillageUiState.finishMemoSummaryRequest(request: MemoSummaryRequest): SillageUiState {
    if (!ownsMemoSummaryRequest(request)) return this
    return withRecords { it.copy(summary = it.summary.finish(request)) }
}

internal fun SillageUiState.invalidateMemoSummaryRequest(): SillageUiState {
    if (!workspace.records.summary.loading) return this
    return withRecords { it.copy(summary = it.summary.invalidate()) }
}

internal fun SillageUiState.aiAutoSummaryContext(): AIAutoSummaryContext =
    AIAutoSummaryContext(
        appMode = clientContext.appMode,
        clientContextGeneration = clientContext.generation,
        anotherMutationInProgress = workspace.settings.loading ||
            workspace.settings.profilesSaving ||
            workspace.settings.diagnostics.busy,
    )

internal fun SillageUiState.nextAIAutoSummaryRequest(
    targetValue: Boolean,
): AIAutoSummaryRequest? {
    return workspace.settings.autoSummary.nextRequest(targetValue, aiAutoSummaryContext())
}

internal fun SillageUiState.startAIAutoSummaryRequest(
    request: AIAutoSummaryRequest,
): SillageUiState {
    val pending = workspace.settings.autoSummary.begin(request, aiAutoSummaryContext()) ?: return this
    return withSettings { it.copy(autoSummary = pending) }
}

internal fun SillageUiState.canApplyAIAutoSummaryRequest(
    request: AIAutoSummaryRequest,
): Boolean {
    return workspace.settings.autoSummary.canApply(request, aiAutoSummaryContext())
}

internal fun SillageUiState.completeAIAutoSummaryRequest(
    request: AIAutoSummaryRequest,
    savedValue: Boolean,
): SillageUiState {
    val completed = workspace.settings.autoSummary.complete(
        request,
        savedValue,
        aiAutoSummaryContext(),
    ) ?: return this
    return withSettings { it.copy(autoSummary = completed) }
}

internal fun SillageUiState.failAIAutoSummaryRequest(request: AIAutoSummaryRequest): SillageUiState {
    val failed = workspace.settings.autoSummary.fail(request, aiAutoSummaryContext()) ?: return this
    return withSettings { it.copy(autoSummary = failed) }
}

internal fun SillageUiState.invalidateAIAutoSummaryRequest(): SillageUiState {
    return withSettings { it.copy(autoSummary = it.autoSummary.invalidate()) }
}

internal fun SillageUiState.nextAIProfilesMutationRequest(
    pendingProfiles: List<AIProfileDraft>,
    submittedProfiles: List<AIProfileDraft> = pendingProfiles,
): AIProfilesMutationRequest? {
    return workspace.settings.profilesMutation.nextRequest(
        pendingProfiles = pendingProfiles.toList(),
        context = aiProfilesMutationContext(),
        submittedProfiles = submittedProfiles.toList(),
    )
}

internal fun SillageUiState.startAIProfilesMutation(
    request: AIProfilesMutationRequest,
): SillageUiState {
    val started = workspace.settings.profilesMutation.begin(request, aiProfilesMutationContext())
        ?: return this
    return withSettings {
        it.copy(
            profilesMutation = started,
            load = it.load.cancel(),
        )
    }
}

internal fun SillageUiState.canApplyAIProfilesMutation(
    request: AIProfilesMutationRequest,
): Boolean {
    return workspace.settings.profilesMutation.canApply(request, aiProfilesMutationContext())
}

internal fun SillageUiState.completeAIProfilesMutation(
    request: AIProfilesMutationRequest,
    savedProfiles: List<AIProfileDraft>,
): SillageUiState {
    val completed = workspace.settings.profilesMutation.complete(
        request = request,
        savedProfiles = savedProfiles,
        context = aiProfilesMutationContext(),
    ) ?: return this
    return withSettings { it.copy(profilesMutation = completed) }
}

internal fun SillageUiState.failAIProfilesMutation(
    request: AIProfilesMutationRequest,
): SillageUiState {
    val failed = workspace.settings.profilesMutation.fail(
        request = request,
        context = aiProfilesMutationContext(),
    ) ?: return this
    return withSettings { it.copy(profilesMutation = failed) }
}

private fun SillageUiState.aiProfilesMutationContext(): AIProfilesMutationContext {
    return AIProfilesMutationContext(
        appMode = clientContext.appMode,
        clientContextGeneration = clientContext.generation,
        anotherOperationInProgress = loading ||
        workspace.settings.loading ||
        workspace.settings.autoSummarySaving ||
        workspace.settings.diagnostics.busy,
    )
}

internal fun SillageUiState.nextAISettingsLoadRequest(): AISettingsLoadRequest? {
    return workspace.settings.load.nextRequest(aiSettingsLoadContext())
}

internal fun SillageUiState.startAISettingsLoad(
    request: AISettingsLoadRequest,
): SillageUiState {
    val started = workspace.settings.load.begin(request, aiSettingsLoadContext()) ?: return this
    return withSettings {
        it.copy(
            load = started,
            autoSummary = it.autoSummary.invalidate(),
            profilesMutation = it.profilesMutation.invalidate(),
        )
    }
}

internal fun SillageUiState.canApplyAISettingsLoad(request: AISettingsLoadRequest): Boolean {
    return workspace.settings.load.canApply(request, aiSettingsLoadContext())
}

internal fun SillageUiState.completeAISettingsLoad(request: AISettingsLoadRequest): SillageUiState {
    val completed = workspace.settings.load.complete(request, aiSettingsLoadContext()) ?: return this
    return withSettings { it.copy(load = completed) }
}

internal fun SillageUiState.failAISettingsLoad(
    request: AISettingsLoadRequest,
    message: String,
): SillageUiState {
    val failed = workspace.settings.load.fail(request, message, aiSettingsLoadContext()) ?: return this
    return withSettings { it.copy(load = failed) }
}

internal fun SillageUiState.invalidateAISettingsLoad(): SillageUiState {
    return withSettings { it.copy(load = it.load.cancel()) }
}

private fun SillageUiState.aiSettingsLoadContext(): AISettingsLoadContext {
    return AISettingsLoadContext(
        appMode = clientContext.appMode,
        clientContextGeneration = clientContext.generation,
        anotherOperationInProgress = loading ||
        workspace.settings.profilesSaving ||
        workspace.settings.autoSummarySaving ||
        workspace.settings.diagnostics.busy,
    )
}

internal fun SillageUiState.nextAIProfileTestRequest(index: Int): AIProfileTestRequest? {
    val profile = workspace.settings.profiles.getOrNull(index) ?: return null
    return workspace.settings.diagnostics.nextTestRequest(
        profile,
        index,
        aiProfileDiagnosticsContext(),
    )
}

internal fun SillageUiState.startAIProfileTest(request: AIProfileTestRequest): SillageUiState {
    val started = workspace.settings.diagnostics.beginTest(
        request,
        workspace.settings.profiles,
        aiProfileDiagnosticsContext(),
    ) ?: return this
    return withSettings { it.copy(diagnostics = started) }
}

internal fun SillageUiState.canApplyAIProfileTest(request: AIProfileTestRequest): Boolean {
    return workspace.settings.diagnostics.canApplyTest(
        request,
        workspace.settings.profiles,
        aiProfileDiagnosticsContext(),
    )
}

internal fun SillageUiState.completeAIProfileTest(
    request: AIProfileTestRequest,
    message: String,
): SillageUiState {
    val completed = workspace.settings.diagnostics.completeTest(
        request,
        message,
        workspace.settings.profiles,
        aiProfileDiagnosticsContext(),
    ) ?: return this
    return withSettings { it.copy(diagnostics = completed) }
}

internal fun SillageUiState.nextAIProfileModelsRequest(index: Int): AIProfileModelsRequest? {
    val profile = workspace.settings.profiles.getOrNull(index) ?: return null
    return workspace.settings.diagnostics.nextModelsRequest(
        profile,
        index,
        aiProfileDiagnosticsContext(),
    )
}

internal fun SillageUiState.startAIProfileModels(request: AIProfileModelsRequest): SillageUiState {
    val started = workspace.settings.diagnostics.beginModels(
        request,
        workspace.settings.profiles,
        aiProfileDiagnosticsContext(),
    ) ?: return this
    return withSettings { it.copy(diagnostics = started) }
}

internal fun SillageUiState.canApplyAIProfileModels(request: AIProfileModelsRequest): Boolean {
    return workspace.settings.diagnostics.canApplyModels(
        request,
        workspace.settings.profiles,
        aiProfileDiagnosticsContext(),
    )
}

internal fun SillageUiState.completeAIProfileModels(
    request: AIProfileModelsRequest,
    models: List<String>,
    message: String,
): SillageUiState {
    val completed = workspace.settings.diagnostics.completeModels(
        request,
        models,
        message,
        workspace.settings.profiles,
        aiProfileDiagnosticsContext(),
    ) ?: return this
    return withSettings { it.copy(diagnostics = completed) }
}

internal fun SillageUiState.failAIProfileModels(
    request: AIProfileModelsRequest,
    message: String,
): SillageUiState {
    val failed = workspace.settings.diagnostics.failModels(
        request,
        message,
        workspace.settings.profiles,
        aiProfileDiagnosticsContext(),
    ) ?: return this
    return withSettings { it.copy(diagnostics = failed) }
}

private fun SillageUiState.aiProfileDiagnosticsContext(): AIProfileDiagnosticsContext {
    return AIProfileDiagnosticsContext(
        appMode = clientContext.appMode,
        clientContextGeneration = clientContext.generation,
        anotherOperationInProgress = loading ||
            workspace.settings.loading ||
            workspace.settings.profilesSaving ||
            workspace.settings.autoSummarySaving,
    )
}
private fun SillageUiState.recordsPageContext(): RecordsPageContext {
    return RecordsPageContext(
        sourceKey = clientContext.appMode,
        sourceAvailable = clientContext.online,
        clientContextGeneration = clientContext.generation,
        filter = workspace.records.browse.filter,
        cacheGeneration = workspace.records.collection.cacheGeneration,
    )
}

internal fun SillageUiState.nextMemoPageRequest(): RecordsPageRequest? {
    return workspace.records.pagination.nextRequest(recordsPageContext())
}

internal fun SillageUiState.beginMemoPage(request: RecordsPageRequest): SillageUiState? {
    val pagination = workspace.records.pagination.begin(request, recordsPageContext()) ?: return null
    return withRecords { it.copy(pagination = pagination) }.copy(
        error = null,
        notice = null,
    )
}

internal fun SillageUiState.canApplyMemoPage(request: RecordsPageRequest): Boolean {
    return workspace.records.pagination.canApply(request, recordsPageContext())
}

internal fun SillageUiState.completeMemoPage(
    request: RecordsPageRequest,
    nextCursor: String,
): SillageUiState? {
    val pagination =
        workspace.records.pagination.complete(
            request,
            recordsPageContext(),
            nextCursor,
        ) ?: return null
    return withRecords { it.copy(pagination = pagination) }
}

internal fun SillageUiState.failMemoPage(request: RecordsPageRequest): SillageUiState? {
    val pagination = workspace.records.pagination.fail(request, recordsPageContext()) ?: return null
    return withRecords { it.copy(pagination = pagination) }
}

private fun SillageUiState.recordsRefreshContext(): RecordsRefreshContext {
    return RecordsRefreshContext(
        sourceKey = clientContext.appMode,
        clientContextGeneration = clientContext.generation,
        filter = workspace.records.browse.filter,
        cacheGeneration = workspace.records.collection.cacheGeneration,
        paginationRequestId = workspace.records.pagination.requestId,
    )
}

internal fun SillageUiState.nextMemoRefreshRequest(): RecordsRefreshRequest {
    return workspace.records.refresh.nextRequest(recordsRefreshContext())
}

internal fun SillageUiState.beginMemoRefresh(request: RecordsRefreshRequest): SillageUiState? {
    val refresh = workspace.records.refresh.begin(request, recordsRefreshContext()) ?: return null
    return withRecords { it.copy(refresh = refresh) }
}

internal fun SillageUiState.canApplyMemoRefresh(request: RecordsRefreshRequest): Boolean {
    return workspace.records.refresh.canApply(request, recordsRefreshContext())
}

internal fun SillageUiState.completeMemoRefresh(request: RecordsRefreshRequest): SillageUiState? {
    val refresh = workspace.records.refresh.complete(request, recordsRefreshContext()) ?: return null
    return withRecords { it.copy(refresh = refresh) }
}

internal fun SillageUiState.failMemoRefresh(request: RecordsRefreshRequest): SillageUiState? {
    val refresh = workspace.records.refresh.fail(request, recordsRefreshContext()) ?: return null
    return withRecords { it.copy(refresh = refresh) }
}

private fun SillageUiState.recordsSearchContext(): RecordsSearchContext {
    return RecordsSearchContext(
        sourceKey = clientContext.appMode,
        clientContextGeneration = clientContext.generation,
        filter = workspace.records.browse.filter,
        cacheGeneration = workspace.records.collection.cacheGeneration,
    )
}

internal fun SillageUiState.nextMemoSearchRequest(): RecordsSearchRequest? {
    return workspace.records.search.nextRequest(recordsSearchContext())
}

internal fun SillageUiState.startMemoSearch(request: RecordsSearchRequest): SillageUiState {
    val search = workspace.records.search.begin(request, recordsSearchContext()) ?: return this
    return withRecords { it.copy(search = search) }.copy(
        error = null,
        notice = null,
    )
}

internal fun SillageUiState.canApplyMemoSearch(request: RecordsSearchRequest): Boolean {
    return workspace.records.search.canApply(request, recordsSearchContext())
}

internal fun SillageUiState.currentMemoSearchResults(): List<Memo>? {
    return workspace.records.search.currentResults()
}

internal fun SillageUiState.completedMemoSearch(): CompletedRecordsSearch? {
    return workspace.records.search.completed()
}

internal fun SillageUiState.completeMemoSearch(
    request: RecordsSearchRequest,
    results: List<Memo>,
): SillageUiState {
    val search = workspace.records.search.complete(
        request,
        recordsSearchContext(),
        results,
    ) ?: return this
    return withRecords { it.copy(search = search) }.copy(error = null)
}

internal fun SillageUiState.failMemoSearch(
    request: RecordsSearchRequest,
    message: String,
): SillageUiState {
    val search = workspace.records.search.fail(request, recordsSearchContext()) ?: return this
    return withRecords { it.copy(search = search) }.copy(error = message)
}

internal fun SillageUiState.applyMemoToCache(memo: Memo): SillageUiState {
    return withRecords { it.applyCanonicalMemo(memo) }
}

internal fun SillageUiState.askStreamContext(): AskStreamContext = AskStreamContext(
    screenSessionId = workspace.ask.session.generation,
    conversationId = workspace.ask.conversation.activeConversationId,
    appMode = clientContext.appMode,
    clientContextGeneration = clientContext.generation,
    anotherRequestInProgress = workspace.ask.loading ||
        workspace.ask.variant.loading ||
        workspace.ask.sourceNavigation.loading,
)

internal fun SillageUiState.nextAskStreamRequest(): AskStreamRequest? {
    return workspace.ask.stream.nextRequest(askStreamContext())
}

internal fun SillageUiState.canApplyAskStream(request: AskStreamRequest): Boolean {
    return workspace.ask.stream.canApply(request, askStreamContext())
}

internal fun SillageUiState.finishAskStream(
    answerAvailable: Boolean,
    clearQuestion: Boolean,
): SillageUiState {
    val completed = answerAvailable && error == null && notice == null
    return withAsk {
        it.finishStream(
            answerCompleted = completed,
            clearQuestion = clearQuestion && error == null,
        )
    }
}

internal fun hasNewCompletedAskAnswer(
    messages: List<AskMessage>,
    headId: String?,
    previousHeadId: String?,
): Boolean {
    return headId != null &&
        headId != previousHeadId &&
        messages.any { message ->
            message.id == headId &&
                message.role == "assistant" &&
                message.status == "complete" &&
                message.deletedAt == null &&
                message.content.isNotBlank()
        }
}

internal fun SillageUiState.askVariantContext(): AskVariantContext = AskVariantContext(
    destinationAvailable = clientContext.screen == Screen.Ask,
    screenSessionId = workspace.ask.session.generation,
    conversationId = workspace.ask.conversation.activeConversationId,
    appMode = clientContext.appMode,
    clientContextGeneration = clientContext.generation,
    anotherRequestInProgress = workspace.ask.loading ||
        workspace.ask.stream.sending ||
        workspace.ask.sourceNavigation.loading,
)

internal fun SillageUiState.nextAskVariantRequest(): AskVariantRequest? {
    return workspace.ask.variant.nextRequest(askVariantContext())
}

internal fun SillageUiState.canApplyAskVariant(request: AskVariantRequest): Boolean {
    return workspace.ask.variant.canApply(request, askVariantContext())
}

internal fun SillageUiState.askMemoSaveContext(): AskMemoSaveContext = AskMemoSaveContext(
    destinationAvailable = clientContext.screen == Screen.Ask,
    anotherRequestInProgress =
        loading ||
            workspace.ask.loading ||
            workspace.ask.stream.sending ||
            workspace.ask.variant.loading ||
            workspace.ask.sourceNavigation.loading,
    screenSessionId = workspace.ask.session.generation,
    conversationId = workspace.ask.conversation.activeConversationId,
    headMessageId = workspace.ask.conversation.headMessageId,
    messages = workspace.ask.conversation.messages,
    appMode = clientContext.appMode,
    clientContextGeneration = clientContext.generation,
)

internal fun SillageUiState.nextAskMemoSaveRequest(
    message: AskMessage,
    memoContent: String,
): AskMemoSaveRequest? = workspace.ask.memoSave.nextRequest(
    message,
    memoContent,
    askMemoSaveContext(),
)

internal fun SillageUiState.startAskMemoSave(request: AskMemoSaveRequest): SillageUiState {
    val pending = workspace.ask.memoSave.begin(request, askMemoSaveContext()) ?: return this
    return withAsk { it.beginMemoSave(pending) }.copy(
        error = null,
        notice = null,
    )
}

internal fun SillageUiState.canApplyAskMemoSave(request: AskMemoSaveRequest): Boolean {
    return workspace.ask.memoSave.canApply(request, askMemoSaveContext())
}

internal fun SillageUiState.finishAskMemoSave(request: AskMemoSaveRequest): SillageUiState {
    val finished = workspace.ask.finishMemoSave(request) ?: return this
    return withAsk { finished }
}

internal fun SillageUiState.askSourceNavigationContext(): AskSourceNavigationContext =
    AskSourceNavigationContext(
        destinationKey = clientContext.screen.name,
        destinationAvailable = clientContext.screen == Screen.Ask,
        historyKeys = clientContext.history.map(Screen::name),
        anotherRequestInProgress = loading ||
            workspace.ask.stream.sending ||
            workspace.ask.variant.loading,
        screenSessionId = workspace.ask.session.generation,
        conversationId = workspace.ask.conversation.activeConversationId,
        appMode = clientContext.appMode,
        clientContextGeneration = clientContext.generation,
    )

internal fun SillageUiState.nextAskSourceNavigationRequest(
    memoId: String,
): AskSourceNavigationRequest? {
    return workspace.ask.sourceNavigation.nextRequest(memoId, askSourceNavigationContext())
}

internal fun SillageUiState.canApplyAskSourceNavigation(
    request: AskSourceNavigationRequest,
): Boolean {
    return workspace.ask.sourceNavigation.canApply(request, askSourceNavigationContext())
}

internal fun SillageUiState.startAskSourceNavigation(
    request: AskSourceNavigationRequest,
): SillageUiState {
    val pending = workspace.ask.sourceNavigation.begin(request, askSourceNavigationContext())
        ?: return this
    return withAsk { it.beginSourceNavigation(pending) }.copy(
        error = null,
        notice = null,
    )
}

internal fun SillageUiState.finishAskSourceNavigation(
    request: AskSourceNavigationRequest,
): SillageUiState {
    val finished = workspace.ask.finishSourceNavigation(request) ?: return this
    return withAsk { finished }
}

/**
 * Opens an Ask source record in detail after a validated source-navigation
 * request, absorbing the memo into records presentation ownership.
 */
internal fun SillageUiState.openAskSourceDetail(
    request: AskSourceNavigationRequest,
    detail: RecordDetail,
): SillageUiState {
    val finished = finishAskSourceNavigation(request)
    if (finished === this) {
        return this
    }
    return finished.copy(
        clientContext = finished.clientContext.navigateTo(
            Screen.MemoDetail,
            request.destinationHistory(),
        ),
        workspace = finished.workspace.updateRecords {
            it.absorbVisibleMemo(
                memo = detail.memo,
                summary = detail.ai,
                filter = it.browse.filter,
            )
        },
    )
}

/**
 * Drops a resolved conflict and, when taking the server memo, updates the
 * selected record presentation if it is still open.
 */
internal fun SillageUiState.applyResolvedSyncConflict(
    resourceId: String,
    serverMemo: Memo? = null,
): SillageUiState {
    val withoutConflict = withSync { it.removeConflict(resourceId) }
    return if (serverMemo == null) {
        withoutConflict
    } else {
        withoutConflict.withRecords { it.replaceSelectedMemo(resourceId, serverMemo) }
    }
}

internal fun AskSourceNavigationRequest.destinationHistory(): List<Screen> {
    return destinationHistoryKeys().map(Screen::valueOf)
}

internal fun SillageUiState.shouldReturnToRecordsOnBack(): Boolean {
    return AppNavigationPolicy.shouldReturnToRecords(
        current = clientContext.screen,
        recordsCalendarActive = workspace.records.browse.viewMode == MemoViewMode.Calendar,
    )
}

typealias CompletedMemoSearch = CompletedRecordsSearch

typealias Screen = AppDestination
