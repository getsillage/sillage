package app.sillage.data

import app.sillage.core.application.records.RecordSummaryGenerator
import app.sillage.core.domain.records.Memo
import app.sillage.core.domain.records.MemoAI

/** Android HTTP adapter for record-summary generation. */
class RemoteRecordSummaryRepository(
    private val api: SillageApi,
) : RecordSummaryGenerator {
    override suspend fun generateRecordSummary(memo: Memo): MemoAI {
        return api.generateMemoSummary(memo)
    }
}
