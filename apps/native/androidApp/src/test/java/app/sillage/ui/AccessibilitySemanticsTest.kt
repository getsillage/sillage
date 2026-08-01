package app.sillage.ui

import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Assert.assertEquals
import org.junit.Test

class AccessibilitySemanticsTest {
    @Test
    fun headingSemanticsExposeAHeadingRole() {
        val semantics = SemanticsConfiguration()

        semantics.applyHeadingSemantics()

        assertEquals(Unit, semantics[SemanticsProperties.Heading])
    }

    @Test
    fun statusExposesOneDescriptionWithoutDuplicatingVisibleText() {
        val semantics = SemanticsConfiguration()

        semantics.applyStatusSemantics("Search results: 2")

        assertEquals(
            listOf("Search results: 2"),
            semantics[SemanticsProperties.ContentDescription],
        )
    }
}
