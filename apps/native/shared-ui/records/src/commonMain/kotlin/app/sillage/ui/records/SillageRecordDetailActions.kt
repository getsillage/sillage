package app.sillage.ui.records

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import app.sillage.core.domain.records.Memo

data class SillageRecordDetailActionStrings(
    val editContentDescription: String,
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

data class SillageRecordDetailActionIcons(
    val edit: ImageVector,
    val more: ImageVector,
    val favorite: ImageVector,
    val unfavorite: ImageVector,
    val archive: ImageVector,
    val delete: ImageVector,
)

@Composable
fun SillageRecordDetailActions(
    memo: Memo?,
    operationBlocked: Boolean,
    mutating: Boolean,
    strings: SillageRecordDetailActionStrings,
    icons: SillageRecordDetailActionIcons,
    onEdit: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val presentation = sillageRecordDetailActionPresentation(
        memo = memo,
        operationBlocked = operationBlocked,
        mutating = mutating,
        strings = strings,
    )

    LaunchedEffect(mutating) {
        if (mutating) {
            menuExpanded = false
            confirmDelete = false
        }
    }

    if (confirmDelete && memo != null) {
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
                    enabled = !operationBlocked,
                ) {
                    Text(strings.cancelAction)
                }
            },
        )
    }

    IconButton(
        onClick = onEdit,
        enabled = presentation.actionsEnabled,
    ) {
        Icon(icons.edit, contentDescription = strings.editContentDescription)
    }
    Box {
        IconButton(
            onClick = { menuExpanded = true },
            enabled = presentation.actionsEnabled,
        ) {
            Icon(icons.more, contentDescription = strings.moreContentDescription)
        }
        DropdownMenu(
            expanded = menuExpanded && !mutating,
            onDismissRequest = { menuExpanded = false },
        ) {
            if (memo != null) {
                DropdownMenuItem(
                    text = { Text(presentation.favoriteAction) },
                    leadingIcon = {
                        Icon(
                            if (presentation.favorited) icons.unfavorite else icons.favorite,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onToggleFavorite()
                    },
                    enabled = presentation.actionsEnabled,
                )
                DropdownMenuItem(
                    text = { Text(presentation.archiveAction) },
                    leadingIcon = { Icon(icons.archive, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onToggleArchive()
                    },
                    enabled = presentation.actionsEnabled,
                )
                DropdownMenuItem(
                    text = { Text(strings.deleteAction) },
                    leadingIcon = { Icon(icons.delete, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        confirmDelete = true
                    },
                    enabled = presentation.actionsEnabled,
                )
            }
        }
    }
}

internal data class SillageRecordDetailActionPresentation(
    val actionsEnabled: Boolean,
    val favorited: Boolean,
    val favoriteAction: String,
    val archiveAction: String,
)

internal fun sillageRecordDetailActionPresentation(
    memo: Memo?,
    operationBlocked: Boolean,
    mutating: Boolean,
    strings: SillageRecordDetailActionStrings,
): SillageRecordDetailActionPresentation = SillageRecordDetailActionPresentation(
    actionsEnabled = memo != null && !operationBlocked && !mutating,
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
