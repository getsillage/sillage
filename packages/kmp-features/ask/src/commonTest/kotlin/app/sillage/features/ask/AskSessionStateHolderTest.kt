package app.sillage.features.ask

import kotlin.test.Test
import kotlin.test.assertEquals

class AskSessionStateHolderTest {
    @Test
    fun advancingSessionIsMonotonic() {
        val advanced = AskSessionStateHolder(generation = 7).advance()

        assertEquals(8, advanced.generation)
        assertEquals(9, advanced.advance().generation)
    }
}
