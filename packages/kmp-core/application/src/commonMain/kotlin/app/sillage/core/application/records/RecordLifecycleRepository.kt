package app.sillage.core.application.records

import app.sillage.core.domain.records.Memo

sealed interface RecordLifecycleCommand {
    val memo: Memo

    data class SetFavorited(
        override val memo: Memo,
        val favorited: Boolean,
    ) : RecordLifecycleCommand

    data class SetArchived(
        override val memo: Memo,
        val archived: Boolean,
    ) : RecordLifecycleCommand

    data class Delete(override val memo: Memo) : RecordLifecycleCommand

    data class Restore(override val memo: Memo) : RecordLifecycleCommand

    data class Purge(override val memo: Memo) : RecordLifecycleCommand
}

/** Application-facing record lifecycle mutation boundary. */
interface RecordLifecycleRepository {
    suspend fun setRecordFavorited(memo: Memo, favorited: Boolean): Memo

    suspend fun setRecordArchived(memo: Memo, archived: Boolean): Memo

    suspend fun deleteRecord(memo: Memo): Memo

    suspend fun restoreRecord(memo: Memo): Memo

    suspend fun purgeRecord(memo: Memo): Memo
}
