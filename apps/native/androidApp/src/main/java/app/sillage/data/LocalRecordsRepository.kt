package app.sillage.data

import app.sillage.core.application.records.RecordsRepository
import app.sillage.core.application.records.RecordDetail
import app.sillage.core.application.records.RecordDetailRepository
import app.sillage.core.application.records.RecordsQueryScope
import app.sillage.core.application.records.RecordsSearchQuery
import app.sillage.core.application.records.RecordsSearchRepository
import app.sillage.core.domain.records.Memo
import app.sillage.features.records.MemoListFilter

/** Android persistence adapter for the shared records application port. */
class LocalRecordsRepository(
    private val localDataStore: LocalDataStore,
) : RecordsRepository, RecordsSearchRepository, RecordDetailRepository {
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
}

internal fun RecordsQueryScope.localFilter(): MemoListFilter {
    return when (this) {
        RecordsQueryScope.Unarchived -> MemoListFilter.Unarchived
        RecordsQueryScope.Archived -> MemoListFilter.Archived
        RecordsQueryScope.Favorited -> MemoListFilter.Favorited
        RecordsQueryScope.Deleted -> MemoListFilter.Deleted
    }
}
