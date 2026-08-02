package app.sillage.ui.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val NavigationIndicatorWidth = 40.dp
private val NavigationIndicatorHeight = 26.dp

@Composable
fun RowScope.SillageNavigationItem(
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    val targetColors = sillageNavigationItemColors(
        selected = selected,
        enabled = enabled,
        colorScheme = MaterialTheme.colorScheme,
    )
    val contentColor by animateColorAsState(
        targetValue = targetColors.content,
        label = "navigationContentColor",
    )
    val indicatorColor by animateColorAsState(
        targetValue = targetColors.indicator,
        label = "navigationIndicatorColor",
    )

    Column(
        modifier = modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(
                selected = selected,
                onClick = onClick,
                enabled = enabled,
                role = Role.Tab,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .width(NavigationIndicatorWidth)
                .height(NavigationIndicatorHeight)
                .background(
                    color = indicatorColor,
                    shape = RoundedCornerShape(NavigationIndicatorHeight / 2),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = contentColor,
            )
        }
        Text(
            text = label,
            modifier = Modifier.padding(top = 2.dp),
            color = contentColor,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                letterSpacing = 0.sp,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal data class SillageNavigationItemColors(
    val content: Color,
    val indicator: Color,
)

internal fun sillageNavigationItemColors(
    selected: Boolean,
    enabled: Boolean,
    colorScheme: ColorScheme,
): SillageNavigationItemColors {
    val disabledColor = colorScheme.onSurface.copy(alpha = 0.38f)
    val content = when {
        !enabled -> disabledColor
        selected -> colorScheme.onSurface
        else -> colorScheme.onSurfaceVariant
    }
    val indicator = if (selected) {
        colorScheme.surfaceContainerHighest.copy(alpha = if (enabled) 1f else 0.38f)
    } else {
        Color.Transparent
    }
    return SillageNavigationItemColors(content = content, indicator = indicator)
}
