package app.sillage.ui.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals

class SillageDesignThemeTest {
    @Test
    fun colorSchemeSelectsStableLightAndDarkTokens() {
        val light = sillageColorScheme(darkTheme = false)
        val dark = sillageColorScheme(darkTheme = true)

        assertEquals(Color(0xFF1F6B5B), light.primary)
        assertEquals(Color(0xFFF7F8F5), light.background)
        assertEquals(Color(0xFF8FDCCA), dark.primary)
        assertEquals(Color(0xFF121513), dark.background)
    }

    @Test
    fun typographyAndShapesRemainSharedAcrossNativeHosts() {
        assertEquals(FontWeight.SemiBold, SillageTypography.titleLarge.fontWeight)
        assertEquals(20.sp, SillageTypography.titleLarge.fontSize)
        assertEquals(28.sp, SillageTypography.titleLarge.lineHeight)
        assertEquals(RoundedCornerShape(8.dp), SillageShapes.medium)
        assertEquals(RoundedCornerShape(4.dp), SillageShapes.extraSmall)
    }
}
