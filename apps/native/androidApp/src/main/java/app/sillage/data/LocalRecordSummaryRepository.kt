package app.sillage.data

import app.sillage.core.application.records.ActiveRecordSummaryProfileRequiredException
import app.sillage.core.application.records.RecordSummaryGenerator
import app.sillage.core.application.records.RecordSummaryStore
import app.sillage.core.domain.records.Memo
import app.sillage.core.domain.records.MemoAI

/** Android local-AI and persistence adapter for record summaries. */
class LocalRecordSummaryRepository(
    private val localDataStore: LocalDataStore,
    private val localAiClient: LocalAiClient,
) : RecordSummaryGenerator, RecordSummaryStore {
    override suspend fun generateRecordSummary(memo: Memo): MemoAI {
        val profile = localDataStore.activeAIProfile()
            ?: throw ActiveRecordSummaryProfileRequiredException()
        return localAiClient.summarizeMemo(profile, memo)
    }

    override suspend fun saveRecordSummary(summary: MemoAI) {
        localDataStore.saveMemoAI(summary)
    }
}
