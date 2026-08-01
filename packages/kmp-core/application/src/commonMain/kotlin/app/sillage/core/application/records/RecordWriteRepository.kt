package app.sillage.core.application.records

import app.sillage.core.domain.records.Memo

data class RecordDraft(
    val content: String,
    val entryDate: String,
)

sealed interface SaveRecordCommand {
    val draft: RecordDraft

    data class Create(
        override val draft: RecordDraft,
    ) : SaveRecordCommand

    data class Update(
        val memo: Memo,
        override val draft: RecordDraft,
    ) : SaveRecordCommand
}

/** Application-facing record creation and update boundary. */
interface RecordWriteRepository {
    suspend fun createRecord(draft: RecordDraft): Memo

    suspend fun updateRecord(memo: Memo, draft: RecordDraft): Memo
}
