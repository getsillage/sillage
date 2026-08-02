package app.sillage.ui.records

import kotlin.test.Test
import kotlin.test.assertEquals

class SillageRecordSwipeRowTest {
    @Test
    fun draggedOffsetIsClampedToActionWidth() {
        assertEquals(92f, sillageRecordSwipeDraggedOffset(80f, 20f, 92f))
        assertEquals(-92f, sillageRecordSwipeDraggedOffset(-80f, -20f, 92f))
        assertEquals(30f, sillageRecordSwipeDraggedOffset(10f, 20f, 92f))
    }

    @Test
    fun settleTargetOpensOnlyPastThreshold() {
        assertEquals(92f, sillageRecordSwipeSettleTarget(52f, 92f))
        assertEquals(-92f, sillageRecordSwipeSettleTarget(-52f, 92f))
        assertEquals(0f, sillageRecordSwipeSettleTarget(51.52f, 92f))
        assertEquals(0f, sillageRecordSwipeSettleTarget(-51.52f, 92f))
    }
}
