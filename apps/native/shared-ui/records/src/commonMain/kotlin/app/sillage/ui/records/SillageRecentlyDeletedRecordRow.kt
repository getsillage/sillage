package app.sillage.ui.records

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sillage.core.domain.records.Memo
import app.sillage.features.records.RecordsFeatureStateHolder
import app.sillage.features.records.excerpt

const val RECENTLY_DELETED_RESTORE_TEST_TAG = "recently-deleted-restore"
const val RECENTLY_DELETED_PURGE_TEST_TAG = "recently-deleted-purge"

data class SillageRecentlyDeletedRecordStrings(
    val blankRecord: String,
    val deletedAtLabel: String,
    val purgeSupporting: String,
    val restoreAction: String,
    val deleteForeverAction: String,
    val confirmDeleteAction: String,
    val cancelAction: String,
)

@Composable
fun SillageRecentlyDeletedRecordRow(
    state: RecordsFeatureStateHolder,
    memo: Memo,
    strings: SillageRecentlyDeletedRecordStrings,
    restoreIcon: ImageVector,
    purgeIcon: ImageVector,
    onRestore: () -> Unit,
    onPurge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = sillageRecentlyDeletedRecordPresentation(
        state = state,
        memo = memo,
        strings = strings,
    )
    var confirmingPurge by remember(memo.id) { mutableStateOf(false) }

    LaunchedEffect(presentation.mutating) {
        if (presentation.mutating) confirmingPurge = false
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = presentation.contentExcerpt,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = presentation.deletedAtLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            if (confirmingPurge) {
                Text(
                    text = strings.purgeSupporting,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onRestore,
                    enabled = !presentation.mutating,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(RECENTLY_DELETED_RESTORE_TEST_TAG),
                ) {
                    Icon(restoreIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.restoreAction)
                }
                Button(
                    onClick = {
                        if (confirmingPurge) onPurge() else confirmingPurge = true
                    },
                    enabled = !presentation.mutating,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .weight(1f)
                        .testTag(RECENTLY_DELETED_PURGE_TEST_TAG),
                ) {
                    Icon(purgeIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (confirmingPurge) {
                            strings.confirmDeleteAction
                        } else {
                            strings.deleteForeverAction
                        },
                    )
                }
            }
            if (confirmingPurge) {
                TextButton(
                    onClick = { confirmingPurge = false },
                    enabled = !presentation.mutating,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(strings.cancelAction)
                }
            }
        }
    }
}

internal data class SillageRecentlyDeletedRecordPresentation(
    val contentExcerpt: String,
    val deletedAtLabel: String,
    val mutating: Boolean,
)

internal fun sillageRecentlyDeletedRecordPresentation(
    state: RecordsFeatureStateHolder,
    memo: Memo,
    strings: SillageRecentlyDeletedRecordStrings,
): SillageRecentlyDeletedRecordPresentation = SillageRecentlyDeletedRecordPresentation(
    contentExcerpt = excerpt(memo.content, max = 120).ifBlank { strings.blankRecord },
    deletedAtLabel = strings.deletedAtLabel,
    mutating = state.mutation.isActive(memo.id),
)
