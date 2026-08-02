package app.sillage.core.localdata

import app.sillage.core.application.preferences.ClientPreferenceValues
import app.sillage.core.application.preferences.ClientPreferences
import app.sillage.core.application.records.RecordDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
