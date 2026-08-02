package app.sillage.ui.records

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import app.sillage.features.records.MemoListFilter
import app.sillage.features.records.MemoViewMode
import app.sillage.features.records.RecordsFeatureStateHolder
import app.sillage.features.records.RecordsRefreshStatus
import app.sillage.features.records.shouldShowRecordListLoadFailure
import app.sillage.features.records.shouldShowRecordSearchFailure

data class SillageRecordsContentStrings(
    val filters: SillageRecordFilterStrings,
    val search: SillageRecordSearchStrings,
    val loadFailed: String,
    val searchFailed: String,
    val retry: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SillageRecordsContent(
    state: RecordsFeatureStateHolder,
    initialLoading: Boolean,
    strings: SillageRecordsContentStrings,
    searchIcon: ImageVector,
    clearSearchIcon: ImageVector,
    refreshIcon: ImageVector,
    onSelectFilter: (MemoListFilter) -> Unit,
    onQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    searchStatusContent: @Composable () -> Unit,
    calendarContent: @Composable () -> Unit,
    listContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val body = sillageRecordsBody(state, initialLoading)

    Column(modifier = modifier.fillMaxSize()) {
        if (state.viewMode == MemoViewMode.List) {
            SillageRecordFilterTabs(
                state = state,
                strings = strings.filters,
                onSelect = onSelectFilter,
            )
            SillageRecordSearchBar(
                state = state,
                strings = strings.search,
                searchIcon = searchIcon,
                clearIcon = clearSearchIcon,
                onQueryChange = onQueryChange,
                onClear = onClearSearch,
                onSearch = onSearch,
            )
            searchStatusContent()
        }

        PullToRefreshBox(
            isRefreshing = state.refreshStatus == RecordsRefreshStatus.Loading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when (body) {
                SillageRecordsBody.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                SillageRecordsBody.Calendar -> calendarContent()
                SillageRecordsBody.ListLoadFailure -> {
                    SillageRecordEmptyState(
                        text = strings.loadFailed,
                        icon = refreshIcon,
                        actionLabel = strings.retry,
                        onAction = onRefresh,
                    )
                }

                SillageRecordsBody.SearchFailure -> {
                    SillageRecordEmptyState(
                        text = strings.searchFailed,
                        icon = refreshIcon,
                        actionLabel = strings.retry,
                        onAction = onSearch,
                    )
                }

                SillageRecordsBody.List -> listContent()
            }
        }
    }
}

internal enum class SillageRecordsBody {
    Loading,
    Calendar,
    ListLoadFailure,
    SearchFailure,
    List,
}

internal fun sillageRecordsBody(
    state: RecordsFeatureStateHolder,
    initialLoading: Boolean,
): SillageRecordsBody {
    val visibleRecords = if (state.search.query.isNotBlank()) {
        state.search.currentResults().orEmpty()
    } else {
        state.records
    }

    return when {
        (initialLoading || state.refreshStatus == RecordsRefreshStatus.Loading) &&
            visibleRecords.isEmpty() -> SillageRecordsBody.Loading
        state.viewMode == MemoViewMode.Calendar -> SillageRecordsBody.Calendar
        state.shouldShowRecordListLoadFailure() -> SillageRecordsBody.ListLoadFailure
        state.shouldShowRecordSearchFailure() -> SillageRecordsBody.SearchFailure
        else -> SillageRecordsBody.List
    }
}
