package app.sillage.ui.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.sillage.core.domain.records.Memo
import app.sillage.features.records.CalendarMemoCoverage
import app.sillage.features.records.RecordsFeatureStateHolder
import app.sillage.features.records.calendarMemoCoverage
import app.sillage.features.records.entriesByDate
import app.sillage.features.records.entryDateCounts

data class SillageRecordCalendarStrings(
    val selectedDateLabel: String,
    val coverage: SillageCalendarCoverageStrings,
    val emptySelection: SillageCalendarEmptySelectionStrings,
)

@Composable
fun SillageRecordCalendar(
    state: RecordsFeatureStateHolder,
    today: String,
    weekdayLabels: List<String>,
    weeks: List<List<String?>>,
    strings: SillageRecordCalendarStrings,
    dayDescription: @Composable (date: String, count: Int, isToday: Boolean) -> String,
    onSelectDate: (String) -> Unit,
    onLoadMore: () -> Unit,
    headerContent: @Composable () -> Unit,
    selectedRecordContent: @Composable (Memo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = remember(state) {
        sillageRecordCalendarPresentation(state)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            headerContent()
        }
        item {
            SillageCalendarGrid(
                weekdayLabels = weekdayLabels,
                weeks = weeks,
                counts = presentation.counts,
                today = today,
                selectedDate = state.browse.selectedCalendarDate,
                dayDescription = dayDescription,
                onSelectDate = onSelectDate,
            )
        }
        if (presentation.showCoverageNotice) {
            item {
                SillageCalendarCoverageNotice(
                    state = state,
                    coverage = presentation.coverage,
                    strings = strings.coverage,
                    onLoadMore = onLoadMore,
                )
            }
        }
        item {
            Text(
                strings.selectedDateLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (presentation.showEmptySelection) {
            item {
                SillageCalendarEmptySelection(
                    coverage = presentation.coverage,
                    strings = strings.emptySelection,
                )
            }
        }
        items(presentation.selectedEntries, key = { it.id }) { memo ->
            selectedRecordContent(memo)
        }
    }
}

internal data class SillageRecordCalendarPresentation(
    val counts: Map<String, Int>,
    val selectedEntries: List<Memo>,
    val coverage: CalendarMemoCoverage,
    val showCoverageNotice: Boolean,
    val showEmptySelection: Boolean,
)

internal fun sillageRecordCalendarPresentation(
    state: RecordsFeatureStateHolder,
): SillageRecordCalendarPresentation {
    val selectedDate = state.browse.selectedCalendarDate
    val selectedEntries = selectedDate?.let { entriesByDate(state.records, it) }.orEmpty()
    val coverage = calendarMemoCoverage(
        memos = state.records,
        nextCursor = state.nextCursor,
        year = state.browse.calendarYear,
        month = state.browse.calendarMonth,
    )

    return SillageRecordCalendarPresentation(
        counts = entryDateCounts(state.records),
        selectedEntries = selectedEntries,
        coverage = coverage,
        showCoverageNotice = coverage.hasMoreOlderRecords,
        showEmptySelection = selectedDate != null && selectedEntries.isEmpty(),
    )
}
