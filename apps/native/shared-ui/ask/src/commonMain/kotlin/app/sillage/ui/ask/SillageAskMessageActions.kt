package app.sillage.ui.ask

import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.sillage.core.domain.ask.AskMessage

class SillageAskMessageActionStrings(
    val previousVariantContentDescription: String,
    val nextVariantContentDescription: String,
    val variantCounter: @Composable (position: Int, total: Int) -> String,
    val variantPositionDescription: @Composable (position: Int, total: Int) -> String,
    val regenerateContentDescription: String,
    val generatingContentDescription: String,
    val saveContentDescription: String,
    val savingContentDescription: String,
)

class SillageAskMessageActionIcons(
    val previousVariant: ImageVector,
    val nextVariant: ImageVector,
    val regenerate: ImageVector,
    val save: ImageVector,
)

@Composable
fun SillageAskMessageActions(
    message: AskMessage,
    variants: List<AskMessage>,
    selectedIndex: Int,
    canRegenerate: Boolean,
    regenerating: Boolean,
    variantChanging: Boolean,
    savingDisabled: Boolean,
    saving: Boolean,
    strings: SillageAskMessageActionStrings,
    icons: SillageAskMessageActionIcons,
    onRegenerate: () -> Unit,
    onSave: () -> Unit,
    onSelectVariant: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = sillageAskMessageActionsPresentation(
        content = message.content,
        variantIds = variants.map(AskMessage::id),
        selectedIndex = selectedIndex,
        canRegenerate = canRegenerate,
        regenerating = regenerating,
        variantChanging = variantChanging,
        savingDisabled = savingDisabled,
    )
    if (!presentation.visible) return

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (presentation.showVariants) {
            IconButton(
                onClick = {
                    presentation.previousVariantId?.let(onSelectVariant)
                },
                enabled = presentation.previousVariantEnabled,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    icons.previousVariant,
                    contentDescription = strings.previousVariantContentDescription,
                    modifier = Modifier.size(20.dp),
                )
            }
            val variantPositionDescription = strings.variantPositionDescription(
                presentation.position,
                presentation.totalVariants,
            )
            Text(
                strings.variantCounter(
                    presentation.position,
                    presentation.totalVariants,
                ),
                modifier = Modifier.clearAndSetSemantics {
                    applySillageAskVariantSemantics(variantPositionDescription)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            IconButton(
                onClick = {
                    presentation.nextVariantId?.let(onSelectVariant)
                },
                enabled = presentation.nextVariantEnabled,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    icons.nextVariant,
                    contentDescription = strings.nextVariantContentDescription,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (presentation.showRegenerate) {
            IconButton(
                onClick = onRegenerate,
                enabled = presentation.regenerateEnabled,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    icons.regenerate,
                    contentDescription = if (regenerating) {
                        strings.generatingContentDescription
                    } else {
                        strings.regenerateContentDescription
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (presentation.showSave) {
            IconButton(
                onClick = onSave,
                enabled = presentation.saveEnabled,
                modifier = Modifier.size(48.dp),
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(20.dp)
                            .semantics {
                                contentDescription = strings.savingContentDescription
                            },
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        icons.save,
                        contentDescription = strings.saveContentDescription,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

internal data class SillageAskMessageActionsPresentation(
    val visible: Boolean,
    val showVariants: Boolean,
    val position: Int,
    val totalVariants: Int,
    val previousVariantId: String?,
    val previousVariantEnabled: Boolean,
    val nextVariantId: String?,
    val nextVariantEnabled: Boolean,
    val showRegenerate: Boolean,
    val regenerateEnabled: Boolean,
    val showSave: Boolean,
    val saveEnabled: Boolean,
)

internal fun sillageAskMessageActionsPresentation(
    content: String,
    variantIds: List<String>,
    selectedIndex: Int,
    canRegenerate: Boolean,
    regenerating: Boolean,
    variantChanging: Boolean,
    savingDisabled: Boolean,
): SillageAskMessageActionsPresentation {
    val showVariants = variantIds.size > 1
    val previousVariantId = variantIds.getOrNull(selectedIndex - 1)
    val nextVariantId = if (selectedIndex >= 0) {
        variantIds.getOrNull(selectedIndex + 1)
    } else {
        null
    }
    val showRegenerate = canRegenerate || regenerating
    val showSave = content.isNotBlank()
    return SillageAskMessageActionsPresentation(
        visible = showVariants || showRegenerate || showSave,
        showVariants = showVariants,
        position = selectedIndex + 1,
        totalVariants = variantIds.size,
        previousVariantId = previousVariantId,
        previousVariantEnabled = previousVariantId != null &&
            !regenerating &&
            !variantChanging,
        nextVariantId = nextVariantId,
        nextVariantEnabled = nextVariantId != null &&
            !regenerating &&
            !variantChanging,
        showRegenerate = showRegenerate,
        regenerateEnabled = canRegenerate && !regenerating,
        showSave = showSave,
        saveEnabled = !savingDisabled && !regenerating,
    )
}

internal fun SemanticsPropertyReceiver.applySillageAskVariantSemantics(description: String) {
    contentDescription = description
    liveRegion = LiveRegionMode.Polite
}
