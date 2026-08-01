package app.sillage.ui.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals

class SillageSettingsSectionCardTest {
    @Test
    fun settingsSectionUsesSemanticSurfaceColors() {
        val lightColors = sillageSettingsSectionColors(SillageLightColors)
        val darkColors = sillageSettingsSectionColors(SillageDarkColors)

        assertEquals(SillageLightColors.onSurfaceVariant, lightColors.title)
        assertEquals(SillageLightColors.surfaceContainerLow, lightColors.container)
        assertEquals(SillageLightColors.outlineVariant, lightColors.border)
        assertEquals(SillageDarkColors.onSurfaceVariant, darkColors.title)
        assertEquals(SillageDarkColors.surfaceContainerLow, darkColors.container)
        assertEquals(SillageDarkColors.outlineVariant, darkColors.border)
    }
}
