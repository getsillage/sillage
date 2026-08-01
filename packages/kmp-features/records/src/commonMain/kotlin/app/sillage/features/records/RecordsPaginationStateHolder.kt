package app.sillage.features.records

data class RecordsPageContext(
    val sourceKey: String,
    val sourceAvailable: Boolean,
    val clientContextGeneration: Long,
    val filter: MemoListFilter,
    val cacheGeneration: Long,
)

data class RecordsPageRequest(
    val requestId: Long,
    val cursor: String,
    val sourceKey: String,
    val clientContextGeneration: Long,
    val filter: MemoListFilter,
    val cacheGeneration: Long,
)

/**
 * Immutable state holder for paginated record loading.
 *
 * Every transition validates the captured query context so late responses
 * cannot modify a different source, filter, client session, or cache version.
 */
data class RecordsPaginationStateHolder(
    val nextCursor: String = "",
    val loadingMore: Boolean = false,
    val requestId: Long = 0,
) {
    fun nextRequest(context: RecordsPageContext): RecordsPageRequest? {
        if (nextCursor.isBlank() || loadingMore || !context.sourceAvailable) {
            return null
        }
        return RecordsPageRequest(
            requestId = requestId + 1,
            cursor = nextCursor,
            sourceKey = context.sourceKey,
            clientContextGeneration = context.clientContextGeneration,
            filter = context.filter,
            cacheGeneration = context.cacheGeneration,
        )
    }

    fun begin(
        request: RecordsPageRequest,
        context: RecordsPageContext,
    ): RecordsPaginationStateHolder? {
        if (nextRequest(context) != request) {
            return null
        }
        return copy(loadingMore = true, requestId = request.requestId)
    }

    fun canApply(request: RecordsPageRequest, context: RecordsPageContext): Boolean {
        return loadingMore &&
            requestId == request.requestId &&
            nextCursor == request.cursor &&
            context.sourceAvailable &&
            context.sourceKey == request.sourceKey &&
            context.clientContextGeneration == request.clientContextGeneration &&
            context.filter == request.filter &&
            context.cacheGeneration == request.cacheGeneration
    }

    fun complete(
        request: RecordsPageRequest,
        context: RecordsPageContext,
        nextCursor: String,
    ): RecordsPaginationStateHolder? {
        if (!canApply(request, context)) {
            return null
        }
        return copy(nextCursor = nextCursor, loadingMore = false)
    }

    fun fail(
        request: RecordsPageRequest,
        context: RecordsPageContext,
    ): RecordsPaginationStateHolder? {
        if (!canApply(request, context)) {
            return null
        }
        return copy(loadingMore = false)
    }

    fun cancel(): RecordsPaginationStateHolder {
        return copy(loadingMore = false, requestId = requestId + 1)
    }
}
