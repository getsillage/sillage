package app.sillage.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp

private const val ErrorBorderAlpha = 0.55f

@Composable
fun SillageInlineError(
    message: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val colors = sillageInlineErrorColors(MaterialTheme.colorScheme)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { applySillageErrorSemantics(message) },
        shape = RoundedCornerShape(8.dp),
        color = colors.container,
        contentColor = colors.content,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

fun SemanticsPropertyReceiver.applySillageErrorSemantics(message: String) {
    liveRegion = LiveRegionMode.Assertive
    error(message)
}

internal data class SillageInlineErrorColors(
    val container: Color,
    val content: Color,
    val border: Color,
)

internal fun sillageInlineErrorColors(colorScheme: ColorScheme) = SillageInlineErrorColors(
    container = colorScheme.errorContainer,
    content = colorScheme.onErrorContainer,
    border = colorScheme.error.copy(alpha = ErrorBorderAlpha),
)
