package app.sillage.ui.records

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.sillage.core.domain.records.Memo

data class SillageRecordEditorActionStrings(
    val saveContentDescription: String,
    val savingContentDescription: String,
    val attachmentUploadingContentDescription: String,
    val moreContentDescription: String,
    val favoriteAction: String,
    val unfavoriteAction: String,
    val archiveAction: String,
    val unarchiveAction: String,
    val deleteAction: String,
    val deleteTitle: String,
    val deleteSupporting: String,
    val confirmDeleteAction: String,
    val cancelAction: String,
)

data class SillageRecordEditorActionIcons(
    val save: ImageVector,
    val more: ImageVector,
    val favorite: ImageVector,
    val unfavorite: ImageVector,
    val archive: ImageVector,
    val delete: ImageVector,
)

@Composable
fun SillageRecordEditorActions(
    memo: Memo?,
    actionsEnabled: Boolean,
    saving: Boolean,
    uploadingAttachment: Boolean,
    deleteDismissEnabled: Boolean,
    strings: SillageRecordEditorActionStrings,
    icons: SillageRecordEditorActionIcons,
    onSave: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val presentation = sillageRecordEditorActionPresentation(
        memo = memo,
        actionsEnabled = actionsEnabled,
        saving = saving,
        uploadingAttachment = uploadingAttachment,
        strings = strings,
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(strings.deleteTitle) },
            text = { Text(strings.deleteSupporting) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    enabled = presentation.actionsEnabled,
                ) {
                    Text(strings.confirmDeleteAction)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmDelete = false },
                    enabled = deleteDismissEnabled,
                ) {
                    Text(strings.cancelAction)
                }
            },
        )
    }

    IconButton(
        onClick = onSave,
        enabled = presentation.actionsEnabled,
    ) {
        if (presentation.showSaveProgress) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(20.dp)
                    .semantics { contentDescription = presentation.saveContentDescription },
                strokeWidth = 2.dp,
            )
        } else {
            Icon(icons.save, contentDescription = presentation.saveContentDescription)
        }
    }

    if (memo != null) {
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                enabled = presentation.actionsEnabled,
            ) {
                Icon(icons.more, contentDescription = strings.moreContentDescription)
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(presentation.favoriteAction) },
                    leadingIcon = {
                        Icon(
                            if (presentation.favorited) icons.unfavorite else icons.favorite,
                            contentDescription = null,
                        )
                    },
                    enabled = presentation.actionsEnabled,
                    onClick = {
                        menuExpanded = false
                        onToggleFavorite()
                    },
                )
                DropdownMenuItem(
                    text = { Text(presentation.archiveAction) },
                    leadingIcon = { Icon(icons.archive, contentDescription = null) },
                    enabled = presentation.actionsEnabled,
                    onClick = {
                        menuExpanded = false
                        onToggleArchive()
                    },
                )
                DropdownMenuItem(
                    text = { Text(strings.deleteAction) },
                    leadingIcon = { Icon(icons.delete, contentDescription = null) },
                    enabled = presentation.actionsEnabled,
                    onClick = {
                        menuExpanded = false
                        confirmDelete = true
                    },
                )
            }
        }
    }
}

internal data class SillageRecordEditorActionPresentation(
    val actionsEnabled: Boolean,
    val saveContentDescription: String,
    val showSaveProgress: Boolean,
    val favorited: Boolean,
    val favoriteAction: String,
    val archiveAction: String,
)

internal fun sillageRecordEditorActionPresentation(
    memo: Memo?,
    actionsEnabled: Boolean,
    saving: Boolean,
    uploadingAttachment: Boolean,
    strings: SillageRecordEditorActionStrings,
): SillageRecordEditorActionPresentation = SillageRecordEditorActionPresentation(
    actionsEnabled = actionsEnabled,
    saveContentDescription = when {
        uploadingAttachment -> strings.attachmentUploadingContentDescription
        saving -> strings.savingContentDescription
        else -> strings.saveContentDescription
    },
    showSaveProgress = uploadingAttachment || saving,
    favorited = memo?.favoritedAt != null,
    favoriteAction = if (memo?.favoritedAt == null) {
        strings.favoriteAction
    } else {
        strings.unfavoriteAction
    },
    archiveAction = if (memo?.archivedAt == null) {
        strings.archiveAction
    } else {
        strings.unarchiveAction
    },
)
