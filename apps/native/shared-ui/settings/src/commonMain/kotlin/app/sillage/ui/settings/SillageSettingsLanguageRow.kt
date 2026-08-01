package app.sillage.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class SillageSettingsLanguageStrings(
    val title: String,
    val supporting: String,
)

data class SillageSettingsLanguageOption(
    val value: String,
    val label: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SillageSettingsLanguageRow(
    selectedLanguage: String,
    options: List<SillageSettingsLanguageOption>,
    strings: SillageSettingsLanguageStrings,
    icon: ImageVector,
    enabled: Boolean,
    onLanguageChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = sillageSettingsLanguagePresentation(
        selectedLanguage = selectedLanguage,
        options = options,
        strings = strings,
        enabled = enabled,
    )
    val disabledAlpha = 0.38f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (presentation.enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = disabledAlpha)
                },
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    presentation.title,
                    color = if (presentation.enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = disabledAlpha)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    presentation.supporting,
                    color = if (presentation.enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = disabledAlpha)
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 34.dp, top = 10.dp),
        ) {
            presentation.options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option.selected,
                    onClick = { onLanguageChange(option.value) },
                    enabled = presentation.enabled,
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = presentation.options.size,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp),
                    label = {
                        Text(
                            option.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}

internal data class SillageSettingsLanguageOptionPresentation(
    val value: String,
    val label: String,
    val selected: Boolean,
)

internal data class SillageSettingsLanguagePresentation(
    val title: String,
    val supporting: String,
    val options: List<SillageSettingsLanguageOptionPresentation>,
    val enabled: Boolean,
)

internal fun sillageSettingsLanguagePresentation(
    selectedLanguage: String,
    options: List<SillageSettingsLanguageOption>,
    strings: SillageSettingsLanguageStrings,
    enabled: Boolean,
) = SillageSettingsLanguagePresentation(
    title = strings.title,
    supporting = strings.supporting,
    options = options.map { option ->
        SillageSettingsLanguageOptionPresentation(
            value = option.value,
            label = option.label,
            selected = option.value == selectedLanguage,
        )
    },
    enabled = enabled,
)
