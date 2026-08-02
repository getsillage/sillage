package app.sillage.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private const val DisabledContentAlpha = 0.38f

@Composable
fun SillageSettingsInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    showDivider: Boolean = false,
) {
    Column(modifier = modifier) {
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 14.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            SelectionContainer {
                Text(
                    value,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
fun SillageSettingsActionRow(
    icon: ImageVector,
    title: String,
    supporting: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    showDivider: Boolean = false,
) {
    val colors = sillageSettingsActionRowColors(
        enabled = enabled,
        selected = selected,
        colorScheme = MaterialTheme.colorScheme,
    )

    Column(modifier = modifier) {
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 50.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            color = colors.container,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 68.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = colors.icon,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        title,
                        color = colors.title,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (supporting.isNotBlank()) {
                        Text(
                            supporting,
                            color = colors.supporting,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SillageSettingsSwitchRow(
    icon: ImageVector,
    title: String,
    supporting: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = sillageSettingsSwitchRowColors(
        enabled = enabled,
        colorScheme = MaterialTheme.colorScheme,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .heightIn(min = 68.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = colors.icon,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                color = colors.title,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                supporting,
                color = colors.supporting,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
        )
    }
}

@Composable
fun SillageSettingsEmptyCard(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text,
            modifier = Modifier.padding(14.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

internal data class SillageSettingsActionRowColors(
    val title: Color,
    val supporting: Color,
    val icon: Color,
    val container: Color,
)

internal fun sillageSettingsActionRowColors(
    enabled: Boolean,
    selected: Boolean,
    colorScheme: ColorScheme,
): SillageSettingsActionRowColors {
    val activeContent = enabled || selected
    val title = colorScheme.onSurface.withEnabledAlpha(activeContent)
    val supporting = colorScheme.onSurfaceVariant.withEnabledAlpha(activeContent)

    return SillageSettingsActionRowColors(
        title = title,
        supporting = supporting,
        icon = if (selected) colorScheme.primary else supporting,
        container = if (selected) {
            colorScheme.surfaceContainerHigh
        } else {
            colorScheme.surfaceContainerLow
        },
    )
}

internal data class SillageSettingsSwitchRowColors(
    val title: Color,
    val supporting: Color,
    val icon: Color,
)

internal fun sillageSettingsSwitchRowColors(
    enabled: Boolean,
    colorScheme: ColorScheme,
): SillageSettingsSwitchRowColors {
    val title = colorScheme.onSurface.withEnabledAlpha(enabled)
    val supporting = colorScheme.onSurfaceVariant.withEnabledAlpha(enabled)

    return SillageSettingsSwitchRowColors(
        title = title,
        supporting = supporting,
        icon = supporting,
    )
}

private fun Color.withEnabledAlpha(enabled: Boolean) =
    if (enabled) this else copy(alpha = DisabledContentAlpha)
