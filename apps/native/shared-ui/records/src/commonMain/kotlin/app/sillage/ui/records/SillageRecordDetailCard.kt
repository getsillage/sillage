package app.sillage.ui.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.sillage.core.domain.records.Memo

data class SillageRecordDetailStrings(
    val entryDateLabel: String,
    val blankRecord: String,
    val favoritedStatus: String,
    val archivedStatus: String,
)

@Composable
fun SillageRecordStatusLine(
    memo: Memo?,
    favoritedStatus: String,
    archivedStatus: String,
    modifier: Modifier = Modifier,
) {
    val statusLine = remember(memo, favoritedStatus, archivedStatus) {
        sillageRecordStatusLine(memo, favoritedStatus, archivedStatus)
    } ?: return

    Text(
        statusLine,
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium,
    )
}

@Composable
fun SillageRecordDetailCard(
    memo: Memo,
    strings: SillageRecordDetailStrings,
    modifier: Modifier = Modifier,
    recordContent: @Composable (Memo) -> Unit,
) {
    val presentation = remember(memo, strings) {
        sillageRecordDetailPresentation(memo, strings)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                strings.entryDateLabel,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
            presentation.statusLine?.let { status ->
                Text(
                    status,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
        if (presentation.showBlankRecord) {
            Text(
                strings.blankRecord,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            recordContent(memo)
        }
    }
}

internal data class SillageRecordDetailPresentation(
    val statusLine: String?,
    val showBlankRecord: Boolean,
)

internal fun sillageRecordDetailPresentation(
    memo: Memo,
    strings: SillageRecordDetailStrings,
): SillageRecordDetailPresentation = SillageRecordDetailPresentation(
    statusLine = sillageRecordStatusLine(
        memo = memo,
        favoritedStatus = strings.favoritedStatus,
        archivedStatus = strings.archivedStatus,
    ),
    showBlankRecord = memo.content.isBlank(),
)

internal fun sillageRecordStatusLine(
    memo: Memo?,
    favoritedStatus: String,
    archivedStatus: String,
): String? = listOfNotNull(
    favoritedStatus.takeIf { memo?.favoritedAt != null },
    archivedStatus.takeIf { memo?.archivedAt != null },
).joinToString(" · ").ifBlank { null }
