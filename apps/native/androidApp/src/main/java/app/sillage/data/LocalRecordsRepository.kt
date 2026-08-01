package app.sillage.data

import app.sillage.core.application.records.RecordsRepository
import app.sillage.core.domain.records.Memo

/** Android persistence adapter for the shared records application port. */
class LocalRecordsRepository(
    private val localDataStore: LocalDataStore,
) : RecordsRepository {
    override fun listRecords(): List<Memo> = localDataStore.listMemos()
}
