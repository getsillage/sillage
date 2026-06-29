package app.sillage.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SillageLightColors = lightColorScheme(
    primary = Color(0xFF1F6B5B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7F2E8),
    onPrimaryContainer = Color(0xFF063D33),
    secondary = Color(0xFF73643D),
    secondaryContainer = Color(0xFFF3E4B5),
    tertiary = Color(0xFF465E7A),
    tertiaryContainer = Color(0xFFD7E3F8),
    background = Color(0xFFF7F8F5),
    surface = Color(0xFFFBFCFA),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF0F4F1),
    surfaceContainerHigh = Color(0xFFE4ECE7),
    onSurface = Color(0xFF111827),
    onSurfaceVariant = Color(0xFF5C6863),
    error = Color(0xFFB42318),
    errorContainer = Color(0xFFFEE4E2),
    onErrorContainer = Color(0xFF7A271A),
)

@Composable
fun SillageTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) SillageDarkColors else SillageLightColors,
        content = content,
    )
}

private val SillageDarkColors = darkColorScheme(
    primary = Color(0xFF8FDCCA),
    onPrimary = Color(0xFF003C34),
    primaryContainer = Color(0xFF0E5549),
    onPrimaryContainer = Color(0xFFCEF4E9),
    secondary = Color(0xFFE0CA84),
    secondaryContainer = Color(0xFF594B28),
    tertiary = Color(0xFFAFC8E8),
    tertiaryContainer = Color(0xFF304861),
    background = Color(0xFF111827),
    surface = Color(0xFF151B22),
    surfaceContainerLow = Color(0xFF1D2530),
    surfaceContainer = Color(0xFF26313D),
    surfaceContainerHigh = Color(0xFF33414E),
    onSurface = Color(0xFFF9FAFB),
    onSurfaceVariant = Color(0xFFBAC8C2),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)
