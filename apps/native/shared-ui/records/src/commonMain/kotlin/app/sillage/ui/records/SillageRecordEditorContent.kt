package app.sillage.ui.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.sillage.core.domain.records.Memo

data class SillageRecordEditorContentStrings(
    val entryDateLabel: String,
    val entryDatePlaceholder: String,
    val pickDateContentDescription: String,
    val favoritedStatus: String,
    val archivedStatus: String,
    val addAttachmentAction: String,
    val uploadingAttachmentAction: String,
)

data class SillageRecordEditorContentIcons(
    val pickDate: ImageVector,
    val addAttachment: ImageVector,
)

@Composable
fun SillageRecordEditorContent(
    memo: Memo?,
    entryDate: String,
    actionsEnabled: Boolean,
    showAttachmentAction: Boolean,
    uploadingAttachment: Boolean,
    strings: SillageRecordEditorContentStrings,
    icons: SillageRecordEditorContentIcons,
    onEntryDateChange: (String) -> Unit,
    onPickDate: () -> Unit,
    onAddAttachment: () -> Unit,
    editorContent: @Composable (Modifier, Dp) -> Unit,
    summaryContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = sillageRecordEditorContentPresentation(
        memo = memo,
        showAttachmentAction = showAttachmentAction,
        uploadingAttachment = uploadingAttachment,
        strings = strings,
    )

    BoxWithConstraints(modifier = modifier) {
        val editorHeight = (maxHeight * 0.6f).coerceIn(320.dp, 560.dp)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SillageRecordStatusLine(
                        memo = memo,
                        favoritedStatus = strings.favoritedStatus,
                        archivedStatus = strings.archivedStatus,
                    )
                    OutlinedTextField(
                        value = entryDate,
                        onValueChange = onEntryDateChange,
                        enabled = actionsEnabled,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(strings.entryDateLabel) },
                        placeholder = { Text(strings.entryDatePlaceholder) },
                        trailingIcon = {
                            IconButton(
                                onClick = onPickDate,
                                enabled = actionsEnabled,
                            ) {
                                Icon(
                                    icons.pickDate,
                                    contentDescription = strings.pickDateContentDescription,
                                )
                            }
                        },
                    )
                }
            }

            item {
                editorContent(
                    Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxWidth(),
                    editorHeight,
                )
            }

            if (presentation.showAttachmentAction) {
                item {
                    Row(
                        modifier = Modifier
                            .widthIn(max = 760.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = onAddAttachment,
                            enabled = actionsEnabled,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .widthIn(min = 112.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                icons.addAttachment,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(presentation.attachmentAction)
                        }
                    }
                }
            }

            if (presentation.showSummary) {
                item {
                    summaryContent(
                        Modifier
                            .widthIn(max = 760.dp)
                            .fillMaxWidth(),
                    )
                }
            }
        }
    }
}

internal data class SillageRecordEditorContentPresentation(
    val showAttachmentAction: Boolean,
    val attachmentAction: String,
    val showSummary: Boolean,
)

internal fun sillageRecordEditorContentPresentation(
    memo: Memo?,
    showAttachmentAction: Boolean,
    uploadingAttachment: Boolean,
    strings: SillageRecordEditorContentStrings,
): SillageRecordEditorContentPresentation = SillageRecordEditorContentPresentation(
    showAttachmentAction = showAttachmentAction,
    attachmentAction = if (uploadingAttachment) {
        strings.uploadingAttachmentAction
    } else {
        strings.addAttachmentAction
    },
    showSummary = memo != null,
)
