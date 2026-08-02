package app.sillage.ui

import app.sillage.core.domain.auth.Account
import app.sillage.core.domain.ask.AskMessage
import app.sillage.features.ask.AskComposerStateHolder
import app.sillage.features.ask.AskFeatureStateHolder
import app.sillage.features.ask.AskLoadStateHolder
import app.sillage.features.ask.AskMemoSaveContext
import app.sillage.features.ask.AskMemoSaveRequest
import app.sillage.features.ask.AskSourceNavigationContext
import app.sillage.features.ask.AskSourceNavigationRequest
import app.sillage.features.ask.AskSourceNavigationStateHolder
import app.sillage.features.ask.AskStreamContext
import app.sillage.features.ask.AskStreamRequest
import app.sillage.features.ask.AskVariantContext
import app.sillage.features.ask.AskVariantRequest
import app.sillage.features.ask.AskVariantStateHolder
import app.sillage.features.auth.AuthFeatureStateHolder
import app.sillage.features.auth.AuthenticationStateHolder
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
import app.sillage.features.records.RecordsRefreshStatus
import app.sillage.features.records.CompletedRecordsSearch
import app.sillage.features.records.RecordsSearchContext
import app.sillage.features.records.RecordsSearchRequest
import app.sillage.features.records.RecordsSummaryContext
import app.sillage.features.records.RecordsSummaryRequest
import app.sillage.features.records.RecordsDetailContext
import app.sillage.features.records.RecordsDetailRequest
import app.sillage.features.records.RecordsDetailResponseDisposition
import app.sillage.features.records.RecordsAttachmentOpenRequest
import app.sillage.features.records.RecordsAttachmentOpenStateHolder
import app.sillage.features.records.RecordsBrowseStateHolder
import app.sillage.features.records.RecordsCollectionStateHolder
import app.sillage.features.records.RecordsEditorStateHolder
import app.sillage.features.records.RecordsEditorActionContext
import app.sillage.features.records.RecordsEditorBusyReason
import app.sillage.features.records.RecordsFeatureStateHolder
import app.sillage.features.records.RecordsMutationStateHolder
import app.sillage.features.records.RecordsPaginationStateHolder
import app.sillage.features.records.RecordsRefreshStateHolder
import app.sillage.features.records.RecordsSearchStateHolder
import app.sillage.features.records.RecordsSelectionStateHolder
import app.sillage.features.records.RecordsSummaryStateHolder
import app.sillage.features.records.canRunEditorAction
import app.sillage.features.records.editorBusyReason
import app.sillage.features.records.MemoViewMode
import app.sillage.data.SessionStore
import app.sillage.features.settings.AIAutoSummaryContext
import app.sillage.features.settings.AIAutoSummaryRequest
import app.sillage.features.settings.AIAutoSummaryStateHolder
import app.sillage.features.settings.AIProfileDraft
import app.sillage.features.settings.AIProfilesMutationContext
import app.sillage.features.settings.AIProfilesMutationRequest
import app.sillage.features.settings.AIProfilesMutationStateHolder
import app.sillage.features.settings.AISettingsLoadContext
import app.sillage.features.settings.AISettingsLoadRequest
import app.sillage.features.settings.AISettingsLoadStateHolder
import app.sillage.features.settings.AIProfileDiagnosticsContext
import app.sillage.features.settings.AIProfileDiagnosticsStateHolder
import app.sillage.features.settings.AIProfileModelsRequest
import app.sillage.features.settings.AIProfileTestRequest
import app.sillage.features.settings.SettingsFeatureStateHolder
import app.sillage.ui.appshell.AppAppearanceStateHolder
import app.sillage.ui.appshell.AppBackNavigation
import app.sillage.ui.appshell.AppDestination
import app.sillage.ui.appshell.AppNavigationPolicy
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
    val screen: Screen,
    val screenHistory: List<Screen> = emptyList(),
    val baseUrl: String,
    val appMode: String = SessionStore.MODE_ONLINE,
    val clientContextGeneration: Long = 0,
    val serverReturnScreen: Screen? = null,
    val appearance: AppAppearanceStateHolder = AppAppearanceStateHolder(),
    val initialized: Boolean? = null,
    val serverVersion: String = "",
    val serverRevision: String = "",
    val apiVersion: String = "",
    val minimumAndroidVersionCode: Int = 0,
    val androidUpdateRequired: Boolean = false,
    val account: Account? = null,
    val records: RecordsFeatureStateHolder = defaultRecordsFeatureState(),
    val settings: SettingsFeatureStateHolder = SettingsFeatureStateHolder(),
    val auth: AuthFeatureStateHolder = AuthFeatureStateHolder(),
    val ask: AskFeatureStateHolder = AskFeatureStateHolder(),
    val sync: SyncFeatureStateHolder = SyncFeatureStateHolder(),
    val loading: Boolean = false,
    val authError: String? = null,
    val authErrorResourceId: Int? = null,
    val error: String? = null,
    val notice: String? = null,
) {
    val themeMode: String
        get() = appearance.themeMode

    val languageMode: String
        get() = appearance.languageMode

    // Transitional slice accessors while hosts finish moving writes onto the
    // aggregate records/ask/settings/sync holders. Prefer the aggregates for
    // coordinated transitions.
    val recordsCollection: RecordsCollectionStateHolder get() = records.collection
    val recordsPagination: RecordsPaginationStateHolder get() = records.pagination
    val recordsRefresh: RecordsRefreshStateHolder get() = records.refresh
    val recordsSelection: RecordsSelectionStateHolder get() = records.selection
    val recordsMutation: RecordsMutationStateHolder get() = records.mutation
    val recordsSummary: RecordsSummaryStateHolder get() = records.summary
    val recordsAttachmentOpen: RecordsAttachmentOpenStateHolder get() = records.attachmentOpen
    val recordsEditor: RecordsEditorStateHolder get() = records.editor
    val recordsSearch: RecordsSearchStateHolder get() = records.search
    val recordsBrowse: RecordsBrowseStateHolder get() = records.browse
    val askComposer: AskComposerStateHolder get() = ask.composer
    val askLoad: AskLoadStateHolder get() = ask.load
    val askVariant: AskVariantStateHolder get() = ask.variant
    val askSourceNavigation: AskSourceNavigationStateHolder get() = ask.sourceNavigation
    val aiProfilesMutation: AIProfilesMutationStateHolder get() = settings.profilesMutation
    val aiAutoSummaryState: AIAutoSummaryStateHolder get() = settings.autoSummary
    val aiSettingsLoad: AISettingsLoadStateHolder get() = settings.load
    val aiProfileDiagnostics: AIProfileDiagnosticsStateHolder get() = settings.diagnostics
    val authentication: AuthenticationStateHolder get() = auth.authentication

    val memoNextCursor: String get() = records.pagination.nextCursor
    val memos: List<Memo> get() = records.collection.records
    val memoCacheGeneration: Long get() = records.collection.cacheGeneration
    val loadingMoreMemos: Boolean get() = records.pagination.loadingMore
    val memoPageRequestId: Long get() = records.pagination.requestId
    val memoListLoadStatus: MemoListLoadStatus get() = records.refresh.status
    val searchQuery: String get() = records.search.query
    val searchResults: List<Memo>? get() = records.search.results
    val searchResultQuery: String get() = records.search.resultQuery
    val searchFailureQuery: String get() = records.search.failureQuery
    val memoSearchRequestId: Long get() = records.search.requestId
    val searchCompletionEventId: Long get() = records.search.completionEventId
    val searching: Boolean get() = records.search.searching
    val selectedMemo: Memo? get() = records.selection.selectedMemo
    val memoDetailRequestId: Long get() = records.selection.detailRequestId
    val selectedSummary: MemoAI? get() = records.summary.summary
    val summaryLoading: Boolean get() = records.summary.loading
    val memoSummaryRequestId: Long get() = records.summary.requestId
    val uploadingAttachment: Boolean get() = records.editor.uploadingAttachment
    val editorSessionId: Long get() = records.editor.sessionId
    val draftContent: String get() = records.editor.draftContent
    val draftEntryDate: String get() = records.editor.draftEntryDate
    val initialDraftContent: String get() = records.editor.initialDraftContent
    val initialDraftEntryDate: String get() = records.editor.initialDraftEntryDate
    val markdownPreview: Boolean get() = records.editor.markdownPreview
    val memoMutationIds: Set<String> get() = records.mutation.activeMemoIds
    val memoViewMode: MemoViewMode get() = records.browse.viewMode
    val memoListFilter: MemoListFilter get() = records.browse.filter
    val calendarYear: Int get() = records.browse.calendarYear
    val calendarMonth: Int get() = records.browse.calendarMonth
    val selectedCalendarDate: String? get() = records.browse.selectedCalendarDate
    val openingAttachmentPath: String? get() = records.attachmentOpen.path
    val attachmentOpenRequestId: Long get() = records.attachmentOpen.requestId
    val askQuestion: String get() = ask.question
    val askScope: String get() = ask.contextScope
    val askSourceKind: String get() = ask.sourceKind
    val aiAutoSummary: Boolean get() = settings.autoSummaryEnabled
    val aiAutoSummarySaving: Boolean get() = settings.autoSummarySaving
    val aiAutoSummaryRequestId: Long get() = settings.autoSummaryRequestId
    val aiProfiles: List<AIProfileDraft> get() = settings.profiles
    val aiSettingsSaving: Boolean get() = settings.profilesSaving
    val aiSettingsRequestId: Long get() = settings.profilesRequestId
    val aiSettingsLoading: Boolean get() = settings.loading
    val aiSettingsLoadError: String? get() = settings.loadErrorMessage
    val aiTestingProfileId: String get() = settings.testingProfileKey
    val aiLoadingModelsProfileId: String get() = settings.loadingModelsProfileKey
    val aiTestResults: Map<String, String> get() = settings.testResults
    val aiModelResults: Map<String, List<String>> get() = settings.modelResults
    val username: String get() = auth.username
    val displayName: String get() = auth.displayName
    val password: String get() = auth.password
    val currentPassword: String get() = auth.currentPassword
    val newPassword: String get() = auth.newPassword
    val confirmPassword: String get() = auth.confirmPassword
    val passwordChanging: Boolean get() = auth.passwordChanging
}

/** Applies a pure records-feature transition without touching host-only fields. */
internal inline fun SillageUiState.withRecords(
    transform: (RecordsFeatureStateHolder) -> RecordsFeatureStateHolder,
): SillageUiState = copy(records = transform(records))

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
): SillageUiState = copy(ask = transform(ask))

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
): SillageUiState = copy(settings = transform(settings))

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
        records = records.clearInteractiveSurface(),
        settings = settings.clearWorkspace(
            profiles = settingsProfiles,
            autoSummaryEnabled = settingsAutoSummaryEnabled,
        ),
        ask = ask.clearWorkspace(
            invalidateStream = askInvalidateStream,
            invalidateVariant = askInvalidateVariant,
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
    val cleared = clearClientWorkspace(
        settingsProfiles = settingsProfiles,
        settingsAutoSummaryEnabled = settingsAutoSummaryEnabled,
    )
    return cleared.copy(
        records = cleared.records.replaceVisibleRecords(memos),
    )
}

/**
 * UI model for one push conflict: local pending content plus the server resource.
 */
typealias SyncConflictItem = MemoSyncConflictItem

internal typealias MemoEditorBusyReason = RecordsEditorBusyReason

internal fun SillageUiState.memoEditorBusyReason(): MemoEditorBusyReason? {
    return records.editorBusyReason(memoEditorActionContext())
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
    return records.canRunEditorAction(memoEditorActionContext())
}

internal fun SillageUiState.memoEditorActionContext(): RecordsEditorActionContext {
    return RecordsEditorActionContext(
        destinationAvailable = screen == Screen.Editor,
        hostOperationInProgress = loading,
    )
}

internal fun SillageUiState.isMemoMutationInProgress(memoId: String): Boolean {
    return recordsMutation.isActive(memoId)
}

internal fun SillageUiState.beginMemoMutation(memoId: String?): SillageUiState {
    return withRecords { it.beginMemoMutation(memoId) }
}

internal fun SillageUiState.finishMemoMutation(memoId: String?): SillageUiState {
    return withRecords { it.finishMemoMutation(memoId) }
}

internal fun SillageUiState.hasClientContextOperationInProgress(): Boolean {
    return loading ||
        summaryLoading ||
        recordsMutation.active ||
        ask.memoSave.savingMessageId.isNotBlank() ||
        aiSettingsSaving ||
        aiAutoSummarySaving ||
        aiTestingProfileId.isNotBlank() ||
        aiLoadingModelsProfileId.isNotBlank() ||
        passwordChanging
}

internal fun SillageUiState.nextPasswordChangeRequest(): PasswordChangeRequest? {
    return authentication.nextPasswordChangeRequest(passwordChangeContext())
}

internal fun SillageUiState.startPasswordChange(request: PasswordChangeRequest): SillageUiState {
    val started = authentication.beginPasswordChange(request, passwordChangeContext()) ?: return this
    return withAuth { it.copy(authentication = started) }
}

internal fun SillageUiState.canApplyPasswordChange(request: PasswordChangeRequest): Boolean {
    return authentication.canApplyPasswordChange(request, passwordChangeContext())
}

internal fun SillageUiState.completePasswordChange(request: PasswordChangeRequest): SillageUiState {
    val completed = authentication.completePasswordChange(
        request,
        passwordChangeContext(),
    ) ?: return this
    return withAuth { it.copy(authentication = completed) }
}

internal fun SillageUiState.failPasswordChange(request: PasswordChangeRequest): SillageUiState {
    val failed = authentication.failPasswordChange(request, passwordChangeContext()) ?: return this
    return withAuth { it.copy(authentication = failed) }
}

private fun SillageUiState.passwordChangeContext(): PasswordChangeContext {
    return PasswordChangeContext(
        appMode = appMode,
        clientContextGeneration = clientContextGeneration,
        online = appMode == SessionStore.MODE_ONLINE,
        anotherOperationInProgress = loading ||
            summaryLoading ||
            recordsMutation.active ||
            ask.memoSave.savingMessageId.isNotBlank() ||
            aiSettingsSaving ||
            aiAutoSummarySaving ||
            aiProfileDiagnostics.busy,
    )
}

internal fun SillageUiState.canApplyAttachmentUpload(sessionId: Long): Boolean {
    return screen == Screen.Editor && recordsEditor.canApplyAttachmentUpload(sessionId)
}

internal fun SillageUiState.canHandleAttachmentOpen(requestId: Long): Boolean {
    return recordsAttachmentOpen.owns(requestId)
}

internal fun SillageUiState.nextAttachmentOpenRequest(
    path: String,
): RecordsAttachmentOpenRequest? {
    return records.nextAttachmentOpenRequest(path)
}

internal fun SillageUiState.beginAttachmentOpenRequest(
    request: RecordsAttachmentOpenRequest,
): SillageUiState? {
    val nextRecords = records.beginAttachmentOpen(request) ?: return null
    return copy(records = nextRecords)
}

internal fun SillageUiState.completeAttachmentOpenRequest(
    requestId: Long,
): SillageUiState {
    val nextRecords = records.completeAttachmentOpen(requestId)
    return if (nextRecords === records) this else copy(records = nextRecords)
}

internal fun SillageUiState.invalidateAttachmentOpenRequest(): SillageUiState {
    val nextRecords = records.invalidateAttachmentOpen()
    return if (nextRecords === records) this else copy(records = nextRecords)
}

internal fun SillageUiState.withAskStreamingStoppedNotice(message: String): SillageUiState {
    if (!ask.stream.sending) {
        return this
    }
    return copy(error = null, notice = message)
}

private fun SillageUiState.recordsDetailContext(): RecordsDetailContext {
    val detailAvailable = screen == Screen.MemoDetail || screen == Screen.Editor
    return RecordsDetailContext(
        sourceKey = appMode,
        clientContextGeneration = clientContextGeneration,
        destinationKey = screen.name,
        destinationGeneration = if (screen == Screen.Editor) editorSessionId else 0,
        cacheGeneration = memoCacheGeneration,
        detailAvailable = detailAvailable,
    )
}

internal fun SillageUiState.nextMemoDetailRequest(memoId: String): RecordsDetailRequest? {
    return recordsSelection.nextDetailRequest(memoId, recordsDetailContext())
}

internal fun SillageUiState.startMemoDetailRequest(request: RecordsDetailRequest): SillageUiState {
    val selection = recordsSelection.beginDetailRequest(request, recordsDetailContext()) ?: return this
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
        recordsSelection.detailResponseDisposition(
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
    return when (recordsSelection.detailFailureDisposition(request, recordsDetailContext())) {
        RecordsDetailResponseDisposition.Ignore -> this
        RecordsDetailResponseDisposition.Superseded -> withRecords { it.finishDetailSummary() }
        RecordsDetailResponseDisposition.Apply -> withRecords { it.finishDetailSummary() }
            .copy(error = message)
    }
}

internal typealias MemoSummaryRequest = RecordsSummaryRequest

private fun SillageUiState.recordsSummaryContext(): RecordsSummaryContext {
    return RecordsSummaryContext(
        sourceKey = appMode,
        clientContextGeneration = clientContextGeneration,
        destinationKey = screen.name,
        destinationGeneration = if (screen == Screen.Editor) editorSessionId else 0,
        detailRequestId = memoDetailRequestId,
        summaryAvailable = screen == Screen.MemoDetail || screen == Screen.Editor,
    )
}

internal fun SillageUiState.nextMemoSummaryRequest(): MemoSummaryRequest? {
    return recordsSummary.nextRequest(selectedMemo, recordsSummaryContext())
}

internal fun SillageUiState.startMemoSummaryRequest(request: MemoSummaryRequest): SillageUiState {
    val summaryState = recordsSummary.begin(request, selectedMemo, recordsSummaryContext())
        ?: return this
    return withRecords { it.copy(summary = summaryState) }.copy(
        error = null,
        notice = null,
    )
}

private fun SillageUiState.ownsMemoSummaryRequest(request: MemoSummaryRequest): Boolean {
    return recordsSummary.owns(request)
}

internal fun SillageUiState.canApplyMemoSummaryRequest(request: MemoSummaryRequest): Boolean {
    return recordsSummary.canApply(request, selectedMemo, recordsSummaryContext())
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
                selectedMemo,
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
        it.copy(summary = it.summary.fail(request, selectedMemo, recordsSummaryContext()))
    }.copy(error = message)
}

internal fun SillageUiState.finishMemoSummaryRequest(request: MemoSummaryRequest): SillageUiState {
    if (!ownsMemoSummaryRequest(request)) return this
    return withRecords { it.copy(summary = it.summary.finish(request)) }
}

internal fun SillageUiState.invalidateMemoSummaryRequest(): SillageUiState {
    if (!summaryLoading) return this
    return withRecords { it.copy(summary = it.summary.invalidate()) }
}

internal fun SillageUiState.aiAutoSummaryContext(): AIAutoSummaryContext =
    AIAutoSummaryContext(
        appMode = appMode,
        clientContextGeneration = clientContextGeneration,
        anotherMutationInProgress = aiSettingsLoading ||
            aiSettingsSaving ||
            aiProfileDiagnostics.busy,
    )

internal fun SillageUiState.nextAIAutoSummaryRequest(
    targetValue: Boolean,
): AIAutoSummaryRequest? {
    return aiAutoSummaryState.nextRequest(targetValue, aiAutoSummaryContext())
}

internal fun SillageUiState.startAIAutoSummaryRequest(
    request: AIAutoSummaryRequest,
): SillageUiState {
    val pending = aiAutoSummaryState.begin(request, aiAutoSummaryContext()) ?: return this
    return withSettings { it.copy(autoSummary = pending) }
}

internal fun SillageUiState.canApplyAIAutoSummaryRequest(
    request: AIAutoSummaryRequest,
): Boolean {
    return aiAutoSummaryState.canApply(request, aiAutoSummaryContext())
}

internal fun SillageUiState.completeAIAutoSummaryRequest(
    request: AIAutoSummaryRequest,
    savedValue: Boolean,
): SillageUiState {
    val completed = aiAutoSummaryState.complete(
        request,
        savedValue,
        aiAutoSummaryContext(),
    ) ?: return this
    return withSettings { it.copy(autoSummary = completed) }
}

internal fun SillageUiState.failAIAutoSummaryRequest(request: AIAutoSummaryRequest): SillageUiState {
    val failed = aiAutoSummaryState.fail(request, aiAutoSummaryContext()) ?: return this
    return withSettings { it.copy(autoSummary = failed) }
}

internal fun SillageUiState.invalidateAIAutoSummaryRequest(): SillageUiState {
    return withSettings { it.copy(autoSummary = it.autoSummary.invalidate()) }
}

internal fun SillageUiState.nextAIProfilesMutationRequest(
    pendingProfiles: List<AIProfileDraft>,
    submittedProfiles: List<AIProfileDraft> = pendingProfiles,
): AIProfilesMutationRequest? {
    return aiProfilesMutation.nextRequest(
        pendingProfiles = pendingProfiles.toList(),
        context = aiProfilesMutationContext(),
        submittedProfiles = submittedProfiles.toList(),
    )
}

internal fun SillageUiState.startAIProfilesMutation(
    request: AIProfilesMutationRequest,
): SillageUiState {
    val started = aiProfilesMutation.begin(request, aiProfilesMutationContext()) ?: return this
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
    return aiProfilesMutation.canApply(request, aiProfilesMutationContext())
}

internal fun SillageUiState.completeAIProfilesMutation(
    request: AIProfilesMutationRequest,
    savedProfiles: List<AIProfileDraft>,
): SillageUiState {
    val completed = aiProfilesMutation.complete(
        request = request,
        savedProfiles = savedProfiles,
        context = aiProfilesMutationContext(),
    ) ?: return this
    return withSettings { it.copy(profilesMutation = completed) }
}

internal fun SillageUiState.failAIProfilesMutation(
    request: AIProfilesMutationRequest,
): SillageUiState {
    val failed = aiProfilesMutation.fail(
        request = request,
        context = aiProfilesMutationContext(),
    ) ?: return this
    return withSettings { it.copy(profilesMutation = failed) }
}

private fun SillageUiState.aiProfilesMutationContext(): AIProfilesMutationContext {
    return AIProfilesMutationContext(
        appMode = appMode,
        clientContextGeneration = clientContextGeneration,
        anotherOperationInProgress = loading ||
            aiSettingsLoading ||
            aiAutoSummarySaving ||
            aiProfileDiagnostics.busy,
    )
}

internal fun SillageUiState.nextAISettingsLoadRequest(): AISettingsLoadRequest? {
    return aiSettingsLoad.nextRequest(aiSettingsLoadContext())
}

internal fun SillageUiState.startAISettingsLoad(
    request: AISettingsLoadRequest,
): SillageUiState {
    val started = aiSettingsLoad.begin(request, aiSettingsLoadContext()) ?: return this
    return withSettings {
        it.copy(
            load = started,
            autoSummary = it.autoSummary.invalidate(),
            profilesMutation = it.profilesMutation.invalidate(),
        )
    }
}

internal fun SillageUiState.canApplyAISettingsLoad(request: AISettingsLoadRequest): Boolean {
    return aiSettingsLoad.canApply(request, aiSettingsLoadContext())
}

internal fun SillageUiState.completeAISettingsLoad(request: AISettingsLoadRequest): SillageUiState {
    val completed = aiSettingsLoad.complete(request, aiSettingsLoadContext()) ?: return this
    return withSettings { it.copy(load = completed) }
}

internal fun SillageUiState.failAISettingsLoad(
    request: AISettingsLoadRequest,
    message: String,
): SillageUiState {
    val failed = aiSettingsLoad.fail(request, message, aiSettingsLoadContext()) ?: return this
    return withSettings { it.copy(load = failed) }
}

internal fun SillageUiState.invalidateAISettingsLoad(): SillageUiState {
    return withSettings { it.copy(load = it.load.cancel()) }
}

private fun SillageUiState.aiSettingsLoadContext(): AISettingsLoadContext {
    return AISettingsLoadContext(
        appMode = appMode,
        clientContextGeneration = clientContextGeneration,
        anotherOperationInProgress = loading ||
            aiSettingsSaving ||
            aiAutoSummarySaving ||
            aiProfileDiagnostics.busy,
    )
}

internal fun SillageUiState.nextAIProfileTestRequest(index: Int): AIProfileTestRequest? {
    val profile = aiProfiles.getOrNull(index) ?: return null
    return aiProfileDiagnostics.nextTestRequest(profile, index, aiProfileDiagnosticsContext())
}

internal fun SillageUiState.startAIProfileTest(request: AIProfileTestRequest): SillageUiState {
    val started = aiProfileDiagnostics.beginTest(
        request,
        aiProfiles,
        aiProfileDiagnosticsContext(),
    ) ?: return this
    return withSettings { it.copy(diagnostics = started) }
}

internal fun SillageUiState.canApplyAIProfileTest(request: AIProfileTestRequest): Boolean {
    return aiProfileDiagnostics.canApplyTest(request, aiProfiles, aiProfileDiagnosticsContext())
}

internal fun SillageUiState.completeAIProfileTest(
    request: AIProfileTestRequest,
    message: String,
): SillageUiState {
    val completed = aiProfileDiagnostics.completeTest(
        request,
        message,
        aiProfiles,
        aiProfileDiagnosticsContext(),
    ) ?: return this
    return withSettings { it.copy(diagnostics = completed) }
}

internal fun SillageUiState.nextAIProfileModelsRequest(index: Int): AIProfileModelsRequest? {
    val profile = aiProfiles.getOrNull(index) ?: return null
    return aiProfileDiagnostics.nextModelsRequest(profile, index, aiProfileDiagnosticsContext())
}

internal fun SillageUiState.startAIProfileModels(request: AIProfileModelsRequest): SillageUiState {
    val started = aiProfileDiagnostics.beginModels(
        request,
        aiProfiles,
        aiProfileDiagnosticsContext(),
    ) ?: return this
    return withSettings { it.copy(diagnostics = started) }
}

internal fun SillageUiState.canApplyAIProfileModels(request: AIProfileModelsRequest): Boolean {
    return aiProfileDiagnostics.canApplyModels(request, aiProfiles, aiProfileDiagnosticsContext())
}

internal fun SillageUiState.completeAIProfileModels(
    request: AIProfileModelsRequest,
    models: List<String>,
    message: String,
): SillageUiState {
    val completed = aiProfileDiagnostics.completeModels(
        request,
        models,
        message,
        aiProfiles,
        aiProfileDiagnosticsContext(),
    ) ?: return this
    return withSettings { it.copy(diagnostics = completed) }
}

internal fun SillageUiState.failAIProfileModels(
    request: AIProfileModelsRequest,
    message: String,
): SillageUiState {
    val failed = aiProfileDiagnostics.failModels(
        request,
        message,
        aiProfiles,
        aiProfileDiagnosticsContext(),
    ) ?: return this
    return withSettings { it.copy(diagnostics = failed) }
}

private fun SillageUiState.aiProfileDiagnosticsContext(): AIProfileDiagnosticsContext {
    return AIProfileDiagnosticsContext(
        appMode = appMode,
        clientContextGeneration = clientContextGeneration,
        anotherOperationInProgress = loading ||
            aiSettingsLoading ||
            aiSettingsSaving ||
            aiAutoSummarySaving,
    )
}
private fun SillageUiState.recordsPageContext(): RecordsPageContext {
    return RecordsPageContext(
        sourceKey = appMode,
        sourceAvailable = appMode != SessionStore.MODE_OFFLINE,
        clientContextGeneration = clientContextGeneration,
        filter = memoListFilter,
        cacheGeneration = memoCacheGeneration,
    )
}

internal fun SillageUiState.nextMemoPageRequest(): RecordsPageRequest? {
    return recordsPagination.nextRequest(recordsPageContext())
}

internal fun SillageUiState.beginMemoPage(request: RecordsPageRequest): SillageUiState? {
    val pagination = recordsPagination.begin(request, recordsPageContext()) ?: return null
    return withRecords { it.copy(pagination = pagination) }.copy(
        error = null,
        notice = null,
    )
}

internal fun SillageUiState.canApplyMemoPage(request: RecordsPageRequest): Boolean {
    return recordsPagination.canApply(request, recordsPageContext())
}

internal fun SillageUiState.completeMemoPage(
    request: RecordsPageRequest,
    nextCursor: String,
): SillageUiState? {
    val pagination = recordsPagination.complete(request, recordsPageContext(), nextCursor) ?: return null
    return withRecords { it.copy(pagination = pagination) }
}

internal fun SillageUiState.failMemoPage(request: RecordsPageRequest): SillageUiState? {
    val pagination = recordsPagination.fail(request, recordsPageContext()) ?: return null
    return withRecords { it.copy(pagination = pagination) }
}

private fun SillageUiState.recordsRefreshContext(): RecordsRefreshContext {
    return RecordsRefreshContext(
        sourceKey = appMode,
        clientContextGeneration = clientContextGeneration,
        filter = memoListFilter,
        cacheGeneration = memoCacheGeneration,
        paginationRequestId = memoPageRequestId,
    )
}

internal fun SillageUiState.nextMemoRefreshRequest(): RecordsRefreshRequest {
    return recordsRefresh.nextRequest(recordsRefreshContext())
}

internal fun SillageUiState.beginMemoRefresh(request: RecordsRefreshRequest): SillageUiState? {
    val refresh = recordsRefresh.begin(request, recordsRefreshContext()) ?: return null
    return withRecords { it.copy(refresh = refresh) }
}

internal fun SillageUiState.canApplyMemoRefresh(request: RecordsRefreshRequest): Boolean {
    return recordsRefresh.canApply(request, recordsRefreshContext())
}

internal fun SillageUiState.completeMemoRefresh(request: RecordsRefreshRequest): SillageUiState? {
    val refresh = recordsRefresh.complete(request, recordsRefreshContext()) ?: return null
    return withRecords { it.copy(refresh = refresh) }
}

internal fun SillageUiState.failMemoRefresh(request: RecordsRefreshRequest): SillageUiState? {
    val refresh = recordsRefresh.fail(request, recordsRefreshContext()) ?: return null
    return withRecords { it.copy(refresh = refresh) }
}

private fun SillageUiState.recordsSearchContext(): RecordsSearchContext {
    return RecordsSearchContext(
        sourceKey = appMode,
        clientContextGeneration = clientContextGeneration,
        filter = memoListFilter,
        cacheGeneration = memoCacheGeneration,
    )
}

internal fun SillageUiState.nextMemoSearchRequest(): RecordsSearchRequest? {
    return recordsSearch.nextRequest(recordsSearchContext())
}

internal fun SillageUiState.startMemoSearch(request: RecordsSearchRequest): SillageUiState {
    val search = recordsSearch.begin(request, recordsSearchContext()) ?: return this
    return withRecords { it.copy(search = search) }.copy(
        error = null,
        notice = null,
    )
}

internal fun SillageUiState.canApplyMemoSearch(request: RecordsSearchRequest): Boolean {
    return recordsSearch.canApply(request, recordsSearchContext())
}

internal fun SillageUiState.currentMemoSearchResults(): List<Memo>? {
    return recordsSearch.currentResults()
}

internal fun SillageUiState.completedMemoSearch(): CompletedRecordsSearch? {
    return recordsSearch.completed()
}

internal fun SillageUiState.completeMemoSearch(
    request: RecordsSearchRequest,
    results: List<Memo>,
): SillageUiState {
    val search = recordsSearch.complete(request, recordsSearchContext(), results) ?: return this
    return withRecords { it.copy(search = search) }.copy(error = null)
}

internal fun SillageUiState.failMemoSearch(
    request: RecordsSearchRequest,
    message: String,
): SillageUiState {
    val search = recordsSearch.fail(request, recordsSearchContext()) ?: return this
    return withRecords { it.copy(search = search) }.copy(error = message)
}

internal fun SillageUiState.applyMemoToCache(memo: Memo): SillageUiState {
    return withRecords { it.applyCanonicalMemo(memo) }
}

internal fun SillageUiState.askStreamContext(): AskStreamContext = AskStreamContext(
    screenSessionId = ask.session.generation,
    conversationId = ask.conversation.activeConversationId,
    appMode = appMode,
    clientContextGeneration = clientContextGeneration,
    anotherRequestInProgress = ask.loading || ask.variant.loading || ask.sourceNavigation.loading,
)

internal fun SillageUiState.nextAskStreamRequest(): AskStreamRequest? {
    return ask.stream.nextRequest(askStreamContext())
}

internal fun SillageUiState.canApplyAskStream(request: AskStreamRequest): Boolean {
    return ask.stream.canApply(request, askStreamContext())
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
    destinationAvailable = screen == Screen.Ask,
    screenSessionId = ask.session.generation,
    conversationId = ask.conversation.activeConversationId,
    appMode = appMode,
    clientContextGeneration = clientContextGeneration,
    anotherRequestInProgress = ask.loading || ask.stream.sending || ask.sourceNavigation.loading,
)

internal fun SillageUiState.nextAskVariantRequest(): AskVariantRequest? {
    return askVariant.nextRequest(askVariantContext())
}

internal fun SillageUiState.canApplyAskVariant(request: AskVariantRequest): Boolean {
    return askVariant.canApply(request, askVariantContext())
}

internal fun SillageUiState.askMemoSaveContext(): AskMemoSaveContext = AskMemoSaveContext(
    destinationAvailable = screen == Screen.Ask,
    anotherRequestInProgress =
        loading ||
            ask.loading ||
            ask.stream.sending ||
            ask.variant.loading ||
            ask.sourceNavigation.loading,
    screenSessionId = ask.session.generation,
    conversationId = ask.conversation.activeConversationId,
    headMessageId = ask.conversation.headMessageId,
    messages = ask.conversation.messages,
    appMode = appMode,
    clientContextGeneration = clientContextGeneration,
)

internal fun SillageUiState.nextAskMemoSaveRequest(
    message: AskMessage,
    memoContent: String,
): AskMemoSaveRequest? = ask.memoSave.nextRequest(message, memoContent, askMemoSaveContext())

internal fun SillageUiState.startAskMemoSave(request: AskMemoSaveRequest): SillageUiState {
    val pending = ask.memoSave.begin(request, askMemoSaveContext()) ?: return this
    return withAsk { it.beginMemoSave(pending) }.copy(
        error = null,
        notice = null,
    )
}

internal fun SillageUiState.canApplyAskMemoSave(request: AskMemoSaveRequest): Boolean {
    return ask.memoSave.canApply(request, askMemoSaveContext())
}

internal fun SillageUiState.finishAskMemoSave(request: AskMemoSaveRequest): SillageUiState {
    val finished = ask.finishMemoSave(request) ?: return this
    return withAsk { finished }
}

internal fun SillageUiState.askSourceNavigationContext(): AskSourceNavigationContext =
    AskSourceNavigationContext(
        destinationKey = screen.name,
        destinationAvailable = screen == Screen.Ask,
        historyKeys = screenHistory.map(Screen::name),
        anotherRequestInProgress = loading || ask.stream.sending || ask.variant.loading,
        screenSessionId = ask.session.generation,
        conversationId = ask.conversation.activeConversationId,
        appMode = appMode,
        clientContextGeneration = clientContextGeneration,
    )

internal fun SillageUiState.nextAskSourceNavigationRequest(
    memoId: String,
): AskSourceNavigationRequest? {
    return askSourceNavigation.nextRequest(memoId, askSourceNavigationContext())
}

internal fun SillageUiState.canApplyAskSourceNavigation(
    request: AskSourceNavigationRequest,
): Boolean {
    return askSourceNavigation.canApply(request, askSourceNavigationContext())
}

internal fun SillageUiState.startAskSourceNavigation(
    request: AskSourceNavigationRequest,
): SillageUiState {
    val pending = askSourceNavigation.begin(request, askSourceNavigationContext())
        ?: return this
    return withAsk { it.beginSourceNavigation(pending) }.copy(
        error = null,
        notice = null,
    )
}

internal fun SillageUiState.finishAskSourceNavigation(
    request: AskSourceNavigationRequest,
): SillageUiState {
    val finished = ask.finishSourceNavigation(request) ?: return this
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
        screen = Screen.MemoDetail,
        screenHistory = request.destinationHistory(),
        records = finished.records.absorbVisibleMemo(
            memo = detail.memo,
            summary = detail.ai,
            filter = finished.memoListFilter,
        ),
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

internal typealias BackNavigation = AppBackNavigation

internal fun SillageUiState.historyFor(destination: Screen): List<Screen> {
    return AppNavigationPolicy.historyFor(screen, screenHistory, destination)
}

internal fun SillageUiState.backNavigation(fallback: Screen): BackNavigation {
    return AppNavigationPolicy.back(screenHistory, fallback)
}

internal fun SillageUiState.shouldReturnToRecordsOnBack(): Boolean {
    return AppNavigationPolicy.shouldReturnToRecords(
        current = screen,
        recordsCalendarActive = memoViewMode == MemoViewMode.Calendar,
    )
}

typealias MemoListLoadStatus = RecordsRefreshStatus
typealias CompletedMemoSearch = CompletedRecordsSearch

typealias Screen = AppDestination
