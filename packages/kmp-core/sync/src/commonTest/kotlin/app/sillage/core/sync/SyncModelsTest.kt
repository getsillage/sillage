package app.sillage.core.sync

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncModelsTest {
    @Test
    fun pushSummaryEmptinessUsesAllResultClasses() {
        assertTrue(SyncPushSummary(applied = 0, conflict = 0, rejected = 0).empty)
        assertFalse(SyncPushSummary(applied = 1, conflict = 0, rejected = 0).empty)
        assertFalse(SyncPushSummary(applied = 0, conflict = 1, rejected = 0).empty)
        assertFalse(SyncPushSummary(applied = 0, conflict = 0, rejected = 1).empty)
    }
}
