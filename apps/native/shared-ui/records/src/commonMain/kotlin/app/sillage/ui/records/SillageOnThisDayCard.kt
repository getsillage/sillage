package app.sillage.ui.records

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sillage.core.domain.records.Memo
import app.sillage.features.records.excerpt
import app.sillage.features.records.yearsBetween

data class SillageOnThisDayStrings(
    val title: String,
    val blankRecord: String,
)

@Composable
fun SillageOnThisDayCard(
    entries: List<Memo>,
    today: String,
    strings: SillageOnThisDayStrings,
    calendarIcon: ImageVector,
    recordLabel: @Composable (yearsAgo: Int, contentExcerpt: String) -> String,
    onMemoClick: (Memo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = sillageOnThisDayPresentation(
        entries = entries,
        today = today,
        blankRecord = strings.blankRecord,
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(26.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = calendarIcon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = strings.title,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            presentation.entries.forEachIndexed { index, entry ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    )
                }
                Text(
                    text = recordLabel(entry.yearsAgo, entry.contentExcerpt),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onMemoClick(entry.memo) }
                        .heightIn(min = 48.dp)
                        .padding(vertical = 8.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

internal data class SillageOnThisDayPresentation(
    val entries: List<SillageOnThisDayEntryPresentation>,
)

internal data class SillageOnThisDayEntryPresentation(
    val memo: Memo,
    val yearsAgo: Int,
    val contentExcerpt: String,
)

internal fun sillageOnThisDayPresentation(
    entries: List<Memo>,
    today: String,
    blankRecord: String,
): SillageOnThisDayPresentation = SillageOnThisDayPresentation(
    entries = entries.map { memo ->
        SillageOnThisDayEntryPresentation(
            memo = memo,
            yearsAgo = yearsBetween(memo.entryDate, today),
            contentExcerpt = excerpt(memo.content, max = 56).ifBlank { blankRecord },
        )
    },
)
