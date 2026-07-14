package app.sillage.ui

import app.sillage.R
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.sillage.data.Account
import app.sillage.data.AIProfileDraft
import app.sillage.data.AskConversation
import app.sillage.data.AskMessage
import app.sillage.data.AttachmentUpload
import app.sillage.data.DownloadedAttachment
import app.sillage.data.LocalAiClient
import app.sillage.data.LocalDataStore
import app.sillage.data.MarkdownLinkTarget
import app.sillage.data.Memo
import app.sillage.data.MemoAI
import app.sillage.data.MemoListFilter
import app.sillage.data.MarkdownFormatStyle
import app.sillage.data.SessionStore
import app.sillage.data.SillageApi
import app.sillage.data.SillageExportCodec
import app.sillage.data.SyncPushSummary
import app.sillage.data.askAnswerMemoContent
import app.sillage.data.askBranchLeafId
import app.sillage.data.attachmentMarkdown
import app.sillage.data.buildAskActivePath
import app.sillage.data.firstBlankAIProfileNameIndex
import app.sillage.data.isActive
import app.sillage.data.lastAssistantMessageId
import app.sillage.data.markdownFormatSnippet
import app.sillage.data.memosForFilter
import app.sillage.data.mergeSavedAIProfilesForLocalStorage
import app.sillage.data.preferredAttachmentFilename
import app.sillage.data.resolveAttachmentMimeType
import app.sillage.data.toDraft
import app.sillage.data.toInput
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SillageViewModel(context: Context) : ViewModel() {
    private val appContext = context.applicationContext
    private val sessionStore = SessionStore(appContext)
    private val localDataStore = LocalDataStore(appContext)
    private val localAiClient = LocalAiClient()
    private val api = SillageApi(sessionStore)
    private var askStreamJob: Job? = null
    private var searchJob: Job? = null
    private var attachmentOpenJob: Job? = null
    private var loadMoreMemosJob: Job? = null
    private var aiAutoSummaryJob: Job? = null
    private var memoSummaryJob: Job? = null
    private val authOperationGate = SingleFlightGate()
    private val askMemoSaveGate = KeyedSingleFlightGate<Long>()
    private val aiProfilesMutationGate = KeyedSingleFlightGate<Long>()
    private val memoMutationGate = KeyedSingleFlightGate<MemoMutationKey>()
    private val memoPageLock = Any()
    private val _attachmentOpenEvents = Channel<AttachmentOpenEvent>(Channel.BUFFERED)
    private val _toastEvents = Channel<UiToastEvent>(
        capacity = TOAST_EVENT_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val toastEventEmitter = UiToastEventEmitter { event ->
        _toastEvents.trySend(event)
    }
    private val stateUpdateLock = Any()
    private val _state = MutableStateFlow(
        SillageUiState(
            screen = Screen.Loading,
            baseUrl = sessionStore.baseUrl(),
            account = sessionStore.account(),
            themeMode = sessionStore.themeMode(),
            languageMode = sessionStore.languageMode(),
            appMode = sessionStore.appMode(),
        ),
    )

    val state: StateFlow<SillageUiState> = _state.asStateFlow()
    internal val attachmentOpenEvents: Flow<AttachmentOpenEvent> = _attachmentOpenEvents.receiveAsFlow()
    internal val toastEvents: Flow<UiToastEvent> = _toastEvents.receiveAsFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            pruneAttachmentOpenCache(File(appContext.cacheDir, OPEN_ATTACHMENTS_CACHE_DIRECTORY))
        }
        if (!sessionStore.hasAppModeSelection()) {
            updateState { it.copy(screen = Screen.ModeSelection) }
        } else if (sessionStore.appMode() == SessionStore.MODE_OFFLINE) {
            enterOfflineMode(notice = null)
        } else {
            connect()
        }
    }

    private inline fun updateState(
        forceFeedback: Boolean = false,
        noticeType: UiToastType = UiToastType.SUCCESS,
        transform: (SillageUiState) -> SillageUiState,
    ) {
        synchronized(stateUpdateLock) {
            val before = _state.value
            val after = transform(before)
            _state.value = after
            toastEventEmitter.onStateChanged(
                before = before,
                after = after,
                forceFeedback = forceFeedback,
                noticeType = noticeType,
            )
        }
    }

    fun chooseOnlineMode() {
        updateState {
            it.copy(
                appMode = SessionStore.MODE_ONLINE,
                screen = Screen.Server,
                screenHistory = emptyList(),
                authError = null,
                authErrorResourceId = null,
                error = null,
                notice = null,
            )
        }
    }

    fun updateBaseUrl(value: String) {
        updateState { it.copy(baseUrl = value, authError = null, authErrorResourceId = null) }
    }

    fun saveServer() {
        if (state.value.hasClientContextOperationInProgress()) {
            return
        }
        val normalized = SessionStore.normalizeBaseUrl(state.value.baseUrl)
        if (normalized.isBlank()) {
            updateState {
                it.copy(
                    authError = uiString(R.string.error_server_required),
                    authErrorResourceId = R.string.error_server_required,
                    error = null,
                    notice = null,
                )
            }
            return
        }
        cancelAttachmentOpen()
        cancelMemoPageLoad()
        cancelAskVariant()
        cancelAskStream()
        cancelAIAutoSummarySave()
        sessionStore.saveBaseUrl(state.value.baseUrl)
        sessionStore.saveAppMode(SessionStore.MODE_ONLINE)
        updateState {
            it.invalidateAIAutoSummaryRequest().copy(
                appMode = SessionStore.MODE_ONLINE,
                clientContextGeneration = it.clientContextGeneration + 1,
                baseUrl = sessionStore.baseUrl(),
                account = null,
                memos = emptyList(),
                memoNextCursor = "",
                loadingMoreMemos = false,
                memoListLoadStatus = MemoListLoadStatus.Idle,
                memoMutationIds = emptySet(),
                selectedMemo = null,
                selectedSummary = null,
                summaryLoading = false,
                uploadingAttachment = false,
                aiProfiles = emptyList(),
                aiAutoSummary = false,
                aiSettingsLoading = false,
                aiSettingsLoadError = null,
                aiSettingsSaving = false,
                aiSettingsRequestId = it.aiSettingsRequestId + 1,
                aiTestingProfileId = "",
                aiLoadingModelsProfileId = "",
                aiTestResults = emptyMap(),
                aiModelResults = emptyMap(),
                askConversations = emptyList(),
                activeAskId = "",
                askHeadId = null,
                askMessages = emptyList(),
                askQuestion = "",
                askLoading = false,
                askLoadError = null,
                askScreenSessionId = it.askScreenSessionId + 1,
                askSourceRequestId = it.askSourceRequestId + 1,
                askSourceLoading = false,
                askMemoSaveRequestId = it.askMemoSaveRequestId + 1,
                askSavingMessageId = "",
                serverReturnScreen = null,
                searchQuery = "",
                searchResults = null,
                searching = false,
                authError = null,
                authErrorResourceId = null,
                error = null,
                notice = null,
            )
        }
        connect()
    }

    fun useOnlineMode() {
        if (state.value.hasClientContextOperationInProgress()) {
            return
        }
        cancelAskVariant()
        cancelAskStream()
        cancelAIAutoSummarySave()
        sessionStore.saveAppMode(SessionStore.MODE_ONLINE)
        updateState {
            it.invalidateAIAutoSummaryRequest().copy(
                appMode = SessionStore.MODE_ONLINE,
                clientContextGeneration = it.clientContextGeneration + 1,
                screen = Screen.Loading,
                screenHistory = emptyList(),
                memos = emptyList(),
                memoNextCursor = "",
                loadingMoreMemos = false,
                memoListLoadStatus = MemoListLoadStatus.Idle,
                memoMutationIds = emptySet(),
                selectedMemo = null,
                selectedSummary = null,
                aiProfiles = emptyList(),
                aiAutoSummary = false,
                aiSettingsLoading = false,
                aiSettingsLoadError = null,
                aiSettingsSaving = false,
                aiSettingsRequestId = it.aiSettingsRequestId + 1,
                aiTestingProfileId = "",
                aiLoadingModelsProfileId = "",
                aiTestResults = emptyMap(),
                aiModelResults = emptyMap(),
                askConversations = emptyList(),
                activeAskId = "",
                askHeadId = null,
                askMessages = emptyList(),
                askQuestion = "",
                askLoading = false,
                askLoadError = null,
                askScreenSessionId = it.askScreenSessionId + 1,
                askSourceRequestId = it.askSourceRequestId + 1,
                askSourceLoading = false,
                askMemoSaveRequestId = it.askMemoSaveRequestId + 1,
                askSavingMessageId = "",
                searchQuery = "",
                searchResults = null,
                authError = null,
                authErrorResourceId = null,
                error = null,
                notice = null,
            )
        }
        connect()
    }

    fun useOfflineMode() {
        if (state.value.hasClientContextOperationInProgress()) {
            return
        }
        cancelAttachmentOpen()
        cancelMemoPageLoad()
        cancelAskVariant()
        cancelAskStream()
        sessionStore.saveAppMode(SessionStore.MODE_OFFLINE)
        enterOfflineMode(notice = uiString(R.string.notice_offline_enabled))
    }

    fun openServerSettings() {
        if (state.value.hasClientContextOperationInProgress()) {
            return
        }
        cancelAttachmentOpen()
        updateState {
            it.copy(
                screen = Screen.Server,
                serverReturnScreen = it.screen.takeIf { screen -> screen != Screen.Server && screen != Screen.ModeSelection },
                authError = null,
                authErrorResourceId = null,
                error = null,
                notice = null,
            )
        }
    }

    fun cancelServerConnection() {
        updateState {
            val target = when {
                it.serverReturnScreen != null -> it.serverReturnScreen
                sessionStore.hasAppModeSelection() && sessionStore.appMode() == SessionStore.MODE_OFFLINE -> Screen.Memos
                else -> Screen.ModeSelection
            }
            it.copy(
                screen = target,
                appMode = sessionStore.appMode(),
                serverReturnScreen = null,
                baseUrl = sessionStore.baseUrl(),
                authError = null,
                authErrorResourceId = null,
                error = null,
                notice = null,
            )
        }
    }

    fun openAISettings() {
        if (state.value.askVariantLoading) {
            return
        }
        cancelMemoSummary()
        cancelAttachmentOpen()
        updateState {
            it.copy(
                screen = Screen.AISettings,
                screenHistory = emptyList(),
                summaryLoading = false,
                error = null,
                notice = null,
            )
        }
        loadAISettings()
    }

    fun openAsk() {
        val current = state.value
        val reloadConversations = !current.askLoading && !current.askSending && !current.askVariantLoading
        cancelMemoSummary()
        cancelAttachmentOpen()
        updateState {
            it.copy(
                screen = Screen.Ask,
                screenHistory = emptyList(),
                summaryLoading = false,
                askScreenSessionId = if (it.askLoading || it.askSending || it.askVariantLoading) {
                    it.askScreenSessionId
                } else {
                    it.askScreenSessionId + 1
                },
                askSourceRequestId = it.askSourceRequestId + 1,
                askSourceLoading = false,
                error = null,
                notice = null,
            )
        }
        if (reloadConversations) {
            loadAskConversations()
        }
    }

    fun toggleThemeMode() {
        val next = if (state.value.themeMode == SessionStore.THEME_DARK) {
            SessionStore.THEME_LIGHT
        } else {
            SessionStore.THEME_DARK
        }
        sessionStore.saveThemeMode(next)
        updateState { it.copy(themeMode = next) }
    }

    fun setLanguageMode(value: String) {
        val next = SessionStore.normalizeLanguageMode(value)
        if (state.value.languageMode == next) {
            return
        }
        sessionStore.saveLanguageMode(next)
        updateState {
            it.copy(
                languageMode = next,
                authError = it.authErrorResourceId?.let { resourceId ->
                    appContext.localizedString(next, resourceId)
                } ?: it.authError,
                error = null,
                notice = null,
            )
        }
    }

    fun toggleLanguageMode() {
        setLanguageMode(
            if (state.value.languageMode == SessionStore.LANGUAGE_ZH_CN) {
                SessionStore.LANGUAGE_EN
            } else {
                SessionStore.LANGUAGE_ZH_CN
            },
        )
    }

    private fun uiString(resourceId: Int, vararg formatArgs: Any): String {
        return appContext.localizedString(state.value.languageMode, resourceId, *formatArgs)
    }

    fun connect() {
        if (state.value.appMode == SessionStore.MODE_OFFLINE) {
            enterOfflineMode(notice = null)
            return
        }
        if (SessionStore.normalizeBaseUrl(state.value.baseUrl).isBlank()) {
            updateState {
                it.copy(
                    screen = Screen.Server,
                    authError = null,
                    authErrorResourceId = null,
                    error = null,
                    notice = null,
                )
            }
            return
        }
        launchAuthBusy {
            val initialized = api.bootstrap(state.value.baseUrl)
            val token = sessionStore.accessToken()
            val account = sessionStore.account()
            if (!initialized) {
                updateState { it.copy(screen = Screen.Initialize, initialized = false, account = null) }
                return@launchAuthBusy
            }
            if (token.isNullOrBlank() || account == null) {
                updateState { it.copy(screen = Screen.Login, initialized = true, account = null) }
                return@launchAuthBusy
            }
            val verified = api.me()
            updateState {
                it.copy(
                    screen = Screen.Memos,
                    initialized = true,
                    account = verified,
                    memoListLoadStatus = MemoListLoadStatus.Loading,
                    notice = uiString(R.string.notice_connected),
                )
            }
            refreshMemos()
        }
    }

    fun updateUsername(value: String) = updateState {
        it.copy(username = value, authError = null, authErrorResourceId = null)
    }

    fun updateDisplayName(value: String) = updateState {
        it.copy(displayName = value, authError = null, authErrorResourceId = null)
    }

    fun updatePassword(value: String) = updateState {
        it.copy(password = value, authError = null, authErrorResourceId = null)
    }

    fun initialize() {
        val current = state.value
        launchAuthBusy {
            val session = api.initialize(current.username, current.displayName, current.password)
            updateState {
                it.copy(
                    account = session.account,
                    username = "",
                    displayName = "",
                    password = "",
                    screen = Screen.Memos,
                    screenHistory = emptyList(),
                    initialized = true,
                    memoListLoadStatus = MemoListLoadStatus.Loading,
                    notice = uiString(R.string.notice_account_initialized),
                )
            }
            refreshMemos()
        }
    }

    fun signIn() {
        val current = state.value
        launchAuthBusy {
            val session = api.signIn(current.username, current.password)
            updateState {
                it.copy(
                    account = session.account,
                    username = "",
                    password = "",
                    screen = Screen.Memos,
                    screenHistory = emptyList(),
                    initialized = true,
                    memoListLoadStatus = MemoListLoadStatus.Loading,
                    notice = uiString(R.string.notice_signed_in),
                )
            }
            refreshMemos()
        }
    }

    fun signOut() {
        val current = state.value
        if (current.hasClientContextOperationInProgress()) {
            return
        }
        val lease = authOperationGate.tryAcquire() ?: return
        val clientContextGeneration = current.clientContextGeneration
        val offlineMode = current.appMode == SessionStore.MODE_OFFLINE
        val clientSessionSnapshot = sessionStore.clientSessionSnapshot()
        updateState {
            if (it.clientContextGeneration == clientContextGeneration) {
                it.copy(loading = true, error = null, notice = null)
            } else {
                it
            }
        }
        cancelAttachmentOpen()
        cancelMemoPageLoad()
        cancelAskVariant()
        cancelAskStream()
        cancelAIAutoSummarySave()
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            runSingleFlightOperation(
                lease = lease,
                onFailure = { error ->
                    updateState {
                        if (it.clientContextGeneration == clientContextGeneration) {
                            it.copy(error = error.readableMessage())
                        } else {
                            it
                        }
                    }
                },
                onFinished = {
                    updateState {
                        if (it.clientContextGeneration == clientContextGeneration) {
                            it.copy(loading = false)
                        } else {
                            it
                        }
                    }
                },
            ) {
                val feedback = performSignOut(
                    offlineMode = offlineMode,
                    remoteSignOut = { api.signOut(clientSessionSnapshot) },
                    clearLocalSession = { sessionStore.clearSession(clientSessionSnapshot) },
                ) ?: return@runSingleFlightOperation
                updateState {
                    if (it.clientContextGeneration != clientContextGeneration) {
                        it
                    } else {
                        it.invalidateAIAutoSummaryRequest().copy(
                            clientContextGeneration = it.clientContextGeneration + 1,
                            account = null,
                            memos = emptyList(),
                            memoNextCursor = "",
                            loadingMoreMemos = false,
                            memoListLoadStatus = MemoListLoadStatus.Idle,
                            memoMutationIds = emptySet(),
                            selectedMemo = null,
                            selectedSummary = null,
                            summaryLoading = false,
                            uploadingAttachment = false,
                            aiProfiles = emptyList(),
                            aiAutoSummary = if (offlineMode) localDataStore.autoSummaryEnabled() else false,
                            aiSettingsLoading = false,
                            aiSettingsLoadError = null,
                            aiSettingsSaving = false,
                            aiSettingsRequestId = it.aiSettingsRequestId + 1,
                            aiTestingProfileId = "",
                            aiLoadingModelsProfileId = "",
                            aiTestResults = emptyMap(),
                            aiModelResults = emptyMap(),
                            askConversations = emptyList(),
                            activeAskId = "",
                            askHeadId = null,
                            askMessages = emptyList(),
                            askQuestion = "",
                            askLoading = false,
                            askLoadError = null,
                            askSending = false,
                            askStreaming = false,
                            askVariantLoading = false,
                            askRegeneratingId = "",
                            askLiveUser = null,
                            askLiveAnswer = "",
                            askSourceRequestId = it.askSourceRequestId + 1,
                            askSourceLoading = false,
                            askMemoSaveRequestId = it.askMemoSaveRequestId + 1,
                            askSavingMessageId = "",
                            searchQuery = "",
                            searchResults = null,
                            searching = false,
                            loading = false,
                            screen = if (offlineMode) Screen.Memos else Screen.Login,
                            screenHistory = emptyList(),
                            authError = null,
                            authErrorResourceId = null,
                            notice = feedback.noticeResourceId?.let { resourceId -> uiString(resourceId) },
                            error = feedback.errorResourceId?.let { resourceId -> uiString(resourceId) },
                        )
                    }
                }
            }
        }
    }

    fun refreshMemos() {
        cancelMemoPageLoad()
        val request = state.value.memoRefreshRequest()
        updateState { current ->
            if (current.canApplyMemoRefresh(request)) {
                current.copy(
                    memoListLoadStatus = MemoListLoadStatus.Loading,
                    error = null,
                    notice = null,
                )
            } else {
                current
            }
        }
        viewModelScope.launch {
            runCatching {
                if (request.appMode == SessionStore.MODE_OFFLINE) {
                    MemoListSnapshot(
                        memos = localDataStore.listMemos(),
                        nextCursor = "",
                    )
                } else {
                    listOnlineMemos(request.filter).let { page ->
                        MemoListSnapshot(
                            memos = page.memos,
                            nextCursor = page.nextCursor,
                        )
                    }
                }
            }
                .onSuccess { snapshot ->
                    updateState { current ->
                        if (current.canApplyMemoRefresh(request)) {
                            current.copy(
                                memos = memosForFilter(snapshot.memos, request.filter),
                                memoNextCursor = snapshot.nextCursor,
                                loadingMoreMemos = false,
                                memoListLoadStatus = MemoListLoadStatus.Idle,
                                error = null,
                            )
                        } else {
                            current
                        }
                    }
                }
                .onFailure { error ->
                    updateState { current ->
                        if (current.canApplyMemoRefresh(request)) {
                            current.copy(
                                loadingMoreMemos = false,
                                memoListLoadStatus = MemoListLoadStatus.Failed,
                                error = error.readableMessage(),
                            )
                        } else {
                            current
                        }
                    }
                }
        }
    }

    fun loadMoreMemos() {
        val job = synchronized(memoPageLock) {
            if (loadMoreMemosJob?.isActive == true) {
                return
            }
            val request = state.value.nextMemoPageRequest() ?: return
            updateState { current ->
                if (current.nextMemoPageRequest() == request) {
                    current.copy(
                        loadingMoreMemos = true,
                        memoPageRequestId = request.requestId,
                        error = null,
                        notice = null,
                    )
                } else {
                    current
                }
            }
            if (!state.value.canApplyMemoPage(request)) {
                return
            }
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                runCatching { listOnlineMemos(request.filter, cursor = request.cursor) }
                .onSuccess { page ->
                    updateState { current ->
                        if (current.canApplyMemoPage(request)) {
                            current.copy(
                                memos = memosForFilter(current.memos + page.memos, request.filter),
                                memoNextCursor = page.nextCursor,
                                loadingMoreMemos = false,
                            )
                        } else {
                            current
                        }
                    }
                }
                .onFailure { error ->
                    updateState { current ->
                        if (current.canApplyMemoPage(request)) {
                            current.copy(
                                loadingMoreMemos = false,
                                error = error.readableMessage(),
                            )
                        } else {
                            current
                        }
                    }
                }
                synchronized(memoPageLock) {
                    if (state.value.memoPageRequestId == request.requestId) {
                        loadMoreMemosJob = null
                    }
                }
            }.also { loadMoreMemosJob = it }
        }
        job.start()
    }

    fun startNewMemo() {
        cancelMemoSummary()
        cancelAttachmentOpen()
        val today = LocalDate.now().toString()
        updateState {
            it.copy(
                screen = Screen.Editor,
                screenHistory = it.historyFor(Screen.Editor),
                selectedMemo = null,
                selectedSummary = null,
                summaryLoading = false,
                uploadingAttachment = false,
                editorSessionId = it.editorSessionId + 1,
                draftContent = "",
                draftEntryDate = today,
                initialDraftContent = "",
                initialDraftEntryDate = today,
                markdownPreview = false,
                error = null,
                notice = null,
            )
        }
    }

    fun openMemoDetail(memo: Memo) {
        cancelMemoSummary()
        cancelAttachmentOpen()
        updateState {
            it.copy(
                screen = Screen.MemoDetail,
                screenHistory = it.historyFor(Screen.MemoDetail),
                selectedMemo = memo,
                selectedSummary = null,
                summaryLoading = !isOfflineMode(),
                markdownPreview = false,
                error = null,
                notice = null,
            )
        }
        fetchSelectedMemoDetail(memo.id)
    }

    fun editMemo(memo: Memo) {
        openEditorForMemo(memo)
        fetchSelectedMemoDetail(memo.id)
    }

    fun editSelectedMemo() {
        val memo = state.value.selectedMemo ?: return
        openEditorForMemo(memo)
        fetchSelectedMemoDetail(memo.id)
    }

    fun duplicateMemoDraft(memo: Memo) {
        cancelMemoSummary()
        cancelAttachmentOpen()
        val today = LocalDate.now().toString()
        updateState {
            it.copy(
                screen = Screen.Editor,
                screenHistory = it.historyFor(Screen.Editor),
                selectedMemo = null,
                selectedSummary = null,
                summaryLoading = false,
                uploadingAttachment = false,
                editorSessionId = it.editorSessionId + 1,
                draftContent = memo.content,
                draftEntryDate = today,
                initialDraftContent = "",
                initialDraftEntryDate = today,
                markdownPreview = false,
                error = null,
                notice = null,
            )
        }
    }

    fun updateDraftContent(value: String) = updateState {
        if (it.canRunMemoEditorAction()) it.copy(draftContent = value) else it
    }

    fun updateDraftEntryDate(value: String) = updateState {
        if (it.canRunMemoEditorAction()) it.copy(draftEntryDate = value) else it
    }

    fun updateMarkdownPreview(preview: Boolean) {
        updateState {
            if (it.canRunMemoEditorAction()) it.copy(markdownPreview = preview) else it
        }
    }

    fun appendMarkdownFormat(style: MarkdownFormatStyle) {
        val sampleResource = when (style) {
            MarkdownFormatStyle.Heading -> R.string.markdown_sample_heading
            MarkdownFormatStyle.Bold -> R.string.markdown_sample_bold
            MarkdownFormatStyle.Italic -> R.string.markdown_sample_italic
            MarkdownFormatStyle.Code -> R.string.markdown_sample_code
            MarkdownFormatStyle.List -> R.string.markdown_sample_list
            MarkdownFormatStyle.Quote -> R.string.markdown_sample_quote
        }
        val snippet = markdownFormatSnippet(style, uiString(sampleResource))
        updateState {
            if (it.canRunMemoEditorAction()) {
                val separator = if (it.draftContent.isBlank() || snippet.startsWith("\n")) "" else " "
                it.copy(
                    draftContent = it.draftContent + separator + snippet,
                    markdownPreview = false,
                )
            } else {
                it
            }
        }
    }

    fun updateSearchQuery(value: String) {
        updateState {
            it.copy(
                searchQuery = value,
                searchResults = if (value.isBlank()) null else it.searchResults,
                searching = if (value.isBlank()) false else it.searching,
            )
        }
        searchJob?.cancel()
        if (value.isBlank()) {
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            searchMemos()
        }
    }

    fun searchMemos() {
        searchJob?.cancel()
        val request = state.value.memoSearchRequest()
        if (request == null) {
            clearSearch()
            return
        }
        updateState { current ->
            if (current.memoSearchRequest() == request) {
                current.copy(searching = true, error = null, notice = null)
            } else {
                current
            }
        }
        if (!state.value.canApplyMemoSearch(request)) {
            return
        }
        searchJob = viewModelScope.launch {
            runCatching {
                if (request.appMode == SessionStore.MODE_OFFLINE) {
                    localDataStore.searchMemos(request.query)
                } else {
                    searchOnlineMemos(request.query, request.filter)
                }
            }
                .onSuccess { memos ->
                    updateState { current ->
                        if (current.canApplyMemoSearch(request)) {
                            current.copy(
                                searchResults = memosForFilter(memos, request.filter),
                                searching = false,
                                error = null,
                            )
                        } else {
                            current
                        }
                    }
                }
                .onFailure { error ->
                    updateState { current ->
                        current.failMemoSearch(request, error.readableMessage())
                    }
                }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        updateState {
            it.copy(
                searchQuery = "",
                searchResults = null,
                searching = false,
                error = null,
            )
        }
    }

    fun exportFullData(uri: Uri) {
        viewModelScope.launch {
            updateState { it.copy(loading = true, error = null, notice = null) }
            runCatching {
                if (!isOfflineMode()) {
                    localDataStore.mergeFromServer(exportOnlineData())
                }
                val data = localDataStore.exportData(state.value.themeMode, state.value.memoViewMode.name)
                val json = SillageExportCodec.toJson(data)
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(json.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalArgumentException(uiString(R.string.error_export_write))
                }
            }
                .onSuccess {
                    updateState { it.copy(notice = uiString(R.string.notice_exported)) }
                }
                .onFailure { error ->
                    updateState { it.copy(error = error.readableMessage()) }
                }
            updateState { it.copy(loading = false) }
        }
    }

    fun importFullData(uri: Uri) {
        viewModelScope.launch {
            updateState { it.copy(loading = true, error = null, notice = null) }
            runCatching {
                val raw = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    } ?: throw IllegalArgumentException(uiString(R.string.error_import_read))
                }
                val data = SillageExportCodec.fromJson(raw)
                localDataStore.mergeWith(data)
                data.themeMode.takeIf { it.isNotBlank() }?.let(sessionStore::saveThemeMode)
                val merged = localDataStore.exportData()
                ImportedDataResult(
                    themeMode = sessionStore.themeMode(),
                    memoViewMode = memoViewModeFromName(merged.memoViewMode),
                    aiProfiles = merged.aiProfiles,
                    aiAutoSummary = merged.autoSummary,
                )
            }
                .onSuccess { result ->
                    updateState {
                        it.copy(
                            themeMode = result.themeMode,
                            memoViewMode = result.memoViewMode,
                            selectedMemo = null,
                            selectedSummary = null,
                            summaryLoading = false,
                            uploadingAttachment = false,
                            aiProfiles = result.aiProfiles,
                            aiAutoSummary = result.aiAutoSummary,
                            askConversations = emptyList(),
                            askMessages = emptyList(),
                            searchQuery = "",
                            searchResults = null,
                            searching = false,
                            notice = uiString(R.string.notice_imported),
                        )
                    }
                    refreshMemos()
                }
                .onFailure { error ->
                    updateState { it.copy(error = error.readableMessage()) }
                }
            updateState { it.copy(loading = false) }
        }
    }

    fun syncFromServer() {
        if (isOfflineMode()) {
            updateState(forceFeedback = true) {
                it.copy(error = uiString(R.string.error_sync_online_required), notice = null)
            }
            return
        }
        viewModelScope.launch {
            updateState { it.copy(loading = true, error = null, notice = null) }
            runCatching {
                val data = exportOnlineData()
                localDataStore.mergeFromServer(data)
            }
                .onSuccess {
                    updateState { it.copy(notice = uiString(R.string.notice_synced_local)) }
                }
                .onFailure { error ->
                    updateState { it.copy(error = error.readableMessage()) }
                }
            updateState { it.copy(loading = false) }
        }
    }

    fun syncToServer() {
        if (isOfflineMode()) {
            updateState(forceFeedback = true) {
                it.copy(error = uiString(R.string.error_sync_online_required), notice = null)
            }
            return
        }
        viewModelScope.launch {
            updateState { it.copy(loading = true, error = null, notice = null) }
            runCatching { pushLocalMemosToServer() }
                .onSuccess { summary ->
                    updateState(noticeType = syncPushToastType(summary)) {
                        it.copy(notice = syncPushNotice(summary))
                    }
                }
                .onFailure { error ->
                    updateState { it.copy(error = error.readableMessage()) }
                }
            updateState { it.copy(loading = false) }
        }
    }

    fun syncBothWays() {
        if (isOfflineMode()) {
            updateState(forceFeedback = true) {
                it.copy(error = uiString(R.string.error_sync_online_required), notice = null)
            }
            return
        }
        viewModelScope.launch {
            updateState { it.copy(loading = true, error = null, notice = null) }
            runCatching {
                val push = pushLocalMemosToServer()
                localDataStore.mergeFromServer(exportOnlineData())
                push
            }
                .onSuccess { summary ->
                    updateState(noticeType = syncPushToastType(summary)) {
                        it.copy(notice = uiString(R.string.notice_sync_both, syncPushNotice(summary)))
                    }
                    refreshMemos()
                }
                .onFailure { error ->
                    updateState { it.copy(error = error.readableMessage()) }
                }
            updateState { it.copy(loading = false) }
        }
    }

    fun updateMemoViewMode(mode: MemoViewMode) {
        if (state.value.askVariantLoading) {
            return
        }
        cancelMemoSummary()
        cancelAttachmentOpen()
        val resetFilter = mode == MemoViewMode.Calendar &&
            state.value.memoListFilter != MemoListFilter.Unarchived
        updateState {
            it.copy(
                screen = Screen.Memos,
                screenHistory = emptyList(),
                memoViewMode = mode,
                memoListFilter = if (mode == MemoViewMode.Calendar) {
                    MemoListFilter.Unarchived
                } else {
                    it.memoListFilter
                },
                memos = if (resetFilter) emptyList() else it.memos,
                memoNextCursor = if (resetFilter) "" else it.memoNextCursor,
                loadingMoreMemos = if (resetFilter) false else it.loadingMoreMemos,
                memoListLoadStatus = if (resetFilter) {
                    MemoListLoadStatus.Loading
                } else {
                    it.memoListLoadStatus
                },
                searchQuery = if (mode == MemoViewMode.Calendar) "" else it.searchQuery,
                searchResults = if (mode == MemoViewMode.Calendar) null else it.searchResults,
                searching = if (mode == MemoViewMode.Calendar) false else it.searching,
                selectedMemo = null,
                selectedSummary = null,
                summaryLoading = false,
                error = if (mode == MemoViewMode.Calendar) null else it.error,
            )
        }
        if (resetFilter) {
            refreshMemos()
        }
    }

    fun updateMemoListFilter(filter: MemoListFilter) {
        if (state.value.memoListFilter == filter || state.value.askVariantLoading) {
            return
        }
        searchJob?.cancel()
        updateState {
            it.copy(
                memoListFilter = filter,
                memos = emptyList(),
                memoNextCursor = "",
                loadingMoreMemos = false,
                memoListLoadStatus = MemoListLoadStatus.Loading,
                searchQuery = "",
                searchResults = null,
                searching = false,
                selectedMemo = null,
                selectedSummary = null,
                error = null,
                notice = null,
            )
        }
        refreshMemos()
    }

    fun changeCalendarMonth(delta: Int) {
        updateState {
            val next = java.time.YearMonth.of(it.calendarYear, it.calendarMonth).plusMonths(delta.toLong())
            it.copy(
                calendarYear = next.year,
                calendarMonth = next.monthValue,
                selectedCalendarDate = null,
            )
        }
    }

    fun selectCalendarDate(date: String) {
        updateState { it.copy(selectedCalendarDate = date) }
    }

    fun saveMemo() {
        val current = state.value
        if (!current.canRunMemoEditorAction()) {
            return
        }
        if (current.draftContent.isBlank()) {
            updateState(forceFeedback = true) {
                it.copy(error = uiString(R.string.error_record_empty))
            }
            return
        }
        cancelMemoSummary()
        cancelAttachmentOpen()
        val selectedMemo = current.selectedMemo
        launchMemoMutation(
            key = selectedMemo?.let {
                MemoMutationKey.Memo(it.id, current.clientContextGeneration)
            }
                ?: MemoMutationKey.Editor(
                    sessionId = current.editorSessionId,
                    clientContextGeneration = current.clientContextGeneration,
                ),
            memoId = selectedMemo?.id,
            useGlobalBusy = selectedMemo == null,
        ) {
            val saved = if (current.selectedMemo == null) {
                if (current.appMode == SessionStore.MODE_OFFLINE) {
                    localDataStore.createMemo(current.draftContent.trim(), current.draftEntryDate)
                } else {
                    api.createMemo(current.draftContent.trim(), current.draftEntryDate)
                }
            } else {
                if (current.appMode == SessionStore.MODE_OFFLINE) {
                    localDataStore.updateMemo(current.selectedMemo, current.draftContent.trim(), current.draftEntryDate)
                } else {
                    api.updateMemo(current.selectedMemo, current.draftContent.trim(), current.draftEntryDate)
                }
            }
            if (!applyMemo(saved, current.appMode, current.clientContextGeneration)) {
                return@launchMemoMutation
            }
            var opened = false
            updateState {
                if (
                    it.appMode != current.appMode ||
                    it.clientContextGeneration != current.clientContextGeneration
                ) {
                    it
                } else {
                    opened = true
                    val history = if (it.screenHistory.lastOrNull() == Screen.MemoDetail) {
                        it.screenHistory.dropLast(1)
                    } else {
                        it.screenHistory
                    }
                    it.copy(
                        screen = Screen.MemoDetail,
                        screenHistory = history,
                        selectedMemo = saved,
                        selectedSummary = if (current.selectedMemo?.id == saved.id) it.selectedSummary else null,
                        summaryLoading = current.appMode != SessionStore.MODE_OFFLINE,
                        uploadingAttachment = false,
                        draftContent = "",
                        initialDraftContent = "",
                        initialDraftEntryDate = LocalDate.now().toString(),
                        searchQuery = "",
                        searchResults = null,
                        notice = uiString(R.string.notice_saved),
                    )
                }
            }
            if (opened) {
                fetchSelectedMemoDetail(saved.id)
                refreshMemos()
            }
        }
    }

    fun deleteSelectedMemo() {
        val current = state.value
        if (current.screen == Screen.Editor && !current.canRunMemoEditorAction()) {
            return
        }
        val memo = current.selectedMemo ?: return
        cancelMemoSummary()
        cancelAttachmentOpen()
        val originScreen = current.screen
        val originHistory = current.screenHistory
        val originEditorSessionId = current.editorSessionId
        val originDetailRequestId = current.memoDetailRequestId
        launchMemoMutation(
            MemoMutationKey.Memo(memo.id, current.clientContextGeneration),
            memoId = memo.id,
        ) {
            val deleted = if (current.appMode == SessionStore.MODE_OFFLINE) {
                localDataStore.deleteMemo(memo)
            } else {
                api.deleteMemo(memo)
            }
            if (!applyMemo(deleted, current.appMode, current.clientContextGeneration)) {
                return@launchMemoMutation
            }
            updateState {
                val stillAtOrigin = it.appMode == current.appMode &&
                    it.clientContextGeneration == current.clientContextGeneration &&
                    it.screen == originScreen &&
                    it.screenHistory == originHistory &&
                    it.selectedMemo?.id == memo.id &&
                    when (originScreen) {
                        Screen.Editor -> it.editorSessionId == originEditorSessionId
                        Screen.MemoDetail -> it.memoDetailRequestId == originDetailRequestId
                        else -> true
                    }
                if (stillAtOrigin) {
                    it.copy(
                        screen = Screen.Memos,
                        screenHistory = emptyList(),
                        selectedMemo = null,
                        selectedSummary = null,
                        summaryLoading = false,
                        uploadingAttachment = false,
                        draftContent = "",
                        initialDraftContent = "",
                        initialDraftEntryDate = LocalDate.now().toString(),
                        searchQuery = "",
                        searchResults = null,
                        notice = uiString(R.string.notice_deleted),
                    )
                } else {
                    it
                }
            }
            if (
                state.value.appMode == current.appMode &&
                state.value.clientContextGeneration == current.clientContextGeneration
            ) {
                refreshMemos()
            }
        }
    }

    fun toggleSelectedMemoFavorited() {
        val current = state.value
        if (current.screen == Screen.Editor && !current.canRunMemoEditorAction()) {
            return
        }
        val memo = current.selectedMemo ?: return
        launchMemoMutation(
            MemoMutationKey.Memo(memo.id, current.clientContextGeneration),
            memoId = memo.id,
        ) {
            val updated = if (current.appMode == SessionStore.MODE_OFFLINE) {
                localDataStore.setMemoFavorited(memo, memo.favoritedAt == null)
            } else {
                api.setMemoFavorited(memo, memo.favoritedAt == null)
            }
            if (!applyMemo(updated, current.appMode, current.clientContextGeneration)) {
                return@launchMemoMutation
            }
            updateState {
                if (
                    it.appMode == current.appMode &&
                    it.clientContextGeneration == current.clientContextGeneration
                ) {
                    it.copy(notice = uiString(if (updated.favoritedAt == null) R.string.notice_unfavorited else R.string.notice_favorited))
                } else {
                    it
                }
            }
        }
    }

    fun toggleSelectedMemoArchived() {
        val current = state.value
        if (current.screen == Screen.Editor && !current.canRunMemoEditorAction()) {
            return
        }
        val memo = current.selectedMemo ?: return
        launchMemoMutation(
            MemoMutationKey.Memo(memo.id, current.clientContextGeneration),
            memoId = memo.id,
        ) {
            val updated = if (current.appMode == SessionStore.MODE_OFFLINE) {
                localDataStore.setMemoArchived(memo, memo.archivedAt == null)
            } else {
                api.setMemoArchived(memo, memo.archivedAt == null)
            }
            if (!applyMemo(updated, current.appMode, current.clientContextGeneration)) {
                return@launchMemoMutation
            }
            updateState {
                if (
                    it.appMode == current.appMode &&
                    it.clientContextGeneration == current.clientContextGeneration
                ) {
                    it.copy(notice = uiString(if (updated.archivedAt == null) R.string.notice_unarchived else R.string.notice_archived))
                } else {
                    it
                }
            }
        }
    }

    fun toggleMemoFavorited(memo: Memo) {
        val current = state.value
        val appMode = current.appMode
        val clientContextGeneration = current.clientContextGeneration
        launchMemoMutation(
            MemoMutationKey.Memo(memo.id, clientContextGeneration),
            memoId = memo.id,
        ) {
            val updated = if (appMode == SessionStore.MODE_OFFLINE) {
                localDataStore.setMemoFavorited(memo, memo.favoritedAt == null)
            } else {
                api.setMemoFavorited(memo, memo.favoritedAt == null)
            }
            if (!applyMemo(updated, appMode, clientContextGeneration)) {
                return@launchMemoMutation
            }
            updateState {
                if (
                    it.appMode == appMode &&
                    it.clientContextGeneration == clientContextGeneration
                ) {
                    it.copy(notice = uiString(if (updated.favoritedAt == null) R.string.notice_unfavorited else R.string.notice_favorited))
                } else {
                    it
                }
            }
        }
    }

    fun toggleMemoArchived(memo: Memo) {
        val current = state.value
        val appMode = current.appMode
        val clientContextGeneration = current.clientContextGeneration
        launchMemoMutation(
            MemoMutationKey.Memo(memo.id, clientContextGeneration),
            memoId = memo.id,
        ) {
            val updated = if (appMode == SessionStore.MODE_OFFLINE) {
                localDataStore.setMemoArchived(memo, memo.archivedAt == null)
            } else {
                api.setMemoArchived(memo, memo.archivedAt == null)
            }
            if (!applyMemo(updated, appMode, clientContextGeneration)) {
                return@launchMemoMutation
            }
            updateState {
                if (
                    it.appMode == appMode &&
                    it.clientContextGeneration == clientContextGeneration
                ) {
                    it.copy(notice = uiString(if (updated.archivedAt == null) R.string.notice_unarchived else R.string.notice_archived))
                } else {
                    it
                }
            }
        }
    }

    fun deleteMemo(memo: Memo) {
        val current = state.value
        val appMode = current.appMode
        val clientContextGeneration = current.clientContextGeneration
        launchMemoMutation(
            MemoMutationKey.Memo(memo.id, clientContextGeneration),
            memoId = memo.id,
        ) {
            val deleted = if (appMode == SessionStore.MODE_OFFLINE) {
                localDataStore.deleteMemo(memo)
            } else {
                api.deleteMemo(memo)
            }
            if (!applyMemo(deleted, appMode, clientContextGeneration)) {
                return@launchMemoMutation
            }
            updateState {
                if (
                    it.appMode == appMode &&
                    it.clientContextGeneration == clientContextGeneration
                ) {
                    it.copy(
                        selectedMemo = if (it.selectedMemo?.id == memo.id) null else it.selectedMemo,
                        selectedSummary = if (it.selectedMemo?.id == memo.id) null else it.selectedSummary,
                        notice = uiString(R.string.notice_deleted),
                    )
                } else {
                    it
                }
            }
            if (
                state.value.appMode == appMode &&
                state.value.clientContextGeneration == clientContextGeneration
            ) {
                refreshMemos()
            }
        }
    }

    fun summarizeSelectedMemo() {
        val current = state.value
        if (current.screen == Screen.Editor && !current.canRunMemoEditorAction()) {
            return
        }
        val memo = current.selectedMemo ?: return
        val request = current.nextMemoSummaryRequest() ?: return
        var started = false
        updateState { state ->
            val pending = state.startMemoSummaryRequest(request)
            if (pending.canApplyMemoSummaryRequest(request)) {
                started = true
            }
            pending
        }
        if (!started) {
            return
        }
        memoSummaryJob?.cancel()
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val ai = if (request.appMode == SessionStore.MODE_OFFLINE) {
                    val profile = localDataStore.activeAIProfile()
                        ?: throw IllegalArgumentException(uiString(R.string.error_ai_default_profile_required))
                    val generated = localAiClient.summarizeMemo(profile, memo)
                    val latest = state.value
                    if (
                        latest.appMode != request.appMode ||
                        latest.clientContextGeneration != request.clientContextGeneration ||
                        localDataStore.getMemo(request.memoId).memo.version != request.memoVersion
                    ) {
                        return@launch
                    }
                    localDataStore.saveMemoAI(generated)
                    generated
                } else {
                    api.generateMemoSummary(memo)
                }
                updateState { state ->
                    state.completeMemoSummaryRequest(
                        request,
                        ai,
                        uiString(R.string.notice_summary_generated),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                updateState { state ->
                    state.failMemoSummaryRequest(request, error.readableMessage())
                }
            } finally {
                updateState { state -> state.finishMemoSummaryRequest(request) }
            }
        }
        memoSummaryJob = job
        job.invokeOnCompletion {
            if (memoSummaryJob === job) {
                memoSummaryJob = null
            }
        }
        job.start()
    }

    fun uploadAttachments(uris: List<Uri>) {
        if (uris.isEmpty()) {
            return
        }
        val current = state.value
        if (!current.canRunMemoEditorAction()) {
            return
        }
        if (isOfflineMode()) {
            updateState(forceFeedback = true) {
                it.copy(error = uiString(R.string.error_attachment_online_required))
            }
            return
        }
        val editorSessionId = current.editorSessionId
        updateState {
            if (it.editorSessionId == editorSessionId && it.canRunMemoEditorAction()) {
                it.copy(uploadingAttachment = true, error = null, notice = null)
            } else {
                it
            }
        }
        if (!state.value.canApplyAttachmentUpload(editorSessionId)) {
            return
        }
        viewModelScope.launch {
            runCatching {
                buildString {
                    for (uri in uris) {
                        val upload = readAttachmentUpload(uri)
                        val attachment = api.uploadAttachment(upload)
                        append(attachmentMarkdown(attachment))
                    }
                }
            }
                .onSuccess { snippets ->
                    updateState {
                        if (it.canApplyAttachmentUpload(editorSessionId)) {
                            it.copy(
                                draftContent = it.draftContent + snippets,
                                uploadingAttachment = false,
                                notice = uiString(R.string.notice_attachment_inserted),
                            )
                        } else {
                            it
                        }
                    }
                }
                .onFailure { error ->
                    updateState {
                        if (it.canApplyAttachmentUpload(editorSessionId)) {
                            it.copy(
                                uploadingAttachment = false,
                                error = error.readableMessage(),
                            )
                        } else {
                            it
                        }
                    }
                }
        }
    }

    fun openProtectedAttachment(target: MarkdownLinkTarget.ProtectedAttachment) {
        if (isOfflineMode()) {
            updateState(forceFeedback = true) {
                it.copy(error = uiString(R.string.error_attachment_open_online_required), notice = null)
            }
            return
        }
        val current = state.value
        if (current.openingAttachmentPath != null || attachmentOpenJob?.isActive == true) {
            return
        }
        val requestId = current.attachmentOpenRequestId + 1
        updateState {
            if (it.openingAttachmentPath == null) {
                it.copy(
                    openingAttachmentPath = target.path,
                    attachmentOpenRequestId = requestId,
                    error = null,
                    notice = null,
                )
            } else {
                it
            }
        }
        if (!state.value.canHandleAttachmentOpen(requestId)) {
            return
        }

        attachmentOpenJob = viewModelScope.launch {
            var requestDirectory: File? = null
            try {
                val cacheRoot = File(appContext.cacheDir, OPEN_ATTACHMENTS_CACHE_DIRECTORY)
                withContext(Dispatchers.IO) {
                    pruneAttachmentOpenCache(cacheRoot)
                }
                requestDirectory = File(cacheRoot, UUID.randomUUID().toString())
                val tempFile = createAttachmentDownloadTempFile(requestDirectory)
                val download = api.downloadAttachment(target, tempFile)
                val event = finalizeAttachmentDownload(
                    requestId = requestId,
                    tempFile = tempFile,
                    download = download,
                    fallbackFilename = target.filename,
                )
                if (state.value.canHandleAttachmentOpen(requestId)) {
                    val result = _attachmentOpenEvents.trySend(event)
                    if (result.isFailure) {
                        throw IllegalStateException(uiString(R.string.error_attachment_prepare))
                    }
                    requestDirectory = null
                }
            } catch (error: CancellationException) {
                clearAttachmentOpenRequest(requestId)
                throw error
            } catch (error: Throwable) {
                updateState {
                    if (it.canHandleAttachmentOpen(requestId)) {
                        it.copy(
                            openingAttachmentPath = null,
                            error = error.readableMessage(),
                        )
                    } else {
                        it
                    }
                }
            } finally {
                requestDirectory?.let { directory ->
                    withContext(NonCancellable + Dispatchers.IO) {
                        directory.deleteRecursively()
                    }
                }
            }
        }
    }

    fun onAttachmentOpenHandled(requestId: Long) {
        clearAttachmentOpenRequest(requestId)
    }

    fun onAttachmentOpenFailed(requestId: Long, message: String) {
        updateState {
            if (it.canHandleAttachmentOpen(requestId)) {
                it.copy(
                    openingAttachmentPath = null,
                    error = message,
                    notice = null,
                )
            } else {
                it
            }
        }
    }

    fun addAIProfile() {
        updateState {
            if (
                !it.loading &&
                !it.aiSettingsLoading &&
                !it.aiSettingsSaving
            ) {
                it.copy(
                    aiProfiles = it.aiProfiles + AIProfileDraft(active = it.aiProfiles.isEmpty()),
                )
            } else {
                it
            }
        }
    }

    fun removeAIProfile(index: Int): Boolean {
        val current = state.value
        val currentProfiles = current.aiProfiles
        if (index !in currentProfiles.indices) {
            return false
        }
        val nextProfiles = currentProfiles.filterIndexed { i, _ -> i != index }
        val request = current.nextAIProfilesMutationRequest(nextProfiles) ?: return false
        return launchAIProfilesMutation(request, R.string.notice_ai_profile_deleted)
    }

    fun updateAIProfileName(index: Int, value: String) {
        updateAIProfile(index) { it.copy(name = value) }
    }

    fun updateAIProfileProvider(index: Int, value: String) {
        updateAIProfile(index) { it.copy(provider = value) }
    }

    fun updateAIProfileBaseUrl(index: Int, value: String) {
        updateAIProfile(index) { it.copy(baseUrl = value) }
    }

    fun updateAIProfileModel(index: Int, value: String) {
        updateAIProfile(index) { it.copy(model = value) }
    }

    fun updateAIProfileTemperature(index: Int, value: String) {
        updateAIProfile(index) { profile ->
            profile.copy(
                temperatureInput = value,
                temperature = value.trim().toDoubleOrNull() ?: profile.temperature,
            )
        }
    }

    fun updateAIProfileMaxTokens(index: Int, value: String) {
        updateAIProfile(index) { profile ->
            profile.copy(
                maxTokensInput = value,
                maxTokens = value.trim().toLongOrNull()?.takeIf { it > 0 } ?: profile.maxTokens,
            )
        }
    }

    fun updateAIProfileApiKey(index: Int, value: String) {
        updateAIProfile(index) { it.copy(apiKeyInput = value) }
    }

    fun setAIProfileDefault(index: Int) {
        val current = state.value
        val currentProfiles = current.aiProfiles
        if (index !in currentProfiles.indices) {
            return
        }
        val nextProfiles = currentProfiles.mapIndexed { i, profile ->
            profile.copy(enabled = true, active = i == index)
        }
        val request = current.nextAIProfilesMutationRequest(nextProfiles) ?: return
        launchAIProfilesMutation(request, R.string.notice_ai_default_set)
    }

    fun setAISettingsAutoSummary(enabled: Boolean) {
        val request = state.value.nextAIAutoSummaryRequest(enabled) ?: return
        updateState { current ->
            val started = current.startAIAutoSummaryRequest(request)
            if (started.canApplyAIAutoSummaryRequest(request)) {
                started.copy(error = null, notice = null)
            } else {
                current
            }
        }
        if (!state.value.canApplyAIAutoSummaryRequest(request)) {
            return
        }
        aiAutoSummaryJob = viewModelScope.launch {
            if (!state.value.canApplyAIAutoSummaryRequest(request)) {
                return@launch
            }
            try {
                val savedValue = persistAIAutoSummary(request)
                updateState { current ->
                    if (current.canApplyAIAutoSummaryRequest(request)) {
                        current.completeAIAutoSummaryRequest(request, savedValue).copy(
                            error = null,
                            notice = uiString(
                                if (savedValue) R.string.notice_auto_summary_on else R.string.notice_auto_summary_off,
                            ),
                        )
                    } else {
                        current
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                updateState { current ->
                    if (current.canApplyAIAutoSummaryRequest(request)) {
                        current.failAIAutoSummaryRequest(request).copy(
                            error = error.readableMessage(),
                            notice = null,
                        )
                    } else {
                        current
                    }
                }
            }
        }
    }

    fun loadAISettings() {
        val current = state.value
        if (current.aiSettingsLoading || current.aiSettingsSaving) {
            return
        }
        val mode = current.appMode
        val loadRequestId = current.aiSettingsRequestId + 1
        var started = false
        updateState { latest ->
            if (
                latest.appMode == mode &&
                latest.aiSettingsRequestId == current.aiSettingsRequestId &&
                !latest.aiSettingsLoading &&
                !latest.aiSettingsSaving
            ) {
                started = true
                latest.invalidateAIAutoSummaryRequest().copy(
                    aiSettingsLoading = true,
                    aiSettingsLoadError = null,
                    aiSettingsRequestId = loadRequestId,
                    error = null,
                    notice = null,
                )
            } else {
                latest
            }
        }
        if (!started) {
            return
        }
        cancelAIAutoSummarySave()
        viewModelScope.launch {
            runCatching {
                if (mode == SessionStore.MODE_OFFLINE) {
                    localDataStore.exportData().let { data ->
                        EditableAISettings(
                            profiles = data.aiProfiles,
                            autoSummary = data.autoSummary,
                        )
                    }
                } else {
                    api.getAISettings().let { settings ->
                        EditableAISettings(
                            profiles = settings.profiles.map { it.toDraft() },
                            autoSummary = settings.autoSummary,
                        )
                    }
                }
            }
                .onSuccess { settings ->
                    updateState { current ->
                        if (
                            current.appMode == mode &&
                            current.aiSettingsRequestId == loadRequestId &&
                            current.aiSettingsLoading
                        ) {
                            current.copy(
                                aiProfiles = settings.profiles,
                                aiAutoSummary = settings.autoSummary,
                                aiSettingsLoading = false,
                                aiSettingsLoadError = null,
                                aiTestResults = emptyMap(),
                                aiModelResults = emptyMap(),
                                error = null,
                            )
                        } else {
                            current
                        }
                    }
                }
                .onFailure { error ->
                    updateState { current ->
                        if (
                            current.appMode == mode &&
                            current.aiSettingsRequestId == loadRequestId &&
                            current.aiSettingsLoading
                        ) {
                            val message = error.readableMessage()
                            current.copy(
                                aiSettingsLoading = false,
                                aiSettingsLoadError = message,
                                error = message,
                            )
                        } else {
                            current
                        }
                    }
                }
        }
    }

    fun saveAIProfiles() {
        val current = state.value
        val draftProfiles = current.aiProfiles
        val blankNameIndex = firstBlankAIProfileNameIndex(draftProfiles)
        if (blankNameIndex != null) {
            updateState(forceFeedback = true) {
                it.copy(
                    error = uiString(R.string.error_ai_profile_name_required, blankNameIndex + 1),
                    notice = null,
                )
            }
            return
        }
        val profiles = normalizedAIProfiles(draftProfiles)
        val request = current.nextAIProfilesMutationRequest(
            pendingProfiles = draftProfiles,
            submittedProfiles = profiles,
        ) ?: return
        launchAIProfilesMutation(request, R.string.notice_ai_profiles_saved)
    }

    private fun launchAIProfilesMutation(
        request: AIProfilesMutationRequest,
        successNoticeResourceId: Int,
    ): Boolean {
        val lease = aiProfilesMutationGate.tryAcquire(request.clientContextGeneration) ?: return false
        var started = false
        updateState { current ->
            val pending = current.startAIProfilesMutation(request)
            if (pending.canApplyAIProfilesMutation(request)) {
                started = true
                pending.copy(error = null, notice = null)
            } else {
                current
            }
        }
        if (!started) {
            lease.release()
            return false
        }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            runSingleFlightOperation(
                lease = lease,
                onFailure = { error ->
                    updateState { current ->
                        if (current.canApplyAIProfilesMutation(request)) {
                            current.failAIProfilesMutation(request).copy(
                                error = error.readableMessage(),
                                notice = null,
                            )
                        } else {
                            current
                        }
                    }
                },
                onFinished = {
                    updateState { current ->
                        if (current.canApplyAIProfilesMutation(request)) {
                            current.failAIProfilesMutation(request)
                        } else {
                            current
                        }
                    }
                },
            ) {
                val savedProfiles = persistAIProfiles(
                    request.submittedProfiles,
                    request.appMode,
                    request.clientContextGeneration,
                )
                updateState { current ->
                    if (current.canApplyAIProfilesMutation(request)) {
                        current.completeAIProfilesMutation(request, savedProfiles).copy(
                            aiTestResults = emptyMap(),
                            aiModelResults = emptyMap(),
                            error = null,
                            notice = uiString(successNoticeResourceId),
                        )
                    } else {
                        current
                    }
                }
            }
        }
        return true
    }

    private suspend fun persistAIProfiles(
        profiles: List<AIProfileDraft>,
        appMode: String,
        clientContextGeneration: Long,
    ): List<AIProfileDraft> {
        val blankNameIndex = firstBlankAIProfileNameIndex(profiles)
        if (blankNameIndex != null) {
            throw IllegalArgumentException(
                uiString(R.string.error_ai_profile_name_required, blankNameIndex + 1),
            )
        }
        val normalized = normalizedAIProfiles(profiles)
        return if (appMode == SessionStore.MODE_OFFLINE) {
            localDataStore.saveAIProfiles(normalized)
        } else {
            api.patchAISettings(normalized.map { it.toInput() }).let { settings ->
                val remoteProfiles = settings.profiles.map { it.toDraft() }
                if (
                    state.value.appMode != appMode ||
                    state.value.clientContextGeneration != clientContextGeneration
                ) {
                    return@let remoteProfiles
                }
                val localProfiles = mergeSavedAIProfilesForLocalStorage(
                    currentProfiles = localDataStore.listAIProfiles(),
                    remoteProfiles = remoteProfiles,
                    submittedProfiles = normalized,
                )
                localDataStore.saveAIProfiles(localProfiles)
            }
        }
    }

    private suspend fun persistAIAutoSummary(request: AIAutoSummaryRequest): Boolean {
        if (request.appMode == SessionStore.MODE_OFFLINE) {
            localDataStore.saveAutoSummary(request.targetValue)
            return request.targetValue
        }
        val savedValue = api.setAIAutoSummary(request.targetValue)
        if (
            state.value.appMode == request.appMode &&
            state.value.clientContextGeneration == request.clientContextGeneration
        ) {
            localDataStore.saveAutoSummary(savedValue)
        }
        return savedValue
    }

    private fun normalizedAIProfiles(profiles: List<AIProfileDraft>): List<AIProfileDraft> {
        if (profiles.isEmpty()) {
            return profiles
        }
        val activeIndex = profiles.indexOfFirst { it.active }.takeIf { it >= 0 } ?: 0
        return profiles.mapIndexed { index, profile ->
            profile.copy(enabled = true, active = index == activeIndex)
        }
    }

    fun testAIProfile(index: Int) {
        val profile = state.value.aiProfiles.getOrNull(index) ?: return
        val key = profile.uiKey(index)
        viewModelScope.launch {
            updateState { it.copy(aiTestingProfileId = key, error = null, notice = null) }
            try {
                val model = if (isOfflineMode()) {
                    localAiClient.testConnection(profile)
                } else if (profile.id.isBlank()) {
                    api.testAIConnection(profile.toInput())
                } else {
                    api.testAIConnection(profile.id)
                }
                val message = uiString(R.string.notice_ai_test_success, model)
                updateState {
                    it.copy(
                        aiTestingProfileId = "",
                        aiTestResults = it.aiTestResults + (key to message),
                        error = null,
                        notice = message,
                    )
                }
            } catch (error: Throwable) {
                val message = error.readableMessage()
                updateState {
                    it.copy(
                        aiTestingProfileId = "",
                        aiTestResults = it.aiTestResults + (key to message),
                        error = message,
                        notice = null,
                    )
                }
            }
        }
    }

    fun loadAIModels(index: Int) {
        val profile = state.value.aiProfiles.getOrNull(index) ?: return
        val key = profile.uiKey(index)
        if (isOfflineMode()) {
            val message = uiString(R.string.error_ai_models_offline)
            updateState(forceFeedback = true) {
                it.copy(
                    aiTestResults = it.aiTestResults + (key to message),
                    error = message,
                    notice = null,
                )
            }
            return
        }
        viewModelScope.launch {
            updateState { it.copy(aiLoadingModelsProfileId = key, error = null, notice = null) }
            runCatching { api.listAIModels(profile.toInput()) }
                .onSuccess { models ->
                    val message = uiString(
                        if (models.isEmpty()) R.string.notice_ai_models_empty else R.string.notice_ai_models_loaded,
                    )
                    updateState {
                        it.copy(
                            aiLoadingModelsProfileId = "",
                            aiModelResults = it.aiModelResults + (key to models),
                            aiTestResults = it.aiTestResults + (key to message),
                            error = null,
                            notice = message,
                        )
                    }
                }
                .onFailure { error ->
                    val message = error.readableMessage()
                    updateState {
                        it.copy(
                            aiLoadingModelsProfileId = "",
                            aiTestResults = it.aiTestResults + (key to message),
                            error = message,
                            notice = null,
                        )
                    }
                }
        }
    }

    fun loadAskConversations() {
        val requestState = state.value
        if (
            requestState.askLoading ||
            requestState.askSending ||
            requestState.askVariantLoading ||
            requestState.askSavingMessageId.isNotBlank()
        ) {
            return
        }
        val screenSessionId = requestState.askScreenSessionId
        val appMode = requestState.appMode
        val clientContextGeneration = requestState.clientContextGeneration
        var started = false
        updateState { current ->
            if (
                !current.askLoading &&
                !current.askSending &&
                !current.askVariantLoading &&
                current.askSavingMessageId.isBlank() &&
                current.askScreenSessionId == screenSessionId &&
                current.appMode == appMode &&
                current.clientContextGeneration == clientContextGeneration
            ) {
                started = true
                current.copy(
                    askLoading = true,
                    askLoadError = null,
                    error = null,
                    notice = null,
                )
            } else {
                current
            }
        }
        if (!started) {
            return
        }
        viewModelScope.launch {
            runCatching {
                if (appMode == SessionStore.MODE_OFFLINE) {
                    localDataStore.listAskConversations()
                } else {
                    api.listAskConversations()
                }
            }
                .onSuccess { conversations ->
                    updateState { current ->
                        if (
                            current.askScreenSessionId == screenSessionId &&
                            current.appMode == appMode &&
                            current.clientContextGeneration == clientContextGeneration
                        ) {
                            current.copy(
                                askConversations = conversations.filter(AskConversation::isActive),
                                askLoading = false,
                                askLoadError = null,
                                error = null,
                            )
                        } else {
                            current
                        }
                    }
                }
                .onFailure { error ->
                    updateState { current ->
                        if (
                            current.askScreenSessionId == screenSessionId &&
                            current.appMode == appMode &&
                            current.clientContextGeneration == clientContextGeneration
                        ) {
                            val message = error.readableMessage()
                            current.copy(
                                askLoading = false,
                                askLoadError = message,
                                error = message,
                            )
                        } else {
                            current
                        }
                    }
                }
        }
    }

    fun selectAskConversation(id: String) {
        val current = state.value
        if (
            current.askLoading ||
            current.askSending ||
            current.askVariantLoading ||
            current.askSourceLoading ||
            id.isBlank()
        ) {
            return
        }
        val conversation = current.askConversations.find { it.id == id }
        val appMode = current.appMode
        val clientContextGeneration = current.clientContextGeneration
        val screenSessionId = current.askScreenSessionId + 1
        var started = false
        updateState { latest ->
            if (
                latest.appMode == appMode &&
                latest.clientContextGeneration == clientContextGeneration &&
                latest.askScreenSessionId == current.askScreenSessionId &&
                !latest.askLoading &&
                !latest.askSending &&
                !latest.askVariantLoading &&
                !latest.askSourceLoading
            ) {
                started = true
                latest.copy(
                    activeAskId = id,
                    askHeadId = conversation?.headMessageId,
                    askMessages = emptyList(),
                    askScope = conversation?.contextScope ?: latest.askScope,
                    askLoading = true,
                    askLoadError = null,
                    askScreenSessionId = screenSessionId,
                    askVariantRequestId = latest.askVariantRequestId + 1,
                    askVariantLoading = false,
                    askSourceRequestId = latest.askSourceRequestId + 1,
                    askSourceLoading = false,
                    error = null,
                    notice = null,
                )
            } else {
                latest
            }
        }
        if (!started) {
            return
        }
        viewModelScope.launch {
            runCatching {
                if (appMode == SessionStore.MODE_OFFLINE) {
                    localDataStore.listAskMessages(id)
                } else {
                    api.listAskMessages(id)
                }
            }
                .onSuccess { messages ->
                    updateState { latest ->
                        if (
                            latest.activeAskId == id &&
                            latest.appMode == appMode &&
                            latest.clientContextGeneration == clientContextGeneration &&
                            latest.askScreenSessionId == screenSessionId
                        ) {
                            latest.copy(
                                askMessages = messages,
                                askLoading = false,
                                askLoadError = null,
                            )
                        } else {
                            latest
                        }
                    }
                }
                .onFailure { error ->
                    updateState { latest ->
                        if (
                            latest.activeAskId == id &&
                            latest.appMode == appMode &&
                            latest.clientContextGeneration == clientContextGeneration &&
                            latest.askScreenSessionId == screenSessionId
                        ) {
                            val message = error.readableMessage()
                            latest.copy(
                                askLoading = false,
                                askLoadError = message,
                                error = message,
                            )
                        } else {
                            latest
                        }
                    }
                }
        }
    }

    fun startNewAsk() {
        if (
            state.value.askLoading ||
            state.value.askSending ||
            state.value.askVariantLoading ||
            state.value.askSourceLoading
        ) {
            return
        }
        updateState {
            it.copy(
                activeAskId = "",
                askHeadId = null,
                askMessages = emptyList(),
                askQuestion = "",
                askRegeneratingId = "",
                askLiveUser = null,
                askLiveAnswer = "",
                askStreaming = false,
                askScreenSessionId = it.askScreenSessionId + 1,
                askVariantRequestId = it.askVariantRequestId + 1,
                askVariantLoading = false,
                askSourceRequestId = it.askSourceRequestId + 1,
                askSourceLoading = false,
                error = null,
                notice = null,
            )
        }
    }

    fun updateAskQuestion(value: String) {
        updateState { it.copy(askQuestion = value) }
    }

    fun updateAskScope(value: String) {
        updateState { it.copy(askScope = value) }
    }

    fun updateAskSourceKind(value: String) {
        updateState { it.copy(askSourceKind = value) }
    }

    fun retryAskLoad() {
        val current = state.value
        if (current.activeAskId.isNotBlank() && current.askMessages.isEmpty()) {
            selectAskConversation(current.activeAskId)
        } else {
            loadAskConversations()
        }
    }

    fun sendAskQuestion() {
        val question = state.value.askQuestion.trim()
        if (question.isBlank()) {
            updateState(forceFeedback = true) {
                it.copy(error = uiString(R.string.error_ask_question_required))
            }
            return
        }
        startAskStream(content = question, forkOfId = null)
    }

    fun regenerateAskAnswer(messageId: String) {
        val conversationId = state.value.activeAskId
        if (conversationId.isBlank() || state.value.askSending || state.value.askVariantLoading) {
            return
        }
        startAskStream(content = "", forkOfId = messageId)
    }

    fun stopAskStreaming() {
        val job = askStreamJob?.takeIf { it.isActive } ?: return
        updateState {
            it.withAskStreamingStoppedNotice(uiString(R.string.notice_ask_generation_stopped))
        }
        job.cancel()
    }

    fun selectAskVariant(messageId: String) {
        invalidateAskMemoSaveNavigation()
        val current = state.value
        val request = current.nextAskVariantRequest() ?: return
        val leafId = askBranchLeafId(current.askMessages, messageId)
        val previousHeadId = current.askHeadId
        updateState {
            if (it.nextAskVariantRequest() == request) {
                it.copy(
                    askHeadId = leafId,
                    askVariantRequestId = request.requestId,
                    askVariantLoading = true,
                    error = null,
                    notice = null,
                )
            } else {
                it
            }
        }
        if (!state.value.canApplyAskVariant(request)) {
            return
        }
        if (request.appMode == SessionStore.MODE_OFFLINE) {
            try {
                localDataStore.setAskHead(request.conversationId, leafId)
                completeAskVariantSelection(request, leafId)
            } catch (error: Throwable) {
                failAskVariantSelection(request, previousHeadId, error)
            }
            return
        }
        viewModelScope.launch {
            try {
                api.setAskHead(request.conversationId, leafId)
                completeAskVariantSelection(request, leafId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                failAskVariantSelection(request, previousHeadId, error)
            }
        }
    }

    fun saveAskAnswerAsMemo(message: AskMessage) {
        val content = askAnswerMemoContent(message)
        val request = state.value.nextAskMemoSaveRequest(message, content) ?: return
        val lease = askMemoSaveGate.tryAcquire(request.clientContextGeneration) ?: return
        var started = false
        updateState { current ->
            val pending = current.startAskMemoSave(request)
            if (pending.canApplyAskMemoSave(request)) {
                started = true
                pending
            } else {
                current
            }
        }
        if (!started) {
            lease.release()
            return
        }
        val entryDate = LocalDate.now().toString()
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            runSingleFlightOperation(
                lease = lease,
                onFailure = { error ->
                    updateState { current ->
                        if (current.canApplyAskMemoSave(request)) {
                            current.copy(error = error.readableMessage(), notice = null)
                        } else {
                            current
                        }
                    }
                },
                onFinished = {
                    updateState { current -> current.finishAskMemoSave(request) }
                },
            ) {
                val memo = if (request.appMode == SessionStore.MODE_OFFLINE) {
                    localDataStore.createMemo(request.memoContent, entryDate)
                } else {
                    api.createMemo(request.memoContent, entryDate)
                }
                val applied = applyMemo(
                    memo,
                    request.appMode,
                    request.clientContextGeneration,
                )
                if (!applied) {
                    return@runSingleFlightOperation
                }
                var opened = false
                updateState { current ->
                    if (current.canApplyAskMemoSave(request)) {
                        opened = true
                        current.copy(
                            screen = Screen.MemoDetail,
                            screenHistory = current.historyFor(Screen.MemoDetail),
                            selectedMemo = memo,
                            selectedSummary = null,
                            summaryLoading = request.appMode != SessionStore.MODE_OFFLINE,
                            uploadingAttachment = false,
                            markdownPreview = false,
                            error = null,
                            notice = uiString(R.string.notice_ask_saved_record),
                        )
                    } else {
                        current
                    }
                }
                if (opened) {
                    fetchSelectedMemoDetail(memo.id)
                }
            }
        }
    }

    fun openAskSourceMemo(memoId: String) {
        state.value.nextAskSourceNavigationRequest(memoId) ?: return
        invalidateAskMemoSaveNavigation()
        val request = state.value.nextAskSourceNavigationRequest(memoId) ?: return
        updateState { current ->
            if (current.nextAskSourceNavigationRequest(request.memoId) == request) {
                current.copy(
                    askSourceRequestId = request.requestId,
                    askSourceLoading = true,
                    error = null,
                    notice = null,
                )
            } else {
                current
            }
        }
        if (!state.value.canApplyAskSourceNavigation(request)) {
            return
        }
        viewModelScope.launch {
            runCatching {
                if (request.appMode == SessionStore.MODE_OFFLINE) {
                    localDataStore.getMemo(request.memoId)
                } else {
                    api.getMemo(request.memoId)
                }
            }
                .onSuccess { detail ->
                    updateState { current ->
                        if (!current.canApplyAskSourceNavigation(request)) {
                            if (current.askSourceRequestId == request.requestId) {
                                current.copy(askSourceLoading = false)
                            } else {
                                current
                            }
                        } else {
                            val cached = memosForFilter(
                                current.memos.filter { it.id != detail.memo.id } + detail.memo,
                                current.memoListFilter,
                            )
                            val searched = current.searchResults?.let { results ->
                                memosForFilter(
                                    results.filter { it.id != detail.memo.id } + detail.memo,
                                    current.memoListFilter,
                                )
                            }
                            current.copy(
                                screen = Screen.MemoDetail,
                                screenHistory = request.destinationHistory(),
                                memos = cached,
                                searchResults = searched,
                                selectedMemo = detail.memo,
                                selectedSummary = detail.ai,
                                summaryLoading = false,
                                uploadingAttachment = false,
                                markdownPreview = false,
                                askSourceLoading = false,
                            )
                        }
                    }
                }
                .onFailure { error ->
                    updateState { current ->
                        when {
                            current.canApplyAskSourceNavigation(request) -> current.copy(
                                askSourceLoading = false,
                                error = error.readableMessage(),
                            )
                            current.askSourceRequestId == request.requestId -> current.copy(askSourceLoading = false)
                            else -> current
                        }
                    }
                }
        }
    }

    fun closeMemoDetail() {
        cancelMemoSummary()
        cancelAttachmentOpen()
        updateState {
            val navigation = it.backNavigation(Screen.Memos)
            it.copy(
                screen = navigation.screen,
                screenHistory = navigation.history,
                selectedMemo = null,
                selectedSummary = null,
                summaryLoading = false,
                uploadingAttachment = false,
                error = null,
                notice = null,
            )
        }
    }

    fun closeEditor() {
        if (!state.value.canRunMemoEditorAction()) {
            return
        }
        cancelMemoSummary()
        cancelAttachmentOpen()
        updateState {
            val navigation = it.backNavigation(
                if (it.selectedMemo == null) Screen.Memos else Screen.MemoDetail,
            )
            val returningToDetail = navigation.screen == Screen.MemoDetail && it.selectedMemo != null
            it.copy(
                screen = if (returningToDetail) Screen.MemoDetail else navigation.screen,
                screenHistory = navigation.history,
                selectedMemo = if (returningToDetail) it.selectedMemo else null,
                selectedSummary = null,
                summaryLoading = returningToDetail && !isOfflineMode(),
                uploadingAttachment = false,
                draftContent = "",
                initialDraftContent = "",
                initialDraftEntryDate = LocalDate.now().toString(),
                error = null,
                notice = null,
            )
        }
        state.value
            .takeIf { it.screen == Screen.MemoDetail }
            ?.selectedMemo
            ?.id
            ?.let(::fetchSelectedMemoDetail)
    }

    private fun AIProfileDraft.uiKey(index: Int): String = id.ifBlank { "new-$index" }

    private fun updateAIProfile(index: Int, transform: (AIProfileDraft) -> AIProfileDraft) {
        updateState {
            if (
                !it.loading &&
                !it.aiSettingsLoading &&
                !it.aiSettingsSaving
            ) {
                it.copy(
                    aiProfiles = it.aiProfiles.mapIndexed { i, profile ->
                        if (i == index) transform(profile) else profile
                    },
                )
            } else {
                it
            }
        }
    }

    private suspend fun readAttachmentUpload(uri: Uri): AttachmentUpload = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val filename = displayName(uri).ifBlank { uri.lastPathSegment ?: "attachment" }
        val contentType = resolver.getType(uri) ?: "application/octet-stream"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException(uiString(R.string.error_attachment_read))
        AttachmentUpload(
            filename = filename,
            contentType = contentType,
            bytes = bytes,
        )
    }

    private suspend fun createAttachmentDownloadTempFile(
        requestDirectory: File,
    ): File = withContext(Dispatchers.IO) {
        val cacheRoot = requestDirectory.parentFile
            ?: throw IllegalStateException(uiString(R.string.error_attachment_cache))
        if (!cacheRoot.isDirectory && !cacheRoot.mkdirs()) {
            throw IllegalStateException(uiString(R.string.error_attachment_cache))
        }
        if (!requestDirectory.mkdir()) {
            throw IllegalStateException(uiString(R.string.error_attachment_cache))
        }
        try {
            File(requestDirectory, ATTACHMENT_DOWNLOAD_TEMP_FILENAME).also { tempFile ->
                if (!tempFile.createNewFile()) {
                    throw IllegalStateException(uiString(R.string.error_attachment_cache))
                }
            }
        } catch (error: Throwable) {
            requestDirectory.deleteRecursively()
            throw error
        }
    }

    private suspend fun finalizeAttachmentDownload(
        requestId: Long,
        tempFile: File,
        download: DownloadedAttachment,
        fallbackFilename: String,
    ): AttachmentOpenEvent = withContext(Dispatchers.IO) {
        val filename = preferredAttachmentFilename(
            contentDisposition = download.contentDisposition,
            urlFilename = download.urlFilename.ifBlank { fallbackFilename },
        )
        val mimeType = resolveAttachmentMimeType(download.contentType, filename)
        val requestDirectory = tempFile.parentFile
            ?: throw IllegalStateException(uiString(R.string.error_attachment_prepare))
        val file = File(requestDirectory, filename)
        moveAttachmentTempFile(tempFile, file)
        AttachmentOpenEvent(
            requestId = requestId,
            file = file,
            displayName = filename,
            mimeType = mimeType,
        )
    }

    private fun moveAttachmentTempFile(tempFile: File, destination: File) {
        val sourcePath = tempFile.toPath()
        val destinationPath = destination.toPath()
        if (sourcePath == destinationPath) {
            return
        }
        val atomicFailure = try {
            Files.move(sourcePath, destinationPath, StandardCopyOption.ATOMIC_MOVE)
            return
        } catch (error: IOException) {
            error
        } catch (error: UnsupportedOperationException) {
            error
        }
        if (!tempFile.exists() && destination.isFile) {
            return
        }
        try {
            Files.move(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING)
        } catch (fallbackError: Throwable) {
            fallbackError.addSuppressed(atomicFailure)
            throw fallbackError
        }
    }

    private fun cancelAttachmentOpen() {
        attachmentOpenJob?.cancel()
        attachmentOpenJob = null
        updateState { it.invalidateAttachmentOpenRequest() }
    }

    private fun cancelMemoSummary() {
        memoSummaryJob?.cancel()
        memoSummaryJob = null
        updateState { it.invalidateMemoSummaryRequest() }
    }

    private fun cancelMemoPageLoad() {
        synchronized(memoPageLock) {
            loadMoreMemosJob?.cancel()
            loadMoreMemosJob = null
        }
        updateState {
            it.copy(
                loadingMoreMemos = false,
                memoPageRequestId = it.memoPageRequestId + 1,
            )
        }
    }

    private fun cancelAskVariant() {
        updateState {
            it.copy(
                askVariantLoading = false,
                askVariantRequestId = it.askVariantRequestId + 1,
            )
        }
    }

    private fun completeAskVariantSelection(request: AskVariantRequest, leafId: String) {
        updateState { current ->
            if (current.canApplyAskVariant(request)) {
                current.copy(
                    askHeadId = leafId,
                    askVariantLoading = false,
                    askConversations = current.askConversations.map { conversation ->
                        if (conversation.id == request.conversationId) {
                            conversation.copy(headMessageId = leafId)
                        } else {
                            conversation
                        }
                    },
                )
            } else {
                current
            }
        }
    }

    private fun failAskVariantSelection(
        request: AskVariantRequest,
        previousHeadId: String?,
        error: Throwable,
    ) {
        updateState { current ->
            if (current.canApplyAskVariant(request)) {
                current.copy(
                    askHeadId = previousHeadId,
                    askVariantLoading = false,
                    error = error.readableMessage(),
                )
            } else {
                current
            }
        }
    }

    private fun cancelAskStream() {
        askStreamJob?.cancel()
        askStreamJob = null
        updateState {
            it.copy(
                askSending = false,
                askStreaming = false,
                askStreamRequestId = it.askStreamRequestId + 1,
                askRegeneratingId = "",
                askLiveUser = null,
                askLiveAnswer = "",
            )
        }
    }

    private fun invalidateAskMemoSaveNavigation() {
        updateState {
            if (it.askSavingMessageId.isNotBlank()) {
                it.copy(askScreenSessionId = it.askScreenSessionId + 1)
            } else {
                it
            }
        }
    }

    private fun clearAttachmentOpenRequest(requestId: Long) {
        updateState {
            if (it.canHandleAttachmentOpen(requestId)) {
                it.copy(openingAttachmentPath = null)
            } else {
                it
            }
        }
    }

    private fun displayName(uri: Uri): String {
        val resolver = appContext.contentResolver
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    return cursor.getString(index).orEmpty()
                }
            }
        }
        return ""
    }

    private fun fetchSelectedMemoDetail(memoId: String) {
        val request = state.value.nextMemoDetailRequest(memoId) ?: return
        updateState { current -> current.startMemoDetailRequest(request) }
        if (state.value.memoDetailRequestId != request.requestId) {
            return
        }
        viewModelScope.launch {
            runCatching {
                if (request.appMode == SessionStore.MODE_OFFLINE) {
                    localDataStore.getMemo(memoId)
                } else {
                    api.getMemo(memoId)
                }
            }
                .onSuccess { detail ->
                    updateState { current -> current.completeMemoDetailRequest(request, detail) }
                }
                .onFailure { error ->
                    updateState { current ->
                        current.failMemoDetailRequest(request, error.readableMessage())
                    }
                }
        }
    }

    private suspend fun reloadAskConversation(conversationId: String): AskSnapshot {
        val messages = api.listAskMessages(conversationId)
        val conversations = api.listAskConversations().filter(AskConversation::isActive)
        val headId = conversations.find { it.id == conversationId }?.headMessageId
        return AskSnapshot(
            messages = messages,
            conversations = conversations,
            headId = headId ?: lastAssistantMessageId(buildAskActivePath(messages, null)),
        )
    }

    private suspend fun exportOnlineData() = withContext(Dispatchers.IO) {
        api.pullFullSync().copy(
            themeMode = state.value.themeMode,
            memoViewMode = state.value.memoViewMode.name,
        )
    }

    private suspend fun pushLocalMemosToServer(): SyncPushSummary {
        val pending = localDataStore.pendingCloudMemos()
        if (pending.isEmpty()) {
            return SyncPushSummary(applied = 0, conflict = 0, rejected = 0)
        }
        val summary = api.pushMemos(pending)
        localDataStore.applyCloudSyncedMemos(summary.appliedMemoSyncs)
        return summary
    }

    private fun syncPushNotice(summary: SyncPushSummary): String {
        return if (summary.applied == 0 && summary.conflict == 0 && summary.rejected == 0) {
            uiString(R.string.sync_none)
        } else {
            uiString(R.string.sync_summary, summary.applied, summary.conflict, summary.rejected)
        }
    }

    private fun startAskStream(content: String, forkOfId: String?) {
        invalidateAskMemoSaveNavigation()
        val current = state.value
        val initialRequest = current.nextAskStreamRequest() ?: return
        val contextScope = current.askScope
        val sourceKind = current.askSourceKind
        val regeneratingId = forkOfId.orEmpty()
        updateState {
            if (it.nextAskStreamRequest() == initialRequest) {
                it.copy(
                    askSending = true,
                    askStreaming = false,
                    askStreamRequestId = initialRequest.requestId,
                    askRegeneratingId = regeneratingId,
                    askLiveUser = null,
                    askLiveAnswer = "",
                    error = null,
                    notice = null,
                )
            } else {
                it
            }
        }
        if (!state.value.canApplyAskStream(initialRequest)) {
            return
        }
        if (initialRequest.appMode == SessionStore.MODE_OFFLINE) {
            startLocalAsk(content, forkOfId, initialRequest, contextScope)
            return
        }

        askStreamJob?.cancel()
        askStreamJob = viewModelScope.launch {
            var request = initialRequest
            var conversationId = request.conversationId
            try {
                if (conversationId.isBlank()) {
                    val created = api.createAskConversation(contextScope)
                    val createdRequest = request.copy(conversationId = created.id)
                    conversationId = created.id
                    updateState { currentState ->
                        if (currentState.canApplyAskStream(request)) {
                            currentState.copy(
                                activeAskId = created.id,
                                askHeadId = created.headMessageId,
                                askConversations = listOf(created) + currentState.askConversations.filter { conversation ->
                                    conversation.id != created.id
                                },
                            )
                        } else {
                            currentState
                        }
                    }
                    request = createdRequest
                    if (!state.value.canApplyAskStream(request)) {
                        return@launch
                    }
                }
                api.streamAskMessage(
                    conversationId = conversationId,
                    content = content,
                    contextScope = contextScope,
                    sourceKind = sourceKind,
                    forkOfId = forkOfId,
                    onStart = { userMessage, regenerate ->
                        updateState { currentState ->
                            if (currentState.canApplyAskStream(request)) {
                                currentState.copy(
                                    askStreaming = true,
                                    askLiveAnswer = "",
                                    askLiveUser = if (regenerate) null else userMessage,
                                )
                            } else {
                                currentState
                            }
                        }
                    },
                    onDelta = { text ->
                        updateState { currentState ->
                            if (currentState.canApplyAskStream(request)) {
                                currentState.copy(askLiveAnswer = currentState.askLiveAnswer + text)
                            } else {
                                currentState
                            }
                        }
                    },
                    onError = { message ->
                        updateState { currentState ->
                            if (currentState.canApplyAskStream(request)) {
                                currentState.copy(error = IllegalStateException(message).readableMessage())
                            } else {
                                currentState
                            }
                        }
                    },
                )
            } catch (cancelled: CancellationException) {
                // Stop is user-initiated; the server persists whatever streamed before cancellation.
            } catch (error: Throwable) {
                updateState { currentState ->
                    if (currentState.canApplyAskStream(request)) {
                        currentState.copy(error = error.readableMessage())
                    } else {
                        currentState
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    if (conversationId.isNotBlank() && state.value.canApplyAskStream(request)) {
                        runCatching { reloadAskConversation(conversationId) }
                            .onSuccess { snapshot ->
                                updateState { currentState ->
                                    if (currentState.canApplyAskStream(request)) {
                                        currentState.copy(
                                            askMessages = snapshot.messages,
                                            askConversations = snapshot.conversations,
                                            askHeadId = snapshot.headId,
                                        )
                                    } else {
                                        currentState
                                    }
                                }
                            }
                    }
                    updateState { currentState ->
                        if (currentState.canApplyAskStream(request)) {
                            currentState.copy(
                                askQuestion = if (forkOfId == null && currentState.error == null) "" else currentState.askQuestion,
                                askSending = false,
                                askStreaming = false,
                                askRegeneratingId = "",
                                askLiveUser = null,
                                askLiveAnswer = "",
                            )
                        } else {
                            currentState
                        }
                    }
                    if (state.value.askStreamRequestId == request.requestId) {
                        askStreamJob = null
                    }
                }
            }
        }
    }

    private fun startLocalAsk(
        content: String,
        forkOfId: String?,
        initialRequest: AskStreamRequest,
        contextScope: String,
    ) {
        askStreamJob?.cancel()
        askStreamJob = viewModelScope.launch {
            var request = initialRequest
            var conversationId = request.conversationId
            try {
                if (conversationId.isBlank()) {
                    val created = localDataStore.createAskConversation(contextScope)
                    val createdRequest = request.copy(conversationId = created.id)
                    conversationId = created.id
                    updateState { currentState ->
                        if (currentState.canApplyAskStream(request)) {
                            currentState.copy(
                                activeAskId = created.id,
                                askHeadId = created.headMessageId,
                                askConversations = listOf(created) + currentState.askConversations.filter { conversation ->
                                    conversation.id != created.id
                                },
                            )
                        } else {
                            currentState
                        }
                    }
                    request = createdRequest
                    if (!state.value.canApplyAskStream(request)) {
                        return@launch
                    }
                }
                val messages = localDataStore.listAskMessages(conversationId)
                val parentId = if (forkOfId == null) lastAssistantMessageId(buildAskActivePath(messages, state.value.askHeadId)) else null
                val question = if (forkOfId == null) {
                    content
                } else {
                    messages.find { it.id == forkOfId }?.parentId?.let { parent ->
                        messages.find { it.id == parent }?.content
                    }.orEmpty()
                }
                if (question.isBlank()) {
                    throw IllegalArgumentException(uiString(R.string.error_ask_regenerate_missing))
                }
                val profile = localDataStore.activeAIProfile()
                    ?: throw IllegalArgumentException(uiString(R.string.error_ai_default_profile_required))
                val history = buildAskActivePath(messages, parentId).map { it.message }
                val answer = localAiClient.answerQuestion(
                    profile = profile,
                    question = question,
                    scope = contextScope,
                    loadMemos = localDataStore::listMemos,
                    history = history,
                )
                localDataStore.appendAskTurn(
                    conversationId = conversationId,
                    question = question,
                    answer = answer.answer,
                    sourceRefs = answer.sourceRefs,
                    model = answer.model,
                    promptVersion = answer.promptVersion,
                    parentId = parentId,
                    forkOfId = forkOfId,
                )
                val refreshedMessages = localDataStore.listAskMessages(conversationId)
                val conversations = localDataStore.listAskConversations().filter(AskConversation::isActive)
                updateState { currentState ->
                    if (currentState.canApplyAskStream(request)) {
                        currentState.copy(
                            askMessages = refreshedMessages,
                            askConversations = conversations,
                            askHeadId = conversations.find { conversation -> conversation.id == conversationId }?.headMessageId,
                            askQuestion = if (forkOfId == null) "" else currentState.askQuestion,
                        )
                    } else {
                        currentState
                    }
                }
            } catch (cancelled: CancellationException) {
                // The local request was stopped or superseded.
            } catch (error: Throwable) {
                updateState { currentState ->
                    if (currentState.canApplyAskStream(request)) {
                        currentState.copy(error = error.readableMessage())
                    } else {
                        currentState
                    }
                }
            }
            updateState { currentState ->
                if (currentState.canApplyAskStream(request)) {
                    currentState.copy(
                        askSending = false,
                        askStreaming = false,
                        askRegeneratingId = "",
                        askLiveUser = null,
                        askLiveAnswer = "",
                    )
                } else {
                    currentState
                }
            }
            if (state.value.askStreamRequestId == request.requestId) {
                askStreamJob = null
            }
        }
    }

    private fun launchMemoMutation(
        key: MemoMutationKey,
        memoId: String? = null,
        useGlobalBusy: Boolean = false,
        block: suspend () -> Unit,
    ) {
        val lease = memoMutationGate.tryAcquire(key) ?: return
        val clientContextGeneration = when (key) {
            is MemoMutationKey.Editor -> key.clientContextGeneration
            is MemoMutationKey.Memo -> key.clientContextGeneration
        }
        val current = state.value
        if (current.clientContextGeneration != clientContextGeneration) {
            lease.release()
            return
        }
        val appMode = current.appMode
        updateState { current ->
            current.copy(
                memoMutationIds = memoId?.let { current.memoMutationIds + it }
                    ?: current.memoMutationIds,
                loading = current.loading || useGlobalBusy,
                error = null,
                notice = null,
            )
        }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            runSingleFlightOperation(
                lease = lease,
                onFailure = { error ->
                    updateState { current ->
                        if (
                            current.appMode == appMode &&
                            current.clientContextGeneration == clientContextGeneration
                        ) {
                            current.copy(
                                screen = if (current.screen == Screen.Loading) Screen.Server else current.screen,
                                error = error.readableMessage(),
                            )
                        } else {
                            current
                        }
                    }
                },
                onFinished = {
                    updateState { current ->
                        if (
                            current.appMode == appMode &&
                            current.clientContextGeneration == clientContextGeneration
                        ) {
                            current.copy(
                                memoMutationIds = memoId?.let { current.memoMutationIds - it }
                                    ?: current.memoMutationIds,
                                loading = if (
                                    useGlobalBusy &&
                                    key is MemoMutationKey.Editor &&
                                    current.editorSessionId == key.sessionId
                                ) {
                                    false
                                } else {
                                    current.loading
                                },
                            )
                        } else {
                            current
                        }
                    }
                },
            ) {
                block()
            }
        }
    }

    private fun launchAuthBusy(block: suspend () -> Unit) {
        val lease = authOperationGate.tryAcquire() ?: return
        updateState {
            it.copy(
                loading = true,
                authError = null,
                authErrorResourceId = null,
                error = null,
                notice = null,
            )
        }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            runSingleFlightOperation(
                lease = lease,
                onFailure = { error ->
                    val resourceId = readableErrorResourceId(
                        error.message,
                        state.value.languageMode,
                    )
                    updateState {
                        it.copy(
                            screen = if (it.screen == Screen.Loading) Screen.Server else it.screen,
                            authError = error.readableMessage(),
                            authErrorResourceId = resourceId,
                        )
                    }
                },
                onFinished = {
                    updateState { it.copy(loading = false) }
                },
            ) {
                block()
            }
        }
    }

    private fun enterOfflineMode(notice: String?) {
        cancelAIAutoSummarySave()
        val filter = state.value.memoListFilter
        val memos = memosForFilter(localDataStore.listMemos(), filter)
        val aiProfiles = localDataStore.listAIProfiles()
        updateState {
            it.invalidateAIAutoSummaryRequest().copy(
                appMode = SessionStore.MODE_OFFLINE,
                clientContextGeneration = it.clientContextGeneration + 1,
                initialized = true,
                account = null,
                memos = memos,
                memoNextCursor = "",
                loadingMoreMemos = false,
                memoListLoadStatus = MemoListLoadStatus.Idle,
                memoMutationIds = emptySet(),
                selectedMemo = null,
                selectedSummary = null,
                summaryLoading = false,
                uploadingAttachment = false,
                aiProfiles = aiProfiles,
                aiAutoSummary = localDataStore.autoSummaryEnabled(),
                aiSettingsLoading = false,
                aiSettingsLoadError = null,
                aiSettingsSaving = false,
                aiSettingsRequestId = it.aiSettingsRequestId + 1,
                aiTestingProfileId = "",
                aiLoadingModelsProfileId = "",
                aiTestResults = emptyMap(),
                aiModelResults = emptyMap(),
                askConversations = emptyList(),
                activeAskId = "",
                askHeadId = null,
                askMessages = emptyList(),
                askQuestion = "",
                askLoading = false,
                askLoadError = null,
                askScreenSessionId = it.askScreenSessionId + 1,
                askSourceRequestId = it.askSourceRequestId + 1,
                askSourceLoading = false,
                askMemoSaveRequestId = it.askMemoSaveRequestId + 1,
                askSavingMessageId = "",
                searchQuery = "",
                searchResults = null,
                searching = false,
                screen = Screen.Memos,
                screenHistory = emptyList(),
                authError = null,
                authErrorResourceId = null,
                error = null,
                notice = notice,
            )
        }
    }

    private fun isOfflineMode(): Boolean = state.value.appMode == SessionStore.MODE_OFFLINE

    private fun cancelAIAutoSummarySave() {
        aiAutoSummaryJob?.cancel()
        aiAutoSummaryJob = null
    }

    private fun memoViewModeFromName(value: String): MemoViewMode {
        return runCatching { MemoViewMode.valueOf(value) }.getOrDefault(MemoViewMode.List)
    }

    private fun applyMemo(
        memo: Memo,
        appMode: String,
        clientContextGeneration: Long,
    ): Boolean {
        var applied = false
        synchronized(memoPageLock) {
            if (
                state.value.appMode != appMode ||
                state.value.clientContextGeneration != clientContextGeneration
            ) {
                return false
            }
            loadMoreMemosJob?.cancel()
            loadMoreMemosJob = null
        }
        searchJob?.cancel()
        searchJob = null
        updateState { current ->
            if (
                current.appMode == appMode &&
                current.clientContextGeneration == clientContextGeneration
            ) {
                applied = true
                current.applyMemoToCache(memo)
            } else {
                current
            }
        }
        return applied
    }

    private suspend fun listOnlineMemos(
        filter: MemoListFilter,
        cursor: String = "",
    ) = filter.apiQuery().let { query ->
        api.listMemos(
            cursor = cursor,
            archived = query.archived,
            favorited = query.favorited,
        )
    }

    private suspend fun searchOnlineMemos(query: String, filter: MemoListFilter) =
        filter.apiQuery().let { apiQuery ->
            api.searchMemos(
                query = query,
                archived = apiQuery.archived,
                favorited = apiQuery.favorited,
            )
        }

    private fun openEditorForMemo(memo: Memo) {
        cancelMemoSummary()
        cancelAttachmentOpen()
        updateState {
            it.copy(
                screen = Screen.Editor,
                screenHistory = it.historyFor(Screen.Editor),
                selectedMemo = memo,
                selectedSummary = null,
                summaryLoading = !isOfflineMode(),
                uploadingAttachment = false,
                editorSessionId = it.editorSessionId + 1,
                draftContent = memo.content,
                draftEntryDate = memo.entryDate,
                initialDraftContent = memo.content,
                initialDraftEntryDate = memo.entryDate,
                markdownPreview = false,
                error = null,
                notice = null,
            )
        }
    }

    private fun Throwable.readableMessage(): String {
        val raw = message?.trim().orEmpty()
        val normalized = raw.trimEnd('。')
        val resourceId = readableErrorResourceId(raw, state.value.languageMode)
        if (resourceId != null) {
            return uiString(resourceId)
        }
        if (normalized.startsWith("AI 请求失败：")) {
            return uiString(R.string.error_ai_request, normalized.substringAfter("AI 请求失败："))
        }
        return raw
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SillageViewModel(context) as T
        }
    }
}

private data class AskSnapshot(
    val messages: List<AskMessage>,
    val conversations: List<AskConversation>,
    val headId: String?,
)

private sealed class MemoMutationKey {
    data class Editor(
        val sessionId: Long,
        val clientContextGeneration: Long,
    ) : MemoMutationKey()

    data class Memo(
        val memoId: String,
        val clientContextGeneration: Long,
    ) : MemoMutationKey()
}

internal data class AttachmentOpenEvent(
    val requestId: Long,
    val file: File,
    val displayName: String,
    val mimeType: String,
)

private const val OPEN_ATTACHMENTS_CACHE_DIRECTORY = "open_attachments"
private const val ATTACHMENT_DOWNLOAD_TEMP_FILENAME = "download.tmp"
private const val TOAST_EVENT_BUFFER_CAPACITY = 8
private val HAN_CHARACTER = Regex("[\\u4E00-\\u9FFF]")

internal fun readableErrorResourceId(rawMessage: String?, languageMode: String): Int? {
    val raw = rawMessage?.trim().orEmpty()
    val normalized = raw.trimEnd('。')
    val mapped = when (normalized) {
        "请求失败" -> R.string.error_request_failed
        "操作失败" -> R.string.error_operation_failed
        "无法读取初始化状态" -> R.string.error_auth_bootstrap_failed
        "请求格式不正确" -> R.string.error_auth_invalid_request
        "账号和密码不能为空" -> R.string.error_auth_fields_required
        "这个实例已经初始化" -> R.string.error_auth_already_initialized
        "初始化失败" -> R.string.error_auth_initialize_failed
        "账号或密码不正确", "用户名或密码错误" -> R.string.error_auth_invalid_credentials
        "尝试次数太多，请稍后再试" -> R.string.error_auth_rate_limited
        "登录失败" -> R.string.error_auth_sign_in_failed
        "请重新登录" -> R.string.error_auth_sign_in_required
        "刷新登录状态失败" -> R.string.error_auth_refresh_failed
        "请先登录" -> R.string.error_login_required
        "记录不存在" -> R.string.error_record_missing
        "会话不存在" -> R.string.error_conversation_missing
        "不支持的数据格式版本" -> R.string.error_data_version_unsupported
        "请先配置 AI API 密钥" -> R.string.error_ai_key_required
        "请先配置 AI 模型" -> R.string.error_ai_model_required
        "AI 返回为空" -> R.string.error_ai_empty
        "附件地址无效" -> R.string.error_attachment_address_invalid
        "附件下载失败" -> R.string.error_attachment_download
        "附件内容为空" -> R.string.error_attachment_empty
        "生成回答失败" -> R.string.error_answer_generation
        "无法读取附件" -> R.string.error_attachment_read
        "无法创建附件缓存" -> R.string.error_attachment_cache
        "无法准备附件" -> R.string.error_attachment_prepare
        else -> null
    }
    if (mapped != null || normalized.startsWith("AI 请求失败：")) {
        return mapped
    }
    return if (
        raw.isBlank() ||
        languageMode == SessionStore.LANGUAGE_EN && HAN_CHARACTER.containsMatchIn(raw)
    ) {
        R.string.error_operation_failed
    } else {
        null
    }
}

private data class ImportedDataResult(
    val themeMode: String,
    val memoViewMode: MemoViewMode,
    val aiProfiles: List<AIProfileDraft>,
    val aiAutoSummary: Boolean,
)

private data class EditableAISettings(
    val profiles: List<AIProfileDraft>,
    val autoSummary: Boolean,
)

private data class MemoListSnapshot(
    val memos: List<Memo>,
    val nextCursor: String,
)

internal fun syncPushToastType(summary: SyncPushSummary): UiToastType {
    return if (summary.conflict > 0 || summary.rejected > 0) {
        UiToastType.WARNING
    } else {
        UiToastType.SUCCESS
    }
}

internal data class SignOutFeedback(
    val noticeResourceId: Int?,
    val errorResourceId: Int?,
)

internal fun signOutFeedback(
    offlineMode: Boolean,
    remoteSignOutFailed: Boolean,
): SignOutFeedback {
    return when {
        offlineMode -> SignOutFeedback(
            noticeResourceId = R.string.notice_online_session_cleared,
            errorResourceId = null,
        )
        remoteSignOutFailed -> SignOutFeedback(
            noticeResourceId = null,
            errorResourceId = R.string.error_sign_out_local_only,
        )
        else -> SignOutFeedback(
            noticeResourceId = R.string.notice_signed_out,
            errorResourceId = null,
        )
    }
}

internal suspend fun performSignOut(
    offlineMode: Boolean,
    remoteSignOut: suspend () -> Unit,
    clearLocalSession: () -> Boolean,
): SignOutFeedback? {
    if (offlineMode) {
        return if (clearLocalSession()) {
            signOutFeedback(offlineMode = true, remoteSignOutFailed = false)
        } else {
            null
        }
    }
    return try {
        remoteSignOut()
        signOutFeedback(offlineMode = false, remoteSignOutFailed = false)
    } catch (error: CancellationException) {
        clearLocalSession()
        throw error
    } catch (_: Throwable) {
        if (clearLocalSession()) {
            signOutFeedback(offlineMode = false, remoteSignOutFailed = true)
        } else {
            null
        }
    }
}

internal data class MemoApiQuery(
    val archived: Boolean?,
    val favorited: Boolean,
)

internal fun MemoListFilter.apiQuery(): MemoApiQuery = when (this) {
    MemoListFilter.Unarchived -> MemoApiQuery(archived = false, favorited = false)
    MemoListFilter.Archived -> MemoApiQuery(archived = true, favorited = false)
    MemoListFilter.Favorited -> MemoApiQuery(archived = null, favorited = true)
}
