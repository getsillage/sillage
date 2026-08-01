package app.sillage.core.application.records

import app.sillage.core.domain.records.Memo

class SaveRecordUseCase(
    private val repository: RecordWriteRepository,
) {
    suspend operator fun invoke(command: SaveRecordCommand): Memo {
        return when (command) {
            is SaveRecordCommand.Create -> repository.createRecord(command.draft)
            is SaveRecordCommand.Update -> repository.updateRecord(command.memo, command.draft)
        }
    }
}
