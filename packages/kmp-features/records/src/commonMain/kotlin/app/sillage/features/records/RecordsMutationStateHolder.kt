package app.sillage.features.records

/** Immutable ownership state for concurrent record mutations. */
data class RecordsMutationStateHolder(
    val activeMemoIds: Set<String> = emptySet(),
) {
    val active: Boolean
        get() = activeMemoIds.isNotEmpty()

    fun isActive(memoId: String): Boolean = memoId in activeMemoIds

    fun begin(memoId: String?): RecordsMutationStateHolder {
        return memoId?.let { copy(activeMemoIds = activeMemoIds + it) } ?: this
    }

    fun finish(memoId: String?): RecordsMutationStateHolder {
        return memoId?.let { copy(activeMemoIds = activeMemoIds - it) } ?: this
    }

    fun clear(): RecordsMutationStateHolder {
        return if (activeMemoIds.isEmpty()) this else copy(activeMemoIds = emptySet())
    }
}
