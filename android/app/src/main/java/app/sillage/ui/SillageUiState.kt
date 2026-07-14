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
    val clientContextGeneration: Long = 0,
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
    val memoMutationIds: Set<String> = emptySet(),
    val selectedMemo: Memo? = null,
    val selectedSummary: MemoAI? = null,
    val summaryLoading: Boolean = false,
    val memoSummaryRequestId: Long = 0,
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
    val aiSettingsRequestId: Long = 0,
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
    val askCompletionEventId: Long = 0,
    val askVariantRequestId: Long = 0,
    val askVariantLoading: Boolean = false,
    val askRegeneratingId: String = "",
    val askLiveUser: AskMessage? = null,
    val askLiveAnswer: String = "",
    val askScreenSessionId: Long = 0,
    val askSourceRequestId: Long = 0,
    val askSourceLoading: Boolean = false,
    val askMemoSaveRequestId: Long = 0,
    val askSavingMessageId: String = "",
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
    val authError: String? = null,
    val authErrorResourceId: Int? = null,
    val error: String? = null,
    val notice: String? = null,
)

internal fun SillageUiState.hasUnsavedMemoDraft(): Boolean {
    return screen == Screen.Editor &&
        (draftContent != initialDraftContent || draftEntryDate != initialDraftEntryDate)
}

internal fun SillageUiState.canRunMemoEditorAction(): Boolean {
    return screen == Screen.Editor &&
        !loading &&
        !uploadingAttachment &&
        selectedMemo?.id !in memoMutationIds
}

internal fun SillageUiState.isMemoMutationInProgress(memoId: String): Boolean {
    return memoId in memoMutationIds
}

internal fun SillageUiState.hasClientContextOperationInProgress(): Boolean {
    return loading ||
        summaryLoading ||
        memoMutationIds.isNotEmpty() ||
        askSavingMessageId.isNotBlank() ||
        aiSettingsSaving ||
        aiAutoSummarySaving ||
        aiTestingProfileId.isNotBlank() ||
        aiLoadingModelsProfileId.isNotBlank()
}

internal fun SillageUiState.canApplyAttachmentUpload(sessionId: Long): Boolean {
    return screen == Screen.Editor &&
        editorSessionId == sessionId &&
        uploadingAttachment
}

internal fun SillageUiState.canHandleAttachmentOpen(requestId: Long): Boolean {
    return openingAttachmentPath != null && attachmentOpenRequestId == requestId
}

internal fun SillageUiState.invalidateAttachmentOpenRequest(): SillageUiState {
    if (openingAttachmentPath == null) {
        return this
    }
    return copy(
        openingAttachmentPath = null,
        attachmentOpenRequestId = attachmentOpenRequestId + 1,
    )
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
    val clientContextGeneration: Long,
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
        clientContextGeneration = clientContextGeneration,
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
        clientContextGeneration == request.clientContextGeneration &&
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

internal data class MemoSummaryRequest(
    val requestId: Long,
    val memoId: String,
    val memoVersion: Long,
    val appMode: String,
    val clientContextGeneration: Long,
    val screen: Screen,
    val editorSessionId: Long,
    val memoDetailRequestId: Long,
)

internal fun SillageUiState.nextMemoSummaryRequest(): MemoSummaryRequest? {
    val memo = selectedMemo ?: return null
    if (summaryLoading || (screen != Screen.MemoDetail && screen != Screen.Editor)) {
        return null
    }
    return MemoSummaryRequest(
        requestId = memoSummaryRequestId + 1,
        memoId = memo.id,
        memoVersion = memo.version,
        appMode = appMode,
        clientContextGeneration = clientContextGeneration,
        screen = screen,
        editorSessionId = editorSessionId,
        memoDetailRequestId = memoDetailRequestId,
    )
}

internal fun SillageUiState.startMemoSummaryRequest(request: MemoSummaryRequest): SillageUiState {
    if (nextMemoSummaryRequest() != request) {
        return this
    }
    return copy(
        memoSummaryRequestId = request.requestId,
        summaryLoading = true,
        error = null,
        notice = null,
    )
}

private fun SillageUiState.ownsMemoSummaryRequest(request: MemoSummaryRequest): Boolean {
    return summaryLoading && memoSummaryRequestId == request.requestId
}

internal fun SillageUiState.canApplyMemoSummaryRequest(request: MemoSummaryRequest): Boolean {
    return ownsMemoSummaryRequest(request) &&
        selectedMemo?.id == request.memoId &&
        selectedMemo.version == request.memoVersion &&
        appMode == request.appMode &&
        clientContextGeneration == request.clientContextGeneration &&
        screen == request.screen &&
        editorSessionId == request.editorSessionId &&
        memoDetailRequestId == request.memoDetailRequestId
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
        selectedSummary = summary,
        summaryLoading = false,
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
    return copy(summaryLoading = false, error = message)
}

internal fun SillageUiState.finishMemoSummaryRequest(request: MemoSummaryRequest): SillageUiState {
    return if (ownsMemoSummaryRequest(request)) {
        copy(summaryLoading = false)
    } else {
        this
    }
}

internal fun SillageUiState.invalidateMemoSummaryRequest(): SillageUiState {
    if (!summaryLoading) {
        return this
    }
    return copy(
        summaryLoading = false,
        memoSummaryRequestId = memoSummaryRequestId + 1,
    )
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

internal data class MemoPageRequest(
    val requestId: Long,
    val cursor: String,
    val appMode: String,
    val clientContextGeneration: Long,
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
        clientContextGeneration = clientContextGeneration,
        filter = memoListFilter,
        cacheGeneration = memoCacheGeneration,
    )
}

internal fun SillageUiState.canApplyMemoPage(request: MemoPageRequest): Boolean {
    return loadingMoreMemos &&
        memoPageRequestId == request.requestId &&
        memoNextCursor == request.cursor &&
        appMode == request.appMode &&
        clientContextGeneration == request.clientContextGeneration &&
        memoListFilter == request.filter &&
        memoCacheGeneration == request.cacheGeneration
}

internal data class MemoRefreshRequest(
    val pageRequestId: Long,
    val appMode: String,
    val clientContextGeneration: Long,
    val filter: MemoListFilter,
    val cacheGeneration: Long,
)

internal fun SillageUiState.memoRefreshRequest(): MemoRefreshRequest {
    return MemoRefreshRequest(
        pageRequestId = memoPageRequestId,
        appMode = appMode,
        clientContextGeneration = clientContextGeneration,
        filter = memoListFilter,
        cacheGeneration = memoCacheGeneration,
    )
}

internal fun SillageUiState.canApplyMemoRefresh(request: MemoRefreshRequest): Boolean {
    return memoPageRequestId == request.pageRequestId &&
        appMode == request.appMode &&
        clientContextGeneration == request.clientContextGeneration &&
        memoListFilter == request.filter &&
        memoCacheGeneration == request.cacheGeneration
}

internal data class MemoSearchRequest(
    val query: String,
    val appMode: String,
    val clientContextGeneration: Long,
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
        clientContextGeneration = clientContextGeneration,
        filter = memoListFilter,
        cacheGeneration = memoCacheGeneration,
    )
}

internal fun SillageUiState.canApplyMemoSearch(request: MemoSearchRequest): Boolean {
    return searching &&
        searchQuery.trim() == request.query &&
        appMode == request.appMode &&
        clientContextGeneration == request.clientContextGeneration &&
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

internal data class AskVariantRequest(
    val requestId: Long,
    val screenSessionId: Long,
    val conversationId: String,
    val appMode: String,
    val clientContextGeneration: Long,
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
        clientContextGeneration = clientContextGeneration,
    )
}

internal fun SillageUiState.canApplyAskVariant(request: AskVariantRequest): Boolean {
    return screen == Screen.Ask &&
        askVariantLoading &&
        askVariantRequestId == request.requestId &&
        askScreenSessionId == request.screenSessionId &&
        activeAskId == request.conversationId &&
        appMode == request.appMode &&
        clientContextGeneration == request.clientContextGeneration
}

internal data class AskMemoSaveRequest(
    val requestId: Long,
    val screenSessionId: Long,
    val conversationId: String,
    val headMessageId: String?,
    val messageId: String,
    val sourceMessageContent: String,
    val memoContent: String,
    val appMode: String,
    val clientContextGeneration: Long,
)

internal fun SillageUiState.nextAskMemoSaveRequest(
    message: AskMessage,
    memoContent: String,
): AskMemoSaveRequest? {
    val currentMessage = askMessages.find { it.id == message.id }
    if (
        screen != Screen.Ask ||
        loading ||
        askLoading ||
        askSending ||
        askVariantLoading ||
        askSourceLoading ||
        askSavingMessageId.isNotBlank() ||
        activeAskId.isBlank() ||
        message.role != "assistant" ||
        message.conversationId != activeAskId ||
        currentMessage?.content != message.content ||
        memoContent.isBlank()
    ) {
        return null
    }
    return AskMemoSaveRequest(
        requestId = askMemoSaveRequestId + 1,
        screenSessionId = askScreenSessionId,
        conversationId = activeAskId,
        headMessageId = askHeadId,
        messageId = message.id,
        sourceMessageContent = message.content,
        memoContent = memoContent,
        appMode = appMode,
        clientContextGeneration = clientContextGeneration,
    )
}

internal fun SillageUiState.startAskMemoSave(request: AskMemoSaveRequest): SillageUiState {
    if (
        nextAskMemoSaveRequest(
            message = askMessages.find { it.id == request.messageId } ?: return this,
            memoContent = request.memoContent,
        ) != request
    ) {
        return this
    }
    return copy(
        askMemoSaveRequestId = request.requestId,
        askSavingMessageId = request.messageId,
        error = null,
        notice = null,
    )
}

internal fun SillageUiState.canApplyAskMemoSave(request: AskMemoSaveRequest): Boolean {
    return screen == Screen.Ask &&
        askMemoSaveRequestId == request.requestId &&
        askSavingMessageId == request.messageId &&
        askScreenSessionId == request.screenSessionId &&
        activeAskId == request.conversationId &&
        askHeadId == request.headMessageId &&
        appMode == request.appMode &&
        clientContextGeneration == request.clientContextGeneration &&
        askMessages.any {
            it.id == request.messageId && it.content == request.sourceMessageContent
        }
}

internal fun SillageUiState.finishAskMemoSave(request: AskMemoSaveRequest): SillageUiState {
    if (
        askMemoSaveRequestId != request.requestId ||
        askSavingMessageId != request.messageId
    ) {
        return this
    }
    return copy(askSavingMessageId = "")
}

internal data class AskSourceNavigationRequest(
    val requestId: Long,
    val screenSessionId: Long,
    val conversationId: String,
    val memoId: String,
    val appMode: String,
    val clientContextGeneration: Long,
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
        clientContextGeneration = clientContextGeneration,
        originScreen = screen,
        originHistory = screenHistory.toList(),
    )
}

internal fun SillageUiState.canApplyAskSourceNavigation(request: AskSourceNavigationRequest): Boolean {
    return askSourceLoading &&
        askSourceRequestId == request.requestId &&
        askScreenSessionId == request.screenSessionId &&
        appMode == request.appMode &&
        clientContextGeneration == request.clientContextGeneration &&
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

internal fun SillageUiState.shouldReturnToRecordsOnBack(): Boolean {
    return screen == Screen.Ask ||
        screen == Screen.AISettings ||
        (screen == Screen.Memos && memoViewMode == MemoViewMode.Calendar)
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
