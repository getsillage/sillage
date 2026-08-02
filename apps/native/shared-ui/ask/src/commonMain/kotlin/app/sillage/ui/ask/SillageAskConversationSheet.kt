package app.sillage.ui.ask

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sillage.core.domain.ask.AskConversation
import app.sillage.features.ask.AskFeatureStateHolder
import app.sillage.ui.designsystem.applySillageHeadingSemantics

data class SillageAskConversationStrings(
    val title: String,
    val refreshAction: String,
    val emptyConversations: String,
    val untitledConversation: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SillageAskConversationSheet(
    state: AskFeatureStateHolder,
    strings: SillageAskConversationStrings,
    onRefresh: () -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    currentConversationLabel: @Composable (String) -> String,
) {
    val presentation = sillageAskConversationSheetPresentation(state)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    strings.title,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { applySillageHeadingSemantics() },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(
                    onClick = onRefresh,
                    enabled = presentation.refreshEnabled,
                ) {
                    Text(strings.refreshAction)
                }
            }
            SillageAskConversationList(
                conversations = state.conversations,
                activeConversationId = state.activeConversationId,
                enabled = presentation.selectionEnabled,
                strings = strings,
                currentConversationLabel = currentConversationLabel,
                onSelect = { conversationId ->
                    onSelect(conversationId)
                    onDismiss()
                },
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
fun SillageAskConversationList(
    conversations: List<AskConversation>,
    activeConversationId: String,
    enabled: Boolean,
    strings: SillageAskConversationStrings,
    currentConversationLabel: @Composable (String) -> String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (conversations.isEmpty()) {
        Text(
            strings.emptyConversations,
            modifier = modifier,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(conversations, key = { it.id }) { conversation ->
            val title = sillageAskConversationTitle(
                conversation = conversation,
                untitledConversation = strings.untitledConversation,
            )
            val active = conversation.id == activeConversationId

            Card(
                onClick = { onSelect(conversation.id) },
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (active) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                ),
                border = BorderStroke(
                    1.dp,
                    if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
            ) {
                Text(
                    if (active) currentConversationLabel(title) else title,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

internal data class SillageAskConversationSheetPresentation(
    val refreshEnabled: Boolean,
    val selectionEnabled: Boolean,
)

internal fun sillageAskConversationSheetPresentation(
    state: AskFeatureStateHolder,
): SillageAskConversationSheetPresentation = SillageAskConversationSheetPresentation(
    refreshEnabled = !state.loading &&
        !state.sending &&
        !state.variantLoading &&
        state.savingMessageId.isBlank(),
    selectionEnabled = !state.loading &&
        !state.sending &&
        !state.variantLoading &&
        !state.sourceLoading,
)

internal fun sillageAskConversationTitle(
    conversation: AskConversation,
    untitledConversation: String,
): String = conversation.title.ifBlank { untitledConversation }
