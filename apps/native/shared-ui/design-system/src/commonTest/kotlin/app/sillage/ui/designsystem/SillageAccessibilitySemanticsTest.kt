package app.sillage.ui.designsystem

import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import kotlin.test.Test
import kotlin.test.assertEquals

class SillageAccessibilitySemanticsTest {
    @Test
    fun headingSemanticsExposeAHeadingRole() {
        val semantics = SemanticsConfiguration()

        semantics.applySillageHeadingSemantics()

        assertEquals(Unit, semantics[SemanticsProperties.Heading])
    }

    @Test
    fun statusExposesOneDescriptionWithoutDuplicatingVisibleText() {
        val semantics = SemanticsConfiguration()

        semantics.applySillageStatusSemantics("Search results: 2")

        assertEquals(
            listOf("Search results: 2"),
            semantics[SemanticsProperties.ContentDescription],
        )
    }
}
