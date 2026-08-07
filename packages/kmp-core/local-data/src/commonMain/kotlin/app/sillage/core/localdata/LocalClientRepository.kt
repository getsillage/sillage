package app.sillage.core.localdata

import app.sillage.core.application.preferences.ClientPreferences
import app.sillage.core.application.preferences.ClientPreferencesRepository
import app.sillage.core.application.preferences.normalizeBaseUrl
import app.sillage.core.application.records.RecordDraft
import app.sillage.core.application.records.RecordLifecycleRepository
import app.sillage.core.application.records.RecordWriteRepository
import app.sillage.core.application.records.RecordsRepository
import app.sillage.core.application.records.requireValidRecordDraft
import app.sillage.core.domain.records.Memo
import app.sillage.core.sync.AppliedMemoSync
import app.sillage.core.sync.ConflictMemoSync
import app.sillage.core.sync.MEMO_SYNC_ACTION_CREATE
import app.sillage.core.sync.MEMO_SYNC_ACTION_DELETE
import app.sillage.core.sync.MEMO_SYNC_ACTION_PURGE
import app.sillage.core.sync.MEMO_SYNC_ACTION_RESTORE
import app.sillage.core.sync.MEMO_SYNC_ACTION_UPDATE
import app.sillage.core.sync.MemoSyncServerMismatchException
import app.sillage.core.sync.MemoSyncWorkspace
import app.sillage.core.sync.MemoSyncWorkspaceFactory
import app.sillage.core.sync.PendingMemoMutation
import app.sillage.core.sync.mergeAppliedMemoSyncs
import app.sillage.core.sync.mergePulledMemoSyncs
import app.sillage.core.sync.resolveMemoSyncConflictKeepLocal
import app.sillage.core.sync.resolveMemoSyncConflictTakeServer
import app.sillage.core.sync.resolvePendingMemoSyncs

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
    ClientBackupTransfer,
    MemoSyncWorkspaceFactory {
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
        update { snapshot ->
            check(snapshot.records.none { it.id == created.id }) {
                "The platform generated a duplicate local record identifier."
            }
            snapshot.copy(
                records = snapshot.records + created,
                memoSync = snapshot.memoSync.withPendingMutation(
                    memo = created,
                    requestedAction = MEMO_SYNC_ACTION_CREATE,
                ),
            )
        }
        return created
    }

    override suspend fun updateRecord(memo: Memo, draft: RecordDraft): Memo {
        requireValidRecordDraft(draft)
        return mutateRecord(memo, MEMO_SYNC_ACTION_UPDATE) { current, timestamp ->
            current.copy(
                content = draft.content,
                entryDate = draft.entryDate,
                version = current.version + 1,
                updatedAt = timestamp,
            )
        }
    }

    override suspend fun setRecordFavorited(memo: Memo, favorited: Boolean): Memo {
        return mutateActiveRecord(memo, MEMO_SYNC_ACTION_UPDATE) { current, timestamp ->
            current.copy(
                version = current.version + 1,
                updatedAt = timestamp,
                favoritedAt = timestamp.takeIf { favorited },
            )
        }
    }

    override suspend fun setRecordArchived(memo: Memo, archived: Boolean): Memo {
        return mutateActiveRecord(memo, MEMO_SYNC_ACTION_UPDATE) { current, timestamp ->
            current.copy(
                version = current.version + 1,
                updatedAt = timestamp,
                archivedAt = timestamp.takeIf { archived },
            )
        }
    }

    override suspend fun deleteRecord(memo: Memo): Memo {
        return mutateActiveRecord(memo, MEMO_SYNC_ACTION_DELETE) { current, timestamp ->
            current.copy(
                version = current.version + 1,
                updatedAt = timestamp,
                deletedAt = timestamp,
            )
        }
    }

    override suspend fun restoreRecord(memo: Memo): Memo {
        return mutateRecord(memo, MEMO_SYNC_ACTION_RESTORE) { current, timestamp ->
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
        return mutateRecord(memo, MEMO_SYNC_ACTION_PURGE) { current, timestamp ->
            check(current.deletedAt != null && current.purgedAt == null) {
                "Only a recoverable deleted record can be permanently deleted."
            }
            current.copy(
                content = "",
                entryDate = "1970-01-01",
                version = current.version + 1,
                updatedAt = timestamp,
                favoritedAt = null,
                archivedAt = null,
                purgedAt = timestamp,
            )
        }
    }

    override fun createMemoSyncWorkspace(baseUrl: String): MemoSyncWorkspace {
        val normalized = normalizeBaseUrl(baseUrl)
        require(normalized.isNotBlank()) { "A server address is required for memo synchronization." }
        return LocalMemoSyncWorkspace(normalized)
    }

    private fun mutateActiveRecord(
        memo: Memo,
        requestedAction: String,
        transform: (Memo, String) -> Memo,
    ): Memo {
        return mutateRecord(memo, requestedAction) { current, timestamp ->
            check(current.deletedAt == null && current.purgedAt == null) {
                "Deleted records cannot be changed before restoration."
            }
            transform(current, timestamp)
        }
    }

    private fun mutateRecord(
        memo: Memo,
        requestedAction: String,
        transform: (Memo, String) -> Memo,
    ): Memo {
        var result: Memo? = null
        update { snapshot ->
            val current = snapshot.records.firstOrNull { it.id == memo.id }
                ?: throw NoSuchElementException("Local record ${memo.id} does not exist.")
            if (current.version != memo.version) {
                throw StaleLocalRecordException(memo.id)
            }
            val updated = transform(current, runtimeValues.currentTimestamp())
            result = updated
            snapshot.copy(
                records = snapshot.records.map { if (it.id == updated.id) updated else it },
                memoSync = snapshot.memoSync.withPendingMutation(updated, requestedAction),
            )
        }
        return checkNotNull(result)
    }

    private fun LocalMemoSyncState.withPendingMutation(
        memo: Memo,
        requestedAction: String,
    ): LocalMemoSyncState {
        val previousAction = pendingMutations[memo.id]?.action.orEmpty()
        val cloudVersion = cloudVersions[memo.id]
        val action = when (requestedAction) {
            MEMO_SYNC_ACTION_CREATE -> MEMO_SYNC_ACTION_CREATE
            MEMO_SYNC_ACTION_UPDATE -> when {
                cloudVersion == null -> MEMO_SYNC_ACTION_CREATE
                previousAction == MEMO_SYNC_ACTION_CREATE -> MEMO_SYNC_ACTION_CREATE
                previousAction == MEMO_SYNC_ACTION_RESTORE -> MEMO_SYNC_ACTION_RESTORE
                else -> MEMO_SYNC_ACTION_UPDATE
            }
            MEMO_SYNC_ACTION_DELETE -> if (cloudVersion == null) "" else MEMO_SYNC_ACTION_DELETE
            MEMO_SYNC_ACTION_RESTORE -> when {
                cloudVersion == null -> MEMO_SYNC_ACTION_CREATE
                previousAction == MEMO_SYNC_ACTION_DELETE -> MEMO_SYNC_ACTION_UPDATE
                previousAction == MEMO_SYNC_ACTION_CREATE -> MEMO_SYNC_ACTION_CREATE
                else -> MEMO_SYNC_ACTION_RESTORE
            }
            MEMO_SYNC_ACTION_PURGE -> if (cloudVersion == null) "" else MEMO_SYNC_ACTION_PURGE
            else -> error("Unsupported local memo synchronization action.")
        }
        val mutations = pendingMutations.toMutableMap()
        if (action.isBlank()) {
            mutations.remove(memo.id)
        } else {
            mutations[memo.id] = PendingMemoMutation(
                mutationId = runtimeValues.nextMutationId(),
                memoVersion = memo.version,
                memoUpdatedAt = memo.updatedAt,
                action = action,
            )
        }
        return copy(pendingMutations = mutations)
    }

    private inner class LocalMemoSyncWorkspace(
        private val baseUrl: String,
    ) : MemoSyncWorkspace {
        override suspend fun pendingMemos() = buildList {
            update { snapshot ->
                val memoSync = snapshot.memoSync.requireCompatibleWith(baseUrl)
                val resolved = resolvePendingMemoSyncs(
                    memos = snapshot.records,
                    cloudVersions = memoSync.cloudVersions,
                    pendingMutations = memoSync.pendingMutations,
                    newMutationId = runtimeValues::nextMutationId,
                )
                addAll(resolved.pending)
                val boundBaseUrl = if (resolved.pending.isEmpty() && memoSync.cloudVersions.isEmpty()) {
                    memoSync.serverBaseUrl
                } else {
                    memoSync.serverBaseUrl.ifBlank { baseUrl }
                }
                snapshot.copy(
                    memoSync = memoSync.copy(
                        serverBaseUrl = boundBaseUrl,
                        pendingMutations = resolved.pendingMutations,
                    ),
                )
            }
        }

        override suspend fun applySyncedMemos(applied: List<AppliedMemoSync>) {
            if (applied.isEmpty()) return
            update { snapshot ->
                val memoSync = snapshot.memoSync.requireBoundTo(baseUrl)
                val merged = mergeAppliedMemoSyncs(
                    localMemos = snapshot.records,
                    cloudVersions = memoSync.cloudVersions,
                    pendingMutations = memoSync.pendingMutations,
                    appliedMemos = applied,
                    newMutationId = runtimeValues::nextMutationId,
                    currentTimestamp = runtimeValues::currentTimestamp,
                )
                snapshot.copy(
                    records = merged.memos,
                    memoSync = memoSync.copy(
                        cloudVersions = merged.cloudVersions,
                        pendingMutations = merged.pendingMutations,
                    ),
                )
            }
        }

        override suspend fun mergePulledMemos(memos: List<Memo>): Int {
            var changedMemos = 0
            update { snapshot ->
                val memoSync = snapshot.memoSync.requireCompatibleWith(baseUrl)
                val merged = mergePulledMemoSyncs(
                    localMemos = snapshot.records,
                    cloudVersions = memoSync.cloudVersions,
                    pendingMutations = memoSync.pendingMutations,
                    serverMemos = memos,
                )
                changedMemos = merged.changedMemos
                val boundBaseUrl = if (
                    merged.state.cloudVersions.isEmpty() &&
                    merged.state.pendingMutations.isEmpty()
                ) {
                    memoSync.serverBaseUrl
                } else {
                    memoSync.serverBaseUrl.ifBlank { baseUrl }
                }
                snapshot.copy(
                    records = merged.state.memos,
                    memoSync = memoSync.copy(
                        serverBaseUrl = boundBaseUrl,
                        cloudVersions = merged.state.cloudVersions,
                        pendingMutations = merged.state.pendingMutations,
                    ),
                )
            }
            return changedMemos
        }

        override suspend fun keepLocal(conflict: ConflictMemoSync) {
            update { snapshot ->
                val memoSync = snapshot.memoSync.requireBoundTo(baseUrl)
                val localMemo = snapshot.records.firstOrNull { it.id == conflict.resourceId }
                    ?: throw NoSuchElementException("Local record ${conflict.resourceId} does not exist.")
                val serverVersion = conflict.serverVersion ?: conflict.serverMemo?.version
                    ?: throw IllegalArgumentException("The conflict does not include a server version.")
                val resolved = resolveMemoSyncConflictKeepLocal(
                    localMemos = snapshot.records,
                    cloudVersions = memoSync.cloudVersions,
                    pendingMutations = memoSync.pendingMutations,
                    localMemo = localMemo,
                    serverVersion = serverVersion,
                    newMutationId = runtimeValues::nextMutationId,
                    currentTimestamp = runtimeValues::currentTimestamp,
                )
                snapshot.copy(
                    records = resolved.memos,
                    memoSync = memoSync.copy(
                        cloudVersions = resolved.cloudVersions,
                        pendingMutations = resolved.pendingMutations,
                    ),
                )
            }
        }

        override suspend fun takeServer(conflict: ConflictMemoSync) {
            val serverMemo = conflict.serverMemo
                ?: throw IllegalArgumentException("The conflict does not include a server memo.")
            update { snapshot ->
                val memoSync = snapshot.memoSync.requireBoundTo(baseUrl)
                val resolved = resolveMemoSyncConflictTakeServer(
                    localMemos = snapshot.records,
                    cloudVersions = memoSync.cloudVersions,
                    pendingMutations = memoSync.pendingMutations,
                    serverMemo = serverMemo,
                )
                snapshot.copy(
                    records = resolved.memos,
                    memoSync = memoSync.copy(
                        cloudVersions = resolved.cloudVersions,
                        pendingMutations = resolved.pendingMutations,
                    ),
                )
            }
        }

        override fun localMemo(resourceId: String): Memo? {
            val snapshot = load()
            snapshot.memoSync.requireBoundTo(baseUrl)
            return snapshot.records.firstOrNull { it.id == resourceId }
        }
    }

    private fun LocalMemoSyncState.requireCompatibleWith(requestedBaseUrl: String): LocalMemoSyncState {
        if (serverBaseUrl.isNotBlank() && serverBaseUrl != requestedBaseUrl) {
            throw MemoSyncServerMismatchException(serverBaseUrl, requestedBaseUrl)
        }
        return this
    }

    private fun LocalMemoSyncState.requireBoundTo(requestedBaseUrl: String): LocalMemoSyncState {
        requireCompatibleWith(requestedBaseUrl)
        check(serverBaseUrl == requestedBaseUrl) { "Memo synchronization is not bound to this server." }
        return this
    }

    private fun update(transform: (LocalClientSnapshot) -> LocalClientSnapshot) {
        val current = load()
        storage.write(LocalClientSnapshotCodec.encode(transform(current)))
    }

    private fun load(): LocalClientSnapshot = LocalClientSnapshotCodec.decode(storage.read())
}
