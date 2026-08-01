package app.sillage.core.sync

import app.sillage.core.domain.records.Memo

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

    fun localMemo(conflict: ConflictMemoSync): Memo? {
        return repository.localMemo(conflict.resourceId)
    }
}
