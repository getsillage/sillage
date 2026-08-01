package app.sillage.data

import app.sillage.core.application.records.RecordsRepository
import app.sillage.core.application.records.RecordDetail
import app.sillage.core.application.records.RecordDetailRepository
import app.sillage.core.application.records.RecordDraft
import app.sillage.core.application.records.RecordWriteRepository
import app.sillage.core.application.records.RecordsQueryScope
import app.sillage.core.application.records.RecordsSearchQuery
import app.sillage.core.application.records.RecordsSearchRepository
import app.sillage.core.domain.records.Memo
import app.sillage.features.records.MemoListFilter

/** Android persistence adapter for the shared records application port. */
class LocalRecordsRepository(
    private val localDataStore: LocalDataStore,
) : RecordsRepository,
    RecordsSearchRepository,
    RecordDetailRepository,
    RecordWriteRepository {
    override fun listRecords(): List<Memo> = localDataStore.listMemos()

    override suspend fun search(query: RecordsSearchQuery): List<Memo> {
        return localDataStore.searchMemos(
            query = query.text,
            filter = query.scope.localFilter(),
        )
    }

    override suspend fun getRecordDetail(memoId: String): RecordDetail {
        return localDataStore.getMemo(memoId)
    }

    override suspend fun createRecord(draft: RecordDraft): Memo {
        return localDataStore.createMemo(draft.content, draft.entryDate)
    }

    override suspend fun updateRecord(memo: Memo, draft: RecordDraft): Memo {
        return localDataStore.updateMemo(memo, draft.content, draft.entryDate)
    }
}

internal fun RecordsQueryScope.localFilter(): MemoListFilter {
    return when (this) {
        RecordsQueryScope.Unarchived -> MemoListFilter.Unarchived
        RecordsQueryScope.Archived -> MemoListFilter.Archived
        RecordsQueryScope.Favorited -> MemoListFilter.Favorited
        RecordsQueryScope.Deleted -> MemoListFilter.Deleted
    }
}
