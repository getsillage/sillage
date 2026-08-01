package app.sillage.core.application.records

import app.sillage.core.domain.records.Memo

class MutateRecordLifecycleUseCase(
    private val repository: RecordLifecycleRepository,
) {
    suspend operator fun invoke(command: RecordLifecycleCommand): Memo {
        return when (command) {
            is RecordLifecycleCommand.SetFavorited ->
                repository.setRecordFavorited(command.memo, command.favorited)
            is RecordLifecycleCommand.SetArchived ->
                repository.setRecordArchived(command.memo, command.archived)
            is RecordLifecycleCommand.Delete -> repository.deleteRecord(command.memo)
            is RecordLifecycleCommand.Restore -> repository.restoreRecord(command.memo)
            is RecordLifecycleCommand.Purge -> repository.purgeRecord(command.memo)
        }
    }
}
