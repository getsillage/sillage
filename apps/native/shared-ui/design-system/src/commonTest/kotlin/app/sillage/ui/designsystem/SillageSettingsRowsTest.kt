package app.sillage.ui.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals

class SillageSettingsRowsTest {
    @Test
    fun disabledActionUsesDisabledContentColors() {
        val colors = sillageSettingsActionRowColors(
            enabled = false,
            selected = false,
            colorScheme = SillageLightColors,
        )

        assertEquals(SillageLightColors.onSurface.copy(alpha = 0.38f), colors.title)
        assertEquals(
            SillageLightColors.onSurfaceVariant.copy(alpha = 0.38f),
            colors.supporting,
        )
        assertEquals(colors.supporting, colors.icon)
        assertEquals(SillageLightColors.surfaceContainerLow, colors.container)
    }

    @Test
    fun selectedActionRetainsSelectionColorsWhenDisabled() {
        val colors = sillageSettingsActionRowColors(
            enabled = false,
            selected = true,
            colorScheme = SillageDarkColors,
        )

        assertEquals(SillageDarkColors.onSurface, colors.title)
        assertEquals(SillageDarkColors.onSurfaceVariant, colors.supporting)
        assertEquals(SillageDarkColors.primary, colors.icon)
        assertEquals(SillageDarkColors.surfaceContainerHigh, colors.container)
    }

    @Test
    fun disabledSwitchUsesDisabledContentColors() {
        val colors = sillageSettingsSwitchRowColors(
            enabled = false,
            colorScheme = SillageDarkColors,
        )

        assertEquals(SillageDarkColors.onSurface.copy(alpha = 0.38f), colors.title)
        assertEquals(
            SillageDarkColors.onSurfaceVariant.copy(alpha = 0.38f),
            colors.supporting,
        )
        assertEquals(colors.supporting, colors.icon)
    }
}
