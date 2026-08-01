package app.sillage.core.application.records

import app.sillage.core.domain.records.Memo

data class RecordsSearchQuery(
    val text: String,
    val scope: RecordsQueryScope,
)

/** Application-facing port for full-text record search in one selected source. */
interface RecordsSearchRepository {
    suspend fun search(query: RecordsSearchQuery): List<Memo>
}
