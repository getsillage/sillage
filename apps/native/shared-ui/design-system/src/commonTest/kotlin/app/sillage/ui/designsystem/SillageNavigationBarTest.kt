package app.sillage.ui.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals

class SillageNavigationBarTest {
    @Test
    fun navigationBarUsesSemanticContainerAndDividerColors() {
        val lightColors = sillageNavigationBarColors(SillageLightColors)
        val darkColors = sillageNavigationBarColors(SillageDarkColors)

        assertEquals(SillageLightColors.surfaceContainerLow, lightColors.container)
        assertEquals(SillageLightColors.onSurface.copy(alpha = 0.08f), lightColors.divider)
        assertEquals(SillageDarkColors.surfaceContainerLow, darkColors.container)
        assertEquals(SillageDarkColors.onSurface.copy(alpha = 0.08f), darkColors.divider)
    }
}
