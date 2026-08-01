package app.sillage.core.application.records

import app.sillage.core.domain.records.Memo
import app.sillage.core.domain.records.MemoAI

class GenerateRecordSummaryUseCase(
    private val generator: RecordSummaryGenerator,
) {
    suspend operator fun invoke(memo: Memo): MemoAI {
        return generator.generateRecordSummary(memo)
    }
}

class SaveRecordSummaryUseCase(
    private val store: RecordSummaryStore,
) {
    suspend operator fun invoke(summary: MemoAI) {
        store.saveRecordSummary(summary)
    }
}
