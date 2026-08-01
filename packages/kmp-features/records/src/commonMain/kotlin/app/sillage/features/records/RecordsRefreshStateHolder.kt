package app.sillage.features.records

enum class RecordsRefreshStatus {
    Idle,
    Loading,
    Failed,
}

data class RecordsRefreshContext(
    val sourceKey: String,
    val clientContextGeneration: Long,
    val filter: MemoListFilter,
    val cacheGeneration: Long,
    val paginationRequestId: Long,
)

data class RecordsRefreshRequest(
    val requestId: Long,
    val sourceKey: String,
    val clientContextGeneration: Long,
    val filter: MemoListFilter,
    val cacheGeneration: Long,
    val paginationRequestId: Long,
)

/** Immutable state holder for replacing the visible records snapshot. */
data class RecordsRefreshStateHolder(
    val status: RecordsRefreshStatus = RecordsRefreshStatus.Idle,
    val requestId: Long = 0,
) {
    fun nextRequest(context: RecordsRefreshContext): RecordsRefreshRequest {
        return RecordsRefreshRequest(
            requestId = requestId + 1,
            sourceKey = context.sourceKey,
            clientContextGeneration = context.clientContextGeneration,
            filter = context.filter,
            cacheGeneration = context.cacheGeneration,
            paginationRequestId = context.paginationRequestId,
        )
    }

    fun begin(
        request: RecordsRefreshRequest,
        context: RecordsRefreshContext,
    ): RecordsRefreshStateHolder? {
        if (nextRequest(context) != request) {
            return null
        }
        return copy(status = RecordsRefreshStatus.Loading, requestId = request.requestId)
    }

    fun canApply(request: RecordsRefreshRequest, context: RecordsRefreshContext): Boolean {
        return status == RecordsRefreshStatus.Loading &&
            requestId == request.requestId &&
            context.sourceKey == request.sourceKey &&
            context.clientContextGeneration == request.clientContextGeneration &&
            context.filter == request.filter &&
            context.cacheGeneration == request.cacheGeneration &&
            context.paginationRequestId == request.paginationRequestId
    }

    fun complete(
        request: RecordsRefreshRequest,
        context: RecordsRefreshContext,
    ): RecordsRefreshStateHolder? {
        if (!canApply(request, context)) {
            return null
        }
        return copy(status = RecordsRefreshStatus.Idle)
    }

    fun fail(
        request: RecordsRefreshRequest,
        context: RecordsRefreshContext,
    ): RecordsRefreshStateHolder? {
        if (!canApply(request, context)) {
            return null
        }
        return copy(status = RecordsRefreshStatus.Failed)
    }

    fun cancel(status: RecordsRefreshStatus = RecordsRefreshStatus.Idle): RecordsRefreshStateHolder {
        return copy(status = status, requestId = requestId + 1)
    }
}
