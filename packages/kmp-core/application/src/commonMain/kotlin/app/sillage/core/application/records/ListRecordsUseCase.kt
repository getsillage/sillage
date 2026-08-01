package app.sillage.core.application.records

import app.sillage.core.domain.records.Memo

class ListRecordsUseCase(
    private val repository: RecordsRepository,
) {
    operator fun invoke(): List<Memo> = repository.listRecords()
}
