package app.sillage.features.records

import app.sillage.core.domain.records.Memo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RecordsSelectionStateHolderTest {
    @Test
    fun selectionTransitionsPreserveRequestGeneration() {
        val first = memo("memo-1", version = 1)
        val updated = first.copy(content = "updated", version = 2)
        val state = RecordsSelectionStateHolder(detailRequestId = 4)

        val selected = state.select(first)

        assertEquals(first, selected.selectedMemo)
        assertEquals(4L, selected.detailRequestId)
        assertEquals(updated, selected.mergeMemo(updated).selectedMemo)
        assertEquals(selected, selected.mergeMemo(memo("other", version = 1)))
        assertEquals(selected, selected.clearIfSelected("other"))
        assertEquals(null, selected.clearIfSelected(first.id).selectedMemo)
        assertEquals(updated, selected.replaceIfSelected(first.id, updated).selectedMemo)
        assertEquals(null, selected.replaceIfSelected(first.id, null).selectedMemo)
        assertEquals(null, selected.clear().selectedMemo)
    }

    @Test
    fun detailRequestRequiresSelectionAndAvailableDestination() {
        val empty = RecordsSelectionStateHolder()
        val selected = empty.select(memo("memo-1", version = 3))

        assertNull(empty.nextDetailRequest("memo-1", context()))
        assertNull(selected.nextDetailRequest("other", context()))
        assertNull(selected.nextDetailRequest("memo-1", context(detailAvailable = false)))

        val request = assertNotNull(selected.nextDetailRequest("memo-1", context()))
        assertEquals(1L, request.requestId)
        assertEquals(3L, request.memoVersion)
        assertEquals(
            selected.copy(detailRequestId = 1),
            selected.beginDetailRequest(request, context()),
        )
    }

    @Test
    fun responseCannotCrossContextOrOverwriteNewerMemo() {
        val selected = RecordsSelectionStateHolder(
            selectedMemo = memo("memo-1", version = 3),
            detailRequestId = 4,
        )
        val request = assertNotNull(selected.nextDetailRequest("memo-1", context()))
        val pending = assertNotNull(selected.beginDetailRequest(request, context()))

        assertEquals(
            RecordsDetailResponseDisposition.Apply,
            pending.detailResponseDisposition(request, context(), memo("memo-1", version = 3)),
        )
        assertEquals(
            RecordsDetailResponseDisposition.Ignore,
            pending.detailResponseDisposition(
                request,
                context(destinationKey = "editor"),
                memo("memo-1", version = 3),
            ),
        )
        assertEquals(
            RecordsDetailResponseDisposition.Superseded,
            pending.detailResponseDisposition(
                request,
                context(cacheGeneration = 2),
                memo("memo-1", version = 3),
            ),
        )
        assertEquals(
            RecordsDetailResponseDisposition.Superseded,
            pending.detailResponseDisposition(request, context(), memo("memo-1", version = 2)),
        )
        assertEquals(
            RecordsDetailResponseDisposition.Superseded,
            pending.select(memo("memo-1", version = 4))
                .detailResponseDisposition(request, context(), memo("memo-1", version = 3)),
        )
    }

    @Test
    fun failureDistinguishesIgnoredApplicableAndSupersededRequests() {
        val selected = RecordsSelectionStateHolder(selectedMemo = memo("memo-1", version = 3))
        val request = assertNotNull(selected.nextDetailRequest("memo-1", context()))
        val pending = assertNotNull(selected.beginDetailRequest(request, context()))

        assertEquals(
            RecordsDetailResponseDisposition.Apply,
            pending.detailFailureDisposition(request, context()),
        )
        assertEquals(
            RecordsDetailResponseDisposition.Ignore,
            pending.detailFailureDisposition(request, context(sourceKey = "offline")),
        )
        assertEquals(
            RecordsDetailResponseDisposition.Superseded,
            pending.detailFailureDisposition(request, context(cacheGeneration = 2)),
        )
        assertEquals(
            RecordsDetailResponseDisposition.Superseded,
            pending.select(memo("memo-1", version = 4))
                .detailFailureDisposition(request, context()),
        )
    }

    private fun context(
        sourceKey: String = "online",
        clientContextGeneration: Long = 1,
        destinationKey: String = "detail",
        destinationGeneration: Long = 0,
        cacheGeneration: Long = 1,
        detailAvailable: Boolean = true,
    ): RecordsDetailContext {
        return RecordsDetailContext(
            sourceKey = sourceKey,
            clientContextGeneration = clientContextGeneration,
            destinationKey = destinationKey,
            destinationGeneration = destinationGeneration,
            cacheGeneration = cacheGeneration,
            detailAvailable = detailAvailable,
        )
    }

    private fun memo(id: String, version: Long): Memo {
        return Memo(
            id = id,
            content = id,
            entryDate = "2026-08-01",
            version = version,
            createdAt = "2026-08-01T00:00:00Z",
            updatedAt = "2026-08-01T00:00:00Z",
            favoritedAt = null,
            archivedAt = null,
            deletedAt = null,
        )
    }
}
