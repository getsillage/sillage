package app.sillage.ui.designsystem

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import kotlin.test.Test
import kotlin.test.assertEquals

class SillageInlineErrorTest {
    @Test
    fun errorIsAnAssertiveLiveRegion() {
        val message = "Sign-in failed"
        val semantics = SemanticsConfiguration()

        semantics.applySillageErrorSemantics(message)

        assertEquals(LiveRegionMode.Assertive, semantics[SemanticsProperties.LiveRegion])
        assertEquals(message, semantics[SemanticsProperties.Error])
    }

    @Test
    fun inlineErrorUsesSemanticErrorColors() {
        val lightColors = sillageInlineErrorColors(SillageLightColors)
        val darkColors = sillageInlineErrorColors(SillageDarkColors)

        assertEquals(SillageLightColors.errorContainer, lightColors.container)
        assertEquals(SillageLightColors.onErrorContainer, lightColors.content)
        assertEquals(SillageLightColors.error.copy(alpha = 0.55f), lightColors.border)
        assertEquals(SillageDarkColors.errorContainer, darkColors.container)
        assertEquals(SillageDarkColors.onErrorContainer, darkColors.content)
        assertEquals(SillageDarkColors.error.copy(alpha = 0.55f), darkColors.border)
    }
}
