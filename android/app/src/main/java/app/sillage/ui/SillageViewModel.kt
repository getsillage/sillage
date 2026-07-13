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
                error = null,
                notice = null,
            )
        }
    }

    fun updateBaseUrl(value: String) {
        updateState { it.copy(baseUrl = value) }
    }

    fun saveServer() {
        val normalized = SessionStore.normalizeBaseUrl(state.value.baseUrl)
        if (normalized.isBlank()) {
            updateState(forceFeedback = true) {
                it.copy(error = uiString(R.string.error_server_required), notice = null)
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
                baseUrl = sessionStore.baseUrl(),
                account = null,
                memos = emptyList(),
                memoNextCursor = "",
                loadingMoreMemos = false,
                memoListLoadStatus = MemoListLoadStatus.Idle,
                selectedSummary = null,
                summaryLoading = false,
                uploadingAttachment = false,
                aiSettingsLoading = false,
                aiSettingsLoadError = null,
                askLoading = false,
                askLoadError = null,
                serverReturnScreen = null,
                searchQuery = "",
                searchResults = null,
                searching = false,
                error = null,
                notice = null,
            )
        }
        connect()
    }

    fun useOnlineMode() {
        cancelAskVariant()
        cancelAskStream()
        cancelAIAutoSummarySave()
        sessionStore.saveAppMode(SessionStore.MODE_ONLINE)
        updateState {
            it.invalidateAIAutoSummaryRequest().copy(
                appMode = SessionStore.MODE_ONLINE,
                screenHistory = emptyList(),
                memos = emptyList(),
                memoNextCursor = "",
                loadingMoreMemos = false,
                memoListLoadStatus = MemoListLoadStatus.Idle,
                selectedMemo = null,
                selectedSummary = null,
                aiSettingsLoading = false,
                aiSettingsLoadError = null,
                askLoading = false,
                askLoadError = null,
                searchQuery = "",
                searchResults = null,
                error = null,
                notice = null,
            )
        }
        connect()
    }

    fun useOfflineMode() {
        cancelAttachmentOpen()
        cancelMemoPageLoad()
        cancelAskVariant()
        cancelAskStream()
        sessionStore.saveAppMode(SessionStore.MODE_OFFLINE)
        enterOfflineMode(notice = uiString(R.string.notice_offline_enabled))
    }

    fun openServerSettings() {
        updateState {
            it.copy(
                screen = Screen.Server,
                serverReturnScreen = it.screen.takeIf { screen -> screen != Screen.Server && screen != Screen.ModeSelection },
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
                error = null,
                notice = null,
            )
        }
    }

    fun openAISettings() {
        if (state.value.askVariantLoading) {
            return
        }
        updateState {
            it.copy(
                screen = Screen.AISettings,
                screenHistory = emptyList(),
                error = null,
                notice = null,
            )
        }
        loadAISettings()
    }

    fun openAsk() {
        val current = state.value
        val reloadConversations = !current.askLoading && !current.askSending && !current.askVariantLoading
        updateState {
            it.copy(
                screen = Screen.Ask,
                screenHistory = emptyList(),
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
                    error = null,
                    notice = null,
                )
            }
            return
        }
        launchBusy {
            val initialized = api.bootstrap(state.value.baseUrl)
            val token = sessionStore.accessToken()
            val account = sessionStore.account()
            if (!initialized) {
                updateState { it.copy(screen = Screen.Initialize, initialized = false, account = null) }
                return@launchBusy
            }
            if (token.isNullOrBlank() || account == null) {
                updateState { it.copy(screen = Screen.Login, initialized = true, account = null) }
                return@launchBusy
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

    fun updateUsername(value: String) = updateState { it.copy(username = value) }

    fun updateDisplayName(value: String) = updateState { it.copy(displayName = value) }

    fun updatePassword(value: String) = updateState { it.copy(password = value) }

    fun initialize() {
        val current = state.value
        launchBusy {
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
        launchBusy {
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
        cancelAttachmentOpen()
        cancelMemoPageLoad()
        cancelAskVariant()
        cancelAskStream()
        cancelAIAutoSummarySave()
        viewModelScope.launch {
            val offlineMode = isOfflineMode()
            val feedback = performSignOut(
                offlineMode = offlineMode,
                remoteSignOut = api::signOut,
                clearLocalSession = sessionStore::clearSession,
            )
            updateState {
                it.invalidateAIAutoSummaryRequest().copy(
                    account = null,
                    memos = emptyList(),
                    memoNextCursor = "",
                    loadingMoreMemos = false,
                    memoListLoadStatus = MemoListLoadStatus.Idle,
                    selectedMemo = null,
                    selectedSummary = null,
                    summaryLoading = false,
                    uploadingAttachment = false,
                    aiProfiles = emptyList(),
                    aiAutoSummary = if (isOfflineMode()) localDataStore.autoSummaryEnabled() else false,
                    aiSettingsLoading = false,
                    aiSettingsLoadError = null,
                    aiSettingsSaving = false,
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
                    searchQuery = "",
                    searchResults = null,
                    searching = false,
                    screen = if (offlineMode) Screen.Memos else Screen.Login,
                    screenHistory = emptyList(),
                    notice = feedback.noticeResourceId?.let { resourceId -> uiString(resourceId) },
                    error = feedback.errorResourceId?.let { resourceId -> uiString(resourceId) },
                )
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

    fun updateDraftContent(value: String) = updateState { it.copy(draftContent = value) }

    fun updateDraftEntryDate(value: String) = updateState { it.copy(draftEntryDate = value) }

    fun updateMarkdownPreview(preview: Boolean) {
        updateState { it.copy(markdownPreview = preview) }
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
            val separator = if (it.draftContent.isBlank() || snippet.startsWith("\n")) "" else " "
            it.copy(
                draftContent = it.draftContent + separator + snippet,
                markdownPreview = false,
            )
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
        launchBusy {
            val saved = if (current.selectedMemo == null) {
                if (isOfflineMode()) {
                    localDataStore.createMemo(current.draftContent.trim(), current.draftEntryDate)
                } else {
                    api.createMemo(current.draftContent.trim(), current.draftEntryDate)
                }
            } else {
                if (isOfflineMode()) {
                    localDataStore.updateMemo(current.selectedMemo, current.draftContent.trim(), current.draftEntryDate)
                } else {
                    api.updateMemo(current.selectedMemo, current.draftContent.trim(), current.draftEntryDate)
                }
            }
            applyMemo(saved)
            updateState {
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
                    summaryLoading = !isOfflineMode(),
                    uploadingAttachment = false,
                    draftContent = "",
                    initialDraftContent = "",
                    initialDraftEntryDate = LocalDate.now().toString(),
                    searchQuery = "",
                    searchResults = null,
                    notice = uiString(R.string.notice_saved),
                )
            }
            fetchSelectedMemoDetail(saved.id)
            refreshMemos()
        }
    }

    fun deleteSelectedMemo() {
        val current = state.value
        if (current.screen == Screen.Editor && !current.canRunMemoEditorAction()) {
            return
        }
        val memo = current.selectedMemo ?: return
        launchBusy {
            val deleted = if (isOfflineMode()) {
                localDataStore.deleteMemo(memo)
            } else {
                api.deleteMemo(memo)
            }
            applyMemo(deleted)
            updateState {
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
            }
            refreshMemos()
        }
    }

    fun toggleSelectedMemoFavorited() {
        val current = state.value
        if (current.screen == Screen.Editor && !current.canRunMemoEditorAction()) {
            return
        }
        val memo = current.selectedMemo ?: return
        launchBusy {
            val updated = if (isOfflineMode()) {
                localDataStore.setMemoFavorited(memo, memo.favoritedAt == null)
            } else {
                api.setMemoFavorited(memo, memo.favoritedAt == null)
            }
            applyMemo(updated)
            updateState {
                it.copy(notice = uiString(if (updated.favoritedAt == null) R.string.notice_unfavorited else R.string.notice_favorited))
            }
        }
    }

    fun toggleSelectedMemoArchived() {
        val current = state.value
        if (current.screen == Screen.Editor && !current.canRunMemoEditorAction()) {
            return
        }
        val memo = current.selectedMemo ?: return
        launchBusy {
            val updated = if (isOfflineMode()) {
                localDataStore.setMemoArchived(memo, memo.archivedAt == null)
            } else {
                api.setMemoArchived(memo, memo.archivedAt == null)
            }
            applyMemo(updated)
            updateState {
                it.copy(notice = uiString(if (updated.archivedAt == null) R.string.notice_unarchived else R.string.notice_archived))
            }
        }
    }

    fun toggleMemoFavorited(memo: Memo) {
        launchBusy {
            val updated = if (isOfflineMode()) {
                localDataStore.setMemoFavorited(memo, memo.favoritedAt == null)
            } else {
                api.setMemoFavorited(memo, memo.favoritedAt == null)
            }
            applyMemo(updated)
            updateState {
                it.copy(notice = uiString(if (updated.favoritedAt == null) R.string.notice_unfavorited else R.string.notice_favorited))
            }
        }
    }

    fun toggleMemoArchived(memo: Memo) {
        launchBusy {
            val updated = if (isOfflineMode()) {
                localDataStore.setMemoArchived(memo, memo.archivedAt == null)
            } else {
                api.setMemoArchived(memo, memo.archivedAt == null)
            }
            applyMemo(updated)
            updateState {
                it.copy(notice = uiString(if (updated.archivedAt == null) R.string.notice_unarchived else R.string.notice_archived))
            }
        }
    }

    fun deleteMemo(memo: Memo) {
        launchBusy {
            val deleted = if (isOfflineMode()) {
                localDataStore.deleteMemo(memo)
            } else {
                api.deleteMemo(memo)
            }
            applyMemo(deleted)
            updateState {
                it.copy(
                    selectedMemo = if (it.selectedMemo?.id == memo.id) null else it.selectedMemo,
                    selectedSummary = if (it.selectedMemo?.id == memo.id) null else it.selectedSummary,
                    notice = uiString(R.string.notice_deleted),
                )
            }
            refreshMemos()
        }
    }

    fun summarizeSelectedMemo() {
        val current = state.value
        if (current.screen == Screen.Editor && !current.canRunMemoEditorAction()) {
            return
        }
        val memo = current.selectedMemo ?: return
        if (isOfflineMode()) {
            viewModelScope.launch {
                updateState { it.copy(summaryLoading = true, error = null, notice = null) }
                try {
                    val profile = localDataStore.activeAIProfile()
                        ?: throw IllegalArgumentException(uiString(R.string.error_ai_default_profile_required))
                    val ai = localAiClient.summarizeMemo(profile, memo)
                    localDataStore.saveMemoAI(ai)
                    updateState {
                        it.copy(selectedSummary = ai, summaryLoading = false, notice = uiString(R.string.notice_summary_generated))
                    }
                } catch (error: Throwable) {
                    updateState { it.copy(summaryLoading = false, error = error.readableMessage()) }
                }
            }
            return
        }
        val memoId = memo.id
        viewModelScope.launch {
            updateState { it.copy(summaryLoading = true, error = null, notice = null) }
            runCatching { api.generateMemoSummary(memo) }
                .onSuccess { ai ->
                    updateState { current ->
                        if (current.selectedMemo?.id == memoId) {
                            current.copy(
                                selectedSummary = ai,
                                summaryLoading = false,
                                notice = uiString(R.string.notice_summary_generated),
                            )
                        } else {
                            current
                        }
                    }
                }
                .onFailure { error ->
                    updateState { current ->
                        if (current.selectedMemo?.id == memoId) {
                            current.copy(
                                summaryLoading = false,
                                error = error.readableMessage(),
                            )
                        } else {
                            current
                        }
                    }
                }
        }
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
            it.copy(
                aiProfiles = it.aiProfiles + AIProfileDraft(active = it.aiProfiles.isEmpty()),
            )
        }
    }

    fun removeAIProfile(index: Int) {
        val currentProfiles = state.value.aiProfiles
        if (index !in currentProfiles.indices) {
            return
        }
        val nextProfiles = currentProfiles.filterIndexed { i, _ -> i != index }
        viewModelScope.launch {
            updateState {
                it.copy(
                    aiProfiles = nextProfiles,
                    aiSettingsSaving = true,
                    error = null,
                    notice = null,
                )
            }
            runCatching { persistAIProfiles(nextProfiles) }
                .onSuccess { savedProfiles ->
                    updateState {
                        it.copy(
                            aiProfiles = savedProfiles,
                            aiSettingsSaving = false,
                            aiTestResults = emptyMap(),
                            aiModelResults = emptyMap(),
                            notice = uiString(R.string.notice_ai_profile_deleted),
                        )
                    }
                }
                .onFailure { error ->
                    updateState {
                        it.copy(
                            aiProfiles = currentProfiles,
                            aiSettingsSaving = false,
                            error = error.readableMessage(),
                        )
                    }
                }
        }
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
        val currentProfiles = state.value.aiProfiles
        if (index !in currentProfiles.indices) {
            return
        }
        val nextProfiles = currentProfiles.mapIndexed { i, profile ->
            profile.copy(enabled = true, active = i == index)
        }
        viewModelScope.launch {
            updateState {
                it.copy(
                    aiProfiles = nextProfiles,
                    aiSettingsSaving = true,
                    error = null,
                    notice = null,
                )
            }
            runCatching { persistAIProfiles(nextProfiles) }
                .onSuccess { savedProfiles ->
                    updateState {
                        it.copy(
                            aiProfiles = savedProfiles,
                            aiSettingsSaving = false,
                            aiTestResults = emptyMap(),
                            aiModelResults = emptyMap(),
                            notice = uiString(R.string.notice_ai_default_set),
                        )
                    }
                }
                .onFailure { error ->
                    updateState {
                        it.copy(
                            aiProfiles = currentProfiles,
                            aiSettingsSaving = false,
                            error = error.readableMessage(),
                        )
                    }
                }
        }
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
        cancelAIAutoSummarySave()
        val mode = state.value.appMode
        val loadRequestId = state.value.aiAutoSummaryRequestId + 1
        updateState {
            it.invalidateAIAutoSummaryRequest().copy(
                aiSettingsLoading = true,
                aiSettingsLoadError = null,
                error = null,
                notice = null,
            )
        }
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
                            current.aiAutoSummaryRequestId == loadRequestId &&
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
                            current.aiAutoSummaryRequestId == loadRequestId &&
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
        val draftProfiles = state.value.aiProfiles
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
        viewModelScope.launch {
            updateState { it.copy(aiSettingsSaving = true, error = null, notice = null) }
            runCatching { persistAIProfiles(profiles) }
                .onSuccess { savedProfiles ->
                    updateState {
                        it.copy(
                            aiProfiles = savedProfiles,
                            aiSettingsSaving = false,
                            aiTestResults = emptyMap(),
                            aiModelResults = emptyMap(),
                            notice = uiString(R.string.notice_ai_profiles_saved),
                        )
                    }
                }
                .onFailure { error ->
                    updateState {
                        it.copy(
                            aiSettingsSaving = false,
                            error = error.readableMessage(),
                        )
                    }
                }
        }
    }

    private suspend fun persistAIProfiles(profiles: List<AIProfileDraft>): List<AIProfileDraft> {
        val blankNameIndex = firstBlankAIProfileNameIndex(profiles)
        if (blankNameIndex != null) {
            throw IllegalArgumentException(
                uiString(R.string.error_ai_profile_name_required, blankNameIndex + 1),
            )
        }
        val normalized = normalizedAIProfiles(profiles)
        return if (isOfflineMode()) {
            localDataStore.saveAIProfiles(normalized)
        } else {
            api.patchAISettings(normalized.map { it.toInput() }).let { settings ->
                val localProfiles = mergeSavedAIProfilesForLocalStorage(
                    currentProfiles = localDataStore.listAIProfiles(),
                    remoteProfiles = settings.profiles.map { it.toDraft() },
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
        localDataStore.saveAutoSummary(savedValue)
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
        if (requestState.askLoading || requestState.askSending || requestState.askVariantLoading) {
            return
        }
        val screenSessionId = requestState.askScreenSessionId
        val appMode = requestState.appMode
        updateState { current ->
            if (
                !current.askLoading &&
                !current.askSending &&
                !current.askVariantLoading &&
                current.askScreenSessionId == screenSessionId &&
                current.appMode == appMode
            ) {
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
        if (!state.value.askLoading) {
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
                        if (current.askScreenSessionId == screenSessionId && current.appMode == appMode) {
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
                        if (current.askScreenSessionId == screenSessionId && current.appMode == appMode) {
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
        if (
            state.value.askLoading ||
            state.value.askSending ||
            state.value.askVariantLoading ||
            state.value.askSourceLoading ||
            id.isBlank()
        ) {
            return
        }
        val conversation = state.value.askConversations.find { it.id == id }
        updateState {
            it.copy(
                activeAskId = id,
                askHeadId = conversation?.headMessageId,
                askMessages = emptyList(),
                askScope = conversation?.contextScope ?: it.askScope,
                askLoading = true,
                askLoadError = null,
                askVariantRequestId = it.askVariantRequestId + 1,
                askVariantLoading = false,
                askSourceRequestId = it.askSourceRequestId + 1,
                askSourceLoading = false,
                error = null,
                notice = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                if (isOfflineMode()) {
                    localDataStore.listAskMessages(id)
                } else {
                    api.listAskMessages(id)
                }
            }
                .onSuccess { messages ->
                    updateState {
                        if (it.activeAskId == id) {
                            it.copy(
                                askMessages = messages,
                                askLoading = false,
                                askLoadError = null,
                            )
                        } else {
                            it
                        }
                    }
                }
                .onFailure { error ->
                    updateState {
                        if (it.activeAskId == id) {
                            val message = error.readableMessage()
                            it.copy(
                                askLoading = false,
                                askLoadError = message,
                                error = message,
                            )
                        } else {
                            it
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
        if (state.value.askVariantLoading || state.value.askSourceLoading) {
            return
        }
        val content = askAnswerMemoContent(message)
        if (content.isBlank()) {
            return
        }
        viewModelScope.launch {
            updateState { it.copy(loading = true, error = null, notice = null) }
            runCatching {
                if (isOfflineMode()) {
                    localDataStore.createMemo(content, LocalDate.now().toString())
                } else {
                    api.createMemo(content, LocalDate.now().toString())
                }
            }
                .onSuccess { memo ->
                    applyMemo(memo)
                    updateState {
                        it.copy(
                            screen = Screen.MemoDetail,
                            screenHistory = it.historyFor(Screen.MemoDetail),
                            selectedMemo = memo,
                            selectedSummary = null,
                            summaryLoading = !isOfflineMode(),
                            uploadingAttachment = false,
                            markdownPreview = false,
                            notice = uiString(R.string.notice_ask_saved_record),
                        )
                    }
                    fetchSelectedMemoDetail(memo.id)
                    refreshMemos()
                }
                .onFailure { error ->
                    updateState { it.copy(error = error.readableMessage()) }
                }
            updateState { it.copy(loading = false) }
        }
    }

    fun openAskSourceMemo(memoId: String) {
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
            it.copy(
                aiProfiles = it.aiProfiles.mapIndexed { i, profile ->
                    if (i == index) transform(profile) else profile
                },
            )
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
        updateState { it.copy(openingAttachmentPath = null) }
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
        if (isOfflineMode()) {
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

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            updateState { it.copy(loading = true, error = null, notice = null) }
            runCatching { block() }
                .onFailure { error ->
                    updateState {
                        it.copy(
                            screen = if (it.screen == Screen.Loading) Screen.Server else it.screen,
                            error = error.readableMessage(),
                        )
                    }
                }
            updateState { it.copy(loading = false) }
        }
    }

    private fun enterOfflineMode(notice: String?) {
        cancelAIAutoSummarySave()
        val filter = state.value.memoListFilter
        val memos = memosForFilter(localDataStore.listMemos(), filter)
        updateState {
            it.invalidateAIAutoSummaryRequest().copy(
                appMode = SessionStore.MODE_OFFLINE,
                initialized = true,
                account = null,
                memos = memos,
                memoNextCursor = "",
                loadingMoreMemos = false,
                memoListLoadStatus = MemoListLoadStatus.Idle,
                selectedMemo = null,
                selectedSummary = null,
                summaryLoading = false,
                uploadingAttachment = false,
                aiAutoSummary = localDataStore.autoSummaryEnabled(),
                aiSettingsLoading = false,
                aiSettingsLoadError = null,
                askLoading = false,
                askLoadError = null,
                searchQuery = "",
                searchResults = null,
                searching = false,
                screen = Screen.Memos,
                screenHistory = emptyList(),
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

    private fun applyMemo(memo: Memo) {
        synchronized(memoPageLock) {
            loadMoreMemosJob?.cancel()
            loadMoreMemosJob = null
        }
        searchJob?.cancel()
        searchJob = null
        updateState { current -> current.applyMemoToCache(memo) }
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
        val resourceId = when (normalized) {
            "请求失败" -> R.string.error_request_failed
            "操作失败" -> R.string.error_operation_failed
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
        if (resourceId != null) {
            return uiString(resourceId)
        }
        if (normalized.startsWith("AI 请求失败：")) {
            return uiString(R.string.error_ai_request, normalized.substringAfter("AI 请求失败："))
        }
        if (raw.isBlank()) {
            return uiString(R.string.error_operation_failed)
        }
        val containsHan = HAN_CHARACTER.containsMatchIn(raw)
        return if (state.value.languageMode == SessionStore.LANGUAGE_EN && containsHan) {
            uiString(R.string.error_operation_failed)
        } else {
            raw
        }
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
    clearLocalSession: () -> Unit,
): SignOutFeedback {
    val remoteSignOutFailed = !offlineMode && runCatching { remoteSignOut() }.isFailure
    clearLocalSession()
    return signOutFeedback(
        offlineMode = offlineMode,
        remoteSignOutFailed = remoteSignOutFailed,
    )
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
