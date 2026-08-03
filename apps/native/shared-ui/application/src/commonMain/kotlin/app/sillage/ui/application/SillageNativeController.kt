package app.sillage.ui.application

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.sillage.core.application.auth.AuthenticationFailureException
import app.sillage.core.application.auth.AuthenticationFailureReason
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
import app.sillage.core.domain.records.Memo
import app.sillage.core.sync.MemoSyncGatewayFactory
import app.sillage.core.sync.MemoSyncServerMismatchException
import app.sillage.core.sync.MemoSyncWorkspaceFactory
import app.sillage.core.sync.PushPendingMemosUseCase
import app.sillage.core.sync.ResolveMemoSyncConflictCommand
import app.sillage.core.sync.ResolveMemoSyncConflictUseCase
import app.sillage.features.auth.InstanceAuthenticationContext
import app.sillage.features.auth.InstanceAuthenticationFailure
import app.sillage.features.auth.InstanceAuthenticationOperation
import app.sillage.features.auth.InstanceAuthenticationRequest
import app.sillage.features.auth.InstanceAuthenticationStateHolder
import app.sillage.features.auth.InstanceBootstrapContext
import app.sillage.features.auth.InstanceBootstrapStateHolder
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

enum class SillageNativeFeedback {
    RecordSaved,
    RecordDeleted,
    RecordRestored,
    RecordPurged,
    BackupExported,
    BackupRestored,
    AccountInitialized,
    SignedIn,
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
    DataTransferFailed,
    StorageUnavailable,
}

data class SillageNativePlatform(
    val name: String,
    val dataLocation: String,
    val version: String,
    val authenticationPersistsAcrossLaunches: Boolean = false,
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
    val busy: Boolean = false,
    val storageAvailable: Boolean = true,
    val feedback: SillageNativeFeedback? = null,
    val editorValidationError: RecordDraftValidationError? = null,
)

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
            editorValidationError = null,
        )
    }

    fun navigateToSettings() {
        state = state.copy(
            clientContext = state.clientContext.navigateTo(AppDestination.AISettings),
            editorValidationError = null,
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
            clientContext = state.clientContext.navigateTo(
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
            sync = SyncFeatureStateHolder(),
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
                state = state.copy(authentication = completed)
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
            sync = SyncFeatureStateHolder(),
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

    suspend fun pushPendingMemos() {
        if (!canStartOperation()) return
        val baseUrl = state.serverConnection.checkedBaseUrl ?: return
        if (state.authentication.account == null || activeAuthenticationRepository == null) return
        val workspaceFactory = memoSyncWorkspaceFactory ?: return
        val gatewayFactory = memoSyncGatewayFactory ?: return

        state = state.copy(busy = true, feedback = null)
        try {
            val workspace = workspaceFactory.createMemoSyncWorkspace(baseUrl)
            val summary = PushPendingMemosUseCase(
                outbox = workspace,
                gateway = gatewayFactory.createMemoSyncGateway(baseUrl),
            )()
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
                    summary.applied > 0 -> SillageNativeFeedback.MemoSyncCompleted
                    else -> SillageNativeFeedback.MemoSyncNoChanges
                },
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            handleMemoSyncFailure(error)
        } finally {
            state = state.copy(busy = false)
        }
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
                activeAuthenticationRepository = null
                state = state.copy(
                    authentication = state.authentication.resetForServerChange(),
                    sync = SyncFeatureStateHolder(),
                    feedback = SillageNativeFeedback.MemoSyncSessionExpired,
                )
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
        ).copy(busy = true)
        hydrate()
    }

    private fun markStorageUnavailable() {
        state = state.copy(
            storageAvailable = false,
            feedback = SillageNativeFeedback.StorageUnavailable,
        )
    }

    private fun canStartOperation(): Boolean = state.storageAvailable && !state.busy

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

private fun initialState(today: String, memoSyncSupported: Boolean): SillageNativeState {
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
    )
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
