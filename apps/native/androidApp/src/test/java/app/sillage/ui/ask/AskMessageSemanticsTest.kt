package app.sillage.ui.ask

import androidx.compose.ui.semantics.SemanticsConfiguration
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
}
