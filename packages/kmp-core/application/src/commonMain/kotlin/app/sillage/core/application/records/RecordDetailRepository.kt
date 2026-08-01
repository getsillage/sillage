package app.sillage.core.application.records

import app.sillage.core.domain.records.Memo
import app.sillage.core.domain.records.MemoAI

data class RecordDetail(
    val memo: Memo,
    val ai: MemoAI?,
)

/** Application-facing access to one record and its derived detail metadata. */
interface RecordDetailRepository {
    suspend fun getRecordDetail(memoId: String): RecordDetail
}
