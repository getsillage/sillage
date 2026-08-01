package app.sillage.features.sync

import app.sillage.core.domain.records.Memo
import app.sillage.core.sync.ConflictMemoSync

data class MemoSyncConflictItem(
    val conflict: ConflictMemoSync,
    val localMemo: Memo?,
)

data class MemoSyncConflictStateHolder(
    val items: List<MemoSyncConflictItem> = emptyList(),
) {
    fun replace(items: List<MemoSyncConflictItem>): MemoSyncConflictStateHolder {
        return if (this.items == items) this else copy(items = items)
    }

    fun remove(resourceId: String): MemoSyncConflictStateHolder {
        return replace(items.filterNot { it.conflict.resourceId == resourceId })
    }

    fun find(resourceId: String): MemoSyncConflictItem? {
        return items.find { it.conflict.resourceId == resourceId }
    }
}
