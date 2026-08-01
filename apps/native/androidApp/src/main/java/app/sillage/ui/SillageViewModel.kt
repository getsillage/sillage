package app.sillage.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import app.sillage.BuildConfig
import app.sillage.R
import app.sillage.data.Account
import app.sillage.data.AIProfileDraft
import app.sillage.core.domain.ask.AskConversation
import app.sillage.core.domain.ask.AskMessage
import app.sillage.data.AttachmentUpload
import app.sillage.data.DownloadedAttachment
import app.sillage.data.LocalAiClient
import app.sillage.data.LocalAskRepository
import app.sillage.data.LocalDataStore
import app.sillage.data.LocalMemoSyncConflictRepository
import app.sillage.data.LocalMemoSyncOutbox
import app.sillage.data.LocalSyncSnapshotRepository
import app.sillage.data.LocalRecordsRepository
import app.sillage.data.LocalRecordSummaryRepository
import app.sillage.data.RemoteRecordsRepository
import app.sillage.data.RemoteRecordSummaryRepository
import app.sillage.data.RemoteMemoSyncGateway
import app.sillage.data.RemoteAskRepository
import app.sillage.data.RemoteSyncSnapshotGateway
import app.sillage.data.MarkdownLinkTarget
import app.sillage.core.application.records.GetRecordDetailUseCase
import app.sillage.core.application.records.GenerateRecordSummaryUseCase
import app.sillage.core.application.records.SaveRecordSummaryUseCase
import app.sillage.core.application.records.ActiveRecordSummaryProfileRequiredException
import app.sillage.core.application.records.ListRecordsPageUseCase
import app.sillage.core.domain.records.Memo
import app.sillage.core.domain.records.MemoAI
import app.sillage.core.application.records.ListRecordsUseCase
import app.sillage.core.application.records.RecordsPageQuery
import app.sillage.core.application.records.RecordsQueryScope
import app.sillage.core.application.records.RecordsSearchQuery
import app.sillage.core.application.records.RecordDraft
import app.sillage.core.application.records.RecordLifecycleCommand
import app.sillage.core.application.records.MutateRecordLifecycleUseCase
import app.sillage.core.application.records.SaveRecordCommand
import app.sillage.core.application.records.SaveRecordUseCase
import app.sillage.core.application.records.SearchRecordsUseCase
import app.sillage.core.application.ask.CreateAskConversationUseCase
import app.sillage.core.application.ask.ListAskConversationsUseCase
import app.sillage.core.application.ask.ListAskMessagesUseCase
import app.sillage.core.application.ask.SetAskHeadUseCase
import app.sillage.features.records.MemoListFilter
import app.sillage.features.records.MemoViewMode
import app.sillage.features.ask.AskVariantRequest
import app.sillage.data.MarkdownFormatStyle
import app.sillage.data.PendingLocalAttachment
import app.sillage.data.SessionStore
import app.sillage.data.SillageApi
import app.sillage.data.SillageExportCodec
import app.sillage.core.sync.SyncPushSummary
import app.sillage.core.sync.PushPendingMemosUseCase
import app.sillage.core.sync.PullSyncResult
import app.sillage.core.sync.PullSyncUseCase
import app.sillage.core.sync.ResolveMemoSyncConflictCommand
import app.sillage.core.sync.ResolveMemoSyncConflictUseCase
import app.sillage.core.sync.RunSyncPushUseCase
import app.sillage.core.sync.RunTwoWaySyncUseCase
import app.sillage.core.sync.SyncPushPreparation
import app.sillage.data.askAnswerMemoContent
import app.sillage.data.askBranchLeafId
import app.sillage.data.attachmentMarkdown
import app.sillage.data.buildAskActivePath
import app.sillage.data.firstBlankAIProfileNameIndex
import app.sillage.core.domain.ask.isActive
import app.sillage.data.lastAssistantMessageId
import app.sillage.data.localAttachmentMarkdown
import app.sillage.data.localAttachmentPath
import app.sillage.data.markdownFormatSnippet
import app.sillage.features.records.memosForFilter
import app.sillage.features.sync.MemoSyncConflictItem
import app.sillage.data.mergeSavedAIProfilesForLocalStorage
import app.sillage.data.pendingLocalAttachmentId
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

class SillageViewModel(
    context: Context,
    private val savedStateHandle: SavedStateHandle? = null,
    localDataStore: LocalDataStore? = null,
) : ViewModel() {
    private val appContext = context.applicationContext
    private val sessionStore = SessionStore(appContext)
    private val localDataStore = localDataStore ?: LocalDataStore(appContext)
    private val localMemoSyncConflictRepository =
        LocalMemoSyncConflictRepository(this.localDataStore)
    private val resolveMemoSyncConflict =
        ResolveMemoSyncConflictUseCase(localMemoSyncConflictRepository)
    private val localMemoSyncOutbox = LocalMemoSyncOutbox(this.localDataStore)
    private val localAskRepository = LocalAskRepository(this.localDataStore)
    private val listLocalAskConversations = ListAskConversationsUseCase(localAskRepository)
    private val listLocalAskMessages = ListAskMessagesUseCase(localAskRepository)
    private val createLocalAskConversation = CreateAskConversationUseCase(localAskRepository)
    private val setLocalAskHead = SetAskHeadUseCase(localAskRepository)
    private val localRecordsRepository = LocalRecordsRepository(this.localDataStore)
    private val listLocalRecords = ListRecordsUseCase(localRecordsRepository)
    private val searchLocalRecords = SearchRecordsUseCase(localRecordsRepository)
    private val getLocalRecordDetail = GetRecordDetailUseCase(localRecordsRepository)
    private val saveLocalRecord = SaveRecordUseCase(localRecordsRepository)
    private val mutateLocalRecordLifecycle = MutateRecordLifecycleUseCase(localRecordsRepository)
    private val localAiClient = LocalAiClient()
    private val localRecordSummaryRepository = LocalRecordSummaryRepository(
        this.localDataStore,
        localAiClient,
    )
    private val generateLocalRecordSummary = GenerateRecordSummaryUseCase(localRecordSummaryRepository)
    private val saveLocalRecordSummary = SaveRecordSummaryUseCase(localRecordSummaryRepository)
    private val api = SillageApi(sessionStore)
    private val remoteAskRepository = RemoteAskRepository(api)
    private val listRemoteAskConversations = ListAskConversationsUseCase(remoteAskRepository)
    private val listRemoteAskMessages = ListAskMessagesUseCase(remoteAskRepository)
    private val createRemoteAskConversation = CreateAskConversationUseCase(remoteAskRepository)
    private val setRemoteAskHead = SetAskHeadUseCase(remoteAskRepository)
    private val pullSync = PullSyncUseCase(
        RemoteSyncSnapshotGateway(api),
        LocalSyncSnapshotRepository(this.localDataStore),
    )
    private val pushPendingMemos = PushPendingMemosUseCase(
        localMemoSyncOutbox,
        RemoteMemoSyncGateway(api),
    )
    private val runSyncPush = RunSyncPushUseCase(
        SyncPushPreparation { flushPendingLocalAttachments() },
        pushPendingMemos,
    )
    private val runTwoWaySync = RunTwoWaySyncUseCase(runSyncPush, pullSync)
    private val remoteRecordsRepository = RemoteRecordsRepository(api)
    private val listRemoteRecords = ListRecordsPageUseCase(remoteRecordsRepository)
    private val searchRemoteRecords = SearchRecordsUseCase(remoteRecordsRepository)
    private val getRemoteRecordDetail = GetRecordDetailUseCase(remoteRecordsRepository)
    private val saveRemoteRecord = SaveRecordUseCase(remoteRecordsRepository)
    private val mutateRemoteRecordLifecycle = MutateRecordLifecycleUseCase(remoteRecordsRepository)
    private val generateRemoteRecordSummary = GenerateRecordSummaryUseCase(
        RemoteRecordSummaryRepository(api),
    )
    private var askStreamJob: Job? = null
    private var searchJob: Job? = null
    private var attachmentOpenJob: Job? = null
    private var loadMoreMemosJob: Job? = null
    private var aiAutoSummaryJob: Job? = null
    private var memoSummaryJob: Job? = null
    private val authOperationGate = SingleFlightGate()
    private val dataTransferGate = SingleFlightGate()
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

    // Editor draft rescued from SavedStateHandle after process death; consumed
    // the next time the matching editor session opens.
    private var restoredEditorDraft: RestoredEditorDraft? = savedStateHandle?.let { handle ->
        val content = handle.get<String>(KEY_SAVED_DRAFT_CONTENT)
        if (content.isNullOrBlank()) {
            null
        } else {
            RestoredEditorDraft(
                content = content,
                entryDate = handle.get<String>(KEY_SAVED_DRAFT_ENTRY_DATE).orEmpty(),
                editingMemoId = handle.get<String>(KEY_SAVED_EDITING_MEMO_ID).orEmpty(),
            )
        }
    }

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
            persistEditorDraft(before, after)
            toastEventEmitter.onStateChanged(
                before = before,
                after = after,
                forceFeedback = forceFeedback,
                noticeType = noticeType,
            )
        }
    }

    // Keep the minimal editor draft in SavedStateHandle so process death does
    // not lose unsaved text.
    private fun persistEditorDraft(before: SillageUiState, after: SillageUiState) {
        val handle = savedStateHandle ?: return
        val draftActive = after.screen == Screen.Editor && after.draftContent.isNotBlank()
        val wasActive = before.screen == Screen.Editor && before.draftContent.isNotBlank()
        if (draftActive) {
            if (
                before.draftContent != after.draftContent ||
                before.draftEntryDate != after.draftEntryDate ||
                before.selectedMemo?.id != after.selectedMemo?.id ||
                !wasActive
            ) {
                handle[KEY_SAVED_DRAFT_CONTENT] = after.draftContent
                handle[KEY_SAVED_DRAFT_ENTRY_DATE] = after.draftEntryDate
                handle[KEY_SAVED_EDITING_MEMO_ID] = after.selectedMemo?.id.orEmpty()
            }
        } else if (wasActive) {
            handle[KEY_SAVED_DRAFT_CONTENT] = ""
            handle[KEY_SAVED_DRAFT_ENTRY_DATE] = ""
            handle[KEY_SAVED_EDITING_MEMO_ID] = ""
        }
    }

    private fun consumeRestoredEditorDraft(editingMemoId: String): RestoredEditorDraft? {
        val restored = restoredEditorDraft ?: return null
        if (restored.editingMemoId != editingMemoId) {
            return null
        }
        restoredEditorDraft = null
        return restored
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
                recordsCollection = it.recordsCollection.clear(),
                recordsPagination = it.recordsPagination.copy(nextCursor = "", loadingMore = false),
                recordsRefresh = it.recordsRefresh.cancel(),
                recordsMutation = it.recordsMutation.clear(),
                recordsSelection = it.recordsSelection.clear(),
                recordsSummary = it.recordsSummary.replacePresentation(null, loading = false),
                recordsEditor = it.recordsEditor.stopAttachmentUpload(),
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
                askConversation = it.askConversation.clear(),
                askQuestion = "",
                askLoading = false,
                askLoadError = null,
                askScreenSessionId = it.askScreenSessionId + 1,
                askSourceRequestId = it.askSourceRequestId + 1,
                askSourceLoading = false,
                askMemoSave = it.askMemoSave.invalidate(),
                serverReturnScreen = null,
                recordsSearch = it.recordsSearch.clear(),
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
                recordsCollection = it.recordsCollection.clear(),
                recordsPagination = it.recordsPagination.copy(nextCursor = "", loadingMore = false),
                recordsRefresh = it.recordsRefresh.cancel(),
                recordsMutation = it.recordsMutation.clear(),
                recordsSelection = it.recordsSelection.clear(),
                recordsSummary = it.recordsSummary.replaceSummary(null),
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
                askConversation = it.askConversation.clear(),
                askQuestion = "",
                askLoading = false,
                askLoadError = null,
                askScreenSessionId = it.askScreenSessionId + 1,
                askSourceRequestId = it.askSourceRequestId + 1,
                askSourceLoading = false,
                askMemoSave = it.askMemoSave.invalidate(),
                recordsSearch = it.recordsSearch.clear(),
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
                recordsSummary = it.recordsSummary.finishDetail(),
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
                recordsSummary = it.recordsSummary.finishDetail(),
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
            val bootstrap = api.bootstrap(state.value.baseUrl)
            val updateRequired = bootstrap.minimumAndroidVersionCode > BuildConfig.VERSION_CODE
            updateState {
                it.copy(
                    initialized = bootstrap.initialized,
                    serverVersion = bootstrap.serverVersion,
                    serverRevision = bootstrap.serverRevision,
                    apiVersion = bootstrap.apiVersion,
                    minimumAndroidVersionCode = bootstrap.minimumAndroidVersionCode,
                    androidUpdateRequired = updateRequired,
                )
            }
            if (updateRequired) {
                updateState {
                    it.copy(
                        screen = Screen.Server,
                        authError = uiString(
                            R.string.error_android_update_required,
                            BuildConfig.VERSION_CODE,
                            bootstrap.minimumAndroidVersionCode,
                        ),
                        authErrorResourceId = null,
                    )
                }
                return@launchAuthBusy
            }
            val initialized = bootstrap.initialized
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
                        recordsRefresh = it.recordsRefresh.copy(status = MemoListLoadStatus.Loading),
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

    fun updateCurrentPassword(value: String) = updateState {
        it.copy(currentPassword = value, error = null)
    }

    fun updateNewPassword(value: String) = updateState {
        it.copy(newPassword = value, error = null)
    }

    fun updateConfirmPassword(value: String) = updateState {
        it.copy(confirmPassword = value, error = null)
    }

    fun changePassword() {
        if (isOfflineMode()) {
            updateState(forceFeedback = true) {
                it.copy(error = uiString(R.string.error_password_online_required), notice = null)
            }
            return
        }
        val current = state.value
        val currentPassword = current.currentPassword
        val newPassword = current.newPassword
        val confirmPassword = current.confirmPassword
        if (currentPassword.isBlank() || newPassword.isBlank()) {
            updateState(forceFeedback = true) {
                it.copy(error = uiString(R.string.error_password_required), notice = null)
            }
            return
        }
        if (newPassword != confirmPassword) {
            updateState(forceFeedback = true) {
                it.copy(error = uiString(R.string.error_password_mismatch), notice = null)
            }
            return
        }
        if (currentPassword == newPassword) {
            updateState(forceFeedback = true) {
                it.copy(error = uiString(R.string.error_password_same), notice = null)
            }
            return
        }
        viewModelScope.launch {
            updateState { it.copy(passwordChanging = true, error = null, notice = null) }
            runCatching {
                api.changePassword(currentPassword, newPassword)
            }.onSuccess { session ->
                updateState(noticeType = UiToastType.SUCCESS) {
                    it.copy(
                        account = session.account,
                        currentPassword = "",
                        newPassword = "",
                        confirmPassword = "",
                        passwordChanging = false,
                        notice = uiString(R.string.notice_password_changed),
                    )
                }
            }.onFailure { error ->
                updateState {
                    it.copy(
                        passwordChanging = false,
                        error = error.readableMessage(),
                    )
                }
            }
        }
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
                            recordsRefresh = it.recordsRefresh.copy(status = MemoListLoadStatus.Loading),
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
                            recordsRefresh = it.recordsRefresh.copy(status = MemoListLoadStatus.Loading),
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
                recordsCollection = it.recordsCollection.clear(),
                        recordsPagination = it.recordsPagination.copy(nextCursor = "", loadingMore = false),
                        recordsRefresh = it.recordsRefresh.cancel(),
                recordsMutation = it.recordsMutation.clear(),
                        recordsSelection = it.recordsSelection.clear(),
                        recordsSummary = it.recordsSummary.replacePresentation(null, loading = false),
                        recordsEditor = it.recordsEditor.stopAttachmentUpload(),
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
                            askConversation = it.askConversation.clear(),
                            askQuestion = "",
                            askLoading = false,
                            askLoadError = null,
                            askSending = false,
                            askStreaming = false,
                            askVariant = it.askVariant.invalidate(),
                            askRegeneratingId = "",
                            askLiveUser = null,
                            askLiveAnswer = "",
                            askSourceRequestId = it.askSourceRequestId + 1,
                            askSourceLoading = false,
                            askMemoSave = it.askMemoSave.invalidate(),
                recordsSearch = it.recordsSearch.clear(),
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
        val request = state.value.nextMemoRefreshRequest()
        updateState { current ->
            current.beginMemoRefresh(request)?.copy(error = null, notice = null) ?: current
        }
        if (!state.value.canApplyMemoRefresh(request)) {
            return
        }
        viewModelScope.launch {
            runCatching {
                if (request.sourceKey == SessionStore.MODE_OFFLINE) {
                    MemoListSnapshot(
                            memos = listLocalRecords(),
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
                    current.completeMemoRefresh(request)?.copy(
                        recordsCollection = current.recordsCollection.replace(
                            memosForFilter(snapshot.memos, request.filter),
                        ),
                            recordsPagination = current.recordsPagination.copy(
                                nextCursor = snapshot.nextCursor,
                                loadingMore = false,
                            ),
                            error = null,
                        ) ?: current
                    }
                }
                .onFailure { error ->
                    updateState { current ->
                        current.failMemoRefresh(request)?.copy(
                            recordsPagination = current.recordsPagination.copy(loadingMore = false),
                            error = error.readableMessage(),
                        ) ?: current
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
                current.beginMemoPage(request) ?: current
            }
            if (!state.value.canApplyMemoPage(request)) {
                return
            }
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                runCatching { listOnlineMemos(request.filter, cursor = request.cursor) }
                        .onSuccess { page ->
                updateState { current ->
                    current.completeMemoPage(request, page.nextCursor)?.copy(
                        recordsCollection = current.recordsCollection.replace(
                            memosForFilter(current.memos + page.memos, request.filter),
                        ),
                                ) ?: current
                            }
                        }
                        .onFailure { error ->
                            updateState { current ->
                                current.failMemoPage(request)?.copy(error = error.readableMessage()) ?: current
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
        val restored = consumeRestoredEditorDraft(editingMemoId = "")
        updateState {
            it.copy(
                screen = Screen.Editor,
                screenHistory = it.historyFor(Screen.Editor),
                recordsSelection = it.recordsSelection.clear(),
                recordsSummary = it.recordsSummary.replacePresentation(null, loading = false),
                recordsEditor = it.recordsEditor.open(
                    draftContent = restored?.content ?: "",
                    draftEntryDate = restored?.entryDate?.ifBlank { today } ?: today,
                    initialDraftContent = "",
                    initialDraftEntryDate = today,
                ),
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
                recordsSelection = it.recordsSelection.select(memo),
                recordsSummary = it.recordsSummary.replacePresentation(
                    null,
                    loading = !isOfflineMode(),
                ),
                recordsEditor = it.recordsEditor.setMarkdownPreview(false),
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
                recordsSelection = it.recordsSelection.clear(),
                recordsSummary = it.recordsSummary.replacePresentation(null, loading = false),
                recordsEditor = it.recordsEditor.open(
                    draftContent = memo.content,
                    draftEntryDate = today,
                    initialDraftContent = "",
                    initialDraftEntryDate = today,
                ),
                error = null,
                notice = null,
            )
        }
    }

    fun updateDraftContent(value: String) = updateState {
        if (it.canRunMemoEditorAction()) {
            it.copy(recordsEditor = it.recordsEditor.updateContent(value))
        } else {
            it
        }
    }

    fun updateDraftEntryDate(value: String) = updateState {
        if (it.canRunMemoEditorAction()) {
            it.copy(recordsEditor = it.recordsEditor.updateEntryDate(value))
        } else {
            it
        }
    }

    fun updateMarkdownPreview(preview: Boolean) {
        updateState {
            if (it.canRunMemoEditorAction()) {
                it.copy(recordsEditor = it.recordsEditor.setMarkdownPreview(preview))
            } else {
                it
            }
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
                it.copy(recordsEditor = it.recordsEditor.appendFormattedSnippet(snippet))
            } else {
                it
            }
        }
    }

    fun updateSearchQuery(value: String) {
        val blank = value.isBlank()
        val previousJob = searchJob
        updateState {
            it.copy(
                recordsSearch = it.recordsSearch.updateQuery(value),
                error = null,
            )
        }
        previousJob?.cancel()
        searchJob = null
        if (blank) {
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            searchMemos()
        }
    }

    fun searchMemos() {
        val previousJob = searchJob
        val request = state.value.nextMemoSearchRequest()
        if (request == null) {
            clearSearch()
            return
        }
        updateState { current -> current.startMemoSearch(request) }
        previousJob?.cancel()
        searchJob = null
        if (!state.value.canApplyMemoSearch(request)) {
            return
        }
        searchJob = viewModelScope.launch {
            try {
                val memos = if (request.sourceKey == SessionStore.MODE_OFFLINE) {
                    searchLocalRecords(request.filter.recordsSearchQuery(request.query))
                } else {
                    searchOnlineMemos(request.query, request.filter)
                }
                updateState { current ->
                    current.completeMemoSearch(
                        request = request,
                        results = memosForFilter(memos, request.filter),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                updateState { current ->
                    current.failMemoSearch(request, error.readableMessage())
                }
            }
        }
    }

    fun clearSearch() {
        val previousJob = searchJob
        updateState {
            it.copy(
                recordsSearch = it.recordsSearch.clear(),
                error = null,
            )
        }
        previousJob?.cancel()
        searchJob = null
    }

    fun exportFullData(uri: Uri) {
        launchDataTransfer {
            var aiSettingsFetched = true
            if (!isOfflineMode()) {
                val pulled = pullOnlineData()
                aiSettingsFetched = pulled.aiSettingsAvailable
            }
            val json = withContext(Dispatchers.Default) {
                val data = localDataStore.exportData(state.value.themeMode, state.value.memoViewMode.name)
                SillageExportCodec.toJson(data)
            }
            withContext(Dispatchers.IO) {
                appContext.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(json.toByteArray(Charsets.UTF_8))
                } ?: throw IllegalArgumentException(uiString(R.string.error_export_write))
            }
            updateState(noticeType = if (aiSettingsFetched) UiToastType.SUCCESS else UiToastType.WARNING) {
                it.copy(
                    notice = if (aiSettingsFetched) {
                        uiString(R.string.notice_exported)
                    } else {
                        uiString(R.string.error_sync_ai_settings_failed)
                    },
                )
            }
        }
    }

    fun importFullData(uri: Uri) {
        launchDataTransfer {
            val raw = withContext(Dispatchers.IO) {
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes().toString(Charsets.UTF_8)
                } ?: throw IllegalArgumentException(uiString(R.string.error_import_read))
            }
            val result = withContext(Dispatchers.Default) {
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
            updateState {
            it.copy(
                themeMode = result.themeMode,
                recordsBrowse = it.recordsBrowse.copy(viewMode = result.memoViewMode),
                recordsSelection = it.recordsSelection.clear(),
                recordsSummary = it.recordsSummary.replacePresentation(null, loading = false),
                recordsEditor = it.recordsEditor.stopAttachmentUpload(),
                    aiProfiles = result.aiProfiles,
                    aiAutoSummary = result.aiAutoSummary,
                    askConversation = it.askConversation.copy(
                        conversations = emptyList(),
                        messages = emptyList(),
                    ),
                recordsSearch = it.recordsSearch.clear(),
                    notice = uiString(R.string.notice_imported),
                )
            }
            refreshMemos()
        }
    }

    fun syncFromServer() {
        if (blockForRequiredAndroidUpdate()) return
        if (isOfflineMode()) {
            updateState(forceFeedback = true) {
                it.copy(error = uiString(R.string.error_sync_online_required), notice = null)
            }
            return
        }
        launchDataTransfer {
            val pulled = pullOnlineData()
            updateState(noticeType = if (pulled.aiSettingsAvailable) UiToastType.SUCCESS else UiToastType.WARNING) {
                it.copy(
                    notice = if (pulled.aiSettingsAvailable) {
                        uiString(R.string.notice_synced_local)
                    } else {
                        uiString(R.string.error_sync_ai_settings_failed)
                    },
                )
            }
        }
    }

    fun syncToServer() {
        if (blockForRequiredAndroidUpdate()) return
        if (isOfflineMode()) {
            updateState(forceFeedback = true) {
                it.copy(error = uiString(R.string.error_sync_online_required), notice = null)
            }
            return
        }
        launchDataTransfer {
            val summary = runSyncPush()
            presentSyncPushResult(summary)
        }
    }

    fun syncBothWays() {
        if (blockForRequiredAndroidUpdate()) return
        if (isOfflineMode()) {
            updateState(forceFeedback = true) {
                it.copy(error = uiString(R.string.error_sync_online_required), notice = null)
            }
            return
        }
        launchDataTransfer {
            val result = runTwoWaySync()
            val push = result.push
            val pulled = result.pull
            val warnAiSettings = !pulled.aiSettingsAvailable
            val conflicts = conflictItemsFromSummary(push)
            updateState(
                noticeType = if (warnAiSettings) UiToastType.WARNING else syncPushToastType(push),
            ) {
                it.copy(
                    syncConflictState = if (conflicts.isEmpty()) {
                        it.syncConflictState
                    } else {
                        it.syncConflictState.replace(conflicts)
                    },
                    notice = if (warnAiSettings) {
                        uiString(R.string.error_sync_ai_settings_failed)
                    } else {
                        uiString(R.string.notice_sync_both, syncPushNotice(push))
                    },
                )
            }
            refreshMemos()
        }
    }

    fun resolveSyncConflictKeepLocal(resourceId: String) {
        val item = state.value.syncConflicts.find { it.conflict.resourceId == resourceId } ?: return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    resolveMemoSyncConflict(
                        ResolveMemoSyncConflictCommand.KeepLocal(item.conflict),
                    )
                }
            }.onSuccess {
                updateState(noticeType = UiToastType.SUCCESS) {
                    it.copy(
                        syncConflictState = it.syncConflictState.remove(resourceId),
                        notice = uiString(R.string.notice_conflict_keep_local),
                    )
                }
            }.onFailure { error ->
                updateState { it.copy(error = error.readableMessage()) }
            }
        }
    }

    fun resolveSyncConflictTakeServer(resourceId: String) {
        val item = state.value.syncConflicts.find { it.conflict.resourceId == resourceId } ?: return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    resolveMemoSyncConflict(
                        ResolveMemoSyncConflictCommand.TakeServer(item.conflict),
                    )
                }
            }.onSuccess {
                updateState(noticeType = UiToastType.SUCCESS) {
                    it.copy(
                        syncConflictState = it.syncConflictState.remove(resourceId),
                        notice = uiString(R.string.notice_conflict_take_server),
                    recordsSelection = it.recordsSelection.replaceIfSelected(
                        resourceId,
                        item.conflict.serverMemo,
                    ),
                    )
                }
                refreshMemos()
            }.onFailure { error ->
                updateState { it.copy(error = error.readableMessage()) }
            }
        }
    }

    fun dismissSyncConflict(resourceId: String) {
        updateState {
            it.copy(
                    syncConflictState = it.syncConflictState.remove(resourceId),
            )
        }
    }

    // Single-flight wrapper for full-data operations (sync/import/export) so a
    // rapid double tap cannot start two concurrent transfers.
    private fun launchDataTransfer(block: suspend () -> Unit) {
        val lease = dataTransferGate.tryAcquire() ?: return
        updateState { it.copy(loading = true, error = null, notice = null) }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            runSingleFlightOperation(
                lease = lease,
                onFailure = { error ->
                    updateState { it.copy(error = error.readableMessage()) }
                },
                onFinished = {
                    updateState { it.copy(loading = false) }
                },
            ) {
                block()
            }
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
                recordsBrowse = it.recordsBrowse.selectViewMode(mode),
                recordsCollection = if (resetFilter) {
                    it.recordsCollection.clear()
                } else {
                    it.recordsCollection
                },
                recordsPagination = if (resetFilter) {
                    it.recordsPagination.copy(nextCursor = "", loadingMore = false)
                } else {
                    it.recordsPagination
                },
                recordsRefresh = if (resetFilter) {
                    it.recordsRefresh.copy(status = MemoListLoadStatus.Loading)
                } else {
                    it.recordsRefresh
                },
                recordsSearch = if (mode == MemoViewMode.Calendar) {
                    it.recordsSearch.clear()
                } else {
                    it.recordsSearch
                },
                recordsSelection = it.recordsSelection.clear(),
                recordsSummary = it.recordsSummary.replacePresentation(null, loading = false),
                error = if (mode == MemoViewMode.Calendar) null else it.error,
            )
        }
        if (resetFilter) {
            refreshMemos()
        }
    }

    fun returnToRecords() {
        val current = state.value
        if (!current.shouldReturnToRecordsOnBack()) {
            return
        }
        if (current.askVariantLoading) {
            updateState(forceFeedback = true, noticeType = UiToastType.WARNING) {
                it.copy(
                    error = null,
                    notice = uiString(R.string.notice_ask_variant_saving),
                )
            }
            return
        }
        updateMemoViewMode(MemoViewMode.List)
    }

    fun updateMemoListFilter(filter: MemoListFilter) {
        if (state.value.memoListFilter == filter || state.value.askVariantLoading) {
            return
        }
        searchJob?.cancel()
        updateState {
            it.copy(
                recordsBrowse = it.recordsBrowse.selectFilter(filter),
                recordsCollection = it.recordsCollection.clear(),
                recordsPagination = it.recordsPagination.copy(nextCursor = "", loadingMore = false),
                recordsRefresh = it.recordsRefresh.copy(status = MemoListLoadStatus.Loading),
                recordsSearch = it.recordsSearch.clear(),
                recordsSelection = it.recordsSelection.clear(),
                recordsSummary = it.recordsSummary.replaceSummary(null),
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
                recordsBrowse = it.recordsBrowse.selectMonth(next.year, next.monthValue),
            )
        }
    }

    fun selectCalendarDate(date: String) {
        updateState {
            it.copy(recordsBrowse = it.recordsBrowse.selectCalendarDate(date))
        }
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
        if (runCatching { LocalDate.parse(current.draftEntryDate.trim()) }.isFailure) {
            updateState(forceFeedback = true) {
                it.copy(error = uiString(R.string.error_entry_date_invalid))
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
            val draft = RecordDraft(
                content = current.draftContent.trim(),
                entryDate = current.draftEntryDate.trim(),
            )
            val command = if (selectedMemo == null) {
                SaveRecordCommand.Create(draft)
            } else {
                SaveRecordCommand.Update(selectedMemo, draft)
            }
            val saved = saveRecord(command, current.appMode)
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
                        recordsSelection = it.recordsSelection.select(saved),
                        recordsSummary = it.recordsSummary.replacePresentation(
                            if (current.selectedMemo?.id == saved.id) it.selectedSummary else null,
                            loading = current.appMode != SessionStore.MODE_OFFLINE,
                        ),
                        recordsEditor = it.recordsEditor.reset(LocalDate.now().toString()),
                recordsSearch = it.recordsSearch.clear(),
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
            val deleted = mutateRecordLifecycle(
                RecordLifecycleCommand.Delete(memo),
                current.appMode,
            )
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
                        recordsSelection = it.recordsSelection.clear(),
                        recordsSummary = it.recordsSummary.replacePresentation(null, loading = false),
                        recordsEditor = it.recordsEditor.reset(LocalDate.now().toString()),
                recordsSearch = it.recordsSearch.clear(),
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
            val updated = mutateRecordLifecycle(
                RecordLifecycleCommand.SetFavorited(memo, memo.favoritedAt == null),
                current.appMode,
            )
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
            val updated = mutateRecordLifecycle(
                RecordLifecycleCommand.SetArchived(memo, memo.archivedAt == null),
                current.appMode,
            )
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
            val updated = mutateRecordLifecycle(
                RecordLifecycleCommand.SetFavorited(memo, memo.favoritedAt == null),
                appMode,
            )
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
            val updated = mutateRecordLifecycle(
                RecordLifecycleCommand.SetArchived(memo, memo.archivedAt == null),
                appMode,
            )
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
            val deleted = mutateRecordLifecycle(RecordLifecycleCommand.Delete(memo), appMode)
            if (!applyMemo(deleted, appMode, clientContextGeneration)) {
                return@launchMemoMutation
            }
            updateState {
                if (
                    it.appMode == appMode &&
                    it.clientContextGeneration == clientContextGeneration
                ) {
                    it.copy(
                        recordsSelection = it.recordsSelection.clearIfSelected(memo.id),
                        recordsSummary = it.recordsSummary.replaceSummary(
                            if (it.selectedMemo?.id == memo.id) null else it.selectedSummary,
                        ),
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

    fun restoreMemo(memo: Memo) {
        val current = state.value
        val appMode = current.appMode
        val clientContextGeneration = current.clientContextGeneration
        launchMemoMutation(
            MemoMutationKey.Memo(memo.id, clientContextGeneration),
            memoId = memo.id,
        ) {
            val restored = mutateRecordLifecycle(RecordLifecycleCommand.Restore(memo), appMode)
            if (applyMemo(restored, appMode, clientContextGeneration)) {
                updateState { state ->
                    if (state.appMode == appMode && state.clientContextGeneration == clientContextGeneration) {
                        state.copy(notice = uiString(R.string.notice_restored))
                    } else {
                        state
                    }
                }
            }
        }
    }

    fun purgeMemo(memo: Memo) {
        val current = state.value
        val appMode = current.appMode
        val clientContextGeneration = current.clientContextGeneration
        launchMemoMutation(
            MemoMutationKey.Memo(memo.id, clientContextGeneration),
            memoId = memo.id,
        ) {
            val purged = mutateRecordLifecycle(RecordLifecycleCommand.Purge(memo), appMode)
            if (applyMemo(purged, appMode, clientContextGeneration)) {
                updateState { state ->
                    if (state.appMode == appMode && state.clientContextGeneration == clientContextGeneration) {
                        state.copy(notice = uiString(R.string.notice_purged))
                    } else {
                        state
                    }
                }
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
                val ai = if (request.sourceKey == SessionStore.MODE_OFFLINE) {
                    val generated = generateLocalRecordSummary(memo)
                    val latest = state.value
                    if (
                        latest.appMode != request.sourceKey ||
                        latest.clientContextGeneration != request.clientContextGeneration ||
                    getLocalRecordDetail(request.memoId).memo.version != request.memoVersion
                    ) {
                        return@launch
                    }
                    saveLocalRecordSummary(generated)
                    generated
                } else {
                    generateRemoteRecordSummary(memo)
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
                val message = if (error is ActiveRecordSummaryProfileRequiredException) {
                    uiString(R.string.error_ai_default_profile_required)
                } else {
                    error.readableMessage()
                }
                updateState { state ->
                    state.failMemoSummaryRequest(request, message)
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
        val editorSessionId = current.editorSessionId
        val offline = isOfflineMode()
        updateState {
            if (it.editorSessionId == editorSessionId && it.canRunMemoEditorAction()) {
                val editor = it.recordsEditor.beginAttachmentUpload(editorSessionId)
                    ?: return@updateState it
                it.copy(recordsEditor = editor, error = null, notice = null)
            } else {
                it
            }
        }
        if (!state.value.canApplyAttachmentUpload(editorSessionId)) {
            return
        }
        viewModelScope.launch {
            // Insert each successfully uploaded (or offline-queued) attachment as
            // soon as it lands so a mid-batch failure never discards earlier links.
            var failedCount = 0
            var firstError: Throwable? = null
            for (uri in uris) {
                val snippet = runCatching {
                    if (offline) {
                        val pending = stageOfflineAttachment(uri)
                        localAttachmentMarkdown(pending)
                    } else {
                        val upload = readAttachmentUpload(uri)
                        attachmentMarkdown(api.uploadAttachment(upload))
                    }
                }.getOrElse { error ->
                    if (error is CancellationException) {
                        throw error
                    }
                    failedCount += 1
                    if (firstError == null) {
                        firstError = error
                    }
                    null
                }
                if (snippet != null) {
                updateState {
                    if (it.canApplyAttachmentUpload(editorSessionId)) {
                        it.copy(
                            recordsEditor = it.recordsEditor.appendAttachmentSnippet(snippet),
                        )
                        } else {
                            it
                        }
                    }
                }
                if (!state.value.canApplyAttachmentUpload(editorSessionId)) {
                    return@launch
                }
            }
            val partialFailure = failedCount in 1 until uris.size
            updateState(noticeType = if (partialFailure) UiToastType.WARNING else UiToastType.SUCCESS) {
                if (it.canApplyAttachmentUpload(editorSessionId)) {
                    when {
                    failedCount == 0 -> it.copy(
                        recordsEditor = it.recordsEditor.finishAttachmentUpload(editorSessionId),
                            notice = uiString(
                                if (offline) {
                                    R.string.notice_attachment_queued_offline
                                } else {
                                    R.string.notice_attachment_inserted
                                },
                            ),
                        )
                    partialFailure -> it.copy(
                        recordsEditor = it.recordsEditor.finishAttachmentUpload(editorSessionId),
                            error = null,
                            notice = uiString(R.string.notice_attachment_partial_failure, failedCount),
                        )
                    else -> it.copy(
                        recordsEditor = it.recordsEditor.finishAttachmentUpload(editorSessionId),
                            error = firstError?.readableMessage(),
                        )
                    }
                } else {
                    it
                }
            }
        }
    }

    fun openProtectedAttachment(target: MarkdownLinkTarget.ProtectedAttachment) {
        val pendingId = pendingLocalAttachmentId(target)
        if (pendingId != null) {
            openPendingLocalAttachment(pendingId, target.filename)
            return
        }
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
        val attachmentOpen = current.recordsAttachmentOpen.begin(target.path) ?: return
        val requestId = attachmentOpen.requestId
        updateState {
            if (
                !it.recordsAttachmentOpen.opening &&
                it.recordsAttachmentOpen.requestId + 1 == requestId
            ) {
                it.copy(
                    recordsAttachmentOpen = attachmentOpen,
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
                            recordsAttachmentOpen = it.recordsAttachmentOpen.complete(requestId),
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
                    recordsAttachmentOpen = it.recordsAttachmentOpen.complete(requestId),
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
                    aiProfiles = it.aiProfiles + AIProfileDraft(
                        draftKey = UUID.randomUUID().toString(),
                        active = it.aiProfiles.isEmpty(),
                    ),
                )
            } else {
                it
            }
        }
    }

    fun removeAIProfile(index: Int): Boolean {
        var removed = false
        updateState {
            if (
                !it.loading &&
                !it.aiSettingsLoading &&
                !it.aiSettingsSaving &&
                !it.aiAutoSummarySaving &&
                index in it.aiProfiles.indices
            ) {
                removed = true
                it.copy(
                    aiProfiles = normalizedAIProfiles(
                        it.aiProfiles.filterIndexed { profileIndex, _ -> profileIndex != index },
                    ),
                )
            } else {
                it
            }
        }
        return removed
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
        updateState {
            if (
                !it.loading &&
                !it.aiSettingsLoading &&
                !it.aiSettingsSaving &&
                !it.aiAutoSummarySaving &&
                index in it.aiProfiles.indices
            ) {
                it.copy(
                    aiProfiles = it.aiProfiles.mapIndexed { profileIndex, profile ->
                        profile.copy(enabled = true, active = profileIndex == index)
                    },
                )
            } else {
                it
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
                    listLocalAskConversations()
                } else {
                    listRemoteAskConversations()
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
                                askConversation = current.askConversation.replaceConversations(
                                    conversations.filter(AskConversation::isActive),
                                ),
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
                    askConversation = latest.askConversation.select(
                        conversationId = id,
                        headMessageId = conversation?.headMessageId,
                        messages = emptyList(),
                    ),
                    askScope = conversation?.contextScope ?: latest.askScope,
                    askLoading = true,
                    askLoadError = null,
                    askScreenSessionId = screenSessionId,
                    askVariant = latest.askVariant.invalidate(),
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
                    listLocalAskMessages(id)
                } else {
                    listRemoteAskMessages(id)
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
                                askConversation = latest.askConversation.select(
                                    conversationId = id,
                                    headMessageId = latest.askHeadId,
                                    messages = messages,
                                ),
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
                askConversation = it.askConversation.deselect(),
                askQuestion = "",
                askRegeneratingId = "",
                askLiveUser = null,
                askLiveAnswer = "",
                askStreaming = false,
                askScreenSessionId = it.askScreenSessionId + 1,
                askVariant = it.askVariant.invalidate(),
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
                val variant = it.askVariant.begin(request, it.askVariantContext())
                    ?: return@updateState it
                it.copy(
                    askConversation = it.askConversation.moveHead(
                        request.conversationId,
                        leafId,
                    ),
                    askVariant = variant,
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
        viewModelScope.launch {
            try {
                if (request.appMode == SessionStore.MODE_OFFLINE) {
                    setLocalAskHead(request.conversationId, leafId)
                } else {
                    setRemoteAskHead(request.conversationId, leafId)
                }
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
                val memo = saveRecord(
                    SaveRecordCommand.Create(
                        RecordDraft(request.memoContent, entryDate),
                    ),
                    request.appMode,
                )
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
                        recordsSelection = current.recordsSelection.select(memo),
                        recordsSummary = current.recordsSummary.replacePresentation(
                            null,
                            loading = request.appMode != SessionStore.MODE_OFFLINE,
                        ),
                        recordsEditor = current.recordsEditor
                            .stopAttachmentUpload()
                            .setMarkdownPreview(false),
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
                getLocalRecordDetail(request.memoId)
            } else {
                getRemoteRecordDetail(request.memoId)
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
                current.copy(
                                screen = Screen.MemoDetail,
                                screenHistory = request.destinationHistory(),
                            recordsCollection = current.recordsCollection.replace(cached),
                            recordsSearch = current.recordsSearch.mergeResultMemo(
                                detail.memo,
                                current.memoListFilter,
                            ),
                            recordsSelection = current.recordsSelection.select(detail.memo),
                            recordsSummary = current.recordsSummary.replacePresentation(
                                detail.ai,
                                loading = false,
                            ),
                            recordsEditor = current.recordsEditor
                                .stopAttachmentUpload()
                                .setMarkdownPreview(false),
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
                recordsSelection = it.recordsSelection.clear(),
                recordsSummary = it.recordsSummary.replacePresentation(null, loading = false),
                recordsEditor = it.recordsEditor.stopAttachmentUpload(),
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
                recordsSelection = if (returningToDetail) {
                    it.recordsSelection
                } else {
                    it.recordsSelection.clear()
                },
                recordsSummary = it.recordsSummary.replacePresentation(
                    null,
                    loading = returningToDetail && !isOfflineMode(),
                ),
                recordsEditor = it.recordsEditor.reset(LocalDate.now().toString()),
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

    fun notifyMemoEditorBackBlocked() {
        if (state.value.memoEditorBusyReason() == null) {
            return
        }
        val attachmentUploadNotice = uiString(R.string.notice_editor_back_attachment_uploading)
        val operationNotice = uiString(R.string.notice_editor_back_operation)
        updateState(forceFeedback = true, noticeType = UiToastType.WARNING) {
            it.withMemoEditorBackBlockedNotice(
                attachmentUploadNotice = attachmentUploadNotice,
                operationNotice = operationNotice,
            )
        }
    }

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
        // Reject oversized files before materializing them in memory.
        val size = attachmentSize(uri)
        if (size != null && size > MAX_ATTACHMENT_UPLOAD_BYTES) {
            throw IllegalArgumentException(
                uiString(R.string.error_attachment_too_large, filename, MAX_ATTACHMENT_UPLOAD_MB),
            )
        }
        val contentType = resolver.getType(uri) ?: "application/octet-stream"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException(uiString(R.string.error_attachment_read))
        if (bytes.size > MAX_ATTACHMENT_UPLOAD_BYTES) {
            throw IllegalArgumentException(
                uiString(R.string.error_attachment_too_large, filename, MAX_ATTACHMENT_UPLOAD_MB),
            )
        }
        AttachmentUpload(
            filename = filename,
            contentType = contentType,
            bytes = bytes,
        )
    }

    private fun attachmentSize(uri: Uri): Long? {
        appContext.contentResolver
            .query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0 && !cursor.isNull(index)) {
                        return cursor.getLong(index)
                    }
                }
            }
        return null
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
            it.copy(recordsPagination = it.recordsPagination.cancel())
        }
    }

    private fun cancelAskVariant() {
        updateState {
            it.copy(askVariant = it.askVariant.invalidate())
        }
    }

    private fun completeAskVariantSelection(request: AskVariantRequest, leafId: String) {
        updateState { current ->
            if (current.canApplyAskVariant(request)) {
                val variant = current.askVariant.finish(request, current.askVariantContext())
                    ?: return@updateState current
                current.copy(
                    askConversation = current.askConversation.moveHead(
                        request.conversationId,
                        leafId,
                    ),
                    askVariant = variant,
                    notice = null,
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
                val variant = current.askVariant.finish(request, current.askVariantContext())
                    ?: return@updateState current
                current.copy(
                    askConversation = current.askConversation.moveHead(
                        request.conversationId,
                        previousHeadId,
                    ),
                    askVariant = variant,
                    error = error.readableMessage(),
                    notice = null,
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
                it.copy(recordsAttachmentOpen = it.recordsAttachmentOpen.complete(requestId))
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

    private suspend fun saveRecord(
        command: SaveRecordCommand,
        appMode: String,
    ): Memo {
        return if (appMode == SessionStore.MODE_OFFLINE) {
            saveLocalRecord(command)
        } else {
            saveRemoteRecord(command)
        }
    }

    private suspend fun mutateRecordLifecycle(
        command: RecordLifecycleCommand,
        appMode: String,
    ): Memo {
        return if (appMode == SessionStore.MODE_OFFLINE) {
            mutateLocalRecordLifecycle(command)
        } else {
            mutateRemoteRecordLifecycle(command)
        }
    }

    private fun fetchSelectedMemoDetail(memoId: String) {
        val request = state.value.nextMemoDetailRequest(memoId) ?: return
        updateState { current -> current.startMemoDetailRequest(request) }
        if (state.value.memoDetailRequestId != request.requestId) {
            return
        }
        viewModelScope.launch {
            runCatching {
                if (request.sourceKey == SessionStore.MODE_OFFLINE) {
                    getLocalRecordDetail(memoId)
                } else {
                    getRemoteRecordDetail(memoId)
                }
            }
                .onSuccess { detail ->
                    var restartSearch = false
                    updateState { current ->
                        val completed = current.completeMemoDetailRequest(request, detail)
                        restartSearch = current.searching &&
                            current.searchQuery.isNotBlank() &&
                            completed.memoCacheGeneration != current.memoCacheGeneration
                        completed
                    }
                    if (restartSearch) {
                        searchMemos()
                    }
                }
                .onFailure { error ->
                    updateState { current ->
                        current.failMemoDetailRequest(request, error.readableMessage())
                    }
                }
        }
    }

    private suspend fun reloadAskConversation(conversationId: String): AskSnapshot {
        val messages = listRemoteAskMessages(conversationId)
        val conversations = listRemoteAskConversations().filter(AskConversation::isActive)
        val headId = conversations.find { it.id == conversationId }?.headMessageId
        return AskSnapshot(
            messages = messages,
            conversations = conversations,
            headId = headId ?: lastAssistantMessageId(buildAskActivePath(messages, null)),
        )
    }

    private suspend fun pullOnlineData(): PullSyncResult = withContext(Dispatchers.IO) {
        pullSync()
    }

    private fun presentSyncPushResult(summary: SyncPushSummary) {
        val conflicts = conflictItemsFromSummary(summary)
        updateState(noticeType = syncPushToastType(summary)) {
            it.copy(
                syncConflictState = it.syncConflictState.replace(conflicts),
                notice = syncPushNotice(summary),
            )
        }
    }

    private fun conflictItemsFromSummary(summary: SyncPushSummary): List<SyncConflictItem> {
        return summary.conflictMemoSyncs.map { conflict ->
            MemoSyncConflictItem(
                conflict = conflict,
                localMemo = resolveMemoSyncConflict.localMemo(conflict),
            )
        }
    }

    private fun syncPushNotice(summary: SyncPushSummary): String {
        return if (summary.applied == 0 && summary.conflict == 0 && summary.rejected == 0) {
            uiString(R.string.sync_none)
        } else {
            uiString(R.string.sync_summary, summary.applied, summary.conflict, summary.rejected)
        }
    }

    private suspend fun stageOfflineAttachment(uri: Uri): PendingLocalAttachment {
        val upload = readAttachmentUpload(uri)
        val id = UUID.randomUUID().toString()
        val dir = File(appContext.filesDir, PENDING_ATTACHMENTS_DIRECTORY).apply { mkdirs() }
        val file = File(dir, id)
        withContext(Dispatchers.IO) {
            file.outputStream().use { output -> output.write(upload.bytes) }
        }
        val pending = PendingLocalAttachment(
            id = id,
            filename = upload.filename,
            contentType = upload.contentType,
            absolutePath = file.absolutePath,
            size = upload.bytes.size.toLong(),
        )
        localDataStore.addPendingLocalAttachment(pending)
        return pending
    }

    private suspend fun flushPendingLocalAttachments() {
        val pending = localDataStore.listPendingLocalAttachments()
        if (pending.isEmpty()) {
            return
        }
        val draftRewrites = mutableListOf<Pair<String, String>>()
        for (item in pending) {
            val file = File(item.absolutePath)
            if (!file.isFile) {
                localDataStore.removePendingLocalAttachment(item.id)
                continue
            }
            val bytes = withContext(Dispatchers.IO) { file.readBytes() }
            val uploaded = api.uploadAttachment(
                AttachmentUpload(
                    filename = item.filename,
                    contentType = item.contentType,
                    bytes = bytes,
                ),
            )
            val remoteMarkdown = attachmentMarkdown(uploaded).trim()
            val localMarkdown = localAttachmentMarkdown(item).trim()
            localDataStore.replaceAttachmentMarkdownEverywhere(localMarkdown, remoteMarkdown)
            draftRewrites += localMarkdown to remoteMarkdown
            localDataStore.removePendingLocalAttachment(item.id)
            withContext(Dispatchers.IO) {
                runCatching { file.delete() }
            }
        }
        if (draftRewrites.isNotEmpty()) {
            updateState { current ->
                if (current.screen != Screen.Editor) {
                    return@updateState current
                }
                var draft = current.draftContent
                draftRewrites.forEach { (from, to) ->
                    if (draft.contains(from)) {
                        draft = draft.replace(from, to)
                    }
                }
            if (draft == current.draftContent) {
                current
            } else {
                current.copy(recordsEditor = current.recordsEditor.updateContent(draft))
                }
            }
        }
    }

    private fun openPendingLocalAttachment(pendingId: String, filename: String) {
        val pending = localDataStore.getPendingLocalAttachment(pendingId)
        if (pending == null) {
            updateState(forceFeedback = true) {
                it.copy(error = uiString(R.string.error_attachment_missing_local), notice = null)
            }
            return
        }
        val source = File(pending.absolutePath)
        if (!source.isFile) {
            updateState(forceFeedback = true) {
                it.copy(error = uiString(R.string.error_attachment_missing_local), notice = null)
            }
            return
        }
        if (state.value.openingAttachmentPath != null || attachmentOpenJob?.isActive == true) {
            return
        }
        val openPath = localAttachmentPath(pending)
        val attachmentOpen = state.value.recordsAttachmentOpen.begin(openPath) ?: return
        val requestId = attachmentOpen.requestId
        updateState {
            if (
                !it.recordsAttachmentOpen.opening &&
                it.recordsAttachmentOpen.requestId + 1 == requestId
            ) {
                it.copy(
                    recordsAttachmentOpen = attachmentOpen,
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
                requestDirectory = File(cacheRoot, UUID.randomUUID().toString()).also { it.mkdirs() }
                val displayName = preferredAttachmentFilename(null, filename.ifBlank { pending.filename })
                val shareFile = File(requestDirectory, displayName)
                withContext(Dispatchers.IO) {
                    source.copyTo(shareFile, overwrite = true)
                }
                val event = AttachmentOpenEvent(
                    requestId = requestId,
                    file = shareFile,
                    mimeType = resolveAttachmentMimeType(pending.contentType, displayName),
                    displayName = displayName,
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
                            recordsAttachmentOpen = it.recordsAttachmentOpen.complete(requestId),
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

    private fun startAskStream(content: String, forkOfId: String?) {
        invalidateAskMemoSaveNavigation()
        val current = state.value
        val initialRequest = current.nextAskStreamRequest() ?: return
        val contextScope = current.askScope
        val sourceKind = current.askSourceKind
        val previousHeadId = current.askHeadId
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
            startLocalAsk(
                content = content,
                forkOfId = forkOfId,
                initialRequest = initialRequest,
                contextScope = contextScope,
                previousHeadId = previousHeadId,
            )
            return
        }

        askStreamJob?.cancel()
        askStreamJob = viewModelScope.launch {
            var request = initialRequest
            var conversationId = request.conversationId
            var answerAvailable = false
            try {
                if (conversationId.isBlank()) {
                    val created = createRemoteAskConversation(contextScope)
                    val createdRequest = request.copy(conversationId = created.id)
                    conversationId = created.id
                    updateState { currentState ->
                        if (currentState.canApplyAskStream(request)) {
                            currentState.copy(
                                askConversation = currentState.askConversation.activate(created),
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
                                        answerAvailable = hasNewCompletedAskAnswer(
                                            messages = snapshot.messages,
                                            headId = snapshot.headId,
                                            previousHeadId = previousHeadId,
                                        )
                                        currentState.copy(
                                            askConversation = currentState.askConversation.replaceSnapshot(
                                                conversationId = request.conversationId,
                                                conversations = snapshot.conversations,
                                                headMessageId = snapshot.headId,
                                                messages = snapshot.messages,
                                            ),
                                        )
                                    } else {
                                        currentState
                                    }
                                }
                            }
                    }
                    updateState { currentState ->
                        if (currentState.canApplyAskStream(request)) {
                            currentState.finishAskStream(
                                answerAvailable = answerAvailable,
                                clearQuestion = forkOfId == null,
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
        previousHeadId: String?,
    ) {
        askStreamJob?.cancel()
        askStreamJob = viewModelScope.launch {
            var request = initialRequest
            var conversationId = request.conversationId
            var answerAvailable = false
            try {
                if (conversationId.isBlank()) {
                    val created = createLocalAskConversation(contextScope)
                    val createdRequest = request.copy(conversationId = created.id)
                    conversationId = created.id
                    updateState { currentState ->
                        if (currentState.canApplyAskStream(request)) {
                            currentState.copy(
                                askConversation = currentState.askConversation.activate(created),
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
                val messages = listLocalAskMessages(conversationId)
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
                val refreshedMessages = listLocalAskMessages(conversationId)
                val conversations = listLocalAskConversations().filter(AskConversation::isActive)
                val refreshedHeadId = conversations.find { conversation ->
                    conversation.id == conversationId
                }?.headMessageId
                updateState { currentState ->
                    if (currentState.canApplyAskStream(request)) {
                        answerAvailable = hasNewCompletedAskAnswer(
                            messages = refreshedMessages,
                            headId = refreshedHeadId,
                            previousHeadId = previousHeadId,
                        )
                        currentState.copy(
                            askConversation = currentState.askConversation.replaceSnapshot(
                                conversationId = request.conversationId,
                                conversations = conversations,
                                headMessageId = refreshedHeadId,
                                messages = refreshedMessages,
                            ),
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
                    currentState.finishAskStream(
                        answerAvailable = answerAvailable,
                        clearQuestion = false,
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
                recordsMutation = current.recordsMutation.begin(memoId),
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
                        recordsMutation = current.recordsMutation.finish(memoId),
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
        // Corrupted local data must surface as an error, not crash the app or
        // silently show an empty diary.
        val localSnapshot = runCatching {
            OfflineLocalSnapshot(
                    memos = memosForFilter(listLocalRecords(), filter),
                aiProfiles = localDataStore.listAIProfiles(),
                autoSummary = localDataStore.autoSummaryEnabled(),
            )
        }
        val memos = localSnapshot.getOrNull()?.memos.orEmpty()
        val aiProfiles = localSnapshot.getOrNull()?.aiProfiles.orEmpty()
        val autoSummary = localSnapshot.getOrNull()?.autoSummary == true
        val loadError = localSnapshot.exceptionOrNull()?.readableMessage()
        updateState {
            it.invalidateAIAutoSummaryRequest().copy(
                appMode = SessionStore.MODE_OFFLINE,
                clientContextGeneration = it.clientContextGeneration + 1,
                initialized = true,
                account = null,
                recordsCollection = it.recordsCollection.replace(memos),
                recordsPagination = it.recordsPagination.copy(nextCursor = "", loadingMore = false),
                recordsRefresh = it.recordsRefresh.cancel(),
                recordsMutation = it.recordsMutation.clear(),
                recordsSelection = it.recordsSelection.clear(),
                recordsSummary = it.recordsSummary.replacePresentation(null, loading = false),
                recordsEditor = it.recordsEditor.stopAttachmentUpload(),
                aiProfiles = aiProfiles,
                aiAutoSummary = autoSummary,
                aiSettingsLoading = false,
                aiSettingsLoadError = null,
                aiSettingsSaving = false,
                aiSettingsRequestId = it.aiSettingsRequestId + 1,
                aiTestingProfileId = "",
                aiLoadingModelsProfileId = "",
                aiTestResults = emptyMap(),
                aiModelResults = emptyMap(),
                askConversation = it.askConversation.clear(),
                askQuestion = "",
                askLoading = false,
                askLoadError = null,
                askScreenSessionId = it.askScreenSessionId + 1,
                askSourceRequestId = it.askSourceRequestId + 1,
                askSourceLoading = false,
                askMemoSave = it.askMemoSave.invalidate(),
                recordsSearch = it.recordsSearch.clear(),
                screen = Screen.Memos,
                screenHistory = emptyList(),
                authError = null,
                authErrorResourceId = null,
                error = loadError,
                notice = if (loadError == null) notice else null,
            )
        }
    }

    private data class OfflineLocalSnapshot(
        val memos: List<Memo>,
        val aiProfiles: List<AIProfileDraft>,
        val autoSummary: Boolean,
    )

    private fun isOfflineMode(): Boolean = state.value.appMode == SessionStore.MODE_OFFLINE

    private fun cancelAIAutoSummarySave() {
        aiAutoSummaryJob?.cancel()
        aiAutoSummaryJob = null
    }

    private fun memoViewModeFromName(value: String): MemoViewMode {
        return MemoViewMode.fromName(value)
    }

    private fun applyMemo(
        memo: Memo,
        appMode: String,
        clientContextGeneration: Long,
    ): Boolean {
        var applied = false
        var restartSearch = false
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
                restartSearch = current.searching && current.searchQuery.isNotBlank()
                current.applyMemoToCache(memo)
            } else {
                current
            }
        }
        if (applied && restartSearch) {
            searchMemos()
        }
        return applied
    }

    private suspend fun listOnlineMemos(
        filter: MemoListFilter,
        cursor: String = "",
    ) = listRemoteRecords(filter.recordsPageQuery(cursor))

    private suspend fun searchOnlineMemos(query: String, filter: MemoListFilter) =
        searchRemoteRecords(filter.recordsSearchQuery(query))

    private fun openEditorForMemo(memo: Memo) {
        cancelMemoSummary()
        cancelAttachmentOpen()
        val restored = consumeRestoredEditorDraft(editingMemoId = memo.id)
        updateState {
            it.copy(
                screen = Screen.Editor,
                screenHistory = it.historyFor(Screen.Editor),
                recordsSelection = it.recordsSelection.select(memo),
                recordsSummary = it.recordsSummary.replacePresentation(
                    null,
                    loading = !isOfflineMode(),
                ),
                recordsEditor = it.recordsEditor.open(
                    draftContent = restored?.content ?: memo.content,
                    draftEntryDate = restored?.entryDate?.ifBlank { memo.entryDate } ?: memo.entryDate,
                    initialDraftContent = memo.content,
                    initialDraftEntryDate = memo.entryDate,
                ),
                error = null,
                notice = null,
            )
        }
    }

    private fun Throwable.readableMessage(): String {
        if (this is IOException) {
            // Raw socket-level messages are English and unhelpful; log them and
            // show a localized transport error instead.
            android.util.Log.w("SillageViewModel", "network error: ${message.orEmpty()}", this)
            return uiString(R.string.error_network)
        }
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

    private fun blockForRequiredAndroidUpdate(): Boolean {
        if (
            !state.value.androidUpdateRequired ||
            state.value.appMode == SessionStore.MODE_OFFLINE
        ) {
            return false
        }
        updateState(forceFeedback = true) {
            it.copy(
                error = uiString(
                    R.string.error_android_update_required,
                    BuildConfig.VERSION_CODE,
                    it.minimumAndroidVersionCode,
                ),
                notice = null,
            )
        }
        return true
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val handle = runCatching { extras.createSavedStateHandle() }.getOrNull()
            return SillageViewModel(context, handle) as T
        }

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
private const val PENDING_ATTACHMENTS_DIRECTORY = "pending_attachments"
private const val ATTACHMENT_DOWNLOAD_TEMP_FILENAME = "download.tmp"
private const val TOAST_EVENT_BUFFER_CAPACITY = 8
// Matches the server's default --max-upload-mb (internal/profile/profile.go).
private const val MAX_ATTACHMENT_UPLOAD_MB = 30
private const val MAX_ATTACHMENT_UPLOAD_BYTES = MAX_ATTACHMENT_UPLOAD_MB * 1024L * 1024L
private const val KEY_SAVED_DRAFT_CONTENT = "editor_draft_content"
private const val KEY_SAVED_DRAFT_ENTRY_DATE = "editor_draft_entry_date"
private const val KEY_SAVED_EDITING_MEMO_ID = "editor_editing_memo_id"
private val HAN_CHARACTER = Regex("[\\u4E00-\\u9FFF]")

private data class RestoredEditorDraft(
    val content: String,
    val entryDate: String,
    val editingMemoId: String,
)

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
        "本地数据无法读取" -> R.string.error_local_data_corrupt
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

internal fun MemoListFilter.recordsPageQuery(cursor: String = ""): RecordsPageQuery {
    return RecordsPageQuery(scope = recordsQueryScope(), cursor = cursor)
}

internal fun MemoListFilter.recordsSearchQuery(text: String): RecordsSearchQuery {
    return RecordsSearchQuery(text = text, scope = recordsQueryScope())
}

private fun MemoListFilter.recordsQueryScope(): RecordsQueryScope {
    return when (this) {
        MemoListFilter.Unarchived -> RecordsQueryScope.Unarchived
        MemoListFilter.Archived -> RecordsQueryScope.Archived
        MemoListFilter.Favorited -> RecordsQueryScope.Favorited
        MemoListFilter.Deleted -> RecordsQueryScope.Deleted
    }
}
