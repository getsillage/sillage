package app.sillage.ui.ask

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sillage.features.ask.AskFeatureStateHolder

data class SillageAskContextStrings(
    val recentSevenDays: String,
    val recentThirtyDays: String,
    val allTime: String,
    val recordsSource: String,
    val summariesSource: String,
)

data class SillageAskComposerStrings(
    val context: SillageAskContextStrings,
    val questionLabel: String,
    val sendContentDescription: String,
    val stopContentDescription: String,
)

data class SillageAskComposerIcons(
    val send: ImageVector,
    val stop: ImageVector,
)

@Composable
fun SillageAskContextLabel(
    state: AskFeatureStateHolder,
    strings: SillageAskContextStrings,
    contextSummary: @Composable (scope: String, source: String) -> String,
): String {
    val presentation = sillageAskContextPresentation(state, strings)
    return contextSummary(presentation.scopeLabel, presentation.sourceLabel)
}

@Composable
fun SillageAskComposer(
    state: AskFeatureStateHolder,
    strings: SillageAskComposerStrings,
    icons: SillageAskComposerIcons,
    onQuestionChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    contextSummary: @Composable (scope: String, source: String) -> String,
    characterCount: @Composable (Int) -> String,
    modifier: Modifier = Modifier,
) {
    val presentation = sillageAskComposerPresentation(state, strings.context)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 10.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    contextSummary(
                        presentation.context.scopeLabel,
                        presentation.context.sourceLabel,
                    ),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    characterCount(presentation.trimmedCharacterCount),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = state.question,
                    onValueChange = onQuestionChange,
                    modifier = Modifier.weight(1f),
                    minLines = 1,
                    maxLines = 3,
                    label = { Text(strings.questionLabel) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (presentation.sendEnabled) {
                                onSend()
                            }
                        },
                    ),
                )
                if (presentation.showStop) {
                    FilledIconButton(
                        onClick = onStop,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            icons.stop,
                            contentDescription = strings.stopContentDescription,
                        )
                    }
                } else {
                    FilledIconButton(
                        onClick = onSend,
                        enabled = presentation.sendEnabled,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            icons.send,
                            contentDescription = strings.sendContentDescription,
                        )
                    }
                }
            }
        }
    }
}

internal data class SillageAskContextPresentation(
    val scopeLabel: String,
    val sourceLabel: String,
)

internal data class SillageAskComposerPresentation(
    val context: SillageAskContextPresentation,
    val trimmedCharacterCount: Int,
    val sendEnabled: Boolean,
    val showStop: Boolean,
)

internal fun sillageAskContextPresentation(
    state: AskFeatureStateHolder,
    strings: SillageAskContextStrings,
): SillageAskContextPresentation = SillageAskContextPresentation(
    scopeLabel = when (state.contextScope) {
        "recent_7_days" -> strings.recentSevenDays
        "all" -> strings.allTime
        else -> strings.recentThirtyDays
    },
    sourceLabel = if (state.sourceKind == "summaries") {
        strings.summariesSource
    } else {
        strings.recordsSource
    },
)

internal fun sillageAskComposerPresentation(
    state: AskFeatureStateHolder,
    strings: SillageAskContextStrings,
): SillageAskComposerPresentation = SillageAskComposerPresentation(
    context = sillageAskContextPresentation(state, strings),
    trimmedCharacterCount = state.question.trim().length,
    sendEnabled = !state.loading &&
        !state.sending &&
        !state.variantLoading &&
        !state.sourceLoading &&
        state.question.isNotBlank(),
    showStop = state.sending,
)
