package app.sillage.ui

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
import app.sillage.data.MarkdownFormatStyle
import app.sillage.data.SessionStore
import app.sillage.data.SillageApi
import app.sillage.data.SillageExportCodec
import app.sillage.data.SyncPushSummary
import app.sillage.data.activeMemos
import app.sillage.data.askAnswerMemoContent
import app.sillage.data.askBranchLeafId
import app.sillage.data.attachmentMarkdown
import app.sillage.data.buildAskActivePath
import app.sillage.data.lastAssistantMessageId
import app.sillage.data.markdownFormatSnippet
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
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
    private val memoPageLock = Any()
    private val _attachmentOpenEvents = Channel<AttachmentOpenEvent>(Channel.BUFFERED)
    private val _state = MutableStateFlow(
        SillageUiState(
            screen = Screen.Loading,
            baseUrl = sessionStore.baseUrl(),
            account = sessionStore.account(),
            themeMode = sessionStore.themeMode(),
            appMode = sessionStore.appMode(),
        ),
    )

    val state: StateFlow<SillageUiState> = _state.asStateFlow()
    internal val attachmentOpenEvents: Flow<AttachmentOpenEvent> = _attachmentOpenEvents.receiveAsFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            pruneAttachmentOpenCache(File(appContext.cacheDir, OPEN_ATTACHMENTS_CACHE_DIRECTORY))
        }
        if (!sessionStore.hasAppModeSelection()) {
            _state.update { it.copy(screen = Screen.ModeSelection) }
        } else if (sessionStore.appMode() == SessionStore.MODE_OFFLINE) {
            enterOfflineMode(notice = null)
        } else {
            connect()
        }
    }

    fun chooseOnlineMode() {
        _state.update {
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
        _state.update { it.copy(baseUrl = value) }
    }

    fun saveServer() {
        val normalized = SessionStore.normalizeBaseUrl(state.value.baseUrl)
        if (normalized.isBlank()) {
            _state.update { it.copy(error = "请先填写服务器地址", notice = null) }
            return
        }
        cancelAttachmentOpen()
        cancelMemoPageLoad()
        cancelAskVariant()
        cancelAskStream()
        sessionStore.saveBaseUrl(state.value.baseUrl)
        sessionStore.saveAppMode(SessionStore.MODE_ONLINE)
        _state.update {
            it.copy(
                appMode = SessionStore.MODE_ONLINE,
                baseUrl = sessionStore.baseUrl(),
                account = null,
                memos = emptyList(),
                memoNextCursor = "",
                loadingMoreMemos = false,
                selectedSummary = null,
                summaryLoading = false,
                uploadingAttachment = false,
                askLoading = false,
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
        sessionStore.saveAppMode(SessionStore.MODE_ONLINE)
        _state.update {
            it.copy(
                appMode = SessionStore.MODE_ONLINE,
                screenHistory = emptyList(),
                memos = emptyList(),
                memoNextCursor = "",
                loadingMoreMemos = false,
                selectedMemo = null,
                selectedSummary = null,
                askLoading = false,
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
        enterOfflineMode(notice = "已切换到离线模式")
    }

    fun openServerSettings() {
        _state.update {
            it.copy(
                screen = Screen.Server,
                serverReturnScreen = it.screen.takeIf { screen -> screen != Screen.Server && screen != Screen.ModeSelection },
                error = null,
                notice = null,
            )
        }
    }

    fun cancelServerConnection() {
        _state.update {
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
        _state.update {
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
        _state.update {
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
        _state.update { it.copy(themeMode = next) }
    }

    fun connect() {
        if (state.value.appMode == SessionStore.MODE_OFFLINE) {
            enterOfflineMode(notice = null)
            return
        }
        if (SessionStore.normalizeBaseUrl(state.value.baseUrl).isBlank()) {
            _state.update {
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
                _state.update { it.copy(screen = Screen.Initialize, initialized = false, account = null) }
                return@launchBusy
            }
            if (token.isNullOrBlank() || account == null) {
                _state.update { it.copy(screen = Screen.Login, initialized = true, account = null) }
                return@launchBusy
            }
            val verified = api.me()
            _state.update { it.copy(screen = Screen.Memos, initialized = true, account = verified) }
            refreshMemos()
        }
    }

    fun updateUsername(value: String) = _state.update { it.copy(username = value) }

    fun updateDisplayName(value: String) = _state.update { it.copy(displayName = value) }

    fun updatePassword(value: String) = _state.update { it.copy(password = value) }

    fun initialize() {
        val current = state.value
        launchBusy {
            val session = api.initialize(current.username, current.displayName, current.password)
            _state.update {
                it.copy(
                    account = session.account,
                    username = "",
                    displayName = "",
                    password = "",
                    screen = Screen.Memos,
                    screenHistory = emptyList(),
                    initialized = true,
                )
            }
            refreshMemos()
        }
    }

    fun signIn() {
        val current = state.value
        launchBusy {
            val session = api.signIn(current.username, current.password)
            _state.update {
                it.copy(
                    account = session.account,
                    username = "",
                    password = "",
                    screen = Screen.Memos,
                    screenHistory = emptyList(),
                    initialized = true,
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
        viewModelScope.launch {
            if (!isOfflineMode()) {
                runCatching { api.signOut() }
            }
            sessionStore.clearSession()
            _state.update {
                it.copy(
                    account = null,
                    memos = emptyList(),
                    memoNextCursor = "",
                    loadingMoreMemos = false,
                    selectedMemo = null,
                    selectedSummary = null,
                    summaryLoading = false,
                    uploadingAttachment = false,
                    aiProfiles = emptyList(),
                    aiAutoSummary = if (isOfflineMode()) localDataStore.autoSummaryEnabled() else false,
                    aiSettingsLoading = false,
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
                    askSending = false,
                    askStreaming = false,
                    askVariantLoading = false,
                    askRegeneratingId = "",
                    askLiveUser = null,
                    askLiveAnswer = "",
                    searchQuery = "",
                    searchResults = null,
                    searching = false,
                    screen = if (isOfflineMode()) Screen.Memos else Screen.Login,
                    screenHistory = emptyList(),
                    notice = if (isOfflineMode()) "已清除在线登录信息" else "已退出登录",
                    error = null,
                )
            }
        }
    }

    fun refreshMemos() {
        cancelMemoPageLoad()
        viewModelScope.launch {
            runCatching {
                if (isOfflineMode()) {
                    MemoListSnapshot(
                        memos = localDataStore.listMemos(),
                        nextCursor = "",
                    )
                } else {
                    api.listMemos().let { page ->
                        MemoListSnapshot(
                            memos = page.memos,
                            nextCursor = page.nextCursor,
                        )
                    }
                }
            }
                .onSuccess { snapshot ->
                    _state.update {
                        it.copy(
                            memos = activeMemos(snapshot.memos),
                            memoNextCursor = snapshot.nextCursor,
                            loadingMoreMemos = false,
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(loadingMoreMemos = false, error = error.readableMessage()) }
                }
        }
    }

    fun loadMoreMemos() {
        val job = synchronized(memoPageLock) {
            if (loadMoreMemosJob?.isActive == true) {
                return
            }
            val request = state.value.nextMemoPageRequest() ?: return
            _state.update { current ->
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
                runCatching { api.listMemos(cursor = request.cursor) }
                .onSuccess { page ->
                    _state.update { current ->
                        if (current.canApplyMemoPage(request)) {
                            current.copy(
                                memos = activeMemos(current.memos + page.memos),
                                memoNextCursor = page.nextCursor,
                                loadingMoreMemos = false,
                            )
                        } else {
                            current
                        }
                    }
                }
                .onFailure { error ->
                    _state.update { current ->
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
        _state.update {
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
        _state.update {
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
    }

    fun duplicateMemoDraft(memo: Memo) {
        val today = LocalDate.now().toString()
        _state.update {
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

    fun updateDraftContent(value: String) = _state.update { it.copy(draftContent = value) }

    fun updateDraftEntryDate(value: String) = _state.update { it.copy(draftEntryDate = value) }

    fun updateMarkdownPreview(preview: Boolean) {
        _state.update { it.copy(markdownPreview = preview) }
    }

    fun appendMarkdownFormat(style: MarkdownFormatStyle) {
        val snippet = markdownFormatSnippet(style)
        _state.update {
            val separator = if (it.draftContent.isBlank() || snippet.startsWith("\n")) "" else " "
            it.copy(
                draftContent = it.draftContent + separator + snippet,
                markdownPreview = false,
            )
        }
    }

    fun updateSearchQuery(value: String) {
        _state.update {
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
        val query = state.value.searchQuery.trim()
        if (query.isBlank()) {
            clearSearch()
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(searching = true, error = null, notice = null) }
            runCatching {
                if (isOfflineMode()) {
                    localDataStore.searchMemos(query)
                } else {
                    api.searchMemos(query)
                }
            }
                .onSuccess { memos ->
                    _state.update { current ->
                        if (current.searchQuery.trim() == query) {
                            current.copy(
                                searchResults = activeMemos(memos),
                                searching = false,
                                error = null,
                            )
                        } else {
                            current
                        }
                    }
                }
                .onFailure { error ->
                    _state.update { current ->
                        if (current.searchQuery.trim() == query) {
                            current.copy(
                                searchResults = emptyList(),
                                searching = false,
                                error = error.readableMessage(),
                            )
                        } else {
                            current
                        }
                    }
                }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _state.update {
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
            _state.update { it.copy(loading = true, error = null, notice = null) }
            runCatching {
                if (!isOfflineMode()) {
                    localDataStore.mergeFromServer(exportOnlineData())
                }
                val data = localDataStore.exportData(state.value.themeMode, state.value.memoViewMode.name)
                val json = SillageExportCodec.toJson(data)
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(json.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalArgumentException("无法写入导出文件")
                }
            }
                .onSuccess {
                    _state.update { it.copy(notice = "完整数据已导出") }
                }
                .onFailure { error ->
                    _state.update { it.copy(error = error.readableMessage()) }
                }
            _state.update { it.copy(loading = false) }
        }
    }

    fun importFullData(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, notice = null) }
            runCatching {
                val raw = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    } ?: throw IllegalArgumentException("无法读取导入文件")
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
                    _state.update {
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
                            notice = "完整数据已导入",
                        )
                    }
                    refreshMemos()
                }
                .onFailure { error ->
                    _state.update { it.copy(error = error.readableMessage()) }
                }
            _state.update { it.copy(loading = false) }
        }
    }

    fun syncFromServer() {
        if (isOfflineMode()) {
            _state.update { it.copy(error = "同步需要在线模式", notice = null) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, notice = null) }
            runCatching {
                val data = exportOnlineData()
                localDataStore.mergeFromServer(data)
            }
                .onSuccess {
                    _state.update { it.copy(notice = "已同步到本地") }
                }
                .onFailure { error ->
                    _state.update { it.copy(error = error.readableMessage()) }
                }
            _state.update { it.copy(loading = false) }
        }
    }

    fun syncToServer() {
        if (isOfflineMode()) {
            _state.update { it.copy(error = "同步需要在线模式", notice = null) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, notice = null) }
            runCatching { pushLocalMemosToServer() }
                .onSuccess { summary ->
                    _state.update { it.copy(notice = syncPushNotice(summary)) }
                }
                .onFailure { error ->
                    _state.update { it.copy(error = error.readableMessage()) }
                }
            _state.update { it.copy(loading = false) }
        }
    }

    fun syncBothWays() {
        if (isOfflineMode()) {
            _state.update { it.copy(error = "同步需要在线模式", notice = null) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, notice = null) }
            runCatching {
                val push = pushLocalMemosToServer()
                localDataStore.mergeFromServer(exportOnlineData())
                push
            }
                .onSuccess { summary ->
                    _state.update { it.copy(notice = "双向同步完成。${syncPushNotice(summary)}") }
                    refreshMemos()
                }
                .onFailure { error ->
                    _state.update { it.copy(error = error.readableMessage()) }
                }
            _state.update { it.copy(loading = false) }
        }
    }

    fun updateMemoViewMode(mode: MemoViewMode) {
        if (state.value.askVariantLoading) {
            return
        }
        _state.update {
            it.copy(
                screen = Screen.Memos,
                screenHistory = emptyList(),
                memoViewMode = mode,
                searchQuery = if (mode == MemoViewMode.Calendar) "" else it.searchQuery,
                searchResults = if (mode == MemoViewMode.Calendar) null else it.searchResults,
                searching = if (mode == MemoViewMode.Calendar) false else it.searching,
                selectedMemo = null,
                selectedSummary = null,
                error = if (mode == MemoViewMode.Calendar) null else it.error,
            )
        }
    }

    fun changeCalendarMonth(delta: Int) {
        _state.update {
            val next = java.time.YearMonth.of(it.calendarYear, it.calendarMonth).plusMonths(delta.toLong())
            it.copy(
                calendarYear = next.year,
                calendarMonth = next.monthValue,
                selectedCalendarDate = null,
            )
        }
    }

    fun selectCalendarDate(date: String) {
        _state.update { it.copy(selectedCalendarDate = date) }
    }

    fun saveMemo() {
        val current = state.value
        if (!current.canRunMemoEditorAction()) {
            return
        }
        if (current.draftContent.isBlank()) {
            _state.update { it.copy(error = "记录内容不能为空") }
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
            _state.update {
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
                    notice = "已保存",
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
            _state.update {
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
                    notice = "已删除",
                )
            }
            refreshMemos()
        }
    }

    fun toggleSelectedMemoPinned() {
        val current = state.value
        if (current.screen == Screen.Editor && !current.canRunMemoEditorAction()) {
            return
        }
        val memo = current.selectedMemo ?: return
        launchBusy {
            val updated = if (isOfflineMode()) {
                localDataStore.setMemoPinned(memo, memo.pinnedAt == null)
            } else {
                api.setMemoPinned(memo, memo.pinnedAt == null)
            }
            applyMemo(updated)
            _state.update {
                it.copy(notice = if (updated.pinnedAt == null) "已取消置顶" else "已置顶")
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
            _state.update {
                it.copy(notice = if (updated.archivedAt == null) "已取消归档" else "已归档")
            }
        }
    }

    fun toggleMemoPinned(memo: Memo) {
        launchBusy {
            val updated = if (isOfflineMode()) {
                localDataStore.setMemoPinned(memo, memo.pinnedAt == null)
            } else {
                api.setMemoPinned(memo, memo.pinnedAt == null)
            }
            applyMemo(updated)
            _state.update {
                it.copy(notice = if (updated.pinnedAt == null) "已取消置顶" else "已置顶")
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
            _state.update {
                it.copy(notice = if (updated.archivedAt == null) "已取消归档" else "已归档")
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
            _state.update {
                it.copy(
                    selectedMemo = if (it.selectedMemo?.id == memo.id) null else it.selectedMemo,
                    selectedSummary = if (it.selectedMemo?.id == memo.id) null else it.selectedSummary,
                    notice = "已删除",
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
                _state.update { it.copy(summaryLoading = true, error = null, notice = null) }
                try {
                    val profile = localDataStore.activeAIProfile() ?: throw IllegalArgumentException("请先配置一个默认 AI 档案")
                    val ai = localAiClient.summarizeMemo(profile, memo)
                    localDataStore.saveMemoAI(ai)
                    _state.update { it.copy(selectedSummary = ai, summaryLoading = false, notice = "已生成总结") }
                } catch (error: Throwable) {
                    _state.update { it.copy(summaryLoading = false, error = error.readableMessage()) }
                }
            }
            return
        }
        val memoId = memo.id
        viewModelScope.launch {
            _state.update { it.copy(summaryLoading = true, error = null, notice = null) }
            runCatching { api.generateMemoSummary(memo) }
                .onSuccess { ai ->
                    _state.update { current ->
                        if (current.selectedMemo?.id == memoId) {
                            current.copy(
                                selectedSummary = ai,
                                summaryLoading = false,
                                notice = "已生成总结",
                            )
                        } else {
                            current
                        }
                    }
                }
                .onFailure { error ->
                    _state.update { current ->
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
            _state.update { it.copy(error = "附件上传需要在线模式") }
            return
        }
        val editorSessionId = current.editorSessionId
        _state.update {
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
                    _state.update {
                        if (it.canApplyAttachmentUpload(editorSessionId)) {
                            it.copy(
                                draftContent = it.draftContent + snippets,
                                uploadingAttachment = false,
                                notice = "附件已插入",
                            )
                        } else {
                            it
                        }
                    }
                }
                .onFailure { error ->
                    _state.update {
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
            _state.update { it.copy(error = "打开附件需要在线模式", notice = null) }
            return
        }
        val current = state.value
        if (current.openingAttachmentPath != null || attachmentOpenJob?.isActive == true) {
            return
        }
        val requestId = current.attachmentOpenRequestId + 1
        _state.update {
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
                        throw IllegalStateException("无法准备附件")
                    }
                    requestDirectory = null
                }
            } catch (error: CancellationException) {
                clearAttachmentOpenRequest(requestId)
                throw error
            } catch (error: Throwable) {
                _state.update {
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
        _state.update {
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
        _state.update {
            it.copy(aiProfiles = it.aiProfiles + AIProfileDraft(active = it.aiProfiles.isEmpty()))
        }
    }

    fun removeAIProfile(index: Int) {
        val currentProfiles = state.value.aiProfiles
        if (index !in currentProfiles.indices) {
            return
        }
        val nextProfiles = currentProfiles.filterIndexed { i, _ -> i != index }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    aiProfiles = nextProfiles,
                    aiSettingsSaving = true,
                    error = null,
                    notice = null,
                )
            }
            runCatching { persistAISettings(nextProfiles, state.value.aiAutoSummary) }
                .onSuccess { saved ->
                    _state.update {
                        it.copy(
                            aiProfiles = saved.profiles,
                            aiAutoSummary = saved.autoSummary,
                            aiSettingsSaving = false,
                            aiTestResults = emptyMap(),
                            aiModelResults = emptyMap(),
                            notice = "AI 档案已删除",
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
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
            _state.update {
                it.copy(
                    aiProfiles = nextProfiles,
                    aiSettingsSaving = true,
                    error = null,
                    notice = null,
                )
            }
            runCatching { persistAISettings(nextProfiles, state.value.aiAutoSummary) }
                .onSuccess { saved ->
                    _state.update {
                        it.copy(
                            aiProfiles = saved.profiles,
                            aiAutoSummary = saved.autoSummary,
                            aiSettingsSaving = false,
                            aiTestResults = emptyMap(),
                            aiModelResults = emptyMap(),
                            notice = "已设为默认",
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            aiProfiles = currentProfiles,
                            aiSettingsSaving = false,
                            error = error.readableMessage(),
                        )
                    }
                }
        }
    }

    fun toggleAISettingsAutoSummary() {
        _state.update { it.copy(aiAutoSummary = !it.aiAutoSummary) }
    }

    fun loadAISettings() {
        viewModelScope.launch {
            _state.update { it.copy(aiSettingsLoading = true, error = null, notice = null) }
            runCatching {
                if (isOfflineMode()) {
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
                    _state.update {
                        it.copy(
                            aiProfiles = settings.profiles,
                            aiAutoSummary = settings.autoSummary,
                            aiSettingsLoading = false,
                            aiTestResults = emptyMap(),
                            aiModelResults = emptyMap(),
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            aiSettingsLoading = false,
                            error = error.readableMessage(),
                        )
                    }
                }
        }
    }

    fun saveAISettings() {
        val profiles = normalizedAIProfiles(state.value.aiProfiles)
        val autoSummary = state.value.aiAutoSummary
        viewModelScope.launch {
            _state.update { it.copy(aiSettingsSaving = true, error = null, notice = null) }
            runCatching { persistAISettings(profiles, autoSummary) }
                .onSuccess { saved ->
                    _state.update {
                        it.copy(
                            aiProfiles = saved.profiles,
                            aiAutoSummary = saved.autoSummary,
                            aiSettingsSaving = false,
                            aiTestResults = emptyMap(),
                            aiModelResults = emptyMap(),
                            notice = "AI 设置已保存",
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            aiSettingsSaving = false,
                            error = error.readableMessage(),
                        )
                    }
                }
        }
    }

    private suspend fun persistAISettings(
        profiles: List<AIProfileDraft>,
        autoSummary: Boolean,
    ): EditableAISettings {
        val normalized = normalizedAIProfiles(profiles)
        return if (isOfflineMode()) {
            EditableAISettings(
                profiles = localDataStore.saveAISettings(normalized, autoSummary),
                autoSummary = autoSummary,
            )
        } else {
            api.patchAISettings(normalized.map { it.toInput() }, autoSummary).let { settings ->
                val localProfiles = mergeSavedAIProfilesForLocalStorage(
                    currentProfiles = localDataStore.listAIProfiles(),
                    remoteProfiles = settings.profiles.map { it.toDraft() },
                    submittedProfiles = normalized,
                )
                EditableAISettings(
                    profiles = localDataStore.saveAISettings(localProfiles, settings.autoSummary),
                    autoSummary = settings.autoSummary,
                )
            }
        }
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
            _state.update { it.copy(aiTestingProfileId = key, error = null, notice = null) }
            try {
                val model = if (isOfflineMode()) {
                    localAiClient.testConnection(profile)
                } else if (profile.id.isBlank()) {
                    api.testAIConnection(profile.toInput())
                } else {
                    api.testAIConnection(profile.id)
                }
                _state.update {
                    it.copy(
                        aiTestingProfileId = "",
                        aiTestResults = it.aiTestResults + (key to "连接成功（$model）"),
                    )
                }
            } catch (error: Throwable) {
                _state.update {
                    it.copy(
                        aiTestingProfileId = "",
                        aiTestResults = it.aiTestResults + (key to error.readableMessage()),
                    )
                }
            }
        }
    }

    fun loadAIModels(index: Int) {
        val profile = state.value.aiProfiles.getOrNull(index) ?: return
        val key = profile.uiKey(index)
        if (isOfflineMode()) {
            _state.update { it.copy(aiTestResults = it.aiTestResults + (key to "离线模式无法获取云端模型列表")) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(aiLoadingModelsProfileId = key, error = null, notice = null) }
            runCatching { api.listAIModels(profile.toInput()) }
                .onSuccess { models ->
                    _state.update {
                        it.copy(
                            aiLoadingModelsProfileId = "",
                            aiModelResults = it.aiModelResults + (key to models),
                            aiTestResults = it.aiTestResults + (key to if (models.isEmpty()) "没有可用模型" else "已获取模型列表"),
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            aiLoadingModelsProfileId = "",
                            aiTestResults = it.aiTestResults + (key to error.readableMessage()),
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
        _state.update { current ->
            if (
                !current.askLoading &&
                !current.askSending &&
                !current.askVariantLoading &&
                current.askScreenSessionId == screenSessionId &&
                current.appMode == appMode
            ) {
                current.copy(askLoading = true, error = null, notice = null)
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
                    _state.update { current ->
                        if (current.askScreenSessionId == screenSessionId && current.appMode == appMode) {
                            current.copy(
                                askConversations = conversations.filter { conversation -> conversation.deletedAt == null },
                                askLoading = false,
                                error = null,
                            )
                        } else {
                            current
                        }
                    }
                }
                .onFailure { error ->
                    _state.update { current ->
                        if (current.askScreenSessionId == screenSessionId && current.appMode == appMode) {
                            current.copy(
                                askLoading = false,
                                error = error.readableMessage(),
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
        _state.update {
            it.copy(
                activeAskId = id,
                askHeadId = conversation?.headMessageId,
                askMessages = emptyList(),
                askScope = conversation?.contextScope ?: it.askScope,
                askLoading = true,
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
                    _state.update {
                        if (it.activeAskId == id) {
                            it.copy(
                                askMessages = messages,
                                askLoading = false,
                            )
                        } else {
                            it
                        }
                    }
                }
                .onFailure { error ->
                    _state.update {
                        if (it.activeAskId == id) {
                            it.copy(askLoading = false, error = error.readableMessage())
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
        _state.update {
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
        _state.update { it.copy(askQuestion = value) }
    }

    fun updateAskScope(value: String) {
        _state.update { it.copy(askScope = value) }
    }

    fun updateAskSourceKind(value: String) {
        _state.update { it.copy(askSourceKind = value) }
    }

    fun sendAskQuestion() {
        val question = state.value.askQuestion.trim()
        if (question.isBlank()) {
            _state.update { it.copy(error = "先写下要问的问题") }
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
        askStreamJob?.cancel()
    }

    fun selectAskVariant(messageId: String) {
        val current = state.value
        val request = current.nextAskVariantRequest() ?: return
        val leafId = askBranchLeafId(current.askMessages, messageId)
        val previousHeadId = current.askHeadId
        _state.update {
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
            _state.update { it.copy(loading = true, error = null, notice = null) }
            runCatching {
                if (isOfflineMode()) {
                    localDataStore.createMemo(content, LocalDate.now().toString())
                } else {
                    api.createMemo(content, LocalDate.now().toString())
                }
            }
                .onSuccess { memo ->
                    applyMemo(memo)
                    _state.update {
                        it.copy(
                            screen = Screen.MemoDetail,
                            screenHistory = it.historyFor(Screen.MemoDetail),
                            selectedMemo = memo,
                            selectedSummary = null,
                            summaryLoading = !isOfflineMode(),
                            uploadingAttachment = false,
                            markdownPreview = false,
                            notice = "已存为记录",
                        )
                    }
                    fetchSelectedMemoDetail(memo.id)
                    refreshMemos()
                }
                .onFailure { error ->
                    _state.update { it.copy(error = error.readableMessage()) }
                }
            _state.update { it.copy(loading = false) }
        }
    }

    fun openAskSourceMemo(memoId: String) {
        val request = state.value.nextAskSourceNavigationRequest(memoId) ?: return
        _state.update { current ->
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
                    _state.update { current ->
                        if (!current.canApplyAskSourceNavigation(request)) {
                            if (current.askSourceRequestId == request.requestId) {
                                current.copy(askSourceLoading = false)
                            } else {
                                current
                            }
                        } else {
                            val cached = activeMemos(current.memos.filter { it.id != detail.memo.id } + detail.memo)
                            val searched = current.searchResults?.let { results ->
                                activeMemos(results.filter { it.id != detail.memo.id } + detail.memo)
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
                    _state.update { current ->
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
        _state.update {
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
        _state.update {
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
        _state.update {
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
            ?: throw IllegalArgumentException("无法读取附件")
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
            ?: throw IllegalStateException("无法创建附件缓存")
        if (!cacheRoot.isDirectory && !cacheRoot.mkdirs()) {
            throw IllegalStateException("无法创建附件缓存")
        }
        if (!requestDirectory.mkdir()) {
            throw IllegalStateException("无法创建附件缓存")
        }
        try {
            File(requestDirectory, ATTACHMENT_DOWNLOAD_TEMP_FILENAME).also { tempFile ->
                if (!tempFile.createNewFile()) {
                    throw IllegalStateException("无法创建附件缓存")
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
            ?: throw IllegalStateException("无法准备附件")
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
        _state.update { it.copy(openingAttachmentPath = null) }
    }

    private fun cancelMemoPageLoad() {
        synchronized(memoPageLock) {
            loadMoreMemosJob?.cancel()
            loadMoreMemosJob = null
        }
        _state.update {
            it.copy(
                loadingMoreMemos = false,
                memoPageRequestId = it.memoPageRequestId + 1,
            )
        }
    }

    private fun cancelAskVariant() {
        _state.update {
            it.copy(
                askVariantLoading = false,
                askVariantRequestId = it.askVariantRequestId + 1,
            )
        }
    }

    private fun completeAskVariantSelection(request: AskVariantRequest, leafId: String) {
        _state.update { current ->
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
        _state.update { current ->
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
        _state.update {
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
        _state.update {
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
        viewModelScope.launch {
            runCatching {
                if (isOfflineMode()) {
                    localDataStore.getMemo(memoId)
                } else {
                    api.getMemo(memoId)
                }
            }
                .onSuccess { detail ->
                    applyMemo(detail.memo)
                    _state.update { current ->
                        if (current.selectedMemo?.id == memoId) {
                            current.copy(
                                selectedSummary = detail.ai,
                                summaryLoading = false,
                            )
                        } else {
                            current
                        }
                    }
                }
                .onFailure { error ->
                    _state.update { current ->
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

    private suspend fun reloadAskConversation(conversationId: String): AskSnapshot {
        val messages = api.listAskMessages(conversationId)
        val conversations = api.listAskConversations().filter { conversation -> conversation.deletedAt == null }
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
        if (summary.applied > 0) {
            localDataStore.markCloudSynced(pending.filter { it.memo.id in summary.appliedIds }.map { it.memo })
        }
        return summary
    }

    private fun syncPushNotice(summary: SyncPushSummary): String {
        return if (summary.applied == 0 && summary.conflict == 0 && summary.rejected == 0) {
            "没有需要同步到云端的记录"
        } else {
            "同步到云端：成功 ${summary.applied}，冲突 ${summary.conflict}，失败 ${summary.rejected}"
        }
    }

    private fun startAskStream(content: String, forkOfId: String?) {
        val current = state.value
        val initialRequest = current.nextAskStreamRequest() ?: return
        val contextScope = current.askScope
        val sourceKind = current.askSourceKind
        val regeneratingId = forkOfId.orEmpty()
        _state.update {
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
                    _state.update { currentState ->
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
                        _state.update { currentState ->
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
                        _state.update { currentState ->
                            if (currentState.canApplyAskStream(request)) {
                                currentState.copy(askLiveAnswer = currentState.askLiveAnswer + text)
                            } else {
                                currentState
                            }
                        }
                    },
                    onError = { message ->
                        _state.update { currentState ->
                            if (currentState.canApplyAskStream(request)) {
                                currentState.copy(error = message)
                            } else {
                                currentState
                            }
                        }
                    },
                )
            } catch (cancelled: CancellationException) {
                // Stop is user-initiated; the server persists whatever streamed before cancellation.
            } catch (error: Throwable) {
                _state.update { currentState ->
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
                                _state.update { currentState ->
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
                    _state.update { currentState ->
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
                    _state.update { currentState ->
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
                    throw IllegalArgumentException("找不到要重新生成的问题")
                }
                val profile = localDataStore.activeAIProfile() ?: throw IllegalArgumentException("请先配置一个默认 AI 档案")
                val history = buildAskActivePath(messages, parentId).map { it.message }
                val answer = localAiClient.answerQuestion(
                    profile = profile,
                    question = question,
                    scope = contextScope,
                    memos = localDataStore.listMemos(),
                    history = history,
                )
                localDataStore.appendAskTurn(
                    conversationId = conversationId,
                    question = question,
                    answer = answer.answer,
                    sourceRefs = answer.sourceRefs,
                    model = answer.model,
                    parentId = parentId,
                    forkOfId = forkOfId,
                )
                val refreshedMessages = localDataStore.listAskMessages(conversationId)
                val conversations = localDataStore.listAskConversations().filter { it.deletedAt == null }
                _state.update { currentState ->
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
                _state.update { currentState ->
                    if (currentState.canApplyAskStream(request)) {
                        currentState.copy(error = error.readableMessage())
                    } else {
                        currentState
                    }
                }
            }
            _state.update { currentState ->
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
            _state.update { it.copy(loading = true, error = null, notice = null) }
            runCatching { block() }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            screen = if (it.screen == Screen.Loading) Screen.Server else it.screen,
                            error = error.readableMessage(),
                        )
                    }
                }
            _state.update { it.copy(loading = false) }
        }
    }

    private fun enterOfflineMode(notice: String?) {
        val memos = activeMemos(localDataStore.listMemos())
        _state.update {
            it.copy(
                appMode = SessionStore.MODE_OFFLINE,
                initialized = true,
                account = null,
                memos = memos,
                memoNextCursor = "",
                loadingMoreMemos = false,
                selectedMemo = null,
                selectedSummary = null,
                summaryLoading = false,
                uploadingAttachment = false,
                aiAutoSummary = localDataStore.autoSummaryEnabled(),
                askLoading = false,
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

    private fun memoViewModeFromName(value: String): MemoViewMode {
        return runCatching { MemoViewMode.valueOf(value) }.getOrDefault(MemoViewMode.List)
    }

    private fun applyMemo(memo: Memo) {
        _state.update { current ->
            val cached = activeMemos(current.memos.filter { it.id != memo.id } + memo)
            val searched = current.searchResults?.let { results ->
                activeMemos(results.filter { it.id != memo.id } + memo)
            }
            current.copy(
                memos = cached,
                searchResults = searched,
                selectedMemo = if (current.selectedMemo?.id == memo.id) memo else current.selectedMemo,
            )
        }
    }

    private fun openEditorForMemo(memo: Memo) {
        _state.update {
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
        return message?.takeIf { it.isNotBlank() } ?: "操作失败"
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
