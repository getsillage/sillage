package app.sillage.ui.ask

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.sillage.core.domain.ask.AskMessage

@Composable
fun SillageAskMessageCard(
    message: AskMessage,
    streamingText: String?,
    regenerating: Boolean,
    regeneratingText: String,
    messageDescription: @Composable (isAssistant: Boolean, content: String) -> String,
    finalAssistantContent: @Composable (content: String) -> Unit,
    sourceContent: @Composable () -> Unit,
    actionContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = sillageAskMessageCardPresentation(
        message = message,
        streamingText = streamingText,
        regenerating = regenerating,
        regeneratingText = regeneratingText,
    )
    val description = messageDescription(
        presentation.isAssistant,
        presentation.displayedContent,
    )
    val bubbleColor = if (presentation.isAssistant) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val textColor = if (presentation.isAssistant) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (presentation.isAssistant) Alignment.Start else Alignment.End,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(if (presentation.isAssistant) 0.94f else 0.84f),
            shape = RoundedCornerShape(
                topStart = 8.dp,
                topEnd = 8.dp,
                bottomEnd = if (presentation.isAssistant) 8.dp else 2.dp,
                bottomStart = if (presentation.isAssistant) 2.dp else 8.dp,
            ),
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            border = BorderStroke(
                1.dp,
                if (presentation.isAssistant) {
                    MaterialTheme.colorScheme.outlineVariant
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                },
            ),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (presentation.useFinalAssistantContent) {
                    Box(
                        modifier = Modifier.clearAndSetSemantics {
                            applySillageAskMessageSemantics(description)
                        },
                    ) {
                        finalAssistantContent(presentation.displayedContent)
                    }
                } else {
                    Text(
                        presentation.displayedContent,
                        modifier = Modifier.clearAndSetSemantics {
                            applySillageAskMessageSemantics(description)
                        },
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (presentation.isAssistant) {
                    sourceContent()
                    actionContent()
                }
            }
        }
    }
}

internal data class SillageAskMessageCardPresentation(
    val isAssistant: Boolean,
    val displayedContent: String,
    val useFinalAssistantContent: Boolean,
)

internal fun sillageAskMessageCardPresentation(
    message: AskMessage,
    streamingText: String?,
    regenerating: Boolean,
    regeneratingText: String,
): SillageAskMessageCardPresentation {
    val isAssistant = message.role == "assistant"
    val displayedContent = when {
        streamingText != null && streamingText.isNotBlank() -> streamingText
        regenerating -> regeneratingText
        else -> message.content
    }
    return SillageAskMessageCardPresentation(
        isAssistant = isAssistant,
        displayedContent = displayedContent,
        useFinalAssistantContent = isAssistant && streamingText == null && !regenerating,
    )
}

internal fun SemanticsPropertyReceiver.applySillageAskMessageSemantics(description: String) {
    contentDescription = description
}
