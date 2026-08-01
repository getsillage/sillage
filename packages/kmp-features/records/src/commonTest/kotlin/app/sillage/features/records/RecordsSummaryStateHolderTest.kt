package app.sillage.features.records

import app.sillage.core.domain.records.Memo
import app.sillage.core.domain.records.MemoAI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordsSummaryStateHolderTest {
    @Test
    fun requestLifecycleOwnsSummaryAndRejectsChangedContext() {
        val memo = memo(version = 3)
        val context = context()
        val idle = RecordsSummaryStateHolder(summary = summary("old"), requestId = 8)
        val request = requireNotNull(idle.nextRequest(memo, context))
        val pending = requireNotNull(idle.begin(request, memo, context))

        assertEquals(9, request.requestId)
        assertTrue(pending.canApply(request, memo, context))
        assertNull(pending.nextRequest(memo, context))
        assertFalse(pending.canApply(request, memo.copy(version = 4), context))
        assertFalse(
            pending.canApply(
                request,
                memo,
                context.copy(clientContextGeneration = context.clientContextGeneration + 1),
            ),
        )
        assertFalse(
            pending.canApply(
                request,
                memo,
                context.copy(detailRequestId = context.detailRequestId + 1),
            ),
        )

        val completed = pending.complete(request, memo, context, summary("new"))
        assertEquals("new", completed.summary?.summary)
        assertFalse(completed.loading)
    }

    @Test
    fun staleCompletionCannotReplaceSummaryAndOwnedRequestCanFinish() {
        val memo = memo()
        val context = context()
        val idle = RecordsSummaryStateHolder(summary = summary("old"))
        val request = requireNotNull(idle.nextRequest(memo, context))
        val pending = requireNotNull(idle.begin(request, memo, context))

        assertEquals(
            pending,
            pending.complete(request, memo.copy(version = 2), context, summary("stale")),
        )
        assertEquals(pending, pending.fail(request, memo.copy(version = 2), context))

        val finished = pending.finish(request)
        assertEquals("old", finished.summary?.summary)
        assertFalse(finished.loading)
    }

    @Test
    fun detailLoadingAndInvalidationUseTheSameSingleFlightState() {
        val idle = RecordsSummaryStateHolder(summary = summary("old"), requestId = 4)

        assertFalse(idle.beginDetailLoad(loadSummary = false).loading)
        assertTrue(idle.beginDetailLoad(loadSummary = true).loading)
        assertNull(idle.completeDetail(null).summary)

        val invalidated = idle.beginDetailLoad(loadSummary = true).invalidate()
        assertFalse(invalidated.loading)
        assertEquals(5, invalidated.requestId)
        assertEquals("replacement", idle.replaceSummary(summary("replacement")).summary?.summary)
    }

    private fun context(): RecordsSummaryContext {
        return RecordsSummaryContext(
            sourceKey = "online",
            clientContextGeneration = 2,
            destinationKey = "detail",
            destinationGeneration = 7,
            detailRequestId = 11,
            summaryAvailable = true,
        )
    }

    private fun memo(version: Long = 1): Memo {
        return Memo(
            id = "memo-1",
            content = "content",
            entryDate = "2026-08-01",
            version = version,
            createdAt = "2026-08-01T00:00:00Z",
            updatedAt = "2026-08-01T00:00:00Z",
            favoritedAt = null,
            archivedAt = null,
            deletedAt = null,
        )
    }

    private fun summary(value: String): MemoAI {
        return MemoAI(
            memoId = "memo-1",
            summary = value,
            sentiment = null,
            provider = "openai-compatible",
            model = "model",
            profileId = "profile-1",
            promptVersion = "v1",
            sourceMemoIds = "[\"memo-1\"]",
            status = "completed",
            errorCode = null,
            startedAt = null,
            finishedAt = null,
            inputTokens = 1,
            outputTokens = 1,
            totalTokens = 2,
            createdAt = "2026-08-01T00:00:00Z",
            updatedAt = "2026-08-01T00:00:00Z",
        )
    }
}
