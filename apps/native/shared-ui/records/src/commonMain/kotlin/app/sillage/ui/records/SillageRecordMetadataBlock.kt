package app.sillage.ui.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.sillage.core.domain.records.Memo

@Composable
fun SillageRecordMetadataBlock(
    memo: Memo,
    createdLabel: String,
    updatedLabel: @Composable (revisionCount: Int) -> String,
    modifier: Modifier = Modifier,
) {
    val revisionCount = remember(memo.version) { recordRevisionCount(memo.version) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HorizontalDivider(
            modifier = Modifier.padding(bottom = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        )
        Text(
            createdLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
        if (revisionCount > 0) {
            Text(
                updatedLabel(revisionCount),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

internal fun recordRevisionCount(version: Long): Int =
    (version - 1).coerceAtLeast(0).toInt()
