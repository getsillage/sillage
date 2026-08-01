package app.sillage.ui.auth

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class SillageModeOptionCardTest {
    @Test
    fun colorsUseHostAccentAndSemanticCardTokens() {
        val colorScheme = lightColorScheme(
            surfaceContainerLow = Color(0xFFF7F2FA),
            outline = Color(0xFF79747E),
            onSurfaceVariant = Color(0xFF49454F),
        )
        val iconContainer = Color(0xFFE8DEF8)
        val iconContent = Color(0xFF1D192B)

        val colors = sillageModeOptionCardColors(
            iconContainer = iconContainer,
            iconContent = iconContent,
            colorScheme = colorScheme,
        )

        assertEquals(colorScheme.surfaceContainerLow, colors.container)
        assertEquals(colorScheme.outline, colors.border)
        assertEquals(colorScheme.onSurfaceVariant, colors.supporting)
        assertEquals(colorScheme.onSurfaceVariant, colors.trailing)
        assertEquals(iconContainer, colors.iconContainer)
        assertEquals(iconContent, colors.iconContent)
    }
}
