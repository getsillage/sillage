package app.sillage.ui.ask

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sillage.features.ask.AskFeatureStateHolder
import app.sillage.ui.designsystem.applySillageHeadingSemantics

data class SillageAskTopBarStrings(
    val title: String,
    val savingContentDescription: String,
    val conversationsContentDescription: String,
    val contextContentDescription: String,
    val newConversationContentDescription: String,
)

data class SillageAskTopBarIcons(
    val conversations: ImageVector,
    val context: ImageVector,
    val newConversation: ImageVector,
)

@Composable
fun SillageAskTopBarTitle(
    state: AskFeatureStateHolder,
    strings: SillageAskTopBarStrings,
    contextStrings: SillageAskContextStrings,
    contextSummary: @Composable (scope: String, source: String) -> String,
    modifier: Modifier = Modifier,
) {
    val presentation = sillageAskTopBarPresentation(state)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                strings.title,
                modifier = Modifier.semantics { applySillageHeadingSemantics() },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                SillageAskContextLabel(
                    state = state,
                    strings = contextStrings,
                    contextSummary = contextSummary,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier.size(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (presentation.saving) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(20.dp)
                        .semantics {
                            contentDescription = strings.savingContentDescription
                        },
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

@Composable
fun SillageAskTopBarActions(
    state: AskFeatureStateHolder,
    strings: SillageAskTopBarStrings,
    icons: SillageAskTopBarIcons,
    onShowConversations: () -> Unit,
    onShowContext: () -> Unit,
    onNewConversation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = sillageAskTopBarPresentation(state)
    Row(modifier = modifier) {
        IconButton(
            onClick = onShowConversations,
            enabled = presentation.controlsEnabled,
        ) {
            Icon(
                icons.conversations,
                contentDescription = strings.conversationsContentDescription,
            )
        }
        IconButton(
            onClick = onShowContext,
            enabled = presentation.controlsEnabled,
        ) {
            Icon(
                icons.context,
                contentDescription = strings.contextContentDescription,
            )
        }
        IconButton(
            onClick = onNewConversation,
            enabled = presentation.controlsEnabled,
        ) {
            Icon(
                icons.newConversation,
                contentDescription = strings.newConversationContentDescription,
            )
        }
    }
}

internal data class SillageAskTopBarPresentation(
    val controlsEnabled: Boolean,
    val saving: Boolean,
)

internal fun sillageAskTopBarPresentation(
    state: AskFeatureStateHolder,
): SillageAskTopBarPresentation = SillageAskTopBarPresentation(
    controlsEnabled = sillageAskContextControlsEnabled(state),
    saving = state.savingMessageId.isNotBlank(),
)

fun sillageAskContextControlsEnabled(state: AskFeatureStateHolder): Boolean =
    !state.loading &&
        !state.sending &&
        !state.variantLoading &&
        !state.sourceLoading
