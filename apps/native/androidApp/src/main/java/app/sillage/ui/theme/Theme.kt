package app.sillage.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val SillageLightColors = lightColorScheme(
    primary = Color(0xFF1F6B5B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7F2E8),
    onPrimaryContainer = Color(0xFF063D33),
    inversePrimary = Color(0xFF8FDCCA),
    secondary = Color(0xFF73643D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E4B5),
    onSecondaryContainer = Color(0xFF3A3014),
    tertiary = Color(0xFF465E7A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD7E3F8),
    onTertiaryContainer = Color(0xFF1C324C),
    background = Color(0xFFF7F8F5),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFBFCFA),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE4ECE7),
    onSurfaceVariant = Color(0xFF52605A),
    surfaceTint = Color(0xFF1F6B5B),
    inverseSurface = Color(0xFF29332F),
    inverseOnSurface = Color(0xFFEDF3EF),
    error = Color(0xFFB42318),
    onError = Color.White,
    errorContainer = Color(0xFFFEE4E2),
    onErrorContainer = Color(0xFF7A271A),
    outline = Color(0xFF718078),
    outlineVariant = Color(0xFFC5D0CA),
    scrim = Color.Black,
    surfaceBright = Color(0xFFFBFCFA),
    surfaceDim = Color(0xFFD7DFDA),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF4F7F5),
    surfaceContainer = Color(0xFFEDF2EF),
    surfaceContainerHigh = Color(0xFFE4EBE7),
    surfaceContainerHighest = Color(0xFFDCE4DF),
)

private val SillageDarkColors = darkColorScheme(
    primary = Color(0xFF8FDCCA),
    onPrimary = Color(0xFF003C34),
    primaryContainer = Color(0xFF0E5549),
    onPrimaryContainer = Color(0xFFCEF4E9),
    inversePrimary = Color(0xFF1F6B5B),
    secondary = Color(0xFFE0CA84),
    onSecondary = Color(0xFF3C2F00),
    secondaryContainer = Color(0xFF594B28),
    onSecondaryContainer = Color(0xFFF8E6A3),
    tertiary = Color(0xFFAFC8E8),
    onTertiary = Color(0xFF17324D),
    tertiaryContainer = Color(0xFF304861),
    onTertiaryContainer = Color(0xFFD7E8FF),
    background = Color(0xFF121513),
    onBackground = Color(0xFFF3F7F5),
    surface = Color(0xFF151816),
    onSurface = Color(0xFFF3F7F5),
    surfaceVariant = Color(0xFF343C37),
    onSurfaceVariant = Color(0xFFBCC7C0),
    surfaceTint = Color(0xFF8FDCCA),
    inverseSurface = Color(0xFFE5ECE8),
    inverseOnSurface = Color(0xFF252B27),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF89958E),
    outlineVariant = Color(0xFF414A44),
    scrim = Color.Black,
    surfaceBright = Color(0xFF3A423D),
    surfaceDim = Color(0xFF121513),
    surfaceContainerLowest = Color(0xFF0E100F),
    surfaceContainerLow = Color(0xFF191D1A),
    surfaceContainer = Color(0xFF202521),
    surfaceContainerHigh = Color(0xFF292F2B),
    surfaceContainerHighest = Color(0xFF343B36),
)

private val SillageTypography = Typography(
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.sp,
    ),
)

private val SillageShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

@Composable
fun SillageTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    val colors = if (darkTheme) SillageDarkColors else SillageLightColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            updateSystemBars(window = window, view = view, darkTheme = darkTheme)
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = SillageTypography,
        shapes = SillageShapes,
        content = content,
    )
}

private fun updateSystemBars(
    window: android.view.Window,
    view: android.view.View,
    darkTheme: Boolean,
) {
    WindowCompat.getInsetsController(window, view).apply {
        isAppearanceLightStatusBars = !darkTheme
        isAppearanceLightNavigationBars = !darkTheme
    }
}
