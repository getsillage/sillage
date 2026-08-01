package app.sillage.core.application.records

import app.sillage.core.domain.records.Memo

class SearchRecordsUseCase(
    private val repository: RecordsSearchRepository,
) {
    suspend operator fun invoke(query: RecordsSearchQuery): List<Memo> {
        return repository.search(query)
    }
}
