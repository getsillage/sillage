package app.sillage.ui.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.sillage.core.domain.records.Memo
import app.sillage.features.records.MemoListFilter
import app.sillage.features.records.RecordsFeatureStateHolder
import app.sillage.features.records.onThisDay

data class SillageRecordListStrings(
    val searching: String,
    val searchNoMatches: String,
    val emptyUnarchived: String,
    val emptyArchived: String,
    val emptyFavorited: String,
    val emptyDeleted: String,
    val loadMore: String,
    val loadingMore: String,
)

@Composable
fun SillageRecordList(
    state: RecordsFeatureStateHolder,
    today: String,
    strings: SillageRecordListStrings,
    searchIcon: ImageVector,
    emptyIcon: ImageVector,
    listState: LazyListState,
    onLoadMore: () -> Unit,
    onThisDayContent: @Composable (List<Memo>) -> Unit,
    activeRecordContent: @Composable (Memo) -> Unit,
    deletedRecordContent: @Composable (Memo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = remember(state, today, strings) {
        sillageRecordListPresentation(state, today, strings)
    }

    presentation.emptyMessage?.let { emptyMessage ->
        SillageRecordEmptyState(
            text = emptyMessage,
            icon = if (presentation.emptyUsesSearchIcon) searchIcon else emptyIcon,
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (presentation.onThisDayEntries.isNotEmpty()) {
            item {
                onThisDayContent(presentation.onThisDayEntries)
            }
        }
        items(presentation.visibleRecords, key = { it.id }) { memo ->
            if (presentation.showingDeletedRecords) {
                deletedRecordContent(memo)
            } else {
                activeRecordContent(memo)
            }
        }
        if (presentation.showLoadMore) {
            item {
                Button(
                    onClick = onLoadMore,
                    enabled = !presentation.loadingMore,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (presentation.loadingMore) strings.loadingMore else strings.loadMore)
                }
            }
        }
    }
}

internal data class SillageRecordListPresentation(
    val visibleRecords: List<Memo>,
    val emptyMessage: String?,
    val emptyUsesSearchIcon: Boolean,
    val onThisDayEntries: List<Memo>,
    val showingDeletedRecords: Boolean,
    val showLoadMore: Boolean,
    val loadingMore: Boolean,
)

internal fun sillageRecordListPresentation(
    state: RecordsFeatureStateHolder,
    today: String,
    strings: SillageRecordListStrings,
): SillageRecordListPresentation {
    val showingSearchResults = state.search.query.isNotBlank()
    val visibleRecords = if (showingSearchResults) {
        state.search.currentResults().orEmpty()
    } else {
        state.records
    }
    val emptyMessage = when {
        state.search.searching && visibleRecords.isEmpty() -> strings.searching
        visibleRecords.isNotEmpty() -> null
        showingSearchResults -> strings.searchNoMatches
        else -> when (state.filter) {
            MemoListFilter.Unarchived -> strings.emptyUnarchived
            MemoListFilter.Archived -> strings.emptyArchived
            MemoListFilter.Favorited -> strings.emptyFavorited
            MemoListFilter.Deleted -> strings.emptyDeleted
        }
    }
    val hasVisibleContent = emptyMessage == null

    return SillageRecordListPresentation(
        visibleRecords = visibleRecords,
        emptyMessage = emptyMessage,
        emptyUsesSearchIcon = state.search.searching || showingSearchResults,
        onThisDayEntries = if (hasVisibleContent && !showingSearchResults) {
            onThisDay(state.records, today)
        } else {
            emptyList()
        },
        showingDeletedRecords = state.filter == MemoListFilter.Deleted,
        showLoadMore = hasVisibleContent && !showingSearchResults && state.nextCursor.isNotBlank(),
        loadingMore = state.loadingMore,
    )
}
