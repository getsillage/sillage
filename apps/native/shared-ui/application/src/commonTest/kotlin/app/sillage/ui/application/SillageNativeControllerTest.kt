package app.sillage.ui.application

import app.sillage.core.application.preferences.ClientPreferenceValues
import app.sillage.core.application.preferences.ClientPreferences
import app.sillage.core.application.preferences.ClientPreferencesRepository
import app.sillage.core.application.records.RecordDraft
import app.sillage.core.application.records.RecordLifecycleRepository
import app.sillage.core.application.records.RecordWriteRepository
import app.sillage.core.application.records.RecordsRepository
import app.sillage.core.domain.records.Memo
import app.sillage.features.records.MemoListFilter
import app.sillage.ui.appshell.AppDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SillageNativeControllerTest {
    @Test
    fun reportsUnsavedEditorChangesForHostLifecycleGuards() {
        val controller = controller(FakeRecordsRepository())

        assertFalse(controller.hasUnsavedEditorChanges)
        controller.startNewRecord()
        assertFalse(controller.hasUnsavedEditorChanges)
        controller.updateEditorContent("unsaved")
        assertTrue(controller.hasUnsavedEditorChanges)
        controller.closeEditor()
        assertFalse(controller.hasUnsavedEditorChanges)
    }

    @Test
    fun createsEditsAndDeletesRecordThroughSharedApplicationFlow() = runTest {
        val repository = FakeRecordsRepository()
        val controller = controller(repository)

        controller.startNewRecord()
        controller.updateEditorContent("A portable record")
        controller.saveEditor()

        assertEquals(AppDestination.MemoDetail, controller.state.clientContext.screen)
        assertEquals("A portable record", controller.state.workspace.records.selection.selectedMemo?.content)
        assertEquals(SillageNativeFeedback.RecordSaved, controller.state.feedback)

        controller.editSelectedRecord()
        controller.updateEditorContent("Edited on desktop or iOS")
        controller.saveEditor()
        assertEquals(2L, controller.state.workspace.records.selection.selectedMemo?.version)

        controller.deleteSelectedRecord()
        assertEquals(AppDestination.Memos, controller.state.clientContext.screen)
        assertTrue(controller.state.workspace.records.records.isEmpty())

        controller.selectFilter(MemoListFilter.Deleted)
        assertEquals(1, controller.state.workspace.records.records.size)
    }

    @Test
    fun rejectsImpossibleCalendarDateBeforeStorageMutation() = runTest {
        val repository = FakeRecordsRepository()
        val controller = controller(repository)
        controller.startNewRecord()
        controller.updateEditorEntryDate("2026-02-30")

        controller.saveEditor()

        assertEquals(SillageEditorValidationError.InvalidEntryDate, controller.state.editorValidationError)
        assertTrue(repository.records.isEmpty())
        assertFalse(controller.state.busy)
    }

    @Test
    fun searchPublishesOnlyMatchingFilteredRecords() {
        val repository = FakeRecordsRepository(
            mutableListOf(
                memo("one", "quiet morning"),
                memo("two", "release notes", archivedAt = "2026-08-03T10:00:00Z"),
            ),
        )
        val controller = controller(repository)

        controller.updateSearchQuery("morning")
        controller.searchRecords()

        assertEquals(listOf("one"), controller.state.workspace.records.search.currentResults()?.map(Memo::id))
        controller.selectFilter(MemoListFilter.Archived)
        assertTrue(controller.state.workspace.records.search.query.isBlank())
        assertEquals(listOf("two"), controller.state.workspace.records.records.map(Memo::id))
    }

    @Test
    fun unreadableStorageDisablesMutationsWithoutPretendingDataIsEmpty() {
        val repository = FakeRecordsRepository(failReads = true)
        val controller = controller(repository)

        assertFalse(controller.state.storageAvailable)
        assertEquals(SillageNativeFeedback.StorageUnavailable, controller.state.feedback)
        controller.startNewRecord()
        assertEquals(AppDestination.Editor, controller.state.clientContext.screen)
    }

    @Test
    fun restoreCanRecoverStorageThatFailedInitialHydration() = runTest {
        val repository = FakeRecordsRepository(failReads = true)
        val controller = controller(repository)
        var exportInvoked = false

        controller.exportBackup {
            exportInvoked = true
            true
        }
        assertFalse(exportInvoked)

        controller.restoreBackup { false }
        assertFalse(controller.state.storageAvailable)
        assertEquals(SillageNativeFeedback.StorageUnavailable, controller.state.feedback)

        repository.records += memo("recovered", "from backup")
        repository.preferences = ClientPreferences(
            themeMode = ClientPreferenceValues.THEME_DARK,
            languageMode = ClientPreferenceValues.LANGUAGE_EN,
        )
        controller.restoreBackup {
            repository.failReads = false
            true
        }

        assertTrue(controller.state.storageAvailable)
        assertEquals(listOf("recovered"), controller.state.workspace.records.records.map(Memo::id))
        assertEquals(ClientPreferenceValues.THEME_DARK, controller.state.appearance.themeMode)
        assertEquals(ClientPreferenceValues.LANGUAGE_EN, controller.state.appearance.languageMode)
        assertEquals(SillageNativeFeedback.BackupRestored, controller.state.feedback)
        assertFalse(controller.state.busy)
    }

    @Test
    fun reportsBackupTransferResultsAndReloadsRestoredData() = runTest {
        val repository = FakeRecordsRepository(mutableListOf(memo("before", "before")))
        val controller = controller(repository)

        controller.exportBackup { true }
        assertEquals(SillageNativeFeedback.BackupExported, controller.state.feedback)

        repository.records.clear()
        repository.records += memo("restored", "from backup")
        repository.preferences = ClientPreferences(
            themeMode = ClientPreferenceValues.THEME_DARK,
            languageMode = ClientPreferenceValues.LANGUAGE_EN,
        )
        controller.navigateToSettings()
        controller.restoreBackup { true }

        assertEquals(AppDestination.Memos, controller.state.clientContext.screen)
        assertEquals(listOf("restored"), controller.state.workspace.records.records.map(Memo::id))
        assertEquals(ClientPreferenceValues.THEME_DARK, controller.state.appearance.themeMode)
        assertEquals(ClientPreferenceValues.LANGUAGE_EN, controller.state.appearance.languageMode)
        assertEquals(SillageNativeFeedback.BackupRestored, controller.state.feedback)
        assertFalse(controller.state.busy)
    }

    @Test
    fun backupFailureDoesNotDisableReadableLocalStorage() = runTest {
        val controller = controller(FakeRecordsRepository())

        controller.exportBackup { error("destination unavailable") }

        assertEquals(SillageNativeFeedback.DataTransferFailed, controller.state.feedback)
        assertTrue(controller.state.storageAvailable)
        assertFalse(controller.state.busy)
    }

    @Test
    fun isoDateValidationHandlesLeapYears() {
        assertTrue(isValidIsoDate("2024-02-29"))
        assertFalse(isValidIsoDate("2100-02-29"))
        assertTrue(isValidIsoDate("2000-02-29"))
        assertFalse(isValidIsoDate("2026-13-01"))
    }
}

private fun controller(repository: FakeRecordsRepository) = SillageNativeController(
    recordsRepository = repository,
    recordWriteRepository = repository,
    recordLifecycleRepository = repository,
    preferencesRepository = repository,
    todayProvider = { "2026-08-03" },
)

private class FakeRecordsRepository(
    val records: MutableList<Memo> = mutableListOf(),
    var failReads: Boolean = false,
) : RecordsRepository,
    RecordWriteRepository,
    RecordLifecycleRepository,
    ClientPreferencesRepository {
    var preferences = ClientPreferences()
    private var nextId = records.size

    override fun listRecords(): List<Memo> {
        if (failReads) error("unreadable")
        return records.toList()
    }

    override fun loadPreferences(): ClientPreferences {
        if (failReads) error("unreadable")
        return preferences
    }

    override fun savePreferences(preferences: ClientPreferences) {
        this.preferences = preferences
    }

    override suspend fun createRecord(draft: RecordDraft): Memo {
        val created = memo(
            id = "created-${++nextId}",
            content = draft.content,
            entryDate = draft.entryDate,
        )
        records += created
        return created
    }

    override suspend fun updateRecord(memo: Memo, draft: RecordDraft): Memo = replace(
        memo.copy(
            content = draft.content,
            entryDate = draft.entryDate,
            version = memo.version + 1,
        ),
    )

    override suspend fun setRecordFavorited(memo: Memo, favorited: Boolean): Memo = replace(
        memo.copy(
            version = memo.version + 1,
            favoritedAt = "2026-08-03T11:00:00Z".takeIf { favorited },
        ),
    )

    override suspend fun setRecordArchived(memo: Memo, archived: Boolean): Memo = replace(
        memo.copy(
            version = memo.version + 1,
            archivedAt = "2026-08-03T11:00:00Z".takeIf { archived },
        ),
    )

    override suspend fun deleteRecord(memo: Memo): Memo = replace(
        memo.copy(version = memo.version + 1, deletedAt = "2026-08-03T11:00:00Z"),
    )

    override suspend fun restoreRecord(memo: Memo): Memo = replace(
        memo.copy(version = memo.version + 1, deletedAt = null),
    )

    override suspend fun purgeRecord(memo: Memo): Memo = replace(
        memo.copy(version = memo.version + 1, purgedAt = "2026-08-03T11:00:00Z"),
    )

    private fun replace(memo: Memo): Memo {
        val index = records.indexOfFirst { it.id == memo.id }
        records[index] = memo
        return memo
    }
}

private fun memo(
    id: String,
    content: String,
    entryDate: String = "2026-08-03",
    archivedAt: String? = null,
) = Memo(
    id = id,
    content = content,
    entryDate = entryDate,
    version = 1,
    createdAt = "2026-08-03T10:00:00Z",
    updatedAt = "2026-08-03T10:00:00Z",
    favoritedAt = null,
    archivedAt = archivedAt,
    deletedAt = null,
)
