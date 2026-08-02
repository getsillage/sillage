package app.sillage.ui.sync

import app.sillage.core.domain.records.Memo
import app.sillage.core.sync.ConflictMemoSync
import app.sillage.features.sync.MemoSyncConflictItem
import app.sillage.features.sync.MemoSyncConflictStateHolder
import app.sillage.features.sync.SyncFeatureStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SillageSyncConflictDialogTest {
    private val strings = SillageSyncConflictStrings(
        title = "Conflict",
        supporting = "Choose a version",
        localLabel = "Local",
        serverLabel = "Server",
        emptyLocal = "Empty local",
        emptyServer = "Empty server",
        keepLocal = "Keep local",
        takeServer = "Use server",
        dismiss = "Later",
    )

    @Test
    fun emptyAggregateHasNoDialogPresentation() {
        assertNull(sillageSyncConflictPresentation(SyncFeatureStateHolder(), strings))
    }

    @Test
    fun firstOpenConflictOwnsTrimmedPreviewsAndResourceIdentity() {
        val state = SyncFeatureStateHolder(
            conflicts = MemoSyncConflictStateHolder(
                items = listOf(
                    item("first", localContent = "  local draft  ", serverContent = " server copy "),
                    item("second", localContent = "later local", serverContent = "later server"),
                ),
            ),
        )

        val presentation = sillageSyncConflictPresentation(state, strings)

        assertEquals("first", presentation?.resourceId)
        assertEquals("local draft", presentation?.localPreview)
        assertEquals("server copy", presentation?.serverPreview)
    }

    @Test
    fun blankAndLongPreviewsUseFallbackAndLengthLimit() {
        val state = SyncFeatureStateHolder(
            conflicts = MemoSyncConflictStateHolder(
                items = listOf(
                    item("memo-1", localContent = "   ", serverContent = "x".repeat(900)),
                ),
            ),
        )

        val presentation = sillageSyncConflictPresentation(state, strings)

        assertEquals("Empty local", presentation?.localPreview)
        assertEquals("x".repeat(800), presentation?.serverPreview)
    }

    private fun item(
        resourceId: String,
        localContent: String?,
        serverContent: String?,
    ) = MemoSyncConflictItem(
        conflict = ConflictMemoSync(
            mutationId = "mutation-$resourceId",
            resourceId = resourceId,
            clientVersion = 1,
            serverVersion = 2,
            serverMemo = serverContent?.let { memo(resourceId, it, version = 2) },
        ),
        localMemo = localContent?.let { memo(resourceId, it, version = 1) },
    )

    private fun memo(
        id: String,
        content: String,
        version: Long,
    ) = Memo(
        id = id,
        content = content,
        entryDate = "2026-08-02",
        version = version,
        createdAt = "2026-08-02T00:00:00Z",
        updatedAt = "2026-08-02T00:00:00Z",
        favoritedAt = null,
        archivedAt = null,
        deletedAt = null,
        purgedAt = null,
    )
}
