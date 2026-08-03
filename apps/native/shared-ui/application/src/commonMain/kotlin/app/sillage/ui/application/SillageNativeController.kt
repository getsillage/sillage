package app.sillage.ui.application

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.sillage.core.application.auth.InstanceBootstrapRepository
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
import app.sillage.features.auth.InstanceBootstrapContext
import app.sillage.features.auth.InstanceBootstrapStateHolder
import app.sillage.features.records.MemoListFilter
import app.sillage.features.records.RecordsBrowseStateHolder
import app.sillage.features.records.RecordsEditorStateHolder
import app.sillage.features.records.RecordsFeatureStateHolder
import app.sillage.features.records.RecordsSearchContext
import app.sillage.features.records.memosForFilter
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
    DataTransferFailed,
    StorageUnavailable,
}

data class SillageNativePlatform(
    val name: String,
    val dataLocation: String,
    val version: String,
    val openDataLocation: (() -> Boolean)? = null,
    val exportBackup: (suspend () -> Boolean)? = null,
    val restoreBackup: (suspend () -> Boolean)? = null,
)

data class SillageNativeState(
    val clientContext: AppClientContextStateHolder,
    val appearance: AppAppearanceStateHolder,
    val workspace: AppWorkspaceStateHolder,
    val serverConnection: InstanceBootstrapStateHolder,
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
    private val todayProvider: () -> String,
) {
    private val saveRecord = SaveRecordUseCase(recordWriteRepository)
    private val mutateRecordLifecycle = MutateRecordLifecycleUseCase(recordLifecycleRepository)
    private var allRecords: List<Memo> = emptyList()
    private var preferences = ClientPreferences()

    var state by mutableStateOf(initialState(todayProvider()))
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
        state = state.copy(serverConnection = state.serverConnection.updateBaseUrl(value))
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
            )
        } catch (_: Exception) {
            markStorageUnavailable()
        }
    }

    private fun rehydrateAfterBackupRestore() {
        state = initialState(todayProvider()).copy(busy = true)
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
}

private fun initialState(today: String): SillageNativeState {
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
    )
}

private fun RecordsFeatureStateHolder.searchContext(generation: Long) = RecordsSearchContext(
    sourceKey = ClientPreferenceValues.MODE_OFFLINE,
    clientContextGeneration = generation,
    filter = filter,
    cacheGeneration = cacheGeneration,
)
