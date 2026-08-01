package app.sillage.ui.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals

class SillageErrorCardTest {
    @Test
    fun errorCardUsesSemanticErrorColors() {
        val lightColors = sillageErrorCardColors(SillageLightColors)
        val darkColors = sillageErrorCardColors(SillageDarkColors)

        assertEquals(SillageLightColors.errorContainer, lightColors.container)
        assertEquals(SillageLightColors.error, lightColors.border)
        assertEquals(SillageLightColors.onErrorContainer, lightColors.content)
        assertEquals(SillageDarkColors.errorContainer, darkColors.container)
        assertEquals(SillageDarkColors.error, darkColors.border)
        assertEquals(SillageDarkColors.onErrorContainer, darkColors.content)
    }
}
