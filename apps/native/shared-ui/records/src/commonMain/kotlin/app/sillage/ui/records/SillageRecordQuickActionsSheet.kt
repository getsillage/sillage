package app.sillage.ui.records

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sillage.core.domain.records.Memo
import app.sillage.features.records.excerpt

data class SillageRecordQuickActionsStrings(
    val blankRecord: String,
    val recordDescription: String,
    val editAction: String,
    val editSupporting: String,
    val duplicateAction: String,
    val duplicateSupporting: String,
    val favoriteAction: String,
    val unfavoriteAction: String,
    val favoriteSupporting: String,
    val unfavoriteToRecordsSupporting: String,
    val unfavoriteToArchiveSupporting: String,
    val archiveAction: String,
    val unarchiveAction: String,
    val archiveSupporting: String,
    val unarchiveSupporting: String,
    val deleteAction: String,
    val confirmDeleteAction: String,
    val deleteSupporting: String,
    val confirmDeleteSupporting: String,
)

data class SillageRecordQuickActionIcons(
    val edit: ImageVector,
    val duplicate: ImageVector,
    val favorite: ImageVector,
    val favorited: ImageVector,
    val archive: ImageVector,
    val delete: ImageVector,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SillageRecordQuickActionsSheet(
    memo: Memo,
    strings: SillageRecordQuickActionsStrings,
    icons: SillageRecordQuickActionIcons,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmingDelete by remember(memo.id) { mutableStateOf(false) }
    val presentation = sillageRecordQuickActionsPresentation(memo, confirmingDelete, strings)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Text(
                presentation.contentExcerpt,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                strings.recordDescription,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            SillageQuickActionRow(
                icon = icons.edit,
                title = strings.editAction,
                supporting = strings.editSupporting,
                onClick = onEdit,
            )
            SillageQuickActionDivider()
            SillageQuickActionRow(
                icon = icons.duplicate,
                title = strings.duplicateAction,
                supporting = strings.duplicateSupporting,
                onClick = onDuplicate,
            )
            SillageQuickActionDivider()
            SillageQuickActionRow(
                icon = if (presentation.favoriteActive) icons.favorited else icons.favorite,
                title = presentation.favoriteTitle,
                supporting = presentation.favoriteSupporting,
                onClick = onToggleFavorite,
            )
            SillageQuickActionDivider()
            SillageQuickActionRow(
                icon = icons.archive,
                title = presentation.archiveTitle,
                supporting = presentation.archiveSupporting,
                onClick = onToggleArchive,
            )
            SillageQuickActionDivider()
            SillageQuickActionRow(
                icon = icons.delete,
                title = presentation.deleteTitle,
                supporting = presentation.deleteSupporting,
                destructive = true,
                onClick = {
                    if (confirmingDelete) {
                        onDelete()
                    } else {
                        confirmingDelete = true
                    }
                },
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun SillageQuickActionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 48.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
    )
}

@Composable
private fun SillageQuickActionRow(
    icon: ImageVector,
    title: String,
    supporting: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(34.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = if (destructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                supporting,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

internal data class SillageRecordQuickActionsPresentation(
    val contentExcerpt: String,
    val favoriteActive: Boolean,
    val favoriteTitle: String,
    val favoriteSupporting: String,
    val archiveTitle: String,
    val archiveSupporting: String,
    val deleteTitle: String,
    val deleteSupporting: String,
)

internal fun sillageRecordQuickActionsPresentation(
    memo: Memo,
    confirmingDelete: Boolean,
    strings: SillageRecordQuickActionsStrings,
): SillageRecordQuickActionsPresentation {
    val favoriteActive = memo.favoritedAt != null
    return SillageRecordQuickActionsPresentation(
        contentExcerpt = excerpt(memo.content, 64).ifBlank { strings.blankRecord },
        favoriteActive = favoriteActive,
        favoriteTitle = if (favoriteActive) strings.unfavoriteAction else strings.favoriteAction,
        favoriteSupporting = when {
            !favoriteActive -> strings.favoriteSupporting
            memo.archivedAt == null -> strings.unfavoriteToRecordsSupporting
            else -> strings.unfavoriteToArchiveSupporting
        },
        archiveTitle = if (memo.archivedAt == null) strings.archiveAction else strings.unarchiveAction,
        archiveSupporting = if (memo.archivedAt == null) strings.archiveSupporting else strings.unarchiveSupporting,
        deleteTitle = if (confirmingDelete) strings.confirmDeleteAction else strings.deleteAction,
        deleteSupporting = if (confirmingDelete) strings.confirmDeleteSupporting else strings.deleteSupporting,
    )
}
