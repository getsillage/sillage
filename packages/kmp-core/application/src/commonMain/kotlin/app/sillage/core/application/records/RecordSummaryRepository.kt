package app.sillage.core.application.records

import app.sillage.core.domain.records.Memo
import app.sillage.core.domain.records.MemoAI

class ActiveRecordSummaryProfileRequiredException : IllegalStateException()

/** Application-facing AI-summary generation boundary. */
interface RecordSummaryGenerator {
    suspend fun generateRecordSummary(memo: Memo): MemoAI
}

/** Application-facing persistence boundary for a generated summary. */
interface RecordSummaryStore {
    suspend fun saveRecordSummary(summary: MemoAI)
}
