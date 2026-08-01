package app.sillage.features.ask

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AskComposerStateHolderTest {
    @Test
    fun promptAndRetrievalOptionsChangeIndependently() {
        val state = AskComposerStateHolder()
            .updateQuestion("Question")
            .updateContextScope("all")
            .updateSourceKind("favorites")

        assertEquals("Question", state.question)
        assertEquals("all", state.contextScope)
        assertEquals("favorites", state.sourceKind)
        assertEquals("", state.clearQuestion().question)
        assertEquals("all", state.clearQuestion().contextScope)
    }

    @Test
    fun retrievalOptionsCannotBeBlank() {
        assertFailsWith<IllegalArgumentException> {
            AskComposerStateHolder().updateContextScope(" ")
        }
        assertFailsWith<IllegalArgumentException> {
            AskComposerStateHolder().updateSourceKind("")
        }
    }
}
