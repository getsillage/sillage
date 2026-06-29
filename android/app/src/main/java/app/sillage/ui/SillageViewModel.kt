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
import app.sillage.data.LocalAiClient
import app.sillage.data.LocalDataStore
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
import app.sillage.data.toDraft
import app.sillage.data.toInput
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    init {
        if (!sessionStore.hasAppModeSelection()) {
            _state.update { it.copy(screen = Screen.ModeSelection) }
        } else if (sessionStore.appMode() == SessionStore.MODE_OFFLINE) {
            enterOfflineMode(notice = null)
        } else {
            connect()
        }
    }

    fun chooseOnlineMode() {
        sessionStore.saveAppMode(SessionStore.MODE_ONLINE)
        _state.update {
            it.copy(
                appMode = SessionStore.MODE_ONLINE,
                screen = Screen.Server,
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
        sessionStore.saveAppMode(SessionStore.MODE_ONLINE)
        _state.update {
            it.copy(
                appMode = SessionStore.MODE_ONLINE,
                memos = emptyList(),
                memoNextCursor = "",
                loadingMoreMemos = false,
                selectedMemo = null,
                selectedSummary = null,
                searchQuery = "",
                searchResults = null,
                error = null,
                notice = null,
            )
        }
        connect()
    }

    fun useOfflineMode() {
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

    fun closeServerSettings() {
        _state.update {
            val target = it.serverReturnScreen ?: if (it.appMode == SessionStore.MODE_OFFLINE) Screen.Memos else Screen.Login
            it.copy(
                screen = target,
                serverReturnScreen = null,
                baseUrl = sessionStore.baseUrl(),
                error = null,
                notice = null,
            )
        }
    }

    fun openAISettings() {
        _state.update { it.copy(screen = Screen.AISettings, error = null, notice = null) }
        loadAISettings()
    }

    fun openAsk() {
        _state.update { it.copy(screen = Screen.Ask, error = null, notice = null) }
        loadAskConversations()
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
                    initialized = true,
                )
            }
            refreshMemos()
        }
    }

    fun signOut() {
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
                    aiTestResults = emptyMap(),
                    askConversations = emptyList(),
                    activeAskId = "",
                    askHeadId = null,
                    askMessages = emptyList(),
                    askQuestion = "",
                    askLoading = false,
                    askSending = false,
                    askStreaming = false,
                    askRegeneratingId = "",
                    askLiveUser = null,
                    askLiveAnswer = "",
                    searchQuery = "",
                    searchResults = null,
                    searching = false,
                    screen = if (isOfflineMode()) Screen.Memos else Screen.Login,
                    notice = if (isOfflineMode()) "已清除在线登录信息" else "已退出登录",
                    error = null,
                )
            }
        }
    }

    fun refreshMemos() {
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
        val cursor = state.value.memoNextCursor
        if (isOfflineMode() || cursor.isBlank() || state.value.loadingMoreMemos) {
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loadingMoreMemos = true, error = null, notice = null) }
            runCatching { api.listMemos(cursor = cursor) }
                .onSuccess { page ->
                    _state.update { current ->
                        current.copy(
                            memos = activeMemos(current.memos + page.memos),
                            memoNextCursor = page.nextCursor,
                            loadingMoreMemos = false,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(loadingMoreMemos = false, error = error.readableMessage()) }
                }
        }
    }

    fun startNewMemo() {
        _state.update {
            it.copy(
                screen = Screen.Editor,
                selectedMemo = null,
                selectedSummary = null,
                summaryLoading = false,
                uploadingAttachment = false,
                draftContent = "",
                draftEntryDate = LocalDate.now().toString(),
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
        _state.update {
            it.copy(
                screen = Screen.Memos,
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
                it.copy(
                    screen = Screen.MemoDetail,
                    selectedMemo = saved,
                    selectedSummary = if (current.selectedMemo?.id == saved.id) it.selectedSummary else null,
                    summaryLoading = !isOfflineMode(),
                    uploadingAttachment = false,
                    draftContent = "",
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
        val memo = state.value.selectedMemo ?: return
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
                    selectedMemo = null,
                    selectedSummary = null,
                    summaryLoading = false,
                    uploadingAttachment = false,
                    draftContent = "",
                    searchQuery = "",
                    searchResults = null,
                    notice = "已删除",
                )
            }
            refreshMemos()
        }
    }

    fun toggleSelectedMemoPinned() {
        val memo = state.value.selectedMemo ?: return
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
        val memo = state.value.selectedMemo ?: return
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

    fun summarizeSelectedMemo() {
        val memo = state.value.selectedMemo ?: return
        if (isOfflineMode()) {
            viewModelScope.launch {
                _state.update { it.copy(summaryLoading = true, error = null, notice = null) }
                runCatching {
                    val profile = localDataStore.activeAIProfile() ?: throw IllegalArgumentException("请先配置一个默认 AI 档案")
                    localAiClient.summarizeMemo(profile, memo).also(localDataStore::saveMemoAI)
                }
                    .onSuccess { ai ->
                        _state.update { it.copy(selectedSummary = ai, summaryLoading = false, notice = "已生成总结") }
                    }
                    .onFailure { error ->
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
        if (isOfflineMode()) {
            _state.update { it.copy(error = "附件上传需要在线模式") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(uploadingAttachment = true, error = null, notice = null) }
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
                        it.copy(
                            draftContent = it.draftContent + snippets,
                            uploadingAttachment = false,
                            notice = "附件已插入",
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            uploadingAttachment = false,
                            error = error.readableMessage(),
                        )
                    }
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
                EditableAISettings(
                    profiles = settings.profiles.map { it.toDraft() },
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
        if (profile.id.isBlank()) {
            _state.update { it.copy(error = "请先保存后再测试连接") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(aiTestingProfileId = profile.id, error = null, notice = null) }
            runCatching {
                if (isOfflineMode()) {
                    localAiClient.testConnection(profile)
                } else {
                    api.testAIConnection(profile.id)
                }
            }
                .onSuccess { model ->
                    _state.update {
                        it.copy(
                            aiTestingProfileId = "",
                            aiTestResults = it.aiTestResults + (profile.id to "连接成功（$model）"),
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            aiTestingProfileId = "",
                            aiTestResults = it.aiTestResults + (profile.id to error.readableMessage()),
                        )
                    }
                }
        }
    }

    fun loadAskConversations() {
        viewModelScope.launch {
            _state.update { it.copy(askLoading = true, error = null, notice = null) }
            runCatching {
                if (isOfflineMode()) {
                    localDataStore.listAskConversations()
                } else {
                    api.listAskConversations()
                }
            }
                .onSuccess { conversations ->
                    _state.update {
                        it.copy(
                            askConversations = conversations.filter { conversation -> conversation.deletedAt == null },
                            askLoading = false,
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            askLoading = false,
                            error = error.readableMessage(),
                        )
                    }
                }
        }
    }

    fun selectAskConversation(id: String) {
        val conversation = state.value.askConversations.find { it.id == id }
        _state.update {
            it.copy(
                activeAskId = id,
                askHeadId = conversation?.headMessageId,
                askMessages = emptyList(),
                askScope = conversation?.contextScope ?: it.askScope,
                askLoading = true,
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
        if (conversationId.isBlank() || state.value.askSending) {
            return
        }
        startAskStream(content = "", forkOfId = messageId)
    }

    fun stopAskStreaming() {
        askStreamJob?.cancel()
    }

    fun selectAskVariant(messageId: String) {
        val conversationId = state.value.activeAskId
        if (conversationId.isBlank()) {
            return
        }
        val leafId = askBranchLeafId(state.value.askMessages, messageId)
        _state.update { it.copy(askHeadId = leafId, error = null, notice = null) }
        if (isOfflineMode()) {
            localDataStore.setAskHead(conversationId, leafId)
            _state.update {
                it.copy(
                    askConversations = localDataStore.listAskConversations().filter { conversation -> conversation.deletedAt == null },
                )
            }
            return
        }
        viewModelScope.launch {
            runCatching {
                api.setAskHead(conversationId, leafId)
                api.listAskConversations().filter { conversation -> conversation.deletedAt == null }
            }
                .onSuccess { conversations ->
                    _state.update {
                        it.copy(
                            askConversations = conversations,
                            askHeadId = conversations.find { conversation -> conversation.id == conversationId }?.headMessageId ?: leafId,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(error = error.readableMessage()) }
                }
        }
    }

    fun saveAskAnswerAsMemo(message: AskMessage) {
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
        if (memoId.isBlank()) {
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, notice = null) }
            runCatching {
                if (isOfflineMode()) {
                    localDataStore.getMemo(memoId)
                } else {
                    api.getMemo(memoId)
                }
            }
                .onSuccess { detail ->
                    applyMemo(detail.memo)
                    _state.update {
                        it.copy(
                            screen = Screen.MemoDetail,
                            selectedMemo = detail.memo,
                            selectedSummary = detail.ai,
                            summaryLoading = false,
                            uploadingAttachment = false,
                            markdownPreview = false,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(error = error.readableMessage()) }
                }
            _state.update { it.copy(loading = false) }
        }
    }

    fun closeMemoDetail() {
        _state.update {
            it.copy(
                screen = Screen.Memos,
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
        _state.update {
            if (it.selectedMemo == null) {
                it.copy(
                    screen = Screen.Memos,
                    selectedSummary = null,
                    summaryLoading = false,
                    uploadingAttachment = false,
                    draftContent = "",
                    error = null,
                )
            } else {
                it.copy(
                    screen = Screen.MemoDetail,
                    selectedSummary = null,
                    summaryLoading = !isOfflineMode(),
                    uploadingAttachment = false,
                    draftContent = "",
                    error = null,
                    notice = null,
                )
            }
        }
        state.value.selectedMemo?.id?.let(::fetchSelectedMemoDetail)
    }

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
        if (state.value.askSending) {
            return
        }
        if (isOfflineMode()) {
            startLocalAsk(content, forkOfId)
            return
        }
        askStreamJob?.cancel()
        askStreamJob = viewModelScope.launch {
            var conversationId = state.value.activeAskId
            val contextScope = state.value.askScope
            val sourceKind = state.value.askSourceKind
            val regeneratingId = forkOfId.orEmpty()
            _state.update {
                it.copy(
                    askSending = true,
                    askStreaming = false,
                    askRegeneratingId = regeneratingId,
                    askLiveUser = null,
                    askLiveAnswer = "",
                    error = null,
                    notice = null,
                )
            }
            try {
                if (conversationId.isBlank()) {
                    val created = api.createAskConversation(contextScope)
                    conversationId = created.id
                    _state.update {
                        it.copy(
                            activeAskId = created.id,
                            askHeadId = created.headMessageId,
                            askConversations = listOf(created) + it.askConversations.filter { conversation -> conversation.id != created.id },
                        )
                    }
                }
                api.streamAskMessage(
                    conversationId = conversationId,
                    content = content,
                    contextScope = contextScope,
                    sourceKind = sourceKind,
                    forkOfId = forkOfId,
                    onStart = { userMessage, regenerate ->
                        _state.update {
                            it.copy(
                                askStreaming = true,
                                askLiveAnswer = "",
                                askLiveUser = if (regenerate) null else userMessage,
                            )
                        }
                    },
                    onDelta = { text ->
                        _state.update { it.copy(askLiveAnswer = it.askLiveAnswer + text) }
                    },
                    onError = { message ->
                        _state.update { it.copy(error = message) }
                    },
                )
            } catch (cancelled: CancellationException) {
                // Stop is user-initiated; the server persists whatever streamed before cancellation.
            } catch (error: Throwable) {
                _state.update { it.copy(error = error.readableMessage()) }
            } finally {
                withContext(NonCancellable) {
                    if (conversationId.isNotBlank()) {
                        runCatching { reloadAskConversation(conversationId) }
                            .onSuccess { snapshot ->
                                _state.update {
                                    it.copy(
                                        askMessages = snapshot.messages,
                                        askConversations = snapshot.conversations,
                                        askHeadId = snapshot.headId,
                                    )
                                }
                            }
                    }
                    _state.update {
                        it.copy(
                            askQuestion = if (forkOfId == null && it.error == null) "" else it.askQuestion,
                            askSending = false,
                            askStreaming = false,
                            askRegeneratingId = "",
                            askLiveUser = null,
                            askLiveAnswer = "",
                        )
                    }
                    askStreamJob = null
                }
            }
        }
    }

    private fun startLocalAsk(content: String, forkOfId: String?) {
        askStreamJob?.cancel()
        askStreamJob = viewModelScope.launch {
            var conversationId = state.value.activeAskId
            val contextScope = state.value.askScope
            val regeneratingId = forkOfId.orEmpty()
            _state.update {
                it.copy(
                    askSending = true,
                    askStreaming = false,
                    askRegeneratingId = regeneratingId,
                    askLiveUser = null,
                    askLiveAnswer = "",
                    error = null,
                    notice = null,
                )
            }
            runCatching {
                if (conversationId.isBlank()) {
                    val created = localDataStore.createAskConversation(contextScope)
                    conversationId = created.id
                    _state.update {
                        it.copy(
                            activeAskId = created.id,
                            askHeadId = created.headMessageId,
                            askConversations = listOf(created) + it.askConversations.filter { conversation -> conversation.id != created.id },
                        )
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
            }
                .onSuccess {
                    val messages = localDataStore.listAskMessages(conversationId)
                    val conversations = localDataStore.listAskConversations().filter { it.deletedAt == null }
                    _state.update {
                        it.copy(
                            askMessages = messages,
                            askConversations = conversations,
                            askHeadId = conversations.find { conversation -> conversation.id == conversationId }?.headMessageId,
                            askQuestion = if (forkOfId == null) "" else it.askQuestion,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(error = error.readableMessage()) }
                }
            _state.update {
                it.copy(
                    askSending = false,
                    askStreaming = false,
                    askRegeneratingId = "",
                    askLiveUser = null,
                    askLiveAnswer = "",
                )
            }
            askStreamJob = null
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
                searchQuery = "",
                searchResults = null,
                searching = false,
                screen = Screen.Memos,
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
                selectedMemo = memo,
                selectedSummary = null,
                summaryLoading = !isOfflineMode(),
                uploadingAttachment = false,
                draftContent = memo.content,
                draftEntryDate = memo.entryDate,
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
    val aiTestResults: Map<String, String> = emptyMap(),
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
