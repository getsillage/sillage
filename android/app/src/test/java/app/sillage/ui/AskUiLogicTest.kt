package app.sillage.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AskUiLogicTest {
    @Test
    fun nearBottomAllowsSmallRemainingDistance() {
        assertTrue(
            isAskListNearBottom(
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
            isAskListNearBottom(
                lastVisibleIndex = 5,
                totalItemsCount = 6,
                lastVisibleEnd = 1_400,
                viewportEnd = 1_000,
                thresholdPx = 96,
            ),
        )
    }

    @Test
    fun visibleOlderItemIsNotNearConversationBottom() {
        assertFalse(
            isAskListNearBottom(
                lastVisibleIndex = 4,
                totalItemsCount = 6,
                lastVisibleEnd = 980,
                viewportEnd = 1_000,
                thresholdPx = 96,
            ),
        )
    }

    @Test
    fun emptyListIsNotNearBottom() {
        assertFalse(
            isAskListNearBottom(
                lastVisibleIndex = null,
                totalItemsCount = 0,
                lastVisibleEnd = null,
                viewportEnd = 1_000,
                thresholdPx = 96,
            ),
        )
    }
}
