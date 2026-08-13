package app.sillage.ui.application

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.sillage.core.application.ask.AskAnswerStreamEvent
import app.sillage.core.application.ask.AskClient
import app.sillage.core.application.ask.AskClientFactory
import app.sillage.core.application.ask.CreateAskConversationUseCase
import app.sillage.core.application.ask.ListAskConversationsUseCase
import app.sillage.core.application.ask.ListAskMessagesUseCase
import app.sillage.core.application.ask.SetAskHeadUseCase
import app.sillage.core.application.ask.StreamAskAnswerCommand
import app.sillage.core.application.ask.StreamAskAnswerUseCase
import app.sillage.core.application.auth.AuthenticationFailureException
import app.sillage.core.application.auth.AuthenticationFailureReason
import app.sillage.core.application.auth.ChangePasswordCommand
import app.sillage.core.application.auth.ChangePasswordUseCase
import app.sillage.core.application.auth.InitializeAccountCommand
import app.sillage.core.application.auth.InstanceAuthenticationRepository
import app.sillage.core.application.auth.InstanceAuthenticationRepositoryFactory
import app.sillage.core.application.auth.InstanceBootstrapRepository
import app.sillage.core.application.auth.SignInCommand
import app.sillage.core.application.auth.SignOutMode
import app.sillage.core.application.auth.SignOutResult
import app.sillage.core.application.auth.SignOutUseCase
import app.sillage.core.application.preferences.ClientPreferenceValues
import app.sillage.core.application.preferences.ClientPreferences
import app.sillage.core.application.preferences.ClientPreferencesRepository
import app.sillage.core.application.records.MutateRecordLifecycleUseCase
import app.sillage.core.application.records.RecordDraft
import app.sillage.core.application.records.RecordDraftValidationError
import app.sillage.core.application.records.RecordLifecycleCommand
import app.sillage.core.application.records.RecordLifecycleRepository
import app.sillage.core.application.records.RecordWriteRepository
import app.sillage.core.application.records.RecordsRepository
import app.sillage.core.application.records.SaveRecordCommand
import app.sillage.core.application.records.SaveRecordUseCase
import app.sillage.core.application.records.validateRecordDraft
import app.sillage.core.domain.ask.AskConversation
import app.sillage.core.domain.ask.AskMessage
import app.sillage.core.domain.ask.isActive
import app.sillage.core.domain.records.Memo
import app.sillage.core.sync.MemoSyncGatewayFactory
import app.sillage.core.sync.MemoSyncPullFailedException
import app.sillage.core.sync.MemoSyncServerMismatchException
import app.sillage.core.sync.MemoSyncWorkspace
import app.sillage.core.sync.MemoSyncWorkspaceFactory
import app.sillage.core.sync.ResolveMemoSyncConflictCommand
import app.sillage.core.sync.ResolveMemoSyncConflictUseCase
import app.sillage.core.sync.RunMemoTwoWaySyncUseCase
import app.sillage.core.sync.SyncPushSummary
import app.sillage.features.auth.InstanceAuthenticationContext
import app.sillage.features.auth.InstanceAuthenticationFailure
import app.sillage.features.auth.InstanceAuthenticationOperation
import app.sillage.features.auth.InstanceAuthenticationRequest
import app.sillage.features.auth.InstanceAuthenticationStateHolder
import app.sillage.features.auth.InstanceBootstrapContext
import app.sillage.features.auth.InstanceBootstrapStateHolder
import app.sillage.features.auth.PasswordChangeContext
import app.sillage.features.ask.AskMemoSaveContext
import app.sillage.features.ask.AskMemoSaveRequest
import app.sillage.features.ask.AskSourceNavigationContext
import app.sillage.features.ask.AskSourceNavigationRequest
import app.sillage.features.ask.AskStreamContext
import app.sillage.features.ask.AskStreamRequest
import app.sillage.features.ask.AskVariantContext
import app.sillage.features.ask.AskVariantRequest
import app.sillage.features.ask.askAnswerMemoContent
import app.sillage.features.ask.askBranchLeafId
import app.sillage.features.ask.buildAskActivePath
import app.sillage.features.ask.lastAssistantMessageId
import app.sillage.features.records.MemoListFilter
import app.sillage.features.records.RecordsBrowseStateHolder
import app.sillage.features.records.RecordsEditorStateHolder
import app.sillage.features.records.RecordsFeatureStateHolder
import app.sillage.features.records.RecordsSearchContext
import app.sillage.features.records.memosForFilter
import app.sillage.features.sync.MemoSyncConflictItem
import app.sillage.features.sync.SyncFeatureStateHolder
import app.sillage.ui.appshell.AppAppearanceStateHolder
import app.sillage.ui.appshell.AppClientContextStateHolder
import app.sillage.ui.appshell.AppDestination
import app.sillage.ui.appshell.AppWorkspaceStateHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

enum class SillageNativeFeedback {
    RecordSaved,
    RecordDeleted,
    RecordRestored,
    RecordPurged,
    BackupExported,
    BackupRestored,
    AccountInitialized,
    SignedIn,
    PasswordChanged,
    SignedOut,
    SignedOutLocally,
    MemoSyncCompleted,
    MemoSyncNoChanges,
    MemoSyncNeedsReview,
    MemoSyncRejected,
    MemoSyncFailed,
    MemoSyncServerMismatch,
    MemoSyncSessionExpired,
    MemoSyncConflictResolved,
    AskGenerationStopped,
    AskAnswerSaved,
    DataTransferFailed,
    StorageUnavailable,
}

enum class SillageNativeAskFailure {
    AuthenticationRequired,
    QuestionRequired,
    LoadFailed,
    SendFailed,
    VariantFailed,
    SourceUnavailable,
    SaveFailed,
}

data class SillageNativePlatform(
    val name: String,
    val dataLocation: String,
    val version: String,
    val thirdPartyNotices: String? = null,
    val authenticationPersistsAcrossLaunches: Boolean = false,
    val networkStatus: StateFlow<SillageNativeNetworkStatus>? = null,
    val openDataLocation: (() -> Boolean)? = null,
    val exportBackup: (suspend () -> Boolean)? = null,
    val restoreBackup: (suspend () -> Boolean)? = null,
)

data class SillageNativeState(
    val clientContext: AppClientContextStateHolder,
    val appearance: AppAppearanceStateHolder,
    val workspace: AppWorkspaceStateHolder,
    val serverConnection: InstanceBootstrapStateHolder,
    val authentication: InstanceAuthenticationStateHolder,
    val sync: SyncFeatureStateHolder = SyncFeatureStateHolder(),
    val memoSyncSupported: Boolean = false,
    val askSupported: Boolean = false,
    val busy: Boolean = false,
    val storageAvailable: Boolean = true,
    val feedback: SillageNativeFeedback? = null,
    val editorValidationError: RecordDraftValidationError? = null,
    val askFailure: SillageNativeAskFailure? = null,
) {
    val askAvailable: Boolean
        get() = askSupported &&
            serverConnection.checkedBaseUrl != null &&
            authentication.account != null
}

class SillageNativeController(
    private val recordsRepository: RecordsRepository,
    recordWriteRepository: RecordWriteRepository,
    recordLifecycleRepository: RecordLifecycleRepository,
    private val preferencesRepository: ClientPreferencesRepository,
    private val bootstrapRepository: InstanceBootstrapRepository,
    private val authenticationRepositoryFactory: InstanceAuthenticationRepositoryFactory,
    private val todayProvider: () -> String,
    private val memoSyncWorkspaceFactory: MemoSyncWorkspaceFactory? = null,
    private val memoSyncGatewayFactory: MemoSyncGatewayFactory? = null,
    private val askClientFactory: AskClientFactory? = null,
) {
    private val saveRecord = SaveRecordUseCase(recordWriteRepository)
    private val mutateRecordLifecycle = MutateRecordLifecycleUseCase(recordLifecycleRepository)
    private var allRecords: List<Memo> = emptyList()
    private var preferences = ClientPreferences()
    private var activeAuthenticationRepository: InstanceAuthenticationRepository? = null

    var state by mutableStateOf(
        initialState(
            today = todayProvider(),
            memoSyncSupported = memoSyncWorkspaceFactory != null && memoSyncGatewayFactory != null,
            askSupported = askClientFactory != null,
        ),
    )
        private set

    val hasUnsavedEditorChanges: Boolean
        get() = state.clientContext.screen == AppDestination.Editor &&
            state.workspace.records.editor.dirty

    init {
        hydrate()
    }

    fun navigateToRecords() {
        state = state.copy(
            clientContext = state.clientContext.navigateTo(AppDestination.Memos),
            workspace = state.workspace.afterLeavingAsk(state.clientContext.screen),
            editorValidationError = null,
            askFailure = null,
        )
    }

    fun navigateToSettings() {
        state = state.copy(
            clientContext = state.clientContext.navigateTo(AppDestination.AISettings),
            workspace = state.workspace.afterLeavingAsk(state.clientContext.screen),
            editorValidationError = null,
            askFailure = null,
        )
    }

    fun navigateToAsk() {
        if (!canUseAsk()) {
            state = state.copy(askFailure = SillageNativeAskFailure.AuthenticationRequired)
            return
        }
        val ask = state.workspace.ask
        state = state.copy(
            clientContext = state.clientContext.navigateTo(AppDestination.Ask),
            workspace = state.workspace.updateAsk {
                it.enterScreen(
                    requestInFlight = ask.loading ||
                        ask.sending ||
                        ask.variantLoading ||
                        ask.sourceLoading ||
                        ask.savingMessageId.isNotBlank(),
                )
            },
            editorValidationError = null,
            askFailure = null,
        )
    }

    suspend fun loadAskConversations() {
        val client = currentAskClient() ?: return
        val key = currentAskRequestKey() ?: return
        if (
            state.workspace.ask.loading ||
            state.workspace.ask.sending ||
            state.workspace.ask.variantLoading ||
            state.workspace.ask.savingMessageId.isNotBlank()
        ) {
            return
        }
        state = state.copy(
            workspace = state.workspace.updateAsk { it.beginConversationCatalogLoad() },
            askFailure = null,
        )
        try {
            val conversations = ListAskConversationsUseCase(client.repository)()
                .filter(AskConversation::isActive)
            if (matchesAskRequest(key)) {
                state = state.copy(
                    workspace = state.workspace.updateAsk {
                        it.completeConversationCatalog(conversations)
                    },
                )
            }
        } catch (error: CancellationException) {
            if (matchesAskRequest(key)) {
                state = state.copy(
                    workspace = state.workspace.updateAsk {
                        it.copy(load = it.load.cancel())
                    },
                )
            }
            throw error
        } catch (error: AuthenticationFailureException) {
            if (error.invalidatesAskSession()) {
                expireAskAuthentication(error.reason)
            } else {
                failAskLoad(key)
            }
        } catch (_: Exception) {
            failAskLoad(key)
        }
    }

    suspend fun selectAskConversation(conversationId: String) {
        val client = currentAskClient() ?: return
        currentAskRequestKey() ?: return
        val ask = state.workspace.ask
        if (
            conversationId.isBlank() ||
            ask.loading ||
            ask.sending ||
            ask.variantLoading ||
            ask.sourceLoading
        ) {
            return
        }
        val conversation = ask.conversations.find { it.id == conversationId }
        state = state.copy(
            workspace = state.workspace.updateAsk {
                it.beginConversationLoad(
                    conversationId = conversationId,
                    headMessageId = conversation?.headMessageId,
                    contextScope = conversation?.contextScope,
                )
            },
            askFailure = null,
        )
        val key = currentAskRequestKey() ?: return

        try {
            val messages = ListAskMessagesUseCase(client.repository)(conversationId)
            if (
                matchesAskRequest(key) &&
                state.workspace.ask.activeConversationId == conversationId
            ) {
                state = state.copy(
                    workspace = state.workspace.updateAsk {
                        it.completeConversationLoad(
                            conversationId = conversationId,
                            headMessageId = state.workspace.ask.headMessageId,
                            messages = messages,
                        )
                    },
                )
            }
        } catch (error: CancellationException) {
            if (matchesAskRequest(key)) {
                state = state.copy(
                    workspace = state.workspace.updateAsk {
                        it.copy(load = it.load.cancel())
                    },
                )
            }
            throw error
        } catch (error: AuthenticationFailureException) {
            if (error.invalidatesAskSession()) {
                expireAskAuthentication(error.reason)
            } else {
                failAskLoad(key)
            }
        } catch (_: Exception) {
            failAskLoad(key)
        }
    }

    suspend fun retryAskLoad() {
        val conversationId = state.workspace.ask.activeConversationId
        if (conversationId.isNotBlank() && state.workspace.ask.messages.isEmpty()) {
            selectAskConversation(conversationId)
        } else {
            loadAskConversations()
        }
    }

    fun startNewAskConversation() {
        val ask = state.workspace.ask
        if (ask.loading || ask.sending || ask.variantLoading || ask.sourceLoading) return
        state = state.copy(
            workspace = state.workspace.updateAsk { it.startNewConversation() },
            askFailure = null,
        )
    }

    fun updateAskQuestion(value: String) {
        state = state.copy(
            workspace = state.workspace.updateAsk { it.updateQuestion(value) },
            askFailure = null,
        )
    }

    fun updateAskContextScope(value: String) {
        if (value.isBlank() || !askContextControlsEnabled()) return
        state = state.copy(
            workspace = state.workspace.updateAsk { it.updateContextScope(value) },
            askFailure = null,
        )
    }

    fun updateAskSourceKind(value: String) {
        if (value.isBlank() || !askContextControlsEnabled()) return
        state = state.copy(
            workspace = state.workspace.updateAsk { it.updateSourceKind(value) },
            askFailure = null,
        )
    }

    fun dismissAskFailure() {
        state = state.copy(askFailure = null)
    }

    suspend fun sendAskQuestion() {
        val question = state.workspace.ask.question.trim()
        if (question.isBlank()) {
            state = state.copy(askFailure = SillageNativeAskFailure.QuestionRequired)
            return
        }
        streamAskAnswer(content = question, forkOfMessageId = null)
    }

    suspend fun regenerateAskAnswer(messageId: String) {
        val message = state.workspace.ask.messages.find { it.id == messageId }
        if (
            message == null ||
            message.role != "assistant" ||
            message.deletedAt != null ||
            message.conversationId != state.workspace.ask.activeConversationId
        ) {
            state = state.copy(askFailure = SillageNativeAskFailure.SendFailed)
            return
        }
        streamAskAnswer(content = "", forkOfMessageId = messageId)
    }

    fun stopAskStreaming() {
        if (!state.workspace.ask.sending) return
        state = state.copy(
            feedback = SillageNativeFeedback.AskGenerationStopped,
            askFailure = null,
        )
    }

    suspend fun selectAskVariant(messageId: String) {
        val client = currentAskClient() ?: return
        val ask = state.workspace.ask
        if (ask.messages.none { it.id == messageId && it.deletedAt == null }) {
            state = state.copy(askFailure = SillageNativeAskFailure.VariantFailed)
            return
        }
        val context = askVariantContext()
        val request = ask.variant.nextRequest(context) ?: return
        val pending = ask.variant.begin(request, context) ?: return
        val leafId = askBranchLeafId(ask.messages, messageId)
        val previousHeadMessageId = ask.headMessageId
        state = state.copy(
            workspace = state.workspace.updateAsk {
                it.applyVariantHead(
                    conversationId = request.conversationId,
                    headMessageId = leafId,
                    variant = pending,
                )
            },
            askFailure = null,
        )

        try {
            SetAskHeadUseCase(client.repository)(request.conversationId, leafId)
            finishAskVariant(request, leafId, failure = null)
        } catch (error: CancellationException) {
            finishAskVariant(
                request = request,
                headMessageId = previousHeadMessageId,
                failure = null,
            )
            throw error
        } catch (error: AuthenticationFailureException) {
            if (error.invalidatesAskSession()) {
                expireAskAuthentication(error.reason)
            } else {
                finishAskVariant(
                    request = request,
                    headMessageId = previousHeadMessageId,
                    failure = SillageNativeAskFailure.VariantFailed,
                )
            }
        } catch (_: Exception) {
            finishAskVariant(
                request = request,
                headMessageId = previousHeadMessageId,
                failure = SillageNativeAskFailure.VariantFailed,
            )
        }
    }

    fun openAskSource(memoId: String) {
        val context = askSourceNavigationContext()
        val request = state.workspace.ask.sourceNavigation.nextRequest(memoId, context) ?: return
        val pending = state.workspace.ask.sourceNavigation.begin(request, context) ?: return
        state = state.copy(
            workspace = state.workspace.updateAsk { it.beginSourceNavigation(pending) },
            askFailure = null,
        )

        val latestContext = askSourceNavigationContext()
        if (!state.workspace.ask.sourceNavigation.canApply(request, latestContext)) return
        val finishedAsk = state.workspace.ask.finishSourceNavigation(request) ?: return
        val memo = allRecords.find { it.id == memoId && it.purgedAt == null }
        if (memo == null) {
            state = state.copy(
                workspace = state.workspace.copy(ask = finishedAsk),
                askFailure = SillageNativeAskFailure.SourceUnavailable,
            )
            return
        }

        state = state.copy(
            clientContext = state.clientContext.navigateTo(AppDestination.MemoDetail),
            workspace = state.workspace.copy(
                ask = finishedAsk,
                records = state.workspace.records.absorbVisibleMemo(memo),
            ),
            askFailure = null,
        )
    }

    suspend fun saveAskAnswerAsRecord(message: AskMessage) {
        if (!state.storageAvailable) {
            state = state.copy(askFailure = SillageNativeAskFailure.SaveFailed)
            return
        }
        val memoContent = askAnswerMemoContent(message)
        val context = askMemoSaveContext()
        val request = state.workspace.ask.memoSave.nextRequest(message, memoContent, context) ?: return
        val pending = state.workspace.ask.memoSave.begin(request, context) ?: return
        state = state.copy(
            workspace = state.workspace.updateAsk { it.beginMemoSave(pending) },
            busy = true,
            feedback = null,
            askFailure = null,
        )

        try {
            val saved = saveRecord(
                SaveRecordCommand.Create(
                    RecordDraft(request.memoContent, todayProvider()),
                ),
            )
            allRecords = recordsRepository.listRecords()
            if (!canApplyAskMemoSave(request)) return
            val finishedAsk = state.workspace.ask.finishMemoSave(request) ?: return
            val records = state.workspace.records
                .presentSavedMemo(
                    memo = saved,
                    resetEditorEntryDate = todayProvider(),
                )
                .replaceVisibleRecords(
                    memosForFilter(allRecords, state.workspace.records.filter),
                )
            state = state.copy(
                clientContext = state.clientContext.navigateTo(AppDestination.MemoDetail),
                workspace = state.workspace.copy(
                    records = records,
                    ask = finishedAsk,
                ),
                feedback = SillageNativeFeedback.AskAnswerSaved,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            if (canApplyAskMemoSave(request)) {
                state = state.copy(askFailure = SillageNativeAskFailure.SaveFailed)
            }
        } finally {
            state = state.copy(
                workspace = state.workspace.updateAsk {
                    it.finishMemoSave(request) ?: it
                },
                busy = false,
            )
        }
    }

    fun navigateBackFromRecordDetail() {
        if (state.clientContext.history.lastOrNull() != AppDestination.Ask) {
            navigateToRecords()
            return
        }
        state = state.copy(
            clientContext = state.clientContext.back(AppDestination.Memos),
            workspace = state.workspace.updateRecords { it.clearPresentedMemo() },
            editorValidationError = null,
            askFailure = null,
        )
    }

    fun openRecord(memo: Memo) {
        state = state.copy(
            clientContext = state.clientContext.navigateTo(AppDestination.MemoDetail),
            workspace = state.workspace.updateRecords { it.presentMemoDetail(memo) },
            editorValidationError = null,
        )
    }

    fun startNewRecord() {
        val today = todayProvider()
        state = state.copy(
            clientContext = state.clientContext.navigateTo(AppDestination.Editor),
            workspace = state.workspace.updateRecords {
                it.beginNewEditorDraft(
                    draftContent = "",
                    draftEntryDate = today,
                    initialDraftEntryDate = today,
                )
            },
            editorValidationError = null,
        )
    }

    fun editSelectedRecord() {
        val memo = state.workspace.records.selection.selectedMemo ?: return
        state = state.copy(
            clientContext = state.clientContext.navigateTo(AppDestination.Editor),
            workspace = state.workspace.updateRecords {
                it.beginMemoEditor(
                    memo = memo,
                    draftContent = memo.content,
                    draftEntryDate = memo.entryDate,
                    initialDraftContent = memo.content,
                    initialDraftEntryDate = memo.entryDate,
                )
            },
            editorValidationError = null,
        )
    }

    fun closeEditor() {
        val selected = state.workspace.records.selection.selectedMemo
        state = state.copy(
            clientContext = state.clientContext.back(
                if (selected == null) AppDestination.Memos else AppDestination.MemoDetail,
            ),
            workspace = state.workspace.updateRecords {
                if (selected == null) it.clearPresentedMemo() else it.presentMemoDetail(selected)
            },
            editorValidationError = null,
        )
    }

    fun updateEditorContent(value: String) {
        state = state.copy(
            workspace = state.workspace.updateRecords { it.updateEditorContent(value) },
            editorValidationError = null,
        )
    }

    fun updateEditorEntryDate(value: String) {
        state = state.copy(
            workspace = state.workspace.updateRecords { it.updateEditorEntryDate(value) },
            editorValidationError = null,
        )
    }

    fun selectFilter(filter: MemoListFilter) {
        val records = state.workspace.records
            .applyListFilter(filter)
            .replaceVisibleRecords(memosForFilter(allRecords, filter))
            .copy(refresh = state.workspace.records.refresh.cancel())
        state = state.copy(
            clientContext = state.clientContext.navigateTo(AppDestination.Memos),
            workspace = state.workspace.copy(records = records),
            editorValidationError = null,
        )
    }

    fun updateSearchQuery(value: String) {
        val records = if (value.isBlank()) {
            state.workspace.records.clearSearch()
        } else {
            state.workspace.records.updateSearchQuery(value)
        }
        state = state.copy(workspace = state.workspace.copy(records = records))
    }

    fun searchRecords() {
        val records = state.workspace.records
        val context = records.searchContext(state.clientContext.generation)
        val request = records.search.nextRequest(context) ?: return
        val started = records.search.begin(request, context) ?: return
        val prepared = records.copy(search = started)
        state = state.copy(workspace = state.workspace.copy(records = prepared))

        val query = request.query
        val results = memosForFilter(allRecords, request.filter).filter { memo ->
            memo.content.contains(query, ignoreCase = true) || memo.entryDate.contains(query)
        }
        val completed = prepared.search.complete(request, context, results) ?: return
        state = state.copy(
            workspace = state.workspace.copy(records = prepared.copy(search = completed)),
        )
    }

    suspend fun saveEditor() {
        if (!canStartOperation()) return
        val records = state.workspace.records
        val draft = RecordDraft(records.editor.draftContent, records.editor.draftEntryDate)
        val validationError = validateRecordDraft(draft)
        if (validationError != null) {
            state = state.copy(
                editorValidationError = validationError,
            )
            return
        }

        val selected = records.selection.selectedMemo
        val command = if (selected == null) {
            SaveRecordCommand.Create(draft)
        } else {
            SaveRecordCommand.Update(selected, draft)
        }

        runStorageOperation {
            val saved = saveRecord(command)
            allRecords = recordsRepository.listRecords()
            val updatedRecords = state.workspace.records
                .presentSavedMemo(
                    memo = saved,
                    resetEditorEntryDate = todayProvider(),
                )
                .replaceVisibleRecords(
                    memosForFilter(allRecords, state.workspace.records.filter),
                )
            state = state.copy(
                clientContext = state.clientContext.navigateTo(AppDestination.MemoDetail),
                workspace = state.workspace.copy(records = updatedRecords),
                feedback = SillageNativeFeedback.RecordSaved,
                editorValidationError = null,
            )
        }
    }

    suspend fun toggleSelectedFavorite() {
        val memo = state.workspace.records.selection.selectedMemo ?: return
        mutateSelected(
            RecordLifecycleCommand.SetFavorited(memo, memo.favoritedAt == null),
            returnToList = false,
        )
    }

    suspend fun toggleSelectedArchive() {
        val memo = state.workspace.records.selection.selectedMemo ?: return
        mutateSelected(
            RecordLifecycleCommand.SetArchived(memo, memo.archivedAt == null),
            returnToList = false,
        )
    }

    suspend fun deleteSelectedRecord() {
        val memo = state.workspace.records.selection.selectedMemo ?: return
        mutateSelected(
            RecordLifecycleCommand.Delete(memo),
            feedback = SillageNativeFeedback.RecordDeleted,
            returnToList = true,
        )
    }

    suspend fun restoreRecord(memo: Memo) {
        mutateSelected(
            RecordLifecycleCommand.Restore(memo),
            feedback = SillageNativeFeedback.RecordRestored,
            returnToList = true,
        )
    }

    suspend fun purgeRecord(memo: Memo) {
        mutateSelected(
            RecordLifecycleCommand.Purge(memo),
            feedback = SillageNativeFeedback.RecordPurged,
            returnToList = true,
        )
    }

    fun setDarkTheme(enabled: Boolean) {
        val appearance = state.appearance.setTheme(
            if (enabled) ClientPreferenceValues.THEME_DARK else ClientPreferenceValues.THEME_LIGHT,
        )
        saveAppearance(appearance)
    }

    fun setLanguage(language: String) {
        saveAppearance(state.appearance.setLanguage(language))
    }

    fun updateServerBaseUrl(value: String) {
        if (state.authentication.account != null || state.authentication.loading) return
        activeAuthenticationRepository?.captureSession()?.clearLocalSession()
        activeAuthenticationRepository = null
        state = state.copy(
            serverConnection = state.serverConnection.updateBaseUrl(value),
            authentication = state.authentication.resetForServerChange(),
            workspace = state.workspace.updateAsk {
                it.clearWorkspace(invalidateStream = true, invalidateVariant = true)
            },
            sync = SyncFeatureStateHolder(),
            askFailure = null,
        )
    }

    suspend fun resumeSavedAuthentication() {
        if (state.serverConnection.baseUrl.isBlank()) return
        checkServerConnection()
    }

    suspend fun checkServerConnection() {
        val context = bootstrapContext()
        val request = state.serverConnection.nextRequest(context) ?: return
        val started = state.serverConnection.begin(request, context) ?: return
        state = state.copy(serverConnection = started)

        val bootstrap = try {
            bootstrapRepository.load(request.baseUrl)
        } catch (error: CancellationException) {
            state.serverConnection.cancel(request, bootstrapContext())?.let { cancelled ->
                state = state.copy(serverConnection = cancelled)
            }
            throw error
        } catch (_: Exception) {
            state.serverConnection.fail(request, bootstrapContext())?.let { failed ->
                state = state.copy(serverConnection = failed)
            }
            return
        }

        val completed = state.serverConnection.complete(
            request = request,
            context = bootstrapContext(),
            result = bootstrap,
        ) ?: return

        state = state.copy(serverConnection = completed)
        try {
            val updated = preferences.copy(serverBaseUrl = request.baseUrl)
            preferencesRepository.savePreferences(updated)
            preferences = updated
        } catch (_: Exception) {
            markStorageUnavailable()
        }
        restoreAuthentication()
    }

    fun updateAuthenticationUsername(value: String) {
        state = state.copy(authentication = state.authentication.updateUsername(value))
    }

    fun updateAuthenticationDisplayName(value: String) {
        state = state.copy(authentication = state.authentication.updateDisplayName(value))
    }

    fun updateAuthenticationPassword(value: String) {
        state = state.copy(authentication = state.authentication.updatePassword(value))
    }

    fun updateAuthenticationCurrentPassword(value: String) {
        state = state.copy(authentication = state.authentication.updateCurrentPassword(value))
    }

    fun updateAuthenticationNewPassword(value: String) {
        state = state.copy(authentication = state.authentication.updateNewPassword(value))
    }

    fun updateAuthenticationConfirmPassword(value: String) {
        state = state.copy(authentication = state.authentication.updateConfirmPassword(value))
    }

    private suspend fun restoreAuthentication() {
        val context = authenticationContext() ?: return
        val request = state.authentication.nextRestoreRequest(context) ?: return
        val started = state.authentication.begin(request, context) ?: return
        state = state.copy(authentication = started)
        val repository = authenticationRepositoryFactory.create(request.baseUrl)

        val session = try {
            repository.restore()
        } catch (error: CancellationException) {
            clearCancelledAuthenticationSession(repository, request, context)
            state.authentication.cancel(request, authenticationContextOr(context))?.let { cancelled ->
                state = state.copy(authentication = cancelled)
            }
            throw error
        } catch (error: AuthenticationFailureException) {
            state.authentication.fail(
                request = request,
                context = authenticationContextOr(context),
                failure = error.reason.toNativeFailure(),
            )?.let { failed ->
                state = state.copy(authentication = failed)
            }
            return
        } catch (_: Exception) {
            state.authentication.fail(
                request,
                authenticationContextOr(context),
                InstanceAuthenticationFailure.Connection,
            )?.let { failed ->
                state = state.copy(authentication = failed)
            }
            return
        }

        if (session == null) {
            state.authentication.completeRestoreWithoutSession(
                request,
                authenticationContextOr(context),
            )?.let { completed ->
                state = state.copy(authentication = completed)
            }
            return
        }

        val completed = state.authentication.completeAuthentication(
            request = request,
            context = authenticationContextOr(context),
            account = session.account,
        )
        if (completed == null) {
            repository.captureSession().clearLocalSession()
            return
        }
        activeAuthenticationRepository = repository
        state = state.copy(
            authentication = completed,
            sync = SyncFeatureStateHolder(),
        )
    }

    suspend fun authenticate() {
        val context = authenticationContext() ?: return
        val request = state.authentication.nextAuthenticationRequest(context)
        if (request == null) {
            state = state.copy(
                authentication = state.authentication.showValidationFailure(context),
            )
            return
        }
        val started = state.authentication.begin(request, context) ?: return
        state = state.copy(authentication = started)
        val repository = authenticationRepositoryFactory.create(request.baseUrl)
        val session = try {
            when (request.operation) {
                InstanceAuthenticationOperation.Initialize -> repository.initialize(
                    InitializeAccountCommand(
                        username = request.username,
                        displayName = request.displayName,
                        password = request.password,
                    ),
                )
                InstanceAuthenticationOperation.SignIn -> repository.signIn(
                    SignInCommand(
                        username = request.username,
                        password = request.password,
                    ),
                )
                InstanceAuthenticationOperation.Restore,
                InstanceAuthenticationOperation.SignOut,
                -> return
            }
        } catch (error: CancellationException) {
            clearCancelledAuthenticationSession(repository, request, context)
            state.authentication.cancel(request, authenticationContextOr(context))?.let { cancelled ->
                state = state.copy(authentication = cancelled)
            }
            throw error
        } catch (error: AuthenticationFailureException) {
            val currentContext = authenticationContextOr(context)
            val failed = state.authentication.fail(
                request = request,
                context = currentContext,
                failure = error.reason.toNativeFailure(),
            )
            if (failed != null) {
                state = state.copy(
                    authentication = failed,
                    serverConnection = if (
                        request.operation == InstanceAuthenticationOperation.Initialize &&
                        error.reason == AuthenticationFailureReason.AlreadyInitialized
                    ) {
                        state.serverConnection.copy(
                            bootstrap = state.serverConnection.bootstrap?.copy(initialized = true),
                        )
                    } else {
                        state.serverConnection
                    },
                )
            }
            return
        } catch (_: Exception) {
            state.authentication.fail(
                request,
                authenticationContextOr(context),
                InstanceAuthenticationFailure.Connection,
            )?.let { failed ->
                state = state.copy(authentication = failed)
            }
            return
        }

        val completed = state.authentication.completeAuthentication(
            request = request,
            context = authenticationContextOr(context),
            account = session.account,
        )
        if (completed == null) {
            repository.captureSession().clearLocalSession()
            return
        }
        activeAuthenticationRepository = repository
        state = state.copy(
            authentication = completed,
            sync = SyncFeatureStateHolder(),
            serverConnection = if (request.operation == InstanceAuthenticationOperation.Initialize) {
                state.serverConnection.copy(
                    bootstrap = state.serverConnection.bootstrap?.copy(initialized = true),
                )
            } else {
                state.serverConnection
            },
            feedback = if (request.operation == InstanceAuthenticationOperation.Initialize) {
                SillageNativeFeedback.AccountInitialized
            } else {
                SillageNativeFeedback.SignedIn
            },
        )
    }

    suspend fun changePassword() {
        if (!canStartOperation()) return
        val repository = activeAuthenticationRepository ?: return
        val context = passwordChangeContext()
        if (state.authentication.passwordChangeValidation() != null) {
            state = state.copy(
                authentication = state.authentication.showPasswordChangeValidationFailure(),
            )
            return
        }
        val request = state.authentication.nextPasswordChangeRequest(context) ?: return
        val started = state.authentication.beginPasswordChange(request, context) ?: return
        state = state.copy(
            authentication = started,
            busy = true,
            feedback = null,
        )

        try {
            val session = ChangePasswordUseCase(repository)(
                ChangePasswordCommand(request.currentPassword, request.newPassword),
            )
            val completed = state.authentication.completePasswordChange(
                request = request,
                context = passwordChangeContext(),
                account = session.account,
            ) ?: return
            state = state.copy(
                authentication = completed,
                feedback = SillageNativeFeedback.PasswordChanged,
            )
        } catch (error: CancellationException) {
            state.authentication.cancelPasswordChange(request, passwordChangeContext())?.let { cancelled ->
                state = state.copy(authentication = cancelled)
            }
            throw error
        } catch (error: AuthenticationFailureException) {
            if (
                error.reason == AuthenticationFailureReason.SessionExpired ||
                error.reason == AuthenticationFailureReason.SecureStorageUnavailable
            ) {
                if (state.authentication.canApplyPasswordChange(request, passwordChangeContext())) {
                    activeAuthenticationRepository = null
                    state = state.copy(
                        authentication = state.authentication.resetForServerChange().copy(
                            failure = error.reason.toNativeFailure(),
                        ),
                        workspace = state.workspace.updateAsk {
                            it.clearWorkspace(invalidateStream = true, invalidateVariant = true)
                        },
                        sync = SyncFeatureStateHolder(),
                        askFailure = SillageNativeAskFailure.AuthenticationRequired,
                    )
                }
            } else {
                state.authentication.failPasswordChange(
                    request = request,
                    context = passwordChangeContext(),
                    failure = error.reason.toNativeFailure(),
                )?.let { failed ->
                    state = state.copy(authentication = failed)
                }
            }
        } catch (_: Exception) {
            state.authentication.failPasswordChange(
                request = request,
                context = passwordChangeContext(),
                failure = InstanceAuthenticationFailure.Connection,
            )?.let { failed ->
                state = state.copy(authentication = failed)
            }
        } finally {
            state = state.copy(busy = false)
        }
    }

    suspend fun signOut() {
        val context = authenticationContext() ?: return
        val repository = activeAuthenticationRepository ?: return
        val request = state.authentication.nextSignOutRequest(context) ?: return
        val started = state.authentication.begin(request, context) ?: return
        state = state.copy(authentication = started)
        val result = try {
            SignOutUseCase(repository).prepare(SignOutMode.Online)()
        } catch (error: CancellationException) {
            state.authentication.completeSignOut(
                request,
                authenticationContextOr(context),
            )?.let { completed ->
                state = state.copy(
                    authentication = completed,
                    workspace = state.workspace.updateAsk {
                        it.clearWorkspace(invalidateStream = true, invalidateVariant = true)
                    },
                    askFailure = null,
                )
                activeAuthenticationRepository = null
            }
            throw error
        } catch (error: AuthenticationFailureException) {
            state.authentication.fail(
                request = request,
                context = authenticationContextOr(context),
                failure = error.reason.toNativeFailure(),
            )?.let { failed ->
                state = state.copy(authentication = failed)
            }
            return
        } ?: return

        val completed = state.authentication.completeSignOut(
            request,
            authenticationContextOr(context),
        ) ?: return
        activeAuthenticationRepository = null
        state = state.copy(
            authentication = completed,
            workspace = state.workspace.updateAsk {
                it.clearWorkspace(invalidateStream = true, invalidateVariant = true)
            },
            sync = SyncFeatureStateHolder(),
            askFailure = null,
            feedback = when (result) {
                SignOutResult.SignedOut,
                SignOutResult.OfflineSessionCleared,
                -> SillageNativeFeedback.SignedOut
                SignOutResult.RemoteFailedLocalSessionCleared -> {
                    SillageNativeFeedback.SignedOutLocally
                }
            },
        )
    }

    suspend fun syncMemos() {
        syncMemos(reportSuccessfulCompletion = true)
    }

    internal suspend fun syncMemosAutomatically() {
        syncMemos(reportSuccessfulCompletion = false)
    }

    private suspend fun syncMemos(reportSuccessfulCompletion: Boolean) {
        if (!canStartOperation()) return
        val baseUrl = state.serverConnection.checkedBaseUrl ?: return
        if (state.authentication.account == null || activeAuthenticationRepository == null) return
        val workspaceFactory = memoSyncWorkspaceFactory ?: return
        val gatewayFactory = memoSyncGatewayFactory ?: return

        state = state.copy(
            busy = true,
            feedback = if (reportSuccessfulCompletion) null else state.feedback,
        )
        try {
            val workspace = workspaceFactory.createMemoSyncWorkspace(baseUrl)
            try {
                val result = RunMemoTwoWaySyncUseCase(
                    workspace = workspace,
                    gateway = gatewayFactory.createMemoSyncGateway(baseUrl),
                )()
                presentMemoSyncResult(
                    workspace = workspace,
                    summary = result.push,
                    pulledMemos = result.pulledMemos,
                    reportSuccessfulCompletion = reportSuccessfulCompletion,
                )
            } catch (error: MemoSyncPullFailedException) {
                presentMemoSyncResult(
                    workspace = workspace,
                    summary = error.push,
                    pulledMemos = 0,
                    reportSuccessfulCompletion = reportSuccessfulCompletion,
                )
                handleMemoSyncFailure(error.pullFailure)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            handleMemoSyncFailure(error)
        } finally {
            state = state.copy(busy = false)
        }
    }

    private fun presentMemoSyncResult(
        workspace: MemoSyncWorkspace,
        summary: SyncPushSummary,
        pulledMemos: Int,
        reportSuccessfulCompletion: Boolean,
    ) {
        allRecords = recordsRepository.listRecords()
        val conflicts = summary.conflictMemoSyncs.map { conflict ->
            MemoSyncConflictItem(
                conflict = conflict,
                localMemo = workspace.localMemo(conflict.resourceId),
            )
        }
        state = state.copy(
            workspace = state.workspace.copy(records = refreshedRecordState()),
            sync = state.sync.applyPushConflicts(conflicts),
            feedback = when {
                summary.conflict > 0 -> SillageNativeFeedback.MemoSyncNeedsReview
                summary.rejected > 0 -> SillageNativeFeedback.MemoSyncRejected
                !reportSuccessfulCompletion -> state.feedback
                summary.applied > 0 || pulledMemos > 0 -> SillageNativeFeedback.MemoSyncCompleted
                else -> SillageNativeFeedback.MemoSyncNoChanges
            },
        )
    }

    suspend fun keepLocalSyncConflict(resourceId: String) {
        resolveMemoSyncConflict(resourceId, keepLocal = true)
    }

    suspend fun takeServerSyncConflict(resourceId: String) {
        resolveMemoSyncConflict(resourceId, keepLocal = false)
    }

    fun dismissSyncConflict(resourceId: String) {
        state = state.copy(sync = state.sync.removeConflict(resourceId))
    }

    fun dismissFeedback() {
        state = state.copy(feedback = null)
    }

    suspend fun exportBackup(operation: suspend () -> Boolean) {
        runDataTransfer(
            operation = operation,
            successFeedback = SillageNativeFeedback.BackupExported,
        )
    }

    suspend fun restoreBackup(operation: suspend () -> Boolean) {
        runDataTransfer(
            operation = operation,
            successFeedback = SillageNativeFeedback.BackupRestored,
            requiresReadableStorage = false,
            onSuccess = ::rehydrateAfterBackupRestore,
        )
    }

    private suspend fun mutateSelected(
        command: RecordLifecycleCommand,
        feedback: SillageNativeFeedback? = null,
        returnToList: Boolean,
    ) {
        if (!canStartOperation()) return
        runStorageOperation {
            val updated = mutateRecordLifecycle(command)
            allRecords = recordsRepository.listRecords()
            val visible = memosForFilter(allRecords, state.workspace.records.filter)
            val nextRecords = if (returnToList) {
                state.workspace.records
                    .forgetMemoIfSelected(updated.id)
                    .replaceVisibleRecords(visible)
            } else {
                state.workspace.records
                    .applyCanonicalMemo(updated)
                    .replaceVisibleRecords(visible)
                    .presentMemoDetail(updated)
            }
            state = state.copy(
                clientContext = if (returnToList) {
                    state.clientContext.navigateTo(AppDestination.Memos)
                } else {
                    state.clientContext
                },
                workspace = state.workspace.copy(records = nextRecords),
                feedback = feedback,
            )
        }
    }

    private suspend fun resolveMemoSyncConflict(resourceId: String, keepLocal: Boolean) {
        if (!canStartOperation()) return
        val item = state.sync.findConflict(resourceId) ?: return
        val baseUrl = state.serverConnection.checkedBaseUrl ?: return
        if (state.authentication.account == null || activeAuthenticationRepository == null) return
        val workspaceFactory = memoSyncWorkspaceFactory ?: return

        state = state.copy(busy = true, feedback = null)
        try {
            val workspace = workspaceFactory.createMemoSyncWorkspace(baseUrl)
            val resolve = ResolveMemoSyncConflictUseCase(workspace)
            resolve(
                if (keepLocal) {
                    ResolveMemoSyncConflictCommand.KeepLocal(item.conflict)
                } else {
                    ResolveMemoSyncConflictCommand.TakeServer(item.conflict)
                },
            )
            allRecords = recordsRepository.listRecords()
            state = state.copy(
                workspace = state.workspace.copy(records = refreshedRecordState()),
                sync = state.sync.removeConflict(resourceId),
                feedback = SillageNativeFeedback.MemoSyncConflictResolved,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            handleMemoSyncFailure(error)
        } finally {
            state = state.copy(busy = false)
        }
    }

    private fun refreshedRecordState(): RecordsFeatureStateHolder {
        var records = state.workspace.records.replaceVisibleRecords(
            memosForFilter(allRecords, state.workspace.records.filter),
        )
        val selectedId = state.workspace.records.selection.selectedMemo?.id
        val selected = selectedId?.let { id -> allRecords.firstOrNull { it.id == id } }
        if (selected != null) {
            records = records.applyCanonicalMemo(selected)
        }
        return records
    }

    private fun handleMemoSyncFailure(error: Exception) {
        when {
            error is MemoSyncServerMismatchException -> {
                state = state.copy(feedback = SillageNativeFeedback.MemoSyncServerMismatch)
            }
            error is AuthenticationFailureException &&
                error.reason == AuthenticationFailureReason.SessionExpired -> {
                expireAskAuthentication(error.reason)
                state = state.copy(feedback = SillageNativeFeedback.MemoSyncSessionExpired)
            }
            else -> state = state.copy(feedback = SillageNativeFeedback.MemoSyncFailed)
        }
    }

    private fun saveAppearance(appearance: AppAppearanceStateHolder) {
        if (!state.storageAvailable) return
        try {
            val updated = preferences.copy(
                themeMode = appearance.themeMode,
                languageMode = appearance.languageMode,
            )
            preferencesRepository.savePreferences(updated)
            preferences = updated
            state = state.copy(appearance = appearance)
        } catch (_: Exception) {
            markStorageUnavailable()
        }
    }

    private suspend fun runStorageOperation(operation: suspend () -> Unit) {
        state = state.copy(busy = true, feedback = null)
        try {
            operation()
        } catch (_: Exception) {
            markStorageUnavailable()
        } finally {
            state = state.copy(busy = false)
        }
    }

    private suspend fun runDataTransfer(
        operation: suspend () -> Boolean,
        successFeedback: SillageNativeFeedback,
        requiresReadableStorage: Boolean = true,
        onSuccess: () -> Unit = {},
    ) {
        if (state.busy || (requiresReadableStorage && !state.storageAvailable)) return
        state = state.copy(
            busy = true,
            feedback = if (state.storageAvailable) {
                null
            } else {
                SillageNativeFeedback.StorageUnavailable
            },
        )
        try {
            if (operation()) {
                onSuccess()
                if (state.storageAvailable) {
                    state = state.copy(feedback = successFeedback)
                }
            }
        } catch (_: Exception) {
            state = state.copy(feedback = SillageNativeFeedback.DataTransferFailed)
        } finally {
            state = state.copy(busy = false)
        }
    }

    private suspend fun streamAskAnswer(
        content: String,
        forkOfMessageId: String?,
    ) {
        val client = currentAskClient() ?: return
        val initialContext = askStreamContext()
        var request = state.workspace.ask.stream.nextRequest(initialContext) ?: return
        val pending = state.workspace.ask.stream.begin(
            request = request,
            context = initialContext,
            regeneratingMessageId = forkOfMessageId.orEmpty(),
        ) ?: return
        val contextScope = state.workspace.ask.contextScope
        val sourceKind = state.workspace.ask.sourceKind
        val previousHeadMessageId = state.workspace.ask.headMessageId
        state = state.copy(
            workspace = state.workspace.updateAsk { it.beginStream(pending) },
            askFailure = null,
        )
        if (!canApplyAskStream(request)) return

        var conversationId = request.conversationId
        var answerCompleted = false
        var requestFailed = false
        var cancelled = false
        try {
            if (conversationId.isBlank()) {
                val created = CreateAskConversationUseCase(client.repository)(contextScope)
                if (!canApplyAskStream(request)) return
                state = state.copy(
                    workspace = state.workspace.updateAsk { it.activateConversation(created) },
                )
                conversationId = created.id
                request = request.copy(conversationId = conversationId)
                if (!canApplyAskStream(request)) return
            }

            StreamAskAnswerUseCase(client.answerStreamer)(
                command = StreamAskAnswerCommand(
                    conversationId = conversationId,
                    content = content,
                    contextScope = contextScope,
                    sourceKind = sourceKind,
                    forkOfMessageId = forkOfMessageId,
                ),
            ) { event ->
                if (canApplyAskStream(request)) {
                    when (event) {
                        is AskAnswerStreamEvent.Started -> {
                            state = state.copy(
                                workspace = state.workspace.updateAsk {
                                    it.startStreaming(
                                        if (event.regenerating) null else event.userMessage,
                                    )
                                },
                            )
                        }
                        is AskAnswerStreamEvent.Delta -> {
                            state = state.copy(
                                workspace = state.workspace.updateAsk {
                                    it.appendStreamDelta(event.text)
                                },
                            )
                        }
                        is AskAnswerStreamEvent.Failed -> {
                            requestFailed = true
                            state = state.copy(askFailure = SillageNativeAskFailure.SendFailed)
                        }
                    }
                }
            }
        } catch (error: CancellationException) {
            cancelled = true
            throw error
        } catch (error: AuthenticationFailureException) {
            requestFailed = true
            if (error.invalidatesAskSession()) {
                expireAskAuthentication(error.reason)
            } else if (canApplyAskStream(request)) {
                state = state.copy(askFailure = SillageNativeAskFailure.SendFailed)
            }
        } catch (_: Exception) {
            requestFailed = true
            if (canApplyAskStream(request)) {
                state = state.copy(askFailure = SillageNativeAskFailure.SendFailed)
            }
        } finally {
            withContext(NonCancellable) {
                if (conversationId.isNotBlank() && canApplyAskStream(request)) {
                    try {
                        val snapshot = reloadAskSnapshot(client, conversationId)
                        if (canApplyAskStream(request)) {
                            answerCompleted = hasNewCompletedAskAnswer(
                                messages = snapshot.messages,
                                headMessageId = snapshot.headMessageId,
                                previousHeadMessageId = previousHeadMessageId,
                            )
                            state = state.copy(
                                workspace = state.workspace.updateAsk {
                                    it.replaceActiveSnapshot(
                                        conversationId = conversationId,
                                        conversations = snapshot.conversations,
                                        headMessageId = snapshot.headMessageId,
                                        messages = snapshot.messages,
                                    )
                                },
                            )
                        }
                    } catch (error: AuthenticationFailureException) {
                        if (error.invalidatesAskSession()) {
                            requestFailed = true
                            expireAskAuthentication(error.reason)
                        } else if (!cancelled && canApplyAskStream(request)) {
                            requestFailed = true
                            state = state.copy(askFailure = SillageNativeAskFailure.SendFailed)
                        }
                    } catch (_: Exception) {
                        if (!cancelled && canApplyAskStream(request)) {
                            requestFailed = true
                            state = state.copy(askFailure = SillageNativeAskFailure.SendFailed)
                        }
                    }
                }
                if (canApplyAskStream(request)) {
                    state = state.copy(
                        workspace = state.workspace.updateAsk {
                            it.finishStream(
                                answerCompleted = answerCompleted && !requestFailed,
                                clearQuestion = forkOfMessageId == null &&
                                    !requestFailed &&
                                    (!cancelled || answerCompleted),
                            )
                        },
                    )
                }
            }
        }
    }

    private suspend fun reloadAskSnapshot(
        client: AskClient,
        conversationId: String,
    ): NativeAskSnapshot {
        val messages = ListAskMessagesUseCase(client.repository)(conversationId)
        val conversations = ListAskConversationsUseCase(client.repository)()
            .filter(AskConversation::isActive)
        val headMessageId = conversations.find { it.id == conversationId }?.headMessageId
            ?: lastAssistantMessageId(buildAskActivePath(messages, headId = null))
        return NativeAskSnapshot(
            conversations = conversations,
            messages = messages,
            headMessageId = headMessageId,
        )
    }

    private fun finishAskVariant(
        request: AskVariantRequest,
        headMessageId: String?,
        failure: SillageNativeAskFailure?,
    ) {
        val context = askVariantContext()
        val variant = state.workspace.ask.variant.finish(request, context) ?: return
        state = state.copy(
            workspace = state.workspace.updateAsk {
                it.applyVariantHead(
                    conversationId = request.conversationId,
                    headMessageId = headMessageId,
                    variant = variant,
                )
            },
            askFailure = failure,
        )
    }

    private fun currentAskClient(): AskClient? {
        if (!canUseAsk()) {
            if (state.clientContext.screen == AppDestination.Ask) {
                state = state.copy(askFailure = SillageNativeAskFailure.AuthenticationRequired)
            }
            return null
        }
        val baseUrl = state.serverConnection.checkedBaseUrl ?: return null
        return askClientFactory?.createAskClient(baseUrl)
    }

    private fun canUseAsk(): Boolean =
        state.askAvailable && activeAuthenticationRepository != null

    private fun currentAskRequestKey(): NativeAskRequestKey? {
        if (state.clientContext.screen != AppDestination.Ask || !canUseAsk()) return null
        return NativeAskRequestKey(
            screenSessionId = state.workspace.ask.screenSessionId,
            clientContextGeneration = state.clientContext.generation,
            baseUrl = state.serverConnection.checkedBaseUrl ?: return null,
            accountId = state.authentication.account?.id ?: return null,
        )
    }

    private fun matchesAskRequest(key: NativeAskRequestKey): Boolean {
        return state.clientContext.screen == AppDestination.Ask &&
            state.workspace.ask.screenSessionId == key.screenSessionId &&
            state.clientContext.generation == key.clientContextGeneration &&
            state.serverConnection.checkedBaseUrl == key.baseUrl &&
            state.authentication.account?.id == key.accountId &&
            canUseAsk()
    }

    private fun failAskLoad(key: NativeAskRequestKey) {
        if (!matchesAskRequest(key)) return
        state = state.copy(
            workspace = state.workspace.updateAsk {
                if (it.activeConversationId.isBlank()) {
                    it.failConversationCatalogLoad(AskLoadFailureMarker)
                } else {
                    it.failConversationLoad(AskLoadFailureMarker)
                }
            },
            askFailure = SillageNativeAskFailure.LoadFailed,
        )
    }

    private fun askContextControlsEnabled(): Boolean {
        val ask = state.workspace.ask
        return state.clientContext.screen == AppDestination.Ask &&
            canUseAsk() &&
            !state.busy &&
            !ask.loading &&
            !ask.sending &&
            !ask.variantLoading &&
            !ask.sourceLoading &&
            ask.savingMessageId.isBlank()
    }

    private fun askStreamContext(): AskStreamContext {
        val ask = state.workspace.ask
        return AskStreamContext(
            screenSessionId = ask.screenSessionId,
            conversationId = ask.activeConversationId,
            appMode = state.clientContext.appMode,
            clientContextGeneration = state.clientContext.generation,
            anotherRequestInProgress = state.clientContext.screen != AppDestination.Ask ||
                !canUseAsk() ||
                state.busy ||
                state.authentication.loading ||
                ask.loading ||
                ask.variantLoading ||
                ask.sourceLoading ||
                ask.savingMessageId.isNotBlank(),
        )
    }

    private fun canApplyAskStream(request: AskStreamRequest): Boolean {
        return state.workspace.ask.stream.canApply(request, askStreamContext())
    }

    private fun askVariantContext(): AskVariantContext {
        val ask = state.workspace.ask
        return AskVariantContext(
            destinationAvailable = state.clientContext.screen == AppDestination.Ask && canUseAsk(),
            screenSessionId = ask.screenSessionId,
            conversationId = ask.activeConversationId,
            appMode = state.clientContext.appMode,
            clientContextGeneration = state.clientContext.generation,
            anotherRequestInProgress = state.busy ||
                state.authentication.loading ||
                ask.loading ||
                ask.sending ||
                ask.sourceLoading ||
                ask.savingMessageId.isNotBlank(),
        )
    }

    private fun askMemoSaveContext(): AskMemoSaveContext {
        val ask = state.workspace.ask
        return AskMemoSaveContext(
            destinationAvailable = state.clientContext.screen == AppDestination.Ask,
            anotherRequestInProgress = !canUseAsk() ||
                state.busy ||
                state.authentication.loading ||
                ask.loading ||
                ask.sending ||
                ask.variantLoading ||
                ask.sourceLoading,
            screenSessionId = ask.screenSessionId,
            conversationId = ask.activeConversationId,
            headMessageId = ask.headMessageId,
            messages = ask.messages,
            appMode = state.clientContext.appMode,
            clientContextGeneration = state.clientContext.generation,
        )
    }

    private fun canApplyAskMemoSave(request: AskMemoSaveRequest): Boolean {
        return state.workspace.ask.memoSave.canApply(request, askMemoSaveContext())
    }

    private fun askSourceNavigationContext(): AskSourceNavigationContext {
        val ask = state.workspace.ask
        return AskSourceNavigationContext(
            destinationKey = state.clientContext.screen.name,
            destinationAvailable = state.clientContext.screen == AppDestination.Ask && canUseAsk(),
            historyKeys = state.clientContext.history.map(AppDestination::name),
            anotherRequestInProgress = state.busy ||
                state.authentication.loading ||
                ask.loading ||
                ask.sending ||
                ask.variantLoading ||
                ask.savingMessageId.isNotBlank(),
            screenSessionId = ask.screenSessionId,
            conversationId = ask.activeConversationId,
            appMode = state.clientContext.appMode,
            clientContextGeneration = state.clientContext.generation,
        )
    }

    private fun expireAskAuthentication(reason: AuthenticationFailureReason) {
        activeAuthenticationRepository = null
        state = state.copy(
            clientContext = if (state.clientContext.screen == AppDestination.Ask) {
                state.clientContext.showRoot(AppDestination.Memos)
            } else {
                state.clientContext
            },
            workspace = state.workspace.updateAsk {
                it.clearWorkspace(invalidateStream = true, invalidateVariant = true)
            },
            authentication = state.authentication.resetForServerChange().copy(
                failure = reason.toNativeFailure(),
            ),
            sync = SyncFeatureStateHolder(),
            askFailure = SillageNativeAskFailure.AuthenticationRequired,
        )
    }

    private fun hydrate() {
        activeAuthenticationRepository?.captureSession()?.clearLocalSession()
        activeAuthenticationRepository = null
        try {
            preferences = preferencesRepository.loadPreferences()
            allRecords = recordsRepository.listRecords()
            val records = state.workspace.records.replaceVisibleRecords(
                memosForFilter(allRecords, state.workspace.records.filter),
            )
            state = state.copy(
                appearance = AppAppearanceStateHolder.hydrate(
                    themeMode = preferences.themeMode,
                    languageMode = preferences.languageMode,
                ),
                workspace = state.workspace.copy(records = records),
                serverConnection = InstanceBootstrapStateHolder(
                    baseUrl = preferences.serverBaseUrl,
                ),
                authentication = InstanceAuthenticationStateHolder(),
                sync = SyncFeatureStateHolder(),
            )
        } catch (_: Exception) {
            markStorageUnavailable()
        }
    }

    private fun rehydrateAfterBackupRestore() {
        state = initialState(
            today = todayProvider(),
            memoSyncSupported = memoSyncWorkspaceFactory != null && memoSyncGatewayFactory != null,
            askSupported = askClientFactory != null,
        ).copy(busy = true)
        hydrate()
    }

    private fun markStorageUnavailable() {
        state = state.copy(
            storageAvailable = false,
            feedback = SillageNativeFeedback.StorageUnavailable,
        )
    }

    private fun canStartOperation(): Boolean =
        state.storageAvailable && !state.busy && !state.authentication.form.passwordChanging

    private fun bootstrapContext(): InstanceBootstrapContext {
        return InstanceBootstrapContext(
            clientContextGeneration = state.clientContext.generation,
        )
    }

    private fun authenticationContext(): InstanceAuthenticationContext? {
        val baseUrl = state.serverConnection.checkedBaseUrl ?: return null
        val bootstrap = state.serverConnection.bootstrap ?: return null
        return InstanceAuthenticationContext(
            baseUrl = baseUrl,
            initialized = bootstrap.initialized,
            clientContextGeneration = state.clientContext.generation,
        )
    }

    private fun authenticationContextOr(
        fallback: InstanceAuthenticationContext,
    ): InstanceAuthenticationContext {
        return authenticationContext() ?: fallback
    }

    private fun passwordChangeContext(): PasswordChangeContext {
        return PasswordChangeContext(
            appMode = state.clientContext.appMode,
            clientContextGeneration = state.clientContext.generation,
            online = state.serverConnection.checkedBaseUrl != null &&
                state.authentication.account != null &&
                activeAuthenticationRepository != null,
            anotherOperationInProgress = state.busy || state.authentication.loading,
        )
    }

    private fun clearCancelledAuthenticationSession(
        repository: InstanceAuthenticationRepository,
        request: InstanceAuthenticationRequest,
        context: InstanceAuthenticationContext,
    ) {
        try {
            repository.captureSession().clearLocalSession()
        } catch (error: AuthenticationFailureException) {
            state.authentication.fail(
                request = request,
                context = authenticationContextOr(context),
                failure = error.reason.toNativeFailure(),
            )?.let { failed ->
                state = state.copy(authentication = failed)
            }
            throw error
        } catch (error: Exception) {
            state.authentication.fail(
                request,
                authenticationContextOr(context),
                InstanceAuthenticationFailure.Connection,
            )?.let { failed ->
                state = state.copy(authentication = failed)
            }
            throw error
        }
    }
}

private data class NativeAskRequestKey(
    val screenSessionId: Long,
    val clientContextGeneration: Long,
    val baseUrl: String,
    val accountId: String,
)

private data class NativeAskSnapshot(
    val conversations: List<AskConversation>,
    val messages: List<AskMessage>,
    val headMessageId: String?,
)

private fun initialState(
    today: String,
    memoSyncSupported: Boolean,
    askSupported: Boolean,
): SillageNativeState {
    val year = today.take(4).toIntOrNull() ?: 1970
    val month = today.drop(5).take(2).toIntOrNull()?.takeIf { it in 1..12 } ?: 1
    val records = RecordsFeatureStateHolder(
        editor = RecordsEditorStateHolder(
            draftEntryDate = today,
            initialDraftEntryDate = today,
        ),
        browse = RecordsBrowseStateHolder(calendarYear = year, calendarMonth = month),
    )
    return SillageNativeState(
        clientContext = AppClientContextStateHolder(
            screen = AppDestination.Memos,
            appMode = ClientPreferenceValues.MODE_OFFLINE,
        ),
        appearance = AppAppearanceStateHolder(),
        workspace = AppWorkspaceStateHolder(records = records),
        serverConnection = InstanceBootstrapStateHolder(),
        authentication = InstanceAuthenticationStateHolder(),
        memoSyncSupported = memoSyncSupported,
        askSupported = askSupported,
    )
}

private fun AppWorkspaceStateHolder.afterLeavingAsk(
    previousScreen: AppDestination,
): AppWorkspaceStateHolder {
    if (previousScreen != AppDestination.Ask) return this
    return updateAsk { ask ->
        ask.copy(
            load = ask.load.cancel(),
            sourceNavigation = ask.sourceNavigation.invalidate(),
            memoSave = ask.memoSave.invalidate(),
        ).invalidateStream()
            .invalidateVariant()
            .advanceSession()
    }
}

private fun AuthenticationFailureException.invalidatesAskSession(): Boolean {
    return reason == AuthenticationFailureReason.SessionExpired ||
        reason == AuthenticationFailureReason.SecureStorageUnavailable
}

private fun hasNewCompletedAskAnswer(
    messages: List<AskMessage>,
    headMessageId: String?,
    previousHeadMessageId: String?,
): Boolean {
    return headMessageId != null &&
        headMessageId != previousHeadMessageId &&
        messages.any { message ->
            message.id == headMessageId &&
                message.role == "assistant" &&
                message.status == "complete" &&
                message.deletedAt == null &&
                message.content.isNotBlank()
        }
}

private fun AuthenticationFailureReason.toNativeFailure(): InstanceAuthenticationFailure {
    return when (this) {
        AuthenticationFailureReason.InvalidRequest -> InstanceAuthenticationFailure.InvalidRequest
        AuthenticationFailureReason.InvalidCredentials -> {
            InstanceAuthenticationFailure.InvalidCredentials
        }
        AuthenticationFailureReason.AlreadyInitialized -> {
            InstanceAuthenticationFailure.AlreadyInitialized
        }
        AuthenticationFailureReason.RateLimited -> InstanceAuthenticationFailure.RateLimited
        AuthenticationFailureReason.SessionExpired -> InstanceAuthenticationFailure.SessionExpired
        AuthenticationFailureReason.ServerRejected -> InstanceAuthenticationFailure.ServerRejected
        AuthenticationFailureReason.InvalidResponse -> InstanceAuthenticationFailure.InvalidResponse
        AuthenticationFailureReason.SecureStorageUnavailable -> {
            InstanceAuthenticationFailure.SecureStorageUnavailable
        }
    }
}

private fun RecordsFeatureStateHolder.searchContext(generation: Long) = RecordsSearchContext(
    sourceKey = ClientPreferenceValues.MODE_OFFLINE,
    clientContextGeneration = generation,
    filter = filter,
    cacheGeneration = cacheGeneration,
)

private const val AskLoadFailureMarker = "ask_load_failed"
