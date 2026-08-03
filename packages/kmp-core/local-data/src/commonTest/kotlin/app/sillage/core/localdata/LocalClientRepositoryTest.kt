package app.sillage.core.localdata

import app.sillage.core.application.preferences.ClientPreferenceValues
import app.sillage.core.application.preferences.ClientPreferences
import app.sillage.core.application.records.InvalidRecordDraftException
import app.sillage.core.application.records.MAX_RECORD_CONTENT_UTF8_BYTES
import app.sillage.core.application.records.RecordDraft
import app.sillage.core.application.records.RecordDraftValidationError
import app.sillage.core.sync.AppliedMemoSync
import app.sillage.core.sync.ConflictMemoSync
import app.sillage.core.sync.MEMO_SYNC_ACTION_CREATE
import app.sillage.core.sync.MEMO_SYNC_ACTION_DELETE
import app.sillage.core.sync.MEMO_SYNC_ACTION_RESTORE
import app.sillage.core.sync.MEMO_SYNC_ACTION_UPDATE
import app.sillage.core.sync.MemoSyncServerMismatchException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class LocalClientRepositoryTest {
    @Test
    fun recordsAndPreferencesRoundTripAcrossRepositoryInstances() = runTest {
        val storage = MemoryStorage()
        val runtime = QueueRuntime()
        val repository = LocalClientRepository(storage, runtime)

        repository.savePreferences(
            ClientPreferences(
                themeMode = ClientPreferenceValues.THEME_DARK,
                languageMode = ClientPreferenceValues.LANGUAGE_EN,
                serverBaseUrl = "https://sillage.example",
            ),
        )
        val created = repository.createRecord(
            RecordDraft(
                content = "First line\n\u4f60\u597d, Sillage",
                entryDate = "2026-08-03",
            ),
        )

        val reloaded = LocalClientRepository(storage, runtime)
        assertEquals(ClientPreferenceValues.THEME_DARK, reloaded.loadPreferences().themeMode)
        assertEquals(ClientPreferenceValues.LANGUAGE_EN, reloaded.loadPreferences().languageMode)
        assertEquals("https://sillage.example", reloaded.loadPreferences().serverBaseUrl)
        assertEquals(listOf(created), reloaded.listRecords())
        assertTrue(storage.value.orEmpty().contains("\\n"))
    }

    @Test
    fun lifecycleMutationsAdvanceVersionAndKeepRecoverableTombstone() = runTest {
        val repository = LocalClientRepository(MemoryStorage(), QueueRuntime())
        val created = repository.createRecord(RecordDraft("body", "2026-08-03"))
        val favorited = repository.setRecordFavorited(created, true)
        val deleted = repository.deleteRecord(favorited)
        val restored = repository.restoreRecord(deleted)
        val deletedAgain = repository.deleteRecord(restored)
        val purged = repository.purgeRecord(deletedAgain)

        assertEquals(6L, purged.version)
        assertEquals("", purged.content)
        assertEquals("1970-01-01", purged.entryDate)
        assertNull(purged.favoritedAt)
        assertNull(purged.archivedAt)
        assertNotNull(purged.deletedAt)
        assertNotNull(purged.purgedAt)
        assertEquals(purged, repository.listRecords().single())
    }

    @Test
    fun staleMutationCannotReplaceNewerSnapshot() = runTest {
        val repository = LocalClientRepository(MemoryStorage(), QueueRuntime())
        val created = repository.createRecord(RecordDraft("body", "2026-08-03"))
        repository.updateRecord(created, RecordDraft("new body", "2026-08-03"))

        assertFailsWith<StaleLocalRecordException> {
            repository.deleteRecord(created)
        }
        assertEquals("new body", repository.listRecords().single().content)
    }

    @Test
    fun corruptSnapshotIsNeverOverwrittenByMutation() = runTest {
        val storage = MemoryStorage("{not-json")
        val repository = LocalClientRepository(storage, QueueRuntime())

        assertFailsWith<InvalidClientSnapshotException> {
            repository.createRecord(RecordDraft("body", "2026-08-03"))
        }
        assertEquals("{not-json", storage.value)
    }

    @Test
    fun existingRecordsRemainReadableAfterDraftRulesTighten() = runTest {
        val oversizedContent = "a".repeat(MAX_RECORD_CONTENT_UTF8_BYTES + 1)
        val storage = MemoryStorage(
            """
            {
              "schemaVersion": 1,
              "records": [
                {
                  "id": "legacy-empty",
                  "content": "",
                  "entryDate": "not-a-date",
                  "version": 1,
                  "createdAt": "2026-08-03T10:00:00Z",
                  "updatedAt": "2026-08-03T10:00:00Z"
                },
                {
                  "id": "legacy-oversized",
                  "content": "$oversizedContent",
                  "entryDate": "2026-08-03",
                  "version": 1,
                  "createdAt": "2026-08-03T10:00:01Z",
                  "updatedAt": "2026-08-03T10:00:01Z"
                }
              ]
            }
            """.trimIndent(),
        )

        val repository = LocalClientRepository(storage, QueueRuntime())
        val existingRecords = repository.listRecords()
        val records = existingRecords.associateBy { it.id }

        assertEquals("", records.getValue("legacy-empty").content)
        assertEquals("not-a-date", records.getValue("legacy-empty").entryDate)
        assertEquals(oversizedContent, records.getValue("legacy-oversized").content)

        val restored = LocalClientRepository(MemoryStorage(), QueueRuntime())
        restored.restoreBackup(repository.exportBackup())
        assertEquals(existingRecords, restored.listRecords())
    }

    @Test
    fun invalidDraftNeverCreatesPrivateSnapshot() = runTest {
        val storage = MemoryStorage()
        val repository = LocalClientRepository(storage, QueueRuntime())

        val error = assertFailsWith<InvalidRecordDraftException> {
            repository.createRecord(RecordDraft("", "2026-08-03"))
        }

        assertEquals(RecordDraftValidationError.EmptyContent, error.validationError)
        assertEquals(null, storage.value)
    }

    @Test
    fun exportsAndRestoresValidatedPortableBackup() = runTest {
        val source = LocalClientRepository(MemoryStorage(), QueueRuntime())
        source.savePreferences(
            ClientPreferences(
                themeMode = ClientPreferenceValues.THEME_DARK,
                languageMode = ClientPreferenceValues.LANGUAGE_EN,
            ),
        )
        val created = source.createRecord(RecordDraft("portable", "2026-08-03"))
        val targetStorage = MemoryStorage()
        val target = LocalClientRepository(targetStorage, QueueRuntime())
        val backup = source.exportBackup()

        target.restoreBackup(backup)

        assertEquals(source.loadPreferences(), target.loadPreferences())
        assertEquals(listOf(created), target.listRecords())
        assertTrue(backup.contains("\"formatVersion\": 1"))
        assertTrue(backup.contains("\"memos\""))
        assertTrue(backup.contains("\"exportedAt\""))
        assertFalse(backup.contains("\"schemaVersion\""))
        assertTrue(targetStorage.value.orEmpty().contains("\"schemaVersion\": 2"))
        assertFalse(targetStorage.value.orEmpty().contains("\"formatVersion\""))
    }

    @Test
    fun portableBackupDoesNotExportOrReplaceDeviceServerAddress() {
        val source = LocalClientRepository(MemoryStorage(), QueueRuntime())
        source.savePreferences(
            ClientPreferences(serverBaseUrl = "https://source.example"),
        )
        val target = LocalClientRepository(MemoryStorage(), QueueRuntime())
        target.savePreferences(
            ClientPreferences(serverBaseUrl = "https://target.example"),
        )

        val backup = source.exportBackup()
        target.restoreBackup(backup)

        assertFalse(backup.contains("source.example"))
        assertEquals("https://target.example", target.loadPreferences().serverBaseUrl)
    }

    @Test
    fun validatedBackupCanReplaceUnreadablePrivateSnapshot() = runTest {
        val source = LocalClientRepository(MemoryStorage(), QueueRuntime())
        source.savePreferences(
            ClientPreferences(
                themeMode = ClientPreferenceValues.THEME_DARK,
                languageMode = ClientPreferenceValues.LANGUAGE_EN,
            ),
        )
        val created = source.createRecord(RecordDraft("recovered", "2026-08-03"))
        val targetStorage = MemoryStorage("{not-json")
        val target = LocalClientRepository(targetStorage, QueueRuntime())

        target.restoreBackup(source.exportBackup())

        assertEquals(listOf(created), target.listRecords())
        assertEquals(source.loadPreferences(), target.loadPreferences())
    }

    @Test
    fun missingBackupPreferenceUsesDefaultWhenPrivateSnapshotIsUnreadable() {
        val repository = LocalClientRepository(MemoryStorage("{not-json"), QueueRuntime())
        val backup =
            """
            {
              "formatVersion": 1,
              "exportedAt": "2026-08-03T10:00:00Z",
              "themeMode": "dark",
              "memos": []
            }
            """.trimIndent()

        repository.restoreBackup(backup)

        assertEquals(ClientPreferenceValues.THEME_DARK, repository.loadPreferences().themeMode)
        assertEquals(ClientPreferences().languageMode, repository.loadPreferences().languageMode)
    }

    @Test
    fun restoresRecordSubsetFromAndroidV1BackupAndPreservesMissingPreference() {
        val repository = LocalClientRepository(MemoryStorage(), QueueRuntime())
        repository.savePreferences(
            ClientPreferences(
                themeMode = ClientPreferenceValues.THEME_LIGHT,
                languageMode = ClientPreferenceValues.LANGUAGE_EN,
                serverBaseUrl = "https://device.example",
            ),
        )
        val androidBackup = """
            {
              "formatVersion": 1,
              "exportedAt": "2026-08-03T10:00:00Z",
              "themeMode": "dark",
              "memoViewMode": "LIST",
              "autoSummary": false,
              "memos": [
                {
                  "id": "android-record",
                  "content": "from Android",
                  "entryDate": "2026-08-03",
                  "version": 1,
                  "createdAt": "2026-08-03T10:00:00Z",
                  "updatedAt": "2026-08-03T10:00:00Z"
                }
              ],
              "memoAI": [],
              "aiProfiles": [],
              "askConversations": [],
              "askMessages": []
            }
        """.trimIndent()

        repository.restoreBackup(androidBackup)

        assertEquals("from Android", repository.listRecords().single().content)
        assertEquals(ClientPreferenceValues.THEME_DARK, repository.loadPreferences().themeMode)
        assertEquals(ClientPreferenceValues.LANGUAGE_EN, repository.loadPreferences().languageMode)
        assertEquals("https://device.example", repository.loadPreferences().serverBaseUrl)
    }

    @Test
    fun rejectsInvalidBackupWithoutChangingExistingSnapshot() = runTest {
        val storage = MemoryStorage()
        val repository = LocalClientRepository(storage, QueueRuntime())
        repository.createRecord(RecordDraft("keep", "2026-08-03"))
        val before = storage.value

        assertFailsWith<InvalidClientSnapshotException> {
            repository.restoreBackup("{not-json")
        }

        assertEquals(before, storage.value)
        assertEquals("keep", repository.listRecords().single().content)
    }

    @Test
    fun invalidBackupDoesNotReplaceUnreadablePrivateSnapshot() {
        val storage = MemoryStorage("{not-json")
        val repository = LocalClientRepository(storage, QueueRuntime())

        assertFailsWith<InvalidClientSnapshotException> {
            repository.restoreBackup("{also-not-json")
        }
        assertEquals("{not-json", storage.value)
    }

    @Test
    fun outboxPersistsAndAdvancesCloudBaseline() = runTest {
        val storage = MemoryStorage()
        val runtime = QueueRuntime()
        val repository = LocalClientRepository(storage, runtime)
        val created = repository.createRecord(RecordDraft("local", "2026-08-03"))
        val workspace = repository.createMemoSyncWorkspace("https://sillage.example/")

        val pendingCreate = workspace.pendingMemos().single()
        assertEquals(MEMO_SYNC_ACTION_CREATE, pendingCreate.action)
        assertNull(pendingCreate.baseVersion)

        val serverCreated = created.copy(updatedAt = "2026-08-03T11:00:00Z")
        workspace.applySyncedMemos(
            listOf(AppliedMemoSync(pendingCreate.mutationId, serverCreated)),
        )
        assertEquals(serverCreated, repository.listRecords().single())
        assertTrue(workspace.pendingMemos().isEmpty())

        val updated = repository.updateRecord(
            serverCreated,
            RecordDraft("updated locally", "2026-08-04"),
        )
        val pendingUpdate = LocalClientRepository(storage, runtime)
            .createMemoSyncWorkspace("https://sillage.example")
            .pendingMemos()
            .single()
        assertEquals(updated, pendingUpdate.memo)
        assertEquals(1L, pendingUpdate.baseVersion)
        assertEquals(MEMO_SYNC_ACTION_UPDATE, pendingUpdate.action)
        assertNotEquals(pendingCreate.mutationId, pendingUpdate.mutationId)
    }

    @Test
    fun boundOutboxCannotBePushedToAnotherServer() = runTest {
        val storage = MemoryStorage()
        val repository = LocalClientRepository(storage, QueueRuntime())
        repository.createRecord(RecordDraft("local", "2026-08-03"))
        repository.createMemoSyncWorkspace("https://first.example").pendingMemos()
        val before = storage.value

        assertFailsWith<MemoSyncServerMismatchException> {
            repository.createMemoSyncWorkspace("https://second.example").pendingMemos()
        }

        assertEquals(before, storage.value)
    }

    @Test
    fun portableBackupClearsCloudBaselineAndOutbox() = runTest {
        val storage = MemoryStorage()
        val runtime = QueueRuntime()
        val repository = LocalClientRepository(storage, runtime)
        val created = repository.createRecord(RecordDraft("portable", "2026-08-03"))
        val workspace = repository.createMemoSyncWorkspace("https://first.example")
        val pending = workspace.pendingMemos().single()
        workspace.applySyncedMemos(listOf(AppliedMemoSync(pending.mutationId, created)))
        repository.updateRecord(created, RecordDraft("portable update", "2026-08-03"))
        assertTrue(storage.value.orEmpty().contains("\"memoSyncServerBaseUrl\""))

        val backup = repository.exportBackup()
        assertFalse(backup.contains("memoSyncServerBaseUrl"))
        assertFalse(backup.contains("memoCloudVersions"))
        assertFalse(backup.contains("pendingMemoMutations"))

        repository.restoreBackup(backup)
        val restoredPending = repository.createMemoSyncWorkspace("https://second.example")
            .pendingMemos()
            .single()
        assertEquals(MEMO_SYNC_ACTION_CREATE, restoredPending.action)
        assertNull(restoredPending.baseVersion)
    }

    @Test
    fun restoredMemoEditedBeforePushKeepsLocalFieldsAfterRestoreApplies() = runTest {
        val repository = LocalClientRepository(MemoryStorage(), QueueRuntime())
        val created = repository.createRecord(RecordDraft("original", "2026-08-03"))
        val workspace = repository.createMemoSyncWorkspace("https://sillage.example")
        val createMutation = workspace.pendingMemos().single()
        workspace.applySyncedMemos(listOf(AppliedMemoSync(createMutation.mutationId, created)))

        val deleted = repository.deleteRecord(created)
        val deleteMutation = workspace.pendingMemos().single()
        assertEquals(MEMO_SYNC_ACTION_DELETE, deleteMutation.action)
        val serverDeleted = deleted.copy(updatedAt = "2026-08-03T11:00:00Z")
        workspace.applySyncedMemos(
            listOf(AppliedMemoSync(deleteMutation.mutationId, serverDeleted)),
        )

        val restored = repository.restoreRecord(serverDeleted)
        val edited = repository.updateRecord(
            restored,
            RecordDraft("edited after restore", "2026-08-04"),
        )
        val restoreMutation = workspace.pendingMemos().single()
        assertEquals(MEMO_SYNC_ACTION_RESTORE, restoreMutation.action)
        val serverRestored = serverDeleted.copy(
            version = 3,
            updatedAt = "2026-08-03T11:01:00Z",
            deletedAt = null,
        )

        workspace.applySyncedMemos(
            listOf(AppliedMemoSync(restoreMutation.mutationId, serverRestored)),
        )

        val retained = repository.listRecords().single()
        assertEquals(edited.content, retained.content)
        assertEquals(edited.entryDate, retained.entryDate)
        val followUp = workspace.pendingMemos().single()
        assertEquals(MEMO_SYNC_ACTION_UPDATE, followUp.action)
        assertEquals(serverRestored.version, followUp.baseVersion)
        assertNotEquals(restoreMutation.mutationId, followUp.mutationId)
    }

    @Test
    fun conflictResolutionCanResubmitLocalOrAdoptServer() = runTest {
        val repository = LocalClientRepository(MemoryStorage(), QueueRuntime())
        val created = repository.createRecord(RecordDraft("original", "2026-08-03"))
        val workspace = repository.createMemoSyncWorkspace("https://sillage.example")
        val createMutation = workspace.pendingMemos().single()
        workspace.applySyncedMemos(listOf(AppliedMemoSync(createMutation.mutationId, created)))
        val local = repository.updateRecord(
            created,
            RecordDraft("local version", "2026-08-03"),
        )
        val pending = workspace.pendingMemos().single()
        val server = created.copy(
            content = "server version",
            version = 2,
            updatedAt = "2026-08-03T11:00:00Z",
        )
        val conflict = ConflictMemoSync(
            mutationId = pending.mutationId,
            resourceId = local.id,
            clientVersion = local.version,
            serverVersion = server.version,
            serverMemo = server,
        )

        workspace.keepLocal(conflict)
        val resubmitted = workspace.pendingMemos().single()
        assertEquals("local version", resubmitted.memo.content)
        assertEquals(server.version, resubmitted.baseVersion)
        assertTrue(resubmitted.memo.version > server.version)

        workspace.takeServer(conflict)
        assertEquals(server, repository.listRecords().single())
        assertTrue(workspace.pendingMemos().isEmpty())
    }

    @Test
    fun unsyncedDeleteIsDroppedAndRestoreBecomesCreate() = runTest {
        val repository = LocalClientRepository(MemoryStorage(), QueueRuntime())
        val created = repository.createRecord(RecordDraft("local", "2026-08-03"))
        val deleted = repository.deleteRecord(created)
        val workspace = repository.createMemoSyncWorkspace("https://sillage.example")
        assertTrue(workspace.pendingMemos().isEmpty())

        repository.restoreRecord(deleted)
        val pending = workspace.pendingMemos().single()
        assertEquals(MEMO_SYNC_ACTION_CREATE, pending.action)
        assertNull(pending.baseVersion)
    }
}

private class MemoryStorage(
    initialValue: String? = null,
) : ClientSnapshotStorage {
    override val location: String = "memory://client.json"
    var value: String? = initialValue

    override fun read(): String? = value

    override fun write(value: String) {
        this.value = value
    }
}

private class QueueRuntime : ClientRuntimeValues {
    private var id = 0
    private var mutation = 0
    private var tick = 0

    override fun nextRecordId(): String = "record-${++id}"

    override fun nextMutationId(): String = "mutation-${++mutation}"

    override fun currentTimestamp(): String = "2026-08-03T10:00:${tick++.toString().padStart(2, '0')}Z"
}
