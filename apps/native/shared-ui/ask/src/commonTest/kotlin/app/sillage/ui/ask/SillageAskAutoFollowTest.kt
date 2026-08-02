package app.sillage.ui.ask

import app.sillage.core.domain.ask.AskMessage
import app.sillage.features.ask.AskFeatureStateHolder
import app.sillage.features.ask.AskLoadStateHolder
import app.sillage.features.ask.AskPathEntry
import app.sillage.features.ask.AskStreamStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SillageAskAutoFollowTest {
    @Test
    fun nearBottomAllowsSmallRemainingDistance() {
        assertTrue(
            isSillageAskListNearBottom(
                lastVisibleIndex = 5,
                totalItemsCount = 6,
                lastVisibleEnd = 1_090,
                viewportEnd = 1_000,
                thresholdPx = 96,
            ),
        )
    }

    @Test
    fun growingAnswerAwayFromBottomStopsFollowing() {
        assertFalse(
            isSillageAskListNearBottom(
                lastVisibleIndex = 5,
                totalItemsCount = 6,
                lastVisibleEnd = 1_400,
                viewportEnd = 1_000,
                thresholdPx = 96,
            ),
        )
    }

    @Test
    fun olderOrEmptyListIsNotNearConversationBottom() {
        assertFalse(
            isSillageAskListNearBottom(
                lastVisibleIndex = 4,
                totalItemsCount = 6,
                lastVisibleEnd = 980,
                viewportEnd = 1_000,
                thresholdPx = 96,
            ),
        )
        assertFalse(
            isSillageAskListNearBottom(
                lastVisibleIndex = null,
                totalItemsCount = 0,
                lastVisibleEnd = null,
                viewportEnd = 1_000,
                thresholdPx = 96,
            ),
        )
    }

    @Test
    fun renderedItemCountMatchesListOrInitialLoader() {
        val entry = entry("a1")
        val liveUser = message("u2", role = "user")
        val active = AskFeatureStateHolder(
            stream = AskStreamStateHolder(
                sending = true,
                liveUser = liveUser,
                liveAnswer = "Partial",
            ),
        )

        assertEquals(3, sillageAskMessageListItemCount(active, listOf(entry)))
        assertEquals(
            0,
            sillageAskMessageListItemCount(
                AskFeatureStateHolder(load = AskLoadStateHolder(loading = true)),
                emptyList(),
            ),
        )
    }

    private fun entry(id: String): AskPathEntry {
        val message = message(id)
        return AskPathEntry(message = message, variants = listOf(message), index = 0)
    }

    private fun message(
        id: String,
        role: String = "assistant",
    ): AskMessage = AskMessage(
        id = id,
        conversationId = "conversation-id",
        role = role,
        content = id,
        parentId = null,
        forkOfId = null,
        status = "complete",
        sourceRefs = emptyList(),
        model = "model",
        promptVersion = "v1",
        createdAt = "2026-08-02T00:00:00Z",
        updatedAt = "2026-08-02T00:00:00Z",
        deletedAt = null,
    )
}
