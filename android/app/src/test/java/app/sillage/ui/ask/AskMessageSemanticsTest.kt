package app.sillage.ui.ask

import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Assert.assertEquals
import org.junit.Test

class AskMessageSemanticsTest {
    @Test
    fun askMessageExposesSpeakerAndDisplayedContentTogether() {
        val semantics = SemanticsConfiguration()

        semantics.applyAskMessageSemantics("Sillage: The answer")

        assertEquals(
            listOf("Sillage: The answer"),
            semantics[SemanticsProperties.ContentDescription],
        )
    }

    @Test
    fun answerPositionUsesAPoliteLiveRegion() {
        val semantics = SemanticsConfiguration()

        semantics.applyAskVariantSemantics("Answer 2 of 3")

        assertEquals(
            listOf("Answer 2 of 3"),
            semantics[SemanticsProperties.ContentDescription],
        )
        assertEquals(
            LiveRegionMode.Polite,
            semantics[SemanticsProperties.LiveRegion],
        )
    }
}
