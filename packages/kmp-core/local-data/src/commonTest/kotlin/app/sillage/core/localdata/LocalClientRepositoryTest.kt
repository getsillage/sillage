package app.sillage.core.localdata

import app.sillage.core.application.preferences.ClientPreferenceValues
import app.sillage.core.application.preferences.ClientPreferences
import app.sillage.core.application.records.InvalidRecordDraftException
import app.sillage.core.application.records.MAX_RECORD_CONTENT_UTF8_BYTES
import app.sillage.core.application.records.RecordDraft
import app.sillage.core.application.records.RecordDraftValidationError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
        assertTrue(targetStorage.value.orEmpty().contains("\"schemaVersion\": 1"))
        assertFalse(targetStorage.value.orEmpty().contains("\"formatVersion\""))
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
    private var tick = 0

    override fun nextRecordId(): String = "record-${++id}"

    override fun currentTimestamp(): String = "2026-08-03T10:00:${tick++.toString().padStart(2, '0')}Z"
}
