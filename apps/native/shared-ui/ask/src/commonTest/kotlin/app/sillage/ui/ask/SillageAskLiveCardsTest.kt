package app.sillage.ui.ask

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SillageAskLiveCardsTest {
    @Test
    fun blankAnswerUsesThinkingFallback() {
        val presentation = sillageAskLiveAnswerPresentation(
            answer = "",
            thinking = "Thinking…",
        )

        assertEquals("Thinking…", presentation.displayedContent)
        assertTrue(presentation.waiting)
    }

    @Test
    fun whitespaceAnswerAlsoUsesThinkingFallback() {
        val presentation = sillageAskLiveAnswerPresentation(
            answer = "   ",
            thinking = "Thinking…",
        )

        assertEquals("Thinking…", presentation.displayedContent)
        assertTrue(presentation.waiting)
    }

    @Test
    fun streamedAnswerIsPreserved() {
        val presentation = sillageAskLiveAnswerPresentation(
            answer = "A grounded answer",
            thinking = "Thinking…",
        )

        assertEquals("A grounded answer", presentation.displayedContent)
        assertFalse(presentation.waiting)
    }
}
