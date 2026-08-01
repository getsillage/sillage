package app.sillage.features.sync

/**
 * Aggregated immutable ownership for manual synchronization presentation.
 *
 * Conflict identity remains on [MemoSyncConflictStateHolder]. This type is the
 * feature-level surface hosts compose so later sync presentation slices can join
 * without reintroducing top-level root-state fields.
 */
data class SyncFeatureStateHolder(
    val conflicts: MemoSyncConflictStateHolder = MemoSyncConflictStateHolder(),
) {
    val items: List<MemoSyncConflictItem> get() = conflicts.items

    fun replaceConflicts(items: List<MemoSyncConflictItem>): SyncFeatureStateHolder {
        return copy(conflicts = conflicts.replace(items))
    }

    fun removeConflict(resourceId: String): SyncFeatureStateHolder {
        return copy(conflicts = conflicts.remove(resourceId))
    }

    fun findConflict(resourceId: String): MemoSyncConflictItem? {
        return conflicts.find(resourceId)
    }

    /**
     * Replaces conflicts only when the push produced at least one open item;
     * otherwise leaves the current presentation unchanged.
     */
    fun applyPushConflicts(items: List<MemoSyncConflictItem>): SyncFeatureStateHolder {
        return if (items.isEmpty()) this else replaceConflicts(items)
    }
}
