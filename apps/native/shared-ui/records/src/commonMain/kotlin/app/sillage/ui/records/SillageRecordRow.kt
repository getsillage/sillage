package app.sillage.ui.records

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sillage.core.domain.records.Memo

data class SillageRecordRowStrings(
    val blankRecord: String,
    val entryDateLabel: String,
    val moreActionsLabel: String,
    val savingDescription: String,
    val favoritedStatus: String,
    val archivedStatus: String,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SillageRecordRow(
    memo: Memo,
    mutating: Boolean,
    strings: SillageRecordRowStrings,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val presentation = sillageRecordRowPresentation(memo, mutating, strings)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClickLabel = if (onLongClick == null) null else strings.moreActionsLabel,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                presentation.content,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    presentation.entryDateLabel,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
                Box(
                    modifier = Modifier.size(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (presentation.mutating) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(16.dp)
                                .semantics { contentDescription = strings.savingDescription },
                            strokeWidth = 2.dp,
                        )
                    }
                }
                presentation.statusLabel?.let { statusLabel ->
                    Text(
                        statusLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

internal data class SillageRecordRowPresentation(
    val content: String,
    val entryDateLabel: String,
    val statusLabel: String?,
    val mutating: Boolean,
)

internal fun sillageRecordRowPresentation(
    memo: Memo,
    mutating: Boolean,
    strings: SillageRecordRowStrings,
): SillageRecordRowPresentation = SillageRecordRowPresentation(
    content = memo.content.ifBlank { strings.blankRecord },
    entryDateLabel = strings.entryDateLabel,
    statusLabel = listOfNotNull(
        strings.favoritedStatus.takeIf { memo.favoritedAt != null },
        strings.archivedStatus.takeIf { memo.archivedAt != null },
    ).joinToString(" · ").ifBlank { null },
    mutating = mutating,
)
