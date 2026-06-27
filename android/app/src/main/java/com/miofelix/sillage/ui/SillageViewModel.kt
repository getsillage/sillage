package com.miofelix.sillage.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.miofelix.sillage.data.Account
import com.miofelix.sillage.data.AIProfileDraft
import com.miofelix.sillage.data.AskConversation
import com.miofelix.sillage.data.AskMessage
import com.miofelix.sillage.data.AttachmentUpload
import com.miofelix.sillage.data.Memo
import com.miofelix.sillage.data.MemoAI
import com.miofelix.sillage.data.MarkdownFormatStyle
import com.miofelix.sillage.data.SessionStore
import com.miofelix.sillage.data.SillageApi
import com.miofelix.sillage.data.activeMemos
import com.miofelix.sillage.data.askAnswerMemoContent
import com.miofelix.sillage.data.askBranchLeafId
import com.miofelix.sillage.data.attachmentMarkdown
import com.miofelix.sillage.data.buildAskActivePath
import com.miofelix.sillage.data.lastAssistantMessageId
import com.miofelix.sillage.data.markdownFormatSnippet
import com.miofelix.sillage.data.toDraft
import com.miofelix.sillage.data.toInput
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SillageViewModel(context: Context) : ViewModel() {
    private val appContext = context.applicationContext
    private val sessionStore = SessionStore(appContext)
    private val api = SillageApi(sessionStore)
    private var askStreamJob: Job? = null
    private val _state = MutableStateFlow(
        SillageUiState(
            screen = Screen.Loading,
            baseUrl = sessionStore.baseUrl(),
            account = sessionStore.account(),
        ),
    )

    val state: StateFlow<SillageUiState> = _state.asStateFlow()

    init {
        connect()
    }

    fun updateBaseUrl(value: String) {
        _state.update { it.copy(baseUrl = value) }
    }

    fun saveServer() {
        sessionStore.saveBaseUrl(state.value.baseUrl)
        _state.update {
            it.copy(
                baseUrl = sessionStore.baseUrl(),
                account = null,
                memos = emptyList(),
                selectedSummary = null,
                summaryLoading = false,
                uploadingAttachment = false,
                searchQuery = "",
                searchResults = null,
                searching = false,
                error = null,
                notice = null,
            )
        }
        connect()
    }

    fun openServerSettings() {
        _state.update { it.copy(screen = Screen.Server, error = null, notice = null) }
    }

    fun openAISettings() {
        _state.update { it.copy(screen = Screen.AISettings, error = null, notice = null) }
        loadAISettings()
    }

    fun openAsk() {
        _state.update { it.copy(screen = Screen.Ask, error = null, notice = null) }
        loadAskConversations()
    }

    fun connect() {
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
            runCatching { api.signOut() }
            sessionStore.clearSession()
            _state.update {
                it.copy(
                    account = null,
                    memos = emptyList(),
                    selectedMemo = null,
                    selectedSummary = null,
                    summaryLoading = false,
                    uploadingAttachment = false,
                    aiProfiles = emptyList(),
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
                    screen = Screen.Login,
                    notice = "已退出登录",
                    error = null,
                )
            }
        }
    }

    fun refreshMemos() {
        viewModelScope.launch {
            runCatching { api.listMemos() }
                .onSuccess { memos ->
                    _state.update {
                        it.copy(
                            memos = activeMemos(memos),
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(error = error.readableMessage()) }
                }
        }
    }

    fun startNewMemo() {
        _state.update {
            it.copy(
                screen = Screen.Editor,
                quickCaptureOpen = false,
                quickCaptureBody = "",
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

    fun openQuickCapture() {
        _state.update {
            it.copy(
                quickCaptureOpen = true,
                quickCaptureError = null,
                notice = null,
                error = null,
            )
        }
    }

    fun closeQuickCapture() {
        _state.update {
            it.copy(
                quickCaptureOpen = false,
                quickCaptureError = null,
            )
        }
    }

    fun updateQuickCaptureBody(value: String) {
        _state.update { it.copy(quickCaptureBody = value, quickCaptureError = null) }
    }

    fun saveQuickCapture() {
        val body = state.value.quickCaptureBody.trim()
        if (body.isBlank()) {
            _state.update { it.copy(quickCaptureError = "想记录什么？") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(quickCaptureSaving = true, quickCaptureError = null, error = null, notice = null) }
            runCatching { api.createMemo(body, LocalDate.now().toString()) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            quickCaptureOpen = false,
                            quickCaptureBody = "",
                            quickCaptureSaving = false,
                            notice = "已保存",
                        )
                    }
                    refreshMemos()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            quickCaptureSaving = false,
                            quickCaptureError = error.readableMessage(),
                        )
                    }
                }
        }
    }

    fun expandQuickCaptureToEditor() {
        val body = state.value.quickCaptureBody
        _state.update {
            it.copy(
                screen = Screen.Editor,
                quickCaptureOpen = false,
                quickCaptureError = null,
                selectedMemo = null,
                selectedSummary = null,
                summaryLoading = false,
                uploadingAttachment = false,
                draftContent = body,
                draftEntryDate = LocalDate.now().toString(),
                markdownPreview = false,
                error = null,
                notice = null,
            )
        }
    }

    fun editMemo(memo: Memo) {
        _state.update {
            it.copy(
                screen = Screen.Editor,
                selectedMemo = memo,
                selectedSummary = null,
                summaryLoading = true,
                draftContent = memo.content,
                draftEntryDate = memo.entryDate,
                markdownPreview = false,
                error = null,
                notice = null,
            )
        }
        fetchSelectedMemoDetail(memo.id)
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
    }

    fun searchMemos() {
        val query = state.value.searchQuery.trim()
        if (query.isBlank()) {
            clearSearch()
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(searching = true, error = null, notice = null) }
            runCatching { api.searchMemos(query) }
                .onSuccess { memos ->
                    _state.update {
                        it.copy(
                            searchResults = activeMemos(memos),
                            searching = false,
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            searchResults = emptyList(),
                            searching = false,
                            error = error.readableMessage(),
                        )
                    }
                }
        }
    }

    fun clearSearch() {
        _state.update {
            it.copy(
                searchQuery = "",
                searchResults = null,
                searching = false,
                error = null,
            )
        }
    }

    fun updateMemoViewMode(mode: MemoViewMode) {
        _state.update {
            it.copy(
                memoViewMode = mode,
                searchQuery = if (mode == MemoViewMode.Calendar) "" else it.searchQuery,
                searchResults = if (mode == MemoViewMode.Calendar) null else it.searchResults,
                searching = if (mode == MemoViewMode.Calendar) false else it.searching,
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
            if (current.selectedMemo == null) {
                api.createMemo(current.draftContent.trim(), current.draftEntryDate)
            } else {
                api.updateMemo(current.selectedMemo, current.draftContent.trim(), current.draftEntryDate)
            }
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
                    notice = "已保存",
                )
            }
            refreshMemos()
        }
    }

    fun deleteSelectedMemo() {
        val memo = state.value.selectedMemo ?: return
        launchBusy {
            api.deleteMemo(memo)
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
            val updated = api.setMemoPinned(memo, memo.pinnedAt == null)
            applyMemo(updated)
            _state.update {
                it.copy(notice = if (updated.pinnedAt == null) "已取消置顶" else "已置顶")
            }
        }
    }

    fun toggleSelectedMemoArchived() {
        val memo = state.value.selectedMemo ?: return
        launchBusy {
            val updated = api.setMemoArchived(memo, memo.archivedAt == null)
            applyMemo(updated)
            _state.update {
                it.copy(notice = if (updated.archivedAt == null) "已取消归档" else "已归档")
            }
        }
    }

    fun summarizeSelectedMemo() {
        val memo = state.value.selectedMemo ?: return
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
        _state.update { it.copy(aiProfiles = it.aiProfiles + AIProfileDraft()) }
    }

    fun removeAIProfile(index: Int) {
        _state.update {
            it.copy(aiProfiles = it.aiProfiles.filterIndexed { i, _ -> i != index })
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
        updateAIProfile(index) { it.copy(temperature = value.toDoubleOrNull() ?: 0.0) }
    }

    fun updateAIProfileMaxTokens(index: Int, value: String) {
        updateAIProfile(index) { it.copy(maxTokens = value.toLongOrNull() ?: 0) }
    }

    fun updateAIProfileApiKey(index: Int, value: String) {
        updateAIProfile(index) { it.copy(apiKeyInput = value) }
    }

    fun toggleAIProfileEnabled(index: Int) {
        updateAIProfile(index) { it.copy(enabled = !it.enabled) }
    }

    fun toggleAIProfileActive(index: Int) {
        updateAIProfile(index) { it.copy(active = !it.active) }
    }

    fun toggleAIProfileAutoSummary(index: Int) {
        updateAIProfile(index) { it.copy(autoSummary = !it.autoSummary) }
    }

    fun loadAISettings() {
        viewModelScope.launch {
            _state.update { it.copy(aiSettingsLoading = true, error = null, notice = null) }
            runCatching { api.getAISettings() }
                .onSuccess { profiles ->
                    _state.update {
                        it.copy(
                            aiProfiles = profiles.map { profile -> profile.toDraft() },
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
        val profiles = state.value.aiProfiles
        viewModelScope.launch {
            _state.update { it.copy(aiSettingsSaving = true, error = null, notice = null) }
            runCatching { api.patchAISettings(profiles.map { it.toInput() }) }
                .onSuccess { saved ->
                    _state.update {
                        it.copy(
                            aiProfiles = saved.map { profile -> profile.toDraft() },
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

    fun testAIProfile(index: Int) {
        val profile = state.value.aiProfiles.getOrNull(index) ?: return
        if (profile.id.isBlank()) {
            _state.update { it.copy(error = "请先保存后再测试连接") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(aiTestingProfileId = profile.id, error = null, notice = null) }
            runCatching { api.testAIConnection(profile.id) }
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
            runCatching { api.listAskConversations() }
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
            runCatching { api.listAskMessages(id) }
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
            runCatching { api.createMemo(content, LocalDate.now().toString()) }
                .onSuccess { memo ->
                    applyMemo(memo)
                    _state.update {
                        it.copy(
                            screen = Screen.Editor,
                            selectedMemo = memo,
                            selectedSummary = null,
                            summaryLoading = true,
                            uploadingAttachment = false,
                            draftContent = memo.content,
                            draftEntryDate = memo.entryDate,
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
            runCatching { api.getMemo(memoId) }
                .onSuccess { detail ->
                    applyMemo(detail.memo)
                    _state.update {
                        it.copy(
                            screen = Screen.Editor,
                            selectedMemo = detail.memo,
                            selectedSummary = detail.ai,
                            summaryLoading = false,
                            uploadingAttachment = false,
                            draftContent = detail.memo.content,
                            draftEntryDate = detail.memo.entryDate,
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

    fun closeEditor() {
        _state.update {
            it.copy(
                screen = Screen.Memos,
                selectedMemo = null,
                selectedSummary = null,
                summaryLoading = false,
                uploadingAttachment = false,
                draftContent = "",
                error = null,
            )
        }
    }

    fun closeAISettings() {
        _state.update {
            it.copy(
                screen = Screen.Memos,
                aiTestingProfileId = "",
                error = null,
                notice = null,
            )
        }
    }

    fun closeAsk() {
        _state.update {
            it.copy(
                screen = Screen.Memos,
                error = null,
                notice = null,
            )
        }
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
            runCatching { api.getMemo(memoId) }
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

    private fun startAskStream(content: String, forkOfId: String?) {
        if (state.value.askSending) {
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

data class SillageUiState(
    val screen: Screen,
    val baseUrl: String,
    val initialized: Boolean? = null,
    val account: Account? = null,
    val memos: List<Memo> = emptyList(),
    val selectedMemo: Memo? = null,
    val selectedSummary: MemoAI? = null,
    val summaryLoading: Boolean = false,
    val uploadingAttachment: Boolean = false,
    val aiProfiles: List<AIProfileDraft> = emptyList(),
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
    val quickCaptureOpen: Boolean = false,
    val quickCaptureBody: String = "",
    val quickCaptureSaving: Boolean = false,
    val quickCaptureError: String? = null,
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
    Server,
    Initialize,
    Login,
    Memos,
    Editor,
    AISettings,
    Ask,
}
