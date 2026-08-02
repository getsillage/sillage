package app.sillage.ui.records

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.sillage.features.records.CalendarMemoCoverage
import app.sillage.features.records.RecordsFeatureStateHolder

data class SillageCalendarCoverageStrings(
    val partialMonth: String,
    val completeMonth: String,
    val loadEarlierAction: String,
    val loadingEarlierAction: String,
)

@Composable
fun SillageCalendarCoverageNotice(
    state: RecordsFeatureStateHolder,
    coverage: CalendarMemoCoverage,
    strings: SillageCalendarCoverageStrings,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = sillageCalendarCoveragePresentation(
        state = state,
        coverage = coverage,
        strings = strings,
    )

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
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = presentation.message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                onClick = onLoadMore,
                enabled = !presentation.loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (presentation.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                }
                Text(presentation.actionLabel)
            }
        }
    }
}

internal data class SillageCalendarCoveragePresentation(
    val message: String,
    val actionLabel: String,
    val loading: Boolean,
)

internal fun sillageCalendarCoveragePresentation(
    state: RecordsFeatureStateHolder,
    coverage: CalendarMemoCoverage,
    strings: SillageCalendarCoverageStrings,
): SillageCalendarCoveragePresentation {
    val loading = state.pagination.loadingMore
    return SillageCalendarCoveragePresentation(
        message = if (coverage.currentMonthMayBeIncomplete) {
            strings.partialMonth
        } else {
            strings.completeMonth
        },
        actionLabel = if (loading) strings.loadingEarlierAction else strings.loadEarlierAction,
        loading = loading,
    )
}
