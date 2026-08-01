package app.sillage.features.records

import app.sillage.core.domain.records.Memo

data class RecordsSearchContext(
    val sourceKey: String,
    val clientContextGeneration: Long,
    val filter: MemoListFilter,
    val cacheGeneration: Long,
)

data class RecordsSearchRequest(
    val requestId: Long,
    val query: String,
    val sourceKey: String,
    val clientContextGeneration: Long,
    val filter: MemoListFilter,
    val cacheGeneration: Long,
)

data class CompletedRecordsSearch(
    val query: String,
    val resultCount: Int,
)

/** Immutable state holder for record search input, requests, and results. */
data class RecordsSearchStateHolder(
    val query: String = "",
    val results: List<Memo>? = null,
    val resultQuery: String = "",
    val failureQuery: String = "",
    val requestId: Long = 0,
    val completionEventId: Long = 0,
    val searching: Boolean = false,
) {
    fun updateQuery(value: String): RecordsSearchStateHolder {
        val blank = value.isBlank()
        return copy(
            query = value,
            results = if (blank) null else results,
            resultQuery = if (blank) "" else resultQuery,
            failureQuery = "",
            requestId = requestId + 1,
            searching = !blank,
        )
    }

    fun clear(): RecordsSearchStateHolder {
        return copy(
            query = "",
            results = null,
            resultQuery = "",
            failureQuery = "",
            requestId = requestId + 1,
            searching = false,
        )
    }

    fun nextRequest(context: RecordsSearchContext): RecordsSearchRequest? {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            return null
        }
        return RecordsSearchRequest(
            requestId = requestId + 1,
            query = normalizedQuery,
            sourceKey = context.sourceKey,
            clientContextGeneration = context.clientContextGeneration,
            filter = context.filter,
            cacheGeneration = context.cacheGeneration,
        )
    }

    fun begin(
        request: RecordsSearchRequest,
        context: RecordsSearchContext,
    ): RecordsSearchStateHolder? {
        if (nextRequest(context) != request) {
            return null
        }
        return copy(
            failureQuery = "",
            requestId = request.requestId,
            searching = true,
        )
    }

    fun canApply(request: RecordsSearchRequest, context: RecordsSearchContext): Boolean {
        return searching &&
            requestId == request.requestId &&
            query.trim() == request.query &&
            context.sourceKey == request.sourceKey &&
            context.clientContextGeneration == request.clientContextGeneration &&
            context.filter == request.filter &&
            context.cacheGeneration == request.cacheGeneration
    }

    fun complete(
        request: RecordsSearchRequest,
        context: RecordsSearchContext,
        results: List<Memo>,
    ): RecordsSearchStateHolder? {
        if (!canApply(request, context)) {
            return null
        }
        return copy(
            results = results,
            resultQuery = request.query,
            failureQuery = "",
            completionEventId = completionEventId + 1,
            searching = false,
        )
    }

    fun fail(
        request: RecordsSearchRequest,
        context: RecordsSearchContext,
    ): RecordsSearchStateHolder? {
        if (!canApply(request, context)) {
            return null
        }
        return copy(
            resultQuery = "",
            failureQuery = request.query,
            searching = false,
        )
    }

    fun currentResults(): List<Memo>? {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank() || normalizedQuery != resultQuery.trim()) {
            return null
        }
        return results
    }

    fun completed(): CompletedRecordsSearch? {
        val normalizedQuery = query.trim()
        val normalizedResultQuery = resultQuery.trim()
        val currentResults = results
        if (
            searching ||
            currentResults == null ||
            normalizedQuery.isBlank() ||
            normalizedQuery != normalizedResultQuery
        ) {
            return null
        }
        return CompletedRecordsSearch(
            query = normalizedResultQuery,
            resultCount = currentResults.size,
        )
    }

    fun mergeResultMemo(memo: Memo, filter: MemoListFilter): RecordsSearchStateHolder {
        val updatedResults = results?.let { existing ->
            memosForFilter(existing.filter { it.id != memo.id } + memo, filter)
        }
        return copy(results = updatedResults)
    }

    fun invalidateForMemoChange(memo: Memo, filter: MemoListFilter): RecordsSearchStateHolder {
        val merged = mergeResultMemo(memo, filter)
        return merged.copy(
            resultQuery = if (searching) "" else resultQuery,
            failureQuery = if (searching) "" else failureQuery,
            requestId = requestId + 1,
            searching = false,
        )
    }
}
