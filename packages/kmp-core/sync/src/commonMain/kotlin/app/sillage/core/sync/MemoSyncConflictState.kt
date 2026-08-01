package app.sillage.core.sync

import app.sillage.core.domain.records.Memo

data class MemoSyncConflictItem(
    val conflict: ConflictMemoSync,
    val localMemo: Memo?,
)

data class MemoSyncConflictStateHolder(
    val items: List<MemoSyncConflictItem> = emptyList(),
) {
    fun replace(items: List<MemoSyncConflictItem>): MemoSyncConflictStateHolder {
        return if (this.items == items) this else copy(items = items)
    }

    fun remove(resourceId: String): MemoSyncConflictStateHolder {
        return replace(items.filterNot { it.conflict.resourceId == resourceId })
    }

    fun find(resourceId: String): MemoSyncConflictItem? {
        return items.find { it.conflict.resourceId == resourceId }
    }
}

sealed interface ResolveMemoSyncConflictCommand {
    val conflict: ConflictMemoSync

    data class KeepLocal(
        override val conflict: ConflictMemoSync,
    ) : ResolveMemoSyncConflictCommand

    data class TakeServer(
        override val conflict: ConflictMemoSync,
    ) : ResolveMemoSyncConflictCommand
}

interface MemoSyncConflictRepository {
    suspend fun keepLocal(conflict: ConflictMemoSync)

    suspend fun takeServer(conflict: ConflictMemoSync)

    fun localMemo(resourceId: String): Memo?
}

class ResolveMemoSyncConflictUseCase(
    private val repository: MemoSyncConflictRepository,
) {
    suspend operator fun invoke(command: ResolveMemoSyncConflictCommand) {
        when (command) {
            is ResolveMemoSyncConflictCommand.KeepLocal -> repository.keepLocal(command.conflict)
            is ResolveMemoSyncConflictCommand.TakeServer -> repository.takeServer(command.conflict)
        }
    }

    fun item(conflict: ConflictMemoSync): MemoSyncConflictItem {
        return MemoSyncConflictItem(
            conflict = conflict,
            localMemo = repository.localMemo(conflict.resourceId),
        )
    }
}
