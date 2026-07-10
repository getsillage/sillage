package app.sillage.ui

import app.sillage.data.AIProfileDraft
import app.sillage.data.Account
import app.sillage.data.AskConversation
import app.sillage.data.AskMessage
import app.sillage.data.Memo
import app.sillage.data.MemoAI
import app.sillage.data.SessionStore
import java.time.LocalDate

data class SillageUiState(
    val screen: Screen,
    val screenHistory: List<Screen> = emptyList(),
    val baseUrl: String,
    val appMode: String = SessionStore.MODE_ONLINE,
    val serverReturnScreen: Screen? = null,
    val themeMode: String = SessionStore.THEME_LIGHT,
    val initialized: Boolean? = null,
    val account: Account? = null,
    val memos: List<Memo> = emptyList(),
    val memoNextCursor: String = "",
    val loadingMoreMemos: Boolean = false,
    val memoPageRequestId: Long = 0,
    val selectedMemo: Memo? = null,
    val selectedSummary: MemoAI? = null,
    val summaryLoading: Boolean = false,
    val uploadingAttachment: Boolean = false,
    val openingAttachmentPath: String? = null,
    val attachmentOpenRequestId: Long = 0,
    val aiProfiles: List<AIProfileDraft> = emptyList(),
    val aiAutoSummary: Boolean = false,
    val aiSettingsLoading: Boolean = false,
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

internal data class MemoPageRequest(
    val requestId: Long,
    val cursor: String,
    val appMode: String,
)

internal fun SillageUiState.nextMemoPageRequest(): MemoPageRequest? {
    if (memoNextCursor.isBlank() || loadingMoreMemos || appMode == SessionStore.MODE_OFFLINE) {
        return null
    }
    return MemoPageRequest(
        requestId = memoPageRequestId + 1,
        cursor = memoNextCursor,
        appMode = appMode,
    )
}

internal fun SillageUiState.canApplyMemoPage(request: MemoPageRequest): Boolean {
    return loadingMoreMemos &&
        memoPageRequestId == request.requestId &&
        memoNextCursor == request.cursor &&
        appMode == request.appMode
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
