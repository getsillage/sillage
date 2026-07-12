package app.sillage.ui

import app.sillage.data.AIProfileDraft
import app.sillage.data.Account
import app.sillage.data.AskConversation
import app.sillage.data.AskMessage
import app.sillage.data.Memo
import app.sillage.data.MemoAI
import app.sillage.data.MemoDetail
import app.sillage.data.MemoListFilter
import app.sillage.data.SessionStore
import app.sillage.data.memosForFilter
import java.time.LocalDate

data class SillageUiState(
    val screen: Screen,
    val screenHistory: List<Screen> = emptyList(),
    val baseUrl: String,
    val appMode: String = SessionStore.MODE_ONLINE,
    val serverReturnScreen: Screen? = null,
    val themeMode: String = SessionStore.THEME_LIGHT,
    val languageMode: String = SessionStore.LANGUAGE_ZH_CN,
    val initialized: Boolean? = null,
    val account: Account? = null,
    val memos: List<Memo> = emptyList(),
    val memoNextCursor: String = "",
    val loadingMoreMemos: Boolean = false,
    val memoListLoadStatus: MemoListLoadStatus = MemoListLoadStatus.Idle,
    val memoPageRequestId: Long = 0,
    val memoCacheGeneration: Long = 0,
    val memoDetailRequestId: Long = 0,
    val selectedMemo: Memo? = null,
    val selectedSummary: MemoAI? = null,
    val summaryLoading: Boolean = false,
    val uploadingAttachment: Boolean = false,
    val openingAttachmentPath: String? = null,
    val attachmentOpenRequestId: Long = 0,
    val aiProfiles: List<AIProfileDraft> = emptyList(),
    val aiAutoSummary: Boolean = false,
    val aiAutoSummarySaving: Boolean = false,
    val aiAutoSummaryRequestId: Long = 0,
    val aiSettingsLoading: Boolean = false,
    val aiSettingsLoadError: String? = null,
    val aiSettingsSaving: Boolean = false,
    val aiTestingProfileId: String = "",
    val aiLoadingModelsProfileId: String = "",
    val aiTestResults: Map<String, String> = emptyMap(),
    val aiModelResults: Map<String, List<String>> = emptyMap(),
    val askConversations: List<AskConversation> = emptyList(),
    val activeAskId: String = "",
    val askHeadId: String? = null,
    val askMessages: List<AskMessage> = emptyList(),
    val askQuestion: String = "",
    val askScope: String = "recent_30_days",
    val askSourceKind: String = "records",
    val askLoading: Boolean = false,
    val askLoadError: String? = null,
    val askSending: Boolean = false,
    val askStreaming: Boolean = false,
    val askStreamRequestId: Long = 0,
    val askVariantRequestId: Long = 0,
    val askVariantLoading: Boolean = false,
    val askRegeneratingId: String = "",
    val askLiveUser: AskMessage? = null,
    val askLiveAnswer: String = "",
    val askScreenSessionId: Long = 0,
    val askSourceRequestId: Long = 0,
    val askSourceLoading: Boolean = false,
    val editorSessionId: Long = 0,
    val draftContent: String = "",
    val draftEntryDate: String = LocalDate.now().toString(),
    val initialDraftContent: String = "",
    val initialDraftEntryDate: String = LocalDate.now().toString(),
    val markdownPreview: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<Memo>? = null,
    val searching: Boolean = false,
    val memoViewMode: MemoViewMode = MemoViewMode.List,
    val memoListFilter: MemoListFilter = MemoListFilter.Unarchived,
    val calendarYear: Int = LocalDate.now().year,
    val calendarMonth: Int = LocalDate.now().monthValue,
    val selectedCalendarDate: String? = null,
    val username: String = "",
    val displayName: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
)

internal fun SillageUiState.hasUnsavedMemoDraft(): Boolean {
    return screen == Screen.Editor &&
        (draftContent != initialDraftContent || draftEntryDate != initialDraftEntryDate)
}

internal fun SillageUiState.canRunMemoEditorAction(): Boolean {
    return screen == Screen.Editor && !loading && !uploadingAttachment
}

internal fun SillageUiState.canApplyAttachmentUpload(sessionId: Long): Boolean {
    return screen == Screen.Editor &&
        editorSessionId == sessionId &&
        uploadingAttachment
}

internal fun SillageUiState.canHandleAttachmentOpen(requestId: Long): Boolean {
    return openingAttachmentPath != null && attachmentOpenRequestId == requestId
}

internal fun SillageUiState.withAskStreamingStoppedNotice(message: String): SillageUiState {
    if (!askSending) {
        return this
    }
    return copy(error = null, notice = message)
}

internal data class MemoDetailRequest(
    val requestId: Long,
    val memoId: String,
    val memoVersion: Long,
    val appMode: String,
    val screen: Screen,
    val editorSessionId: Long,
    val cacheGeneration: Long,
)

internal fun SillageUiState.nextMemoDetailRequest(memoId: String): MemoDetailRequest? {
    val selected = selectedMemo ?: return null
    if (selected.id != memoId || (screen != Screen.MemoDetail && screen != Screen.Editor)) {
        return null
    }
    return MemoDetailRequest(
        requestId = memoDetailRequestId + 1,
        memoId = memoId,
        memoVersion = selected.version,
        appMode = appMode,
        screen = screen,
        editorSessionId = editorSessionId,
        cacheGeneration = memoCacheGeneration,
    )
}

internal fun SillageUiState.startMemoDetailRequest(request: MemoDetailRequest): SillageUiState {
    if (nextMemoDetailRequest(request.memoId) != request) {
        return this
    }
    return copy(
        memoDetailRequestId = request.requestId,
        summaryLoading = request.appMode != SessionStore.MODE_OFFLINE,
    )
}

private fun SillageUiState.matchesMemoDetailRequest(request: MemoDetailRequest): Boolean {
    return memoDetailRequestId == request.requestId &&
        selectedMemo?.id == request.memoId &&
        appMode == request.appMode &&
        screen == request.screen &&
        (request.screen != Screen.Editor || editorSessionId == request.editorSessionId)
}

internal fun SillageUiState.completeMemoDetailRequest(
    request: MemoDetailRequest,
    detail: MemoDetail,
): SillageUiState {
    if (!matchesMemoDetailRequest(request)) {
        return this
    }
    val currentVersion = selectedMemo?.version ?: return copy(summaryLoading = false)
    if (
        memoCacheGeneration != request.cacheGeneration ||
        detail.memo.id != request.memoId ||
        detail.memo.version < request.memoVersion ||
        detail.memo.version < currentVersion
    ) {
        return copy(summaryLoading = false)
    }
    return applyMemoToCache(detail.memo).copy(
        selectedSummary = detail.ai,
        summaryLoading = false,
    )
}

internal fun SillageUiState.failMemoDetailRequest(
    request: MemoDetailRequest,
    message: String,
): SillageUiState {
    if (!matchesMemoDetailRequest(request)) {
        return this
    }
    val superseded = memoCacheGeneration != request.cacheGeneration ||
        (selectedMemo?.version ?: Long.MIN_VALUE) > request.memoVersion
    return if (superseded) {
        copy(summaryLoading = false)
    } else {
        copy(summaryLoading = false, error = message)
    }
}

internal data class AIAutoSummaryRequest(
    val requestId: Long,
    val previousValue: Boolean,
    val targetValue: Boolean,
    val appMode: String,
)

internal fun SillageUiState.nextAIAutoSummaryRequest(targetValue: Boolean): AIAutoSummaryRequest? {
    if (aiSettingsLoading || aiAutoSummarySaving || targetValue == aiAutoSummary) {
        return null
    }
    return AIAutoSummaryRequest(
        requestId = aiAutoSummaryRequestId + 1,
        previousValue = aiAutoSummary,
        targetValue = targetValue,
        appMode = appMode,
    )
}

internal fun SillageUiState.startAIAutoSummaryRequest(request: AIAutoSummaryRequest): SillageUiState {
    if (
        aiSettingsLoading ||
        aiAutoSummarySaving ||
        request.requestId != aiAutoSummaryRequestId + 1 ||
        request.appMode != appMode ||
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
        appMode == request.appMode
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

internal data class MemoPageRequest(
    val requestId: Long,
    val cursor: String,
    val appMode: String,
    val filter: MemoListFilter,
    val cacheGeneration: Long,
)

internal fun SillageUiState.nextMemoPageRequest(): MemoPageRequest? {
    if (memoNextCursor.isBlank() || loadingMoreMemos || appMode == SessionStore.MODE_OFFLINE) {
        return null
    }
    return MemoPageRequest(
        requestId = memoPageRequestId + 1,
        cursor = memoNextCursor,
        appMode = appMode,
        filter = memoListFilter,
        cacheGeneration = memoCacheGeneration,
    )
}

internal fun SillageUiState.canApplyMemoPage(request: MemoPageRequest): Boolean {
    return loadingMoreMemos &&
        memoPageRequestId == request.requestId &&
        memoNextCursor == request.cursor &&
        appMode == request.appMode &&
        memoListFilter == request.filter &&
        memoCacheGeneration == request.cacheGeneration
}

internal data class MemoRefreshRequest(
    val pageRequestId: Long,
    val appMode: String,
    val filter: MemoListFilter,
    val cacheGeneration: Long,
)

internal fun SillageUiState.memoRefreshRequest(): MemoRefreshRequest {
    return MemoRefreshRequest(
        pageRequestId = memoPageRequestId,
        appMode = appMode,
        filter = memoListFilter,
        cacheGeneration = memoCacheGeneration,
    )
}

internal fun SillageUiState.canApplyMemoRefresh(request: MemoRefreshRequest): Boolean {
    return memoPageRequestId == request.pageRequestId &&
        appMode == request.appMode &&
        memoListFilter == request.filter &&
        memoCacheGeneration == request.cacheGeneration
}

internal data class MemoSearchRequest(
    val query: String,
    val appMode: String,
    val filter: MemoListFilter,
    val cacheGeneration: Long,
)

internal fun SillageUiState.memoSearchRequest(): MemoSearchRequest? {
    val query = searchQuery.trim()
    if (query.isBlank()) {
        return null
    }
    return MemoSearchRequest(
        query = query,
        appMode = appMode,
        filter = memoListFilter,
        cacheGeneration = memoCacheGeneration,
    )
}

internal fun SillageUiState.canApplyMemoSearch(request: MemoSearchRequest): Boolean {
    return searching &&
        searchQuery.trim() == request.query &&
        appMode == request.appMode &&
        memoListFilter == request.filter &&
        memoCacheGeneration == request.cacheGeneration
}

internal fun SillageUiState.failMemoSearch(
    request: MemoSearchRequest,
    message: String,
): SillageUiState {
    if (!canApplyMemoSearch(request)) {
        return this
    }
    return copy(searching = false, error = message)
}

internal fun SillageUiState.applyMemoToCache(memo: Memo): SillageUiState {
    val cached = memosForFilter(
        memos.filter { it.id != memo.id } + memo,
        memoListFilter,
    )
    val searched = searchResults?.let { results ->
        memosForFilter(
            results.filter { it.id != memo.id } + memo,
            memoListFilter,
        )
    }
    return copy(
        memos = cached,
        searchResults = searched,
        searching = false,
        loadingMoreMemos = false,
        memoListLoadStatus = MemoListLoadStatus.Idle,
        memoPageRequestId = memoPageRequestId + 1,
        memoCacheGeneration = memoCacheGeneration + 1,
        selectedMemo = if (selectedMemo?.id == memo.id) memo else selectedMemo,
    )
}

internal fun SillageUiState.shouldShowMemoListLoadFailure(): Boolean {
    return memoViewMode == MemoViewMode.List &&
        memoListLoadStatus == MemoListLoadStatus.Failed &&
        memos.isEmpty() &&
        searchResults == null
}

internal fun SillageUiState.shouldShowMemoSearchFailure(): Boolean {
    return memoViewMode == MemoViewMode.List &&
        searchQuery.isNotBlank() &&
        searchResults?.isEmpty() == true &&
        !searching &&
        error != null
}

internal data class AskStreamRequest(
    val requestId: Long,
    val screenSessionId: Long,
    val conversationId: String,
    val appMode: String,
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
    )
}

internal fun SillageUiState.canApplyAskStream(request: AskStreamRequest): Boolean {
    return askSending &&
        askStreamRequestId == request.requestId &&
        askScreenSessionId == request.screenSessionId &&
        activeAskId == request.conversationId &&
        appMode == request.appMode
}

internal data class AskVariantRequest(
    val requestId: Long,
    val screenSessionId: Long,
    val conversationId: String,
    val appMode: String,
)

internal fun SillageUiState.nextAskVariantRequest(): AskVariantRequest? {
    if (
        screen != Screen.Ask ||
        activeAskId.isBlank() ||
        askLoading ||
        askSending ||
        askVariantLoading ||
        askSourceLoading
    ) {
        return null
    }
    return AskVariantRequest(
        requestId = askVariantRequestId + 1,
        screenSessionId = askScreenSessionId,
        conversationId = activeAskId,
        appMode = appMode,
    )
}

internal fun SillageUiState.canApplyAskVariant(request: AskVariantRequest): Boolean {
    return screen == Screen.Ask &&
        askVariantLoading &&
        askVariantRequestId == request.requestId &&
        askScreenSessionId == request.screenSessionId &&
        activeAskId == request.conversationId &&
        appMode == request.appMode
}

internal data class AskSourceNavigationRequest(
    val requestId: Long,
    val screenSessionId: Long,
    val conversationId: String,
    val memoId: String,
    val appMode: String,
    val originScreen: Screen,
    val originHistory: List<Screen>,
) {
    fun destinationHistory(): List<Screen> = originHistory + originScreen
}

internal fun SillageUiState.nextAskSourceNavigationRequest(memoId: String): AskSourceNavigationRequest? {
    if (
        screen != Screen.Ask ||
        memoId.isBlank() ||
        loading ||
        askSending ||
        askVariantLoading ||
        askSourceLoading
    ) {
        return null
    }
    return AskSourceNavigationRequest(
        requestId = askSourceRequestId + 1,
        screenSessionId = askScreenSessionId,
        conversationId = activeAskId,
        memoId = memoId,
        appMode = appMode,
        originScreen = screen,
        originHistory = screenHistory.toList(),
    )
}

internal fun SillageUiState.canApplyAskSourceNavigation(request: AskSourceNavigationRequest): Boolean {
    return askSourceLoading &&
        askSourceRequestId == request.requestId &&
        askScreenSessionId == request.screenSessionId &&
        appMode == request.appMode &&
        screen == request.originScreen &&
        screenHistory == request.originHistory &&
        activeAskId == request.conversationId
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

enum class MemoViewMode {
    List,
    Calendar,
}

enum class MemoListLoadStatus {
    Idle,
    Loading,
    Failed,
}

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
