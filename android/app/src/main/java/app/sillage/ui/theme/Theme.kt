package app.sillage.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SillageLightColors = lightColorScheme(
    primary = Color(0xFF244E47),
    onPrimary = Color.White,
    secondary = Color(0xFF7A6A44),
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFF8FAFC),
    surfaceContainerLow = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    onSurfaceVariant = Color(0xFF5D766C),
    error = Color(0xFFB42318),
)

@Composable
fun SillageTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) SillageDarkColors else SillageLightColors,
        content = content,
    )
}

private val SillageDarkColors = darkColorScheme(
    primary = Color(0xFF9BD5C8),
    onPrimary = Color(0xFF003C34),
    secondary = Color(0xFFE0CA84),
    background = Color(0xFF111827),
    surface = Color(0xFF111827),
    surfaceContainerLow = Color(0xFF1F2937),
    surfaceContainerHigh = Color(0xFF374151),
    onSurface = Color(0xFFF9FAFB),
    onSurfaceVariant = Color(0xFFB8C7C0),
    error = Color(0xFFFFB4AB),
)
