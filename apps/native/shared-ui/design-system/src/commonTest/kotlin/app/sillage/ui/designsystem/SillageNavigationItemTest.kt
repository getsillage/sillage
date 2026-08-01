package app.sillage.ui.designsystem

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class SillageNavigationItemTest {
    @Test
    fun selectedAndUnselectedItemsUseSemanticContentColors() {
        val selected = sillageNavigationItemColors(
            selected = true,
            enabled = true,
            colorScheme = SillageLightColors,
        )
        val unselected = sillageNavigationItemColors(
            selected = false,
            enabled = true,
            colorScheme = SillageLightColors,
        )

        assertEquals(SillageLightColors.onSurface, selected.content)
        assertEquals(SillageLightColors.surfaceContainerHighest, selected.indicator)
        assertEquals(SillageLightColors.onSurfaceVariant, unselected.content)
        assertEquals(Color.Transparent, unselected.indicator)
    }

    @Test
    fun disabledItemUsesDisabledContentAndIndicatorAlpha() {
        val disabled = sillageNavigationItemColors(
            selected = true,
            enabled = false,
            colorScheme = SillageDarkColors,
        )

        assertEquals(SillageDarkColors.onSurface.copy(alpha = 0.38f), disabled.content)
        assertEquals(
            SillageDarkColors.surfaceContainerHighest.copy(alpha = 0.38f),
            disabled.indicator,
        )
    }
}
