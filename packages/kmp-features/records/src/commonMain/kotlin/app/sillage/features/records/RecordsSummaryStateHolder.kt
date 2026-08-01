package app.sillage.features.records

import app.sillage.core.domain.records.Memo
import app.sillage.core.domain.records.MemoAI

data class RecordsSummaryContext(
    val sourceKey: String,
    val clientContextGeneration: Long,
    val destinationKey: String,
    val destinationGeneration: Long,
    val detailRequestId: Long,
    val summaryAvailable: Boolean,
)

data class RecordsSummaryRequest(
    val requestId: Long,
    val memoId: String,
    val memoVersion: Long,
    val sourceKey: String,
    val clientContextGeneration: Long,
    val destinationKey: String,
    val destinationGeneration: Long,
    val detailRequestId: Long,
)

/** Immutable AI-summary presentation state and request ownership. */
data class RecordsSummaryStateHolder(
    val summary: MemoAI? = null,
    val loading: Boolean = false,
    val requestId: Long = 0,
) {
    fun nextRequest(memo: Memo?, context: RecordsSummaryContext): RecordsSummaryRequest? {
        memo ?: return null
        if (loading || !context.summaryAvailable) return null
        return RecordsSummaryRequest(
            requestId = requestId + 1,
            memoId = memo.id,
            memoVersion = memo.version,
            sourceKey = context.sourceKey,
            clientContextGeneration = context.clientContextGeneration,
            destinationKey = context.destinationKey,
            destinationGeneration = context.destinationGeneration,
            detailRequestId = context.detailRequestId,
        )
    }

    fun begin(
        request: RecordsSummaryRequest,
        memo: Memo?,
        context: RecordsSummaryContext,
    ): RecordsSummaryStateHolder? {
        if (nextRequest(memo, context) != request) return null
        return copy(requestId = request.requestId, loading = true)
    }

    fun owns(request: RecordsSummaryRequest): Boolean {
        return loading && requestId == request.requestId
    }

    fun canApply(
        request: RecordsSummaryRequest,
        memo: Memo?,
        context: RecordsSummaryContext,
    ): Boolean {
        return owns(request) &&
            memo?.id == request.memoId &&
            memo.version == request.memoVersion &&
            context.sourceKey == request.sourceKey &&
            context.clientContextGeneration == request.clientContextGeneration &&
            context.destinationKey == request.destinationKey &&
            context.destinationGeneration == request.destinationGeneration &&
            context.detailRequestId == request.detailRequestId
    }

    fun complete(
        request: RecordsSummaryRequest,
        memo: Memo?,
        context: RecordsSummaryContext,
        result: MemoAI,
    ): RecordsSummaryStateHolder {
        if (!canApply(request, memo, context) || result.memoId != request.memoId) return this
        return copy(summary = result, loading = false)
    }

    fun fail(
        request: RecordsSummaryRequest,
        memo: Memo?,
        context: RecordsSummaryContext,
    ): RecordsSummaryStateHolder {
        return if (canApply(request, memo, context)) copy(loading = false) else this
    }

    fun finish(request: RecordsSummaryRequest): RecordsSummaryStateHolder {
        return if (owns(request)) copy(loading = false) else this
    }

    fun invalidate(): RecordsSummaryStateHolder {
        return if (loading) copy(loading = false, requestId = requestId + 1) else this
    }

    fun beginDetailLoad(loadSummary: Boolean): RecordsSummaryStateHolder {
        return copy(loading = loadSummary)
    }

    fun completeDetail(result: MemoAI?): RecordsSummaryStateHolder {
        return copy(summary = result, loading = false)
    }

    fun finishDetail(): RecordsSummaryStateHolder = copy(loading = false)

    fun replaceSummary(value: MemoAI?): RecordsSummaryStateHolder = copy(summary = value)

    fun replacePresentation(
        value: MemoAI?,
        loading: Boolean,
    ): RecordsSummaryStateHolder = copy(summary = value, loading = loading)
}
