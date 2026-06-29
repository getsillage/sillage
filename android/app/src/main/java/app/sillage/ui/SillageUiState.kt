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
    val baseUrl: String,
    val appMode: String = SessionStore.MODE_ONLINE,
    val serverReturnScreen: Screen? = null,
    val themeMode: String = SessionStore.THEME_LIGHT,
    val initialized: Boolean? = null,
    val account: Account? = null,
    val memos: List<Memo> = emptyList(),
    val memoNextCursor: String = "",
    val loadingMoreMemos: Boolean = false,
    val selectedMemo: Memo? = null,
    val selectedSummary: MemoAI? = null,
    val summaryLoading: Boolean = false,
    val uploadingAttachment: Boolean = false,
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
    val askRegeneratingId: String = "",
    val askLiveUser: AskMessage? = null,
    val askLiveAnswer: String = "",
    val draftContent: String = "",
    val draftEntryDate: String = LocalDate.now().toString(),
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
