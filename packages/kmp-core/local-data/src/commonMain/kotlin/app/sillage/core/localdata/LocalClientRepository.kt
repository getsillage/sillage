package app.sillage.core.localdata

import app.sillage.core.application.preferences.ClientPreferences
import app.sillage.core.application.preferences.ClientPreferencesRepository
import app.sillage.core.application.records.RecordDraft
import app.sillage.core.application.records.RecordLifecycleRepository
import app.sillage.core.application.records.RecordWriteRepository
import app.sillage.core.application.records.RecordsRepository
import app.sillage.core.application.records.requireValidRecordDraft
import app.sillage.core.domain.records.Memo

class StaleLocalRecordException(recordId: String) :
    IllegalStateException("Local record $recordId changed before this operation completed.")

/**
 * Portable local repository for desktop and Apple hosts.
 *
 * Hosts serialize calls through the application controller. Every mutation and
 * export first loads the current snapshot. Restore validates its independent
 * backup envelope before replacing even an unreadable private snapshot.
 */
class LocalClientRepository(
    private val storage: ClientSnapshotStorage,
    private val runtimeValues: ClientRuntimeValues,
) : RecordsRepository,
    RecordWriteRepository,
    RecordLifecycleRepository,
    ClientPreferencesRepository,
    ClientBackupTransfer {
    override fun listRecords(): List<Memo> = load().records

    override fun loadPreferences(): ClientPreferences = load().preferences

    override fun savePreferences(preferences: ClientPreferences) {
        update { it.copy(preferences = preferences) }
    }

    override fun exportBackup(): String = LocalClientBackupCodec.encode(
        snapshot = load(),
        exportedAt = runtimeValues.currentTimestamp(),
    )

    override fun restoreBackup(value: String) {
        val fallbackPreferences = try {
            load().preferences
        } catch (_: Exception) {
            ClientPreferences()
        }
        val imported = LocalClientBackupCodec.decode(
            value = value,
            fallbackPreferences = fallbackPreferences,
        )
        storage.write(LocalClientSnapshotCodec.encode(imported))
    }

    override suspend fun createRecord(draft: RecordDraft): Memo {
        requireValidRecordDraft(draft)
        val timestamp = runtimeValues.currentTimestamp()
        val created = Memo(
            id = runtimeValues.nextRecordId(),
            content = draft.content,
            entryDate = draft.entryDate,
            version = 1,
            createdAt = timestamp,
            updatedAt = timestamp,
            favoritedAt = null,
            archivedAt = null,
            deletedAt = null,
        )
        updateRecords { records ->
            check(records.none { it.id == created.id }) {
                "The platform generated a duplicate local record identifier."
            }
            records + created
        }
        return created
    }

    override suspend fun updateRecord(memo: Memo, draft: RecordDraft): Memo {
        requireValidRecordDraft(draft)
        return mutateRecord(memo) { current, timestamp ->
            current.copy(
                content = draft.content,
                entryDate = draft.entryDate,
                version = current.version + 1,
                updatedAt = timestamp,
            )
        }
    }

    override suspend fun setRecordFavorited(memo: Memo, favorited: Boolean): Memo {
        return mutateActiveRecord(memo) { current, timestamp ->
            current.copy(
                version = current.version + 1,
                updatedAt = timestamp,
                favoritedAt = timestamp.takeIf { favorited },
            )
        }
    }

    override suspend fun setRecordArchived(memo: Memo, archived: Boolean): Memo {
        return mutateActiveRecord(memo) { current, timestamp ->
            current.copy(
                version = current.version + 1,
                updatedAt = timestamp,
                archivedAt = timestamp.takeIf { archived },
            )
        }
    }

    override suspend fun deleteRecord(memo: Memo): Memo {
        return mutateActiveRecord(memo) { current, timestamp ->
            current.copy(
                version = current.version + 1,
                updatedAt = timestamp,
                deletedAt = timestamp,
            )
        }
    }

    override suspend fun restoreRecord(memo: Memo): Memo {
        return mutateRecord(memo) { current, timestamp ->
            check(current.deletedAt != null && current.purgedAt == null) {
                "Only a recoverable deleted record can be restored."
            }
            current.copy(
                version = current.version + 1,
                updatedAt = timestamp,
                deletedAt = null,
            )
        }
    }

    override suspend fun purgeRecord(memo: Memo): Memo {
        return mutateRecord(memo) { current, timestamp ->
            check(current.deletedAt != null && current.purgedAt == null) {
                "Only a recoverable deleted record can be permanently deleted."
            }
            current.copy(
                version = current.version + 1,
                updatedAt = timestamp,
                purgedAt = timestamp,
            )
        }
    }

    private fun mutateActiveRecord(
        memo: Memo,
        transform: (Memo, String) -> Memo,
    ): Memo {
        return mutateRecord(memo) { current, timestamp ->
            check(current.deletedAt == null && current.purgedAt == null) {
                "Deleted records cannot be changed before restoration."
            }
            transform(current, timestamp)
        }
    }

    private fun mutateRecord(
        memo: Memo,
        transform: (Memo, String) -> Memo,
    ): Memo {
        var result: Memo? = null
        updateRecords { records ->
            val current = records.firstOrNull { it.id == memo.id }
                ?: throw NoSuchElementException("Local record ${memo.id} does not exist.")
            if (current.version != memo.version) {
                throw StaleLocalRecordException(memo.id)
            }
            val updated = transform(current, runtimeValues.currentTimestamp())
            result = updated
            records.map { if (it.id == updated.id) updated else it }
        }
        return checkNotNull(result)
    }

    private fun updateRecords(transform: (List<Memo>) -> List<Memo>) {
        update { snapshot -> snapshot.copy(records = transform(snapshot.records)) }
    }

    private fun update(transform: (LocalClientSnapshot) -> LocalClientSnapshot) {
        val current = load()
        storage.write(LocalClientSnapshotCodec.encode(transform(current)))
    }

    private fun load(): LocalClientSnapshot = LocalClientSnapshotCodec.decode(storage.read())
}
