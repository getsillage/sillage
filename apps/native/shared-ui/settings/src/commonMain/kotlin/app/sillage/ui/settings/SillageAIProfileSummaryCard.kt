package app.sillage.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sillage.features.settings.SettingsFeatureStateHolder
import app.sillage.features.settings.editorKey

private const val AnthropicProvider = "anthropic"

data class SillageAIProfileSummaryStrings(
    val unnamedProfile: String,
    val anthropicCompatible: String,
    val openAICompatible: String,
    val defaultProfile: String,
    val modelUnset: String,
    val keyPresent: String,
    val keyMissing: String,
    val keyError: String,
    val configure: String,
    val currentDefault: String,
    val setDefault: String,
)

@Composable
fun SillageAIProfileSummaryCard(
    state: SettingsFeatureStateHolder,
    profileIndex: Int,
    strings: SillageAIProfileSummaryStrings,
    selected: Boolean,
    editingBlocked: Boolean,
    mutationBlocked: Boolean,
    onConfigure: () -> Unit,
    onSetDefault: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = sillageAIProfileSummaryPresentation(
        state = state,
        profileIndex = profileIndex,
        strings = strings,
        editingBlocked = editingBlocked,
        mutationBlocked = mutationBlocked,
    )
    val colors = sillageAIProfileSummaryColors(
        selected = selected,
        colorScheme = MaterialTheme.colorScheme,
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = colors.container),
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        presentation.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        presentation.providerLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (presentation.active) {
                    AssistChip(
                        onClick = onConfigure,
                        label = { Text(strings.defaultProfile) },
                        enabled = presentation.configureEnabled,
                    )
                }
            }
            Text(
                presentation.model,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    presentation.keyStatus,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
                if (presentation.keyUnavailable) {
                    Text(
                        strings.keyError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            presentation.testResult?.let { result ->
                Text(
                    result,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onConfigure,
                    enabled = presentation.configureEnabled,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(strings.configure)
                }
                TextButton(
                    onClick = onSetDefault,
                    enabled = presentation.setDefaultEnabled,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(presentation.setDefaultLabel)
                }
            }
        }
    }
}

internal data class SillageAIProfileSummaryPresentation(
    val name: String,
    val providerLabel: String,
    val model: String,
    val keyStatus: String,
    val keyUnavailable: Boolean,
    val active: Boolean,
    val testResult: String?,
    val configureEnabled: Boolean,
    val setDefaultEnabled: Boolean,
    val setDefaultLabel: String,
)

internal fun sillageAIProfileSummaryPresentation(
    state: SettingsFeatureStateHolder,
    profileIndex: Int,
    strings: SillageAIProfileSummaryStrings,
    editingBlocked: Boolean,
    mutationBlocked: Boolean,
): SillageAIProfileSummaryPresentation {
    val profile = state.profiles[profileIndex]
    return SillageAIProfileSummaryPresentation(
        name = profile.name.ifBlank { strings.unnamedProfile },
        providerLabel = if (profile.provider.equals(AnthropicProvider, ignoreCase = true)) {
            strings.anthropicCompatible
        } else {
            strings.openAICompatible
        },
        model = profile.model.ifBlank { strings.modelUnset },
        keyStatus = if (profile.hasApiKey || profile.apiKeyInput.isNotBlank()) {
            strings.keyPresent
        } else {
            strings.keyMissing
        },
        keyUnavailable = profile.keyUnavailable,
        active = profile.active,
        testResult = state.testResults[profile.editorKey(profileIndex)],
        configureEnabled = !editingBlocked,
        setDefaultEnabled = !profile.active && !mutationBlocked,
        setDefaultLabel = if (profile.active) strings.currentDefault else strings.setDefault,
    )
}

internal data class SillageAIProfileSummaryColors(
    val container: Color,
    val border: Color,
)

internal fun sillageAIProfileSummaryColors(
    selected: Boolean,
    colorScheme: ColorScheme,
) = SillageAIProfileSummaryColors(
    container = if (selected) {
        colorScheme.surfaceContainerHigh
    } else {
        colorScheme.surfaceContainerLow
    },
    border = if (selected) colorScheme.primary else colorScheme.outlineVariant,
)
