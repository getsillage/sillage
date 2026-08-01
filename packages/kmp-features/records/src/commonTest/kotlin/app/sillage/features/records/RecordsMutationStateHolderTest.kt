package app.sillage.features.records

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecordsMutationStateHolderTest {
    @Test
    fun tracksIndependentMemoMutationsIdempotently() {
        val first = RecordsMutationStateHolder().begin("memo-1")
        val both = first.begin("memo-2").begin("memo-1")

        assertTrue(both.active)
        assertTrue(both.isActive("memo-1"))
        assertTrue(both.isActive("memo-2"))
        assertEquals(setOf("memo-1", "memo-2"), both.activeMemoIds)

        val remaining = both.finish("memo-1")
        assertFalse(remaining.isActive("memo-1"))
        assertTrue(remaining.isActive("memo-2"))
        assertFalse(remaining.finish("memo-2").active)
    }

    @Test
    fun editorOnlyMutationDoesNotInventMemoIdentity() {
        val idle = RecordsMutationStateHolder()

        assertEquals(idle, idle.begin(null))
        assertEquals(idle, idle.finish(null))
        assertEquals(idle, idle.clear())
    }
}
