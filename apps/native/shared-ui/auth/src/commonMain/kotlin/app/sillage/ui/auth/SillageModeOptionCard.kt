package app.sillage.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SillageModeOptionCard(
    icon: ImageVector,
    trailingIcon: ImageVector,
    title: String,
    supporting: String,
    iconContainer: Color,
    iconContent: Color,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = sillageModeOptionCardColors(
        iconContainer = iconContainer,
        iconContent = iconContent,
        colorScheme = MaterialTheme.colorScheme,
    )

    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = colors.container),
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(8.dp),
                color = colors.iconContainer,
                contentColor = colors.iconContent,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null)
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    supporting,
                    color = colors.supporting,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(
                trailingIcon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = colors.trailing,
            )
        }
    }
}

internal data class SillageModeOptionCardColors(
    val container: Color,
    val border: Color,
    val supporting: Color,
    val trailing: Color,
    val iconContainer: Color,
    val iconContent: Color,
)

internal fun sillageModeOptionCardColors(
    iconContainer: Color,
    iconContent: Color,
    colorScheme: ColorScheme,
) = SillageModeOptionCardColors(
    container = colorScheme.surfaceContainerLow,
    border = colorScheme.outline,
    supporting = colorScheme.onSurfaceVariant,
    trailing = colorScheme.onSurfaceVariant,
    iconContainer = iconContainer,
    iconContent = iconContent,
)
