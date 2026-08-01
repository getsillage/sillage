package app.sillage.features.records

import app.sillage.core.domain.records.Memo

data class RecordsDetailContext(
    val sourceKey: String,
    val clientContextGeneration: Long,
    val destinationKey: String,
    val destinationGeneration: Long,
    val cacheGeneration: Long,
    val detailAvailable: Boolean,
)

data class RecordsDetailRequest(
    val requestId: Long,
    val memoId: String,
    val memoVersion: Long,
    val sourceKey: String,
    val clientContextGeneration: Long,
    val destinationKey: String,
    val destinationGeneration: Long,
    val cacheGeneration: Long,
)

enum class RecordsDetailResponseDisposition {
    Ignore,
    Superseded,
    Apply,
}

/** Immutable selected-record state and detail request coordinator. */
data class RecordsSelectionStateHolder(
    val selectedMemo: Memo? = null,
    val detailRequestId: Long = 0,
) {
    fun select(memo: Memo): RecordsSelectionStateHolder {
        return copy(selectedMemo = memo)
    }

    fun clear(): RecordsSelectionStateHolder {
        return copy(selectedMemo = null)
    }

    fun clearIfSelected(memoId: String): RecordsSelectionStateHolder {
        return if (selectedMemo?.id == memoId) clear() else this
    }

    fun replaceIfSelected(memoId: String, memo: Memo?): RecordsSelectionStateHolder {
        return if (selectedMemo?.id == memoId) copy(selectedMemo = memo) else this
    }

    fun mergeMemo(memo: Memo): RecordsSelectionStateHolder {
        return if (selectedMemo?.id == memo.id) copy(selectedMemo = memo) else this
    }

    fun nextDetailRequest(
        memoId: String,
        context: RecordsDetailContext,
    ): RecordsDetailRequest? {
        val selected = selectedMemo ?: return null
        if (selected.id != memoId || !context.detailAvailable) {
            return null
        }
        return RecordsDetailRequest(
            requestId = detailRequestId + 1,
            memoId = memoId,
            memoVersion = selected.version,
            sourceKey = context.sourceKey,
            clientContextGeneration = context.clientContextGeneration,
            destinationKey = context.destinationKey,
            destinationGeneration = context.destinationGeneration,
            cacheGeneration = context.cacheGeneration,
        )
    }

    fun beginDetailRequest(
        request: RecordsDetailRequest,
        context: RecordsDetailContext,
    ): RecordsSelectionStateHolder? {
        if (nextDetailRequest(request.memoId, context) != request) {
            return null
        }
        return copy(detailRequestId = request.requestId)
    }

    fun detailResponseDisposition(
        request: RecordsDetailRequest,
        context: RecordsDetailContext,
        memo: Memo,
    ): RecordsDetailResponseDisposition {
        if (!matchesDetailRequest(request, context)) {
            return RecordsDetailResponseDisposition.Ignore
        }
        val currentVersion = selectedMemo?.version
            ?: return RecordsDetailResponseDisposition.Superseded
        if (
            context.cacheGeneration != request.cacheGeneration ||
            memo.id != request.memoId ||
            memo.version < request.memoVersion ||
            memo.version < currentVersion
        ) {
            return RecordsDetailResponseDisposition.Superseded
        }
        return RecordsDetailResponseDisposition.Apply
    }

    fun detailFailureDisposition(
        request: RecordsDetailRequest,
        context: RecordsDetailContext,
    ): RecordsDetailResponseDisposition {
        if (!matchesDetailRequest(request, context)) {
            return RecordsDetailResponseDisposition.Ignore
        }
        val superseded = context.cacheGeneration != request.cacheGeneration ||
            (selectedMemo?.version ?: Long.MIN_VALUE) > request.memoVersion
        return if (superseded) {
            RecordsDetailResponseDisposition.Superseded
        } else {
            RecordsDetailResponseDisposition.Apply
        }
    }

    private fun matchesDetailRequest(
        request: RecordsDetailRequest,
        context: RecordsDetailContext,
    ): Boolean {
        return detailRequestId == request.requestId &&
            selectedMemo?.id == request.memoId &&
            context.detailAvailable &&
            context.sourceKey == request.sourceKey &&
            context.clientContextGeneration == request.clientContextGeneration &&
            context.destinationKey == request.destinationKey &&
            context.destinationGeneration == request.destinationGeneration
    }
}
