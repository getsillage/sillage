package app.sillage.ui

import app.sillage.data.AIProfileDraft
import app.sillage.data.Account
import app.sillage.core.domain.ask.AskConversation
import app.sillage.core.domain.ask.AskMessage
import app.sillage.features.ask.AskConversationStateHolder
import app.sillage.features.ask.AskMemoSaveContext
import app.sillage.features.ask.AskMemoSaveRequest
import app.sillage.features.ask.AskMemoSaveStateHolder
import app.sillage.features.ask.AskSourceNavigationContext
import app.sillage.features.ask.AskSourceNavigationRequest
import app.sillage.features.ask.AskSourceNavigationStateHolder
import app.sillage.features.ask.AskVariantContext
import app.sillage.features.ask.AskVariantRequest
import app.sillage.features.ask.AskVariantStateHolder
import app.sillage.features.sync.MemoSyncConflictItem
import app.sillage.features.sync.MemoSyncConflictStateHolder
import app.sillage.core.domain.records.Memo
import app.sillage.core.application.records.RecordDetail
import app.sillage.core.domain.records.MemoAI
import app.sillage.features.records.MemoListFilter
import app.sillage.features.records.RecordsPageContext
import app.sillage.features.records.RecordsPageRequest
import app.sillage.features.records.RecordsPaginationStateHolder
import app.sillage.features.records.RecordsRefreshContext
import app.sillage.features.records.RecordsRefreshRequest
import app.sillage.features.records.RecordsRefreshStateHolder
import app.sillage.features.records.RecordsRefreshStatus
import app.sillage.features.records.CompletedRecordsSearch
import app.sillage.features.records.RecordsSearchContext
import app.sillage.features.records.RecordsSearchRequest
import app.sillage.features.records.RecordsSearchStateHolder
import app.sillage.features.records.RecordsSummaryContext
import app.sillage.features.records.RecordsSummaryRequest
import app.sillage.features.records.RecordsSummaryStateHolder
import app.sillage.features.records.RecordsDetailContext
import app.sillage.features.records.RecordsDetailRequest
import app.sillage.features.records.RecordsDetailResponseDisposition
import app.sillage.features.records.RecordsCollectionStateHolder
import app.sillage.features.records.RecordsAttachmentOpenStateHolder
import app.sillage.features.records.RecordsBrowseStateHolder
import app.sillage.features.records.MemoViewMode
import app.sillage.features.records.RecordsEditorStateHolder
import app.sillage.features.records.RecordsMutationStateHolder
import app.sillage.features.records.RecordsSelectionStateHolder
import app.sillage.data.SessionStore
import app.sillage.features.records.memosForFilter
import java.time.LocalDate

data class SillageUiState(
    val screen: Screen,
    val screenHistory: List<Screen> = emptyList(),
    val baseUrl: String,
    val appMode: String = SessionStore.MODE_ONLINE,
    val clientContextGeneration: Long = 0,
    val serverReturnScreen: Screen? = null,
    val themeMode: String = SessionStore.THEME_LIGHT,
    val languageMode: String = SessionStore.LANGUAGE_ZH_CN,
    val initialized: Boolean? = null,
    val serverVersion: String = "",
    val serverRevision: String = "",
    val apiVersion: String = "",
    val minimumAndroidVersionCode: Int = 0,
    val androidUpdateRequired: Boolean = false,
    val account: Account? = null,
    val recordsCollection: RecordsCollectionStateHolder = RecordsCollectionStateHolder(),
    val recordsPagination: RecordsPaginationStateHolder = RecordsPaginationStateHolder(),
    val recordsRefresh: RecordsRefreshStateHolder = RecordsRefreshStateHolder(),
    val recordsSelection: RecordsSelectionStateHolder = RecordsSelectionStateHolder(),
    val recordsMutation: RecordsMutationStateHolder = RecordsMutationStateHolder(),
    val recordsSummary: RecordsSummaryStateHolder = RecordsSummaryStateHolder(),
    val recordsAttachmentOpen: RecordsAttachmentOpenStateHolder =
        RecordsAttachmentOpenStateHolder(),
    val aiProfiles: List<AIProfileDraft> = emptyList(),
    val aiAutoSummary: Boolean = false,
    val aiAutoSummarySaving: Boolean = false,
    val aiAutoSummaryRequestId: Long = 0,
    val aiSettingsLoading: Boolean = false,
    val aiSettingsLoadError: String? = null,
    val aiSettingsSaving: Boolean = false,
    val aiSettingsRequestId: Long = 0,
    val aiTestingProfileId: String = "",
    val aiLoadingModelsProfileId: String = "",
    val aiTestResults: Map<String, String> = emptyMap(),
    val aiModelResults: Map<String, List<String>> = emptyMap(),
    val askConversation: AskConversationStateHolder = AskConversationStateHolder(),
    val askQuestion: String = "",
    val askScope: String = "recent_30_days",
    val askSourceKind: String = "records",
    val askLoading: Boolean = false,
    val askLoadError: String? = null,
    val askSending: Boolean = false,
    val askStreaming: Boolean = false,
    val askStreamRequestId: Long = 0,
    val askCompletionEventId: Long = 0,
    val askVariant: AskVariantStateHolder = AskVariantStateHolder(),
    val askRegeneratingId: String = "",
    val askLiveUser: AskMessage? = null,
    val askLiveAnswer: String = "",
    val askScreenSessionId: Long = 0,
    val askSourceNavigation: AskSourceNavigationStateHolder = AskSourceNavigationStateHolder(),
    val askMemoSave: AskMemoSaveStateHolder = AskMemoSaveStateHolder(),
    val recordsEditor: RecordsEditorStateHolder = RecordsEditorStateHolder(
        draftEntryDate = LocalDate.now().toString(),
        initialDraftEntryDate = LocalDate.now().toString(),
    ),
    val recordsSearch: RecordsSearchStateHolder = RecordsSearchStateHolder(),
    val recordsBrowse: RecordsBrowseStateHolder = RecordsBrowseStateHolder(
        calendarYear = LocalDate.now().year,
        calendarMonth = LocalDate.now().monthValue,
    ),
    val username: String = "",
    val displayName: String = "",
    val password: String = "",
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val passwordChanging: Boolean = false,
    val loading: Boolean = false,
    val authError: String? = null,
    val authErrorResourceId: Int? = null,
    val error: String? = null,
    val notice: String? = null,
    /** Open sync version conflicts awaiting an explicit user choice. */
    val syncConflictState: MemoSyncConflictStateHolder = MemoSyncConflictStateHolder(),
) {
    // Transitional read accessors while the remaining records state moves into
    // shared feature holders. Pagination, refresh, search, and selection writes
    // use those holders.
    val memoNextCursor: String get() = recordsPagination.nextCursor
    val memos: List<Memo> get() = recordsCollection.records
    val memoCacheGeneration: Long get() = recordsCollection.cacheGeneration
    val loadingMoreMemos: Boolean get() = recordsPagination.loadingMore
    val memoPageRequestId: Long get() = recordsPagination.requestId
    val memoListLoadStatus: MemoListLoadStatus get() = recordsRefresh.status
    val searchQuery: String get() = recordsSearch.query
    val searchResults: List<Memo>? get() = recordsSearch.results
    val searchResultQuery: String get() = recordsSearch.resultQuery
    val searchFailureQuery: String get() = recordsSearch.failureQuery
    val memoSearchRequestId: Long get() = recordsSearch.requestId
    val searchCompletionEventId: Long get() = recordsSearch.completionEventId
    val searching: Boolean get() = recordsSearch.searching
    val selectedMemo: Memo? get() = recordsSelection.selectedMemo
    val memoDetailRequestId: Long get() = recordsSelection.detailRequestId
    val selectedSummary: MemoAI? get() = recordsSummary.summary
    val summaryLoading: Boolean get() = recordsSummary.loading
    val memoSummaryRequestId: Long get() = recordsSummary.requestId
    val uploadingAttachment: Boolean get() = recordsEditor.uploadingAttachment
    val editorSessionId: Long get() = recordsEditor.sessionId
    val draftContent: String get() = recordsEditor.draftContent
    val draftEntryDate: String get() = recordsEditor.draftEntryDate
    val initialDraftContent: String get() = recordsEditor.initialDraftContent
    val initialDraftEntryDate: String get() = recordsEditor.initialDraftEntryDate
    val markdownPreview: Boolean get() = recordsEditor.markdownPreview
    val memoMutationIds: Set<String> get() = recordsMutation.activeMemoIds
    val memoViewMode: MemoViewMode get() = recordsBrowse.viewMode
    val memoListFilter: MemoListFilter get() = recordsBrowse.filter
    val calendarYear: Int get() = recordsBrowse.calendarYear
    val calendarMonth: Int get() = recordsBrowse.calendarMonth
    val selectedCalendarDate: String? get() = recordsBrowse.selectedCalendarDate
    val openingAttachmentPath: String? get() = recordsAttachmentOpen.path
    val attachmentOpenRequestId: Long get() = recordsAttachmentOpen.requestId
    val syncConflicts: List<MemoSyncConflictItem> get() = syncConflictState.items
    val askConversations: List<AskConversation> get() = askConversation.conversations
    val activeAskId: String get() = askConversation.activeConversationId
    val askHeadId: String? get() = askConversation.headMessageId
    val askMessages: List<AskMessage> get() = askConversation.messages
    val askVariantRequestId: Long get() = askVariant.requestId
    val askVariantLoading: Boolean get() = askVariant.loading
    val askMemoSaveRequestId: Long get() = askMemoSave.requestId
    val askSavingMessageId: String get() = askMemoSave.savingMessageId
    val askSourceRequestId: Long get() = askSourceNavigation.requestId
    val askSourceLoading: Boolean get() = askSourceNavigation.loading
}

/**
 * UI model for one push conflict: local pending content plus the server resource.
 */
typealias SyncConflictItem = MemoSyncConflictItem

internal fun AIProfileDraft.uiKey(index: Int): String {
    return id.ifBlank { draftKey.ifBlank { "new-$index" } }
}

internal fun SillageUiState.hasUnsavedMemoDraft(): Boolean {
    return screen == Screen.Editor && recordsEditor.dirty
}

internal enum class MemoEditorBusyReason {
    AttachmentUpload,
    Operation,
}

internal fun SillageUiState.memoEditorBusyReason(): MemoEditorBusyReason? {
    if (screen != Screen.Editor) {
        return null
    }
    return when {
        uploadingAttachment -> MemoEditorBusyReason.AttachmentUpload
        loading || selectedMemo?.id in memoMutationIds -> MemoEditorBusyReason.Operation
        else -> null
    }
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
    return screen == Screen.Editor && memoEditorBusyReason() == null
}

internal fun SillageUiState.isMemoMutationInProgress(memoId: String): Boolean {
    return recordsMutation.isActive(memoId)
}

internal fun SillageUiState.hasClientContextOperationInProgress(): Boolean {
    return loading ||
        summaryLoading ||
        recordsMutation.active ||
        askSavingMessageId.isNotBlank() ||
        aiSettingsSaving ||
        aiAutoSummarySaving ||
        aiTestingProfileId.isNotBlank() ||
        aiLoadingModelsProfileId.isNotBlank()
}

internal fun SillageUiState.canApplyAttachmentUpload(sessionId: Long): Boolean {
    return screen == Screen.Editor && recordsEditor.canApplyAttachmentUpload(sessionId)
}

internal fun SillageUiState.canHandleAttachmentOpen(requestId: Long): Boolean {
    return recordsAttachmentOpen.owns(requestId)
}

internal fun SillageUiState.invalidateAttachmentOpenRequest(): SillageUiState {
    val attachmentOpen = recordsAttachmentOpen.invalidate()
    if (attachmentOpen === recordsAttachmentOpen) {
        return this
    }
    return copy(
        recordsAttachmentOpen = attachmentOpen,
    )
}

internal fun SillageUiState.withAskStreamingStoppedNotice(message: String): SillageUiState {
    if (!askSending) {
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
    return copy(
        recordsSelection = selection,
        recordsSummary = recordsSummary.beginDetailLoad(
            loadSummary = request.sourceKey != SessionStore.MODE_OFFLINE,
        ),
    )
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
        RecordsDetailResponseDisposition.Superseded -> copy(
            recordsSummary = recordsSummary.finishDetail(),
        )
        RecordsDetailResponseDisposition.Apply -> applyMemoToCache(detail.memo).copy(
            recordsSummary = recordsSummary.completeDetail(detail.ai),
        )
    }
}

internal fun SillageUiState.failMemoDetailRequest(
    request: RecordsDetailRequest,
    message: String,
): SillageUiState {
    return when (recordsSelection.detailFailureDisposition(request, recordsDetailContext())) {
        RecordsDetailResponseDisposition.Ignore -> this
        RecordsDetailResponseDisposition.Superseded -> copy(
            recordsSummary = recordsSummary.finishDetail(),
        )
        RecordsDetailResponseDisposition.Apply -> copy(
            recordsSummary = recordsSummary.finishDetail(),
            error = message,
        )
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
    return copy(
        recordsSummary = summaryState,
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
    return copy(
        recordsSummary = recordsSummary.complete(
            request,
            selectedMemo,
            recordsSummaryContext(),
            summary,
        ),
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
    return copy(
        recordsSummary = recordsSummary.fail(request, selectedMemo, recordsSummaryContext()),
        error = message,
    )
}

internal fun SillageUiState.finishMemoSummaryRequest(request: MemoSummaryRequest): SillageUiState {
    if (!ownsMemoSummaryRequest(request)) return this
    return copy(recordsSummary = recordsSummary.finish(request))
}

internal fun SillageUiState.invalidateMemoSummaryRequest(): SillageUiState {
    if (!summaryLoading) return this
    return copy(recordsSummary = recordsSummary.invalidate())
}

internal data class AIAutoSummaryRequest(
    val requestId: Long,
    val previousValue: Boolean,
    val targetValue: Boolean,
    val appMode: String,
    val clientContextGeneration: Long,
)

internal fun SillageUiState.nextAIAutoSummaryRequest(targetValue: Boolean): AIAutoSummaryRequest? {
    if (aiSettingsLoading || aiSettingsSaving || aiAutoSummarySaving || targetValue == aiAutoSummary) {
        return null
    }
    return AIAutoSummaryRequest(
        requestId = aiAutoSummaryRequestId + 1,
        previousValue = aiAutoSummary,
        targetValue = targetValue,
        appMode = appMode,
        clientContextGeneration = clientContextGeneration,
    )
}

internal fun SillageUiState.startAIAutoSummaryRequest(request: AIAutoSummaryRequest): SillageUiState {
    if (
        aiSettingsLoading ||
        aiSettingsSaving ||
        aiAutoSummarySaving ||
        request.requestId != aiAutoSummaryRequestId + 1 ||
        request.appMode != appMode ||
        request.clientContextGeneration != clientContextGeneration ||
        request.previousValue != aiAutoSummary
    ) {
        return this
    }
    return copy(
        aiAutoSummary = request.targetValue,
        aiAutoSummarySaving = true,
        aiAutoSummaryRequestId = request.requestId,
    )
}

internal fun SillageUiState.canApplyAIAutoSummaryRequest(request: AIAutoSummaryRequest): Boolean {
    return aiAutoSummarySaving &&
        aiAutoSummaryRequestId == request.requestId &&
        appMode == request.appMode &&
        clientContextGeneration == request.clientContextGeneration
}

internal fun SillageUiState.completeAIAutoSummaryRequest(
    request: AIAutoSummaryRequest,
    savedValue: Boolean,
): SillageUiState {
    if (!canApplyAIAutoSummaryRequest(request)) {
        return this
    }
    return copy(aiAutoSummary = savedValue, aiAutoSummarySaving = false)
}

internal fun SillageUiState.failAIAutoSummaryRequest(request: AIAutoSummaryRequest): SillageUiState {
    if (!canApplyAIAutoSummaryRequest(request)) {
        return this
    }
    return copy(aiAutoSummary = request.previousValue, aiAutoSummarySaving = false)
}

internal fun SillageUiState.invalidateAIAutoSummaryRequest(): SillageUiState {
    return copy(
        aiAutoSummarySaving = false,
        aiAutoSummaryRequestId = aiAutoSummaryRequestId + 1,
    )
}

internal data class AIProfilesMutationRequest(
    val requestId: Long,
    val appMode: String,
    val clientContextGeneration: Long,
    val previousProfiles: List<AIProfileDraft>,
    val pendingProfiles: List<AIProfileDraft>,
    val submittedProfiles: List<AIProfileDraft>,
)

internal fun SillageUiState.nextAIProfilesMutationRequest(
    pendingProfiles: List<AIProfileDraft>,
    submittedProfiles: List<AIProfileDraft> = pendingProfiles,
): AIProfilesMutationRequest? {
    if (loading || aiSettingsLoading || aiSettingsSaving || aiAutoSummarySaving) {
        return null
    }
    return AIProfilesMutationRequest(
        requestId = aiSettingsRequestId + 1,
        appMode = appMode,
        clientContextGeneration = clientContextGeneration,
        previousProfiles = aiProfiles.toList(),
        pendingProfiles = pendingProfiles.toList(),
        submittedProfiles = submittedProfiles.toList(),
    )
}

internal fun SillageUiState.startAIProfilesMutation(
    request: AIProfilesMutationRequest,
): SillageUiState {
    if (
        loading ||
        aiSettingsLoading ||
        aiSettingsSaving ||
        aiAutoSummarySaving ||
        request.requestId != aiSettingsRequestId + 1 ||
        request.appMode != appMode ||
        request.clientContextGeneration != clientContextGeneration ||
        request.previousProfiles != aiProfiles
    ) {
        return this
    }
    return copy(
        aiProfiles = request.pendingProfiles,
        aiSettingsLoading = false,
        aiSettingsLoadError = null,
        aiSettingsSaving = true,
        aiSettingsRequestId = request.requestId,
    )
}

internal fun SillageUiState.canApplyAIProfilesMutation(
    request: AIProfilesMutationRequest,
): Boolean {
    return aiSettingsSaving &&
        aiSettingsRequestId == request.requestId &&
        appMode == request.appMode &&
        clientContextGeneration == request.clientContextGeneration
}

internal fun SillageUiState.completeAIProfilesMutation(
    request: AIProfilesMutationRequest,
    savedProfiles: List<AIProfileDraft>,
): SillageUiState {
    if (!canApplyAIProfilesMutation(request)) {
        return this
    }
    return copy(
        aiProfiles = if (aiProfiles == request.pendingProfiles) savedProfiles else aiProfiles,
        aiSettingsSaving = false,
    )
}

internal fun SillageUiState.failAIProfilesMutation(
    request: AIProfilesMutationRequest,
): SillageUiState {
    if (!canApplyAIProfilesMutation(request)) {
        return this
    }
    return copy(
        aiProfiles = if (aiProfiles == request.pendingProfiles) request.previousProfiles else aiProfiles,
        aiSettingsSaving = false,
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
    return copy(recordsPagination = pagination, error = null, notice = null)
}

internal fun SillageUiState.canApplyMemoPage(request: RecordsPageRequest): Boolean {
    return recordsPagination.canApply(request, recordsPageContext())
}

internal fun SillageUiState.completeMemoPage(
    request: RecordsPageRequest,
    nextCursor: String,
): SillageUiState? {
    val pagination = recordsPagination.complete(request, recordsPageContext(), nextCursor) ?: return null
    return copy(recordsPagination = pagination)
}

internal fun SillageUiState.failMemoPage(request: RecordsPageRequest): SillageUiState? {
    val pagination = recordsPagination.fail(request, recordsPageContext()) ?: return null
    return copy(recordsPagination = pagination)
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
    return copy(recordsRefresh = refresh)
}

internal fun SillageUiState.canApplyMemoRefresh(request: RecordsRefreshRequest): Boolean {
    return recordsRefresh.canApply(request, recordsRefreshContext())
}

internal fun SillageUiState.completeMemoRefresh(request: RecordsRefreshRequest): SillageUiState? {
    val refresh = recordsRefresh.complete(request, recordsRefreshContext()) ?: return null
    return copy(recordsRefresh = refresh)
}

internal fun SillageUiState.failMemoRefresh(request: RecordsRefreshRequest): SillageUiState? {
    val refresh = recordsRefresh.fail(request, recordsRefreshContext()) ?: return null
    return copy(recordsRefresh = refresh)
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
    return copy(recordsSearch = search, error = null, notice = null)
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
    return copy(recordsSearch = search, error = null)
}

internal fun SillageUiState.failMemoSearch(
    request: RecordsSearchRequest,
    message: String,
): SillageUiState {
    val search = recordsSearch.fail(request, recordsSearchContext()) ?: return this
    return copy(recordsSearch = search, error = message)
}

internal fun SillageUiState.applyMemoToCache(memo: Memo): SillageUiState {
    return copy(
        recordsCollection = recordsCollection.applyMemo(memo, memoListFilter),
        recordsSearch = recordsSearch.invalidateForMemoChange(memo, memoListFilter),
        recordsPagination = recordsPagination.cancel(),
        recordsRefresh = recordsRefresh.cancel(),
        recordsSelection = recordsSelection.mergeMemo(memo),
    )
}

internal fun SillageUiState.shouldShowMemoListLoadFailure(): Boolean {
    return memoViewMode == MemoViewMode.List &&
        searchQuery.isBlank() &&
        memoListLoadStatus == MemoListLoadStatus.Failed &&
        memos.isEmpty() &&
        searchResults == null
}

internal fun SillageUiState.shouldShowMemoSearchFailure(): Boolean {
    val query = searchQuery.trim()
    return memoViewMode == MemoViewMode.List &&
        query.isNotBlank() &&
        query == searchFailureQuery.trim() &&
        !searching &&
        currentMemoSearchResults() == null
}

internal data class AskStreamRequest(
    val requestId: Long,
    val screenSessionId: Long,
    val conversationId: String,
    val appMode: String,
    val clientContextGeneration: Long,
)

internal fun SillageUiState.nextAskStreamRequest(): AskStreamRequest? {
    if (askLoading || askSending || askVariantLoading || askSourceLoading) {
        return null
    }
    return AskStreamRequest(
        requestId = askStreamRequestId + 1,
        screenSessionId = askScreenSessionId,
        conversationId = activeAskId,
        appMode = appMode,
        clientContextGeneration = clientContextGeneration,
    )
}

internal fun SillageUiState.canApplyAskStream(request: AskStreamRequest): Boolean {
    return askSending &&
        askStreamRequestId == request.requestId &&
        askScreenSessionId == request.screenSessionId &&
        activeAskId == request.conversationId &&
        appMode == request.appMode &&
        clientContextGeneration == request.clientContextGeneration
}

internal fun SillageUiState.finishAskStream(
    answerAvailable: Boolean,
    clearQuestion: Boolean,
): SillageUiState {
    val completed = answerAvailable && error == null && notice == null
    return copy(
        askQuestion = if (clearQuestion && error == null) "" else askQuestion,
        askSending = false,
        askStreaming = false,
        askRegeneratingId = "",
        askLiveUser = null,
        askLiveAnswer = "",
        askCompletionEventId = if (completed) askCompletionEventId + 1 else askCompletionEventId,
    )
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
    screenSessionId = askScreenSessionId,
    conversationId = activeAskId,
    appMode = appMode,
    clientContextGeneration = clientContextGeneration,
    anotherRequestInProgress = askLoading || askSending || askSourceLoading,
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
        loading || askLoading || askSending || askVariantLoading || askSourceLoading,
    screenSessionId = askScreenSessionId,
    conversationId = activeAskId,
    headMessageId = askHeadId,
    messages = askMessages,
    appMode = appMode,
    clientContextGeneration = clientContextGeneration,
)

internal fun SillageUiState.nextAskMemoSaveRequest(
    message: AskMessage,
    memoContent: String,
): AskMemoSaveRequest? = askMemoSave.nextRequest(message, memoContent, askMemoSaveContext())

internal fun SillageUiState.startAskMemoSave(request: AskMemoSaveRequest): SillageUiState {
    val pending = askMemoSave.begin(request, askMemoSaveContext()) ?: return this
    return copy(
        askMemoSave = pending,
        error = null,
        notice = null,
    )
}

internal fun SillageUiState.canApplyAskMemoSave(request: AskMemoSaveRequest): Boolean {
    return askMemoSave.canApply(request, askMemoSaveContext())
}

internal fun SillageUiState.finishAskMemoSave(request: AskMemoSaveRequest): SillageUiState {
    val finished = askMemoSave.finish(request) ?: return this
    return copy(askMemoSave = finished)
}

internal fun SillageUiState.askSourceNavigationContext(): AskSourceNavigationContext =
    AskSourceNavigationContext(
        destinationKey = screen.name,
        destinationAvailable = screen == Screen.Ask,
        historyKeys = screenHistory.map(Screen::name),
        anotherRequestInProgress = loading || askSending || askVariantLoading,
        screenSessionId = askScreenSessionId,
        conversationId = activeAskId,
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
    return copy(
        askSourceNavigation = pending,
        error = null,
        notice = null,
    )
}

internal fun SillageUiState.finishAskSourceNavigation(
    request: AskSourceNavigationRequest,
): SillageUiState {
    val finished = askSourceNavigation.finish(request) ?: return this
    return copy(askSourceNavigation = finished)
}

internal fun AskSourceNavigationRequest.destinationHistory(): List<Screen> {
    return destinationHistoryKeys().map(Screen::valueOf)
}

internal data class BackNavigation(
    val screen: Screen,
    val history: List<Screen>,
)

internal fun SillageUiState.historyFor(destination: Screen): List<Screen> {
    return if (screen == destination) screenHistory else screenHistory + screen
}

internal fun SillageUiState.backNavigation(fallback: Screen): BackNavigation {
    return BackNavigation(
        screen = screenHistory.lastOrNull() ?: fallback,
        history = if (screenHistory.isEmpty()) emptyList() else screenHistory.dropLast(1),
    )
}

internal fun SillageUiState.shouldReturnToRecordsOnBack(): Boolean {
    return screen == Screen.Ask ||
        screen == Screen.AISettings ||
        (screen == Screen.Memos && memoViewMode == MemoViewMode.Calendar)
}

typealias MemoListLoadStatus = RecordsRefreshStatus
typealias CompletedMemoSearch = CompletedRecordsSearch

enum class Screen {
    Loading,
    ModeSelection,
    Server,
    Initialize,
    Login,
    Memos,
    MemoDetail,
    Editor,
    AISettings,
    Ask,
}
