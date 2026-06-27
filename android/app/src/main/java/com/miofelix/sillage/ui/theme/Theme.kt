package com.miofelix.sillage.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SillageColors = lightColorScheme(
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
fun SillageTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SillageColors,
        content = content,
    )
}
