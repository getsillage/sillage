package app.sillage.features.records

import app.sillage.core.domain.records.Memo

/** Immutable visible record cache and its mutation generation. */
data class RecordsCollectionStateHolder(
    val records: List<Memo> = emptyList(),
    val cacheGeneration: Long = 0,
) {
    fun replace(records: List<Memo>): RecordsCollectionStateHolder {
        return if (this.records == records) this else copy(records = records)
    }

    fun clear(): RecordsCollectionStateHolder = replace(emptyList())

    fun applyMemo(memo: Memo, filter: MemoListFilter): RecordsCollectionStateHolder {
        val updated = memosForFilter(records.filter { it.id != memo.id } + memo, filter)
        return copy(
            records = updated,
            cacheGeneration = cacheGeneration + 1,
        )
    }
}
