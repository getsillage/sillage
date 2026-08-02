package app.sillage.ui.memos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sillage.core.domain.records.Memo
import app.sillage.features.records.MemoListFilter
import app.sillage.features.records.RecordsFeatureStateHolder
import app.sillage.data.SessionStore
import app.sillage.data.adjacentMonth
import app.sillage.features.records.calendarMemoCoverage
import app.sillage.features.records.entriesByDate
import app.sillage.features.records.entryDateCounts
import app.sillage.data.monthGrid
import app.sillage.features.records.onThisDay
import app.sillage.R
import app.sillage.ui.MemoListLoadStatus
import app.sillage.features.records.MemoViewMode
import app.sillage.ui.SillageUiState
import app.sillage.ui.SillageViewModel
import app.sillage.ui.designsystem.applySillageHeadingSemantics
import app.sillage.ui.completedMemoSearch
import app.sillage.ui.currentMemoSearchResults
import app.sillage.ui.navigation.MainNavigationBar
import app.sillage.ui.records.SillageCalendarEmptySelection
import app.sillage.ui.records.SillageCalendarEmptySelectionStrings
import app.sillage.ui.records.SillageCalendarCoverageNotice
import app.sillage.ui.records.SillageCalendarCoverageStrings
import app.sillage.ui.records.SillageCalendarGrid
import app.sillage.ui.records.SillageCalendarHeader
import app.sillage.ui.records.SillageCalendarHeaderStrings
import app.sillage.ui.records.SillageOnThisDayCard
import app.sillage.ui.records.SillageOnThisDayStrings
import app.sillage.ui.records.SillageRecordFilterStrings
import app.sillage.ui.records.SillageRecordFilterTabs
import app.sillage.ui.records.SillageRecordEmptyState
import app.sillage.ui.records.SillageRecordSearchBar
import app.sillage.ui.records.SillageRecordSearchStatus
import app.sillage.ui.records.SillageRecordSearchStrings
import app.sillage.ui.records.SillageRecentlyDeletedRecordRow
import app.sillage.ui.records.SillageRecentlyDeletedRecordStrings
import app.sillage.ui.records.SillageRecordRowStrings
import app.sillage.ui.records.SillageRecordQuickActionIcons
import app.sillage.ui.records.SillageRecordQuickActionsStrings
import app.sillage.ui.records.SillageRecordSwipeActionStrings
import app.sillage.ui.records.SillageRecordSwipeRow
import app.sillage.ui.records.SillageRecordSwipeRowIcons
import app.sillage.ui.records.SillageRecordSwipeRowStrings
import app.sillage.ui.shouldShowMemoListLoadFailure
import app.sillage.ui.shouldShowMemoSearchFailure
import app.sillage.ui.localizedDate
import app.sillage.ui.localizedTimestamp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemoListScreen(
    state: SillageUiState,
    viewModel: SillageViewModel,
    listState: LazyListState = rememberLazyListState(),
) {
    val showingSearchResults = state.searchQuery.isNotBlank()
    val visibleMemos = if (showingSearchResults) {
        state.currentMemoSearchResults().orEmpty()
    } else {
        state.memos
    }
    val today = remember { LocalDate.now().toString() }
    val memories = remember(state.memos, today) { onThisDay(state.memos, today) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(if (state.memoViewMode == MemoViewMode.Calendar) R.string.nav_calendar else R.string.records_title),
                    modifier = Modifier.semantics { applySillageHeadingSemantics() },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            memoListSubtitle(state),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::refreshMemos,
                        enabled = !state.loading && state.memoListLoadStatus != MemoListLoadStatus.Loading,
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.records_refresh))
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.memoListFilter != MemoListFilter.Deleted) {
                FloatingActionButton(onClick = viewModel::startNewMemo) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.records_new))
                }
            }
        },
        bottomBar = {
            MainNavigationBar(state = state, viewModel = viewModel)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.memoViewMode == MemoViewMode.List) {
                        SillageRecordFilterTabs(
                            state = state.records,
                            strings = SillageRecordFilterStrings(
                                unarchived = stringResource(R.string.filter_unarchived),
                                archived = stringResource(R.string.filter_archived),
                                favorited = stringResource(R.string.filter_favorited),
                                deleted = stringResource(R.string.filter_deleted),
                            ),
                            onSelect = viewModel::updateMemoListFilter,
                        )
                        SillageRecordSearchBar(
                            state = state.records,
                            strings = SillageRecordSearchStrings(
                                label = stringResource(R.string.search_records),
                                clearContentDescription = stringResource(R.string.search_clear),
                                searchContentDescription = stringResource(R.string.action_search),
                            ),
                            searchIcon = Icons.Rounded.Search,
                            clearIcon = Icons.Rounded.Close,
                            onQueryChange = viewModel::updateSearchQuery,
                            onClear = viewModel::clearSearch,
                            onSearch = viewModel::searchMemos,
                        )
                SearchStatusBlock(state = state)
            }
            // Swipe-down-to-refresh is the expected gesture for a manual-sync
            // feed; the toolbar button stays for accessibility.
            PullToRefreshBox(
                isRefreshing = state.memoListLoadStatus == MemoListLoadStatus.Loading,
                onRefresh = viewModel::refreshMemos,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (
                    (state.loading || state.memoListLoadStatus == MemoListLoadStatus.Loading) &&
                    visibleMemos.isEmpty()
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (state.memoViewMode == MemoViewMode.Calendar) {
                    CalendarMemoView(state = state, viewModel = viewModel)
                } else if (state.shouldShowMemoListLoadFailure()) {
                SillageRecordEmptyState(
                    text = stringResource(R.string.records_load_failed),
                    icon = Icons.Rounded.Refresh,
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = viewModel::refreshMemos,
                )
                } else if (state.shouldShowMemoSearchFailure()) {
                SillageRecordEmptyState(
                    text = stringResource(R.string.records_search_failed),
                    icon = Icons.Rounded.Refresh,
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = viewModel::searchMemos,
                )
                } else {
                    MemoListView(
                        visibleMemos = visibleMemos,
                        showingSearchResults = showingSearchResults,
                        searching = state.searching,
                        memories = memories,
                        today = today,
                        hasMore = !showingSearchResults && state.memoNextCursor.isNotBlank(),
                        loadingMore = state.loadingMoreMemos,
                        recordsState = state.records,
                        listState = listState,
                        onLoadMore = viewModel::loadMoreMemos,
                        onMemoClick = viewModel::openMemoDetail,
                        onMemoEdit = viewModel::editMemo,
                        onMemoDuplicate = viewModel::duplicateMemoDraft,
                        onMemoToggleFavorite = viewModel::toggleMemoFavorited,
                        onMemoToggleArchive = viewModel::toggleMemoArchived,
                        onMemoDelete = viewModel::deleteMemo,
                        onMemoRestore = viewModel::restoreMemo,
                        onMemoPurge = viewModel::purgeMemo,
                        filter = state.memoListFilter,
                    )
                }
            }
        }
    }
}

@Composable
private fun memoListSubtitle(state: SillageUiState): String {
    val mode = if (state.appMode == SessionStore.MODE_OFFLINE) {
        stringResource(R.string.status_offline)
    } else {
        state.account?.displayName ?: state.baseUrl.ifBlank { stringResource(R.string.status_online) }
    }
    return stringResource(
        R.string.quantity_joiner,
        mode,
        pluralStringResource(R.plurals.quantity_records, state.memos.size, state.memos.size),
    )
}

@Composable
private fun SearchStatusBlock(state: SillageUiState) {
    val view = LocalView.current
    val completed = state.completedMemoSearch() ?: return
    val summary = stringResource(
        R.string.search_results_summary,
        completed.query,
        pluralStringResource(
            R.plurals.quantity_results,
            completed.resultCount,
            completed.resultCount,
        ),
    )
    SillageRecordSearchStatus(
        summary = summary,
        completionEventId = state.searchCompletionEventId,
        icon = Icons.Rounded.Search,
        onAnnounce = view::announceForAccessibility,
    )
}

@Composable
private fun MemoListView(
    visibleMemos: List<Memo>,
    showingSearchResults: Boolean,
    searching: Boolean,
    memories: List<Memo>,
    today: String,
    hasMore: Boolean,
    loadingMore: Boolean,
    recordsState: RecordsFeatureStateHolder,
    listState: LazyListState,
    onLoadMore: () -> Unit,
    onMemoClick: (Memo) -> Unit,
    onMemoEdit: (Memo) -> Unit,
    onMemoDuplicate: (Memo) -> Unit,
    onMemoToggleFavorite: (Memo) -> Unit,
    onMemoToggleArchive: (Memo) -> Unit,
    onMemoDelete: (Memo) -> Unit,
    onMemoRestore: (Memo) -> Unit,
    onMemoPurge: (Memo) -> Unit,
    filter: MemoListFilter,
) {
    if (searching && visibleMemos.isEmpty()) {
        SillageRecordEmptyState(
            text = stringResource(R.string.searching),
            icon = Icons.Rounded.Search,
        )
        return
    }
    if (visibleMemos.isEmpty()) {
        SillageRecordEmptyState(
            text = if (showingSearchResults) {
                stringResource(R.string.search_no_matches)
            } else {
                when (filter) {
                    MemoListFilter.Unarchived -> stringResource(R.string.empty_unarchived)
                    MemoListFilter.Archived -> stringResource(R.string.empty_archived)
                    MemoListFilter.Favorited -> stringResource(R.string.empty_favorited)
                    MemoListFilter.Deleted -> stringResource(R.string.empty_deleted)
                }
            },
            icon = if (showingSearchResults) Icons.Rounded.Search else Icons.Rounded.Edit,
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!showingSearchResults && memories.isNotEmpty()) {
            item {
                SillageOnThisDayCard(
                    entries = memories,
                    today = today,
                    strings = SillageOnThisDayStrings(
                        title = stringResource(R.string.on_this_day),
                        blankRecord = stringResource(R.string.blank_record),
                    ),
                    calendarIcon = Icons.Rounded.CalendarMonth,
                    recordLabel = { yearsAgo, contentExcerpt ->
                        stringResource(
                            R.string.years_ago_record,
                            pluralStringResource(
                                R.plurals.quantity_years_ago,
                                yearsAgo,
                                yearsAgo,
                            ),
                            contentExcerpt,
                        )
                    },
                    onMemoClick = onMemoClick,
                )
            }
        }
        items(visibleMemos, key = { it.id }) { memo ->
            if (filter == MemoListFilter.Deleted) {
                val deletedAt = memo.deletedAt
                SillageRecentlyDeletedRecordRow(
                    state = recordsState,
                    memo = memo,
                    strings = SillageRecentlyDeletedRecordStrings(
                        blankRecord = stringResource(R.string.blank_record),
                        deletedAtLabel = stringResource(
                            R.string.deleted_at,
                            if (deletedAt != null) localizedTimestamp(deletedAt) else "—",
                        ),
                        purgeSupporting = stringResource(R.string.purge_record_supporting),
                        restoreAction = stringResource(R.string.action_restore),
                        deleteForeverAction = stringResource(R.string.action_delete_forever),
                        confirmDeleteAction = stringResource(R.string.action_confirm_delete),
                        cancelAction = stringResource(R.string.action_cancel),
                    ),
                    restoreIcon = Icons.Rounded.RestoreFromTrash,
                    purgeIcon = Icons.Rounded.DeleteForever,
                    onRestore = { onMemoRestore(memo) },
                    onPurge = { onMemoPurge(memo) },
                )
            } else {
                MemoSwipeRow(
                    memo = memo,
                    mutating = recordsState.mutation.isActive(memo.id),
                    onClick = { onMemoClick(memo) },
                    onEdit = { onMemoEdit(memo) },
                    onDuplicate = { onMemoDuplicate(memo) },
                    onToggleFavorite = { onMemoToggleFavorite(memo) },
                    onToggleArchive = { onMemoToggleArchive(memo) },
                    onDelete = { onMemoDelete(memo) },
                )
            }
        }
        if (hasMore) {
            item {
                Button(
                    onClick = onLoadMore,
                    enabled = !loadingMore,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(if (loadingMore) R.string.loading_more else R.string.load_more))
                }
            }
        }
    }
}

@Composable
private fun CalendarMemoView(state: SillageUiState, viewModel: SillageViewModel) {
    val today = remember { LocalDate.now().toString() }
    val locale = LocalConfiguration.current.locales[0]
    val firstDayOfWeek = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val weeks = remember(state.calendarYear, state.calendarMonth, firstDayOfWeek) {
        monthGrid(state.calendarYear, state.calendarMonth, firstDayOfWeek)
    }
    val counts = remember(state.memos) { entryDateCounts(state.memos) }
    val selectedEntries = remember(state.memos, state.selectedCalendarDate) {
        state.selectedCalendarDate?.let { entriesByDate(state.memos, it) }.orEmpty()
    }
    val coverage = remember(
        state.memos,
        state.memoNextCursor,
        state.calendarYear,
        state.calendarMonth,
    ) {
        calendarMemoCoverage(
            memos = state.memos,
            nextCursor = state.memoNextCursor,
            year = state.calendarYear,
            month = state.calendarMonth,
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            CalendarHeader(state, viewModel)
        }
        item {
            SillageCalendarGrid(
                weekdayLabels = calendarWeekdayLabels(firstDayOfWeek),
                weeks = weeks,
                counts = counts,
                today = today,
                selectedDate = state.selectedCalendarDate,
                dayDescription = { date, count, isToday ->
                    stringResource(
                        if (isToday) {
                            R.string.calendar_day_today_description
                        } else {
                            R.string.calendar_day_description
                        },
                        localizedDate(date),
                        pluralStringResource(R.plurals.quantity_records, count, count),
                    )
                },
                onSelectDate = viewModel::selectCalendarDate,
            )
        }
        if (coverage.hasMoreOlderRecords) {
            item {
                SillageCalendarCoverageNotice(
                    state = state.records,
                    coverage = coverage,
                    strings = SillageCalendarCoverageStrings(
                        partialMonth = stringResource(
                            R.string.calendar_partial_month,
                            pluralStringResource(
                                R.plurals.quantity_records,
                                state.memos.size,
                                state.memos.size,
                            ),
                        ),
                        completeMonth = stringResource(R.string.calendar_complete_month),
                        loadEarlierAction = stringResource(R.string.calendar_load_earlier),
                        loadingEarlierAction = stringResource(R.string.calendar_loading_earlier),
                    ),
                    onLoadMore = viewModel::loadMoreMemos,
                )
            }
        }
        item {
            Text(
                state.selectedCalendarDate?.let { localizedDate(it) }
                    ?: stringResource(R.string.calendar_select_day),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (state.selectedCalendarDate != null && selectedEntries.isEmpty()) {
            item {
                    SillageCalendarEmptySelection(
                        coverage = coverage,
                        strings = SillageCalendarEmptySelectionStrings(
                            empty = stringResource(R.string.calendar_day_empty),
                            mayBeIncomplete = stringResource(R.string.calendar_day_maybe_incomplete),
                        ),
                    )
            }
        }
        items(selectedEntries, key = { it.id }) { memo ->
            MemoSwipeRow(
                memo = memo,
                mutating = memo.id in state.memoMutationIds,
                onClick = { viewModel.openMemoDetail(memo) },
                onEdit = { viewModel.editMemo(memo) },
                onDuplicate = { viewModel.duplicateMemoDraft(memo) },
                onToggleFavorite = { viewModel.toggleMemoFavorited(memo) },
                onToggleArchive = { viewModel.toggleMemoArchived(memo) },
                onDelete = { viewModel.deleteMemo(memo) },
            )
        }
    }
}

@Composable
private fun CalendarHeader(state: SillageUiState, viewModel: SillageViewModel) {
    val previous = adjacentMonth(state.calendarYear, state.calendarMonth, -1)
    val next = adjacentMonth(state.calendarYear, state.calendarMonth, 1)
    SillageCalendarHeader(
        strings = SillageCalendarHeaderStrings(
            currentMonth = localizedMonth(state.calendarYear, state.calendarMonth),
            browseByDate = stringResource(R.string.calendar_browse_by_date),
            previousMonthDescription = localizedMonth(previous.first, previous.second),
            nextMonthDescription = localizedMonth(next.first, next.second),
        ),
        previousIcon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
        nextIcon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        onPreviousMonth = { viewModel.changeCalendarMonth(-1) },
        onNextMonth = { viewModel.changeCalendarMonth(1) },
    )
}

@Composable
private fun localizedMonth(year: Int, month: Int): String {
    val locale = LocalConfiguration.current.locales[0]
    val pattern = stringResource(R.string.calendar_month_pattern)
    return remember(year, month, locale, pattern) {
        YearMonth.of(year, month).format(DateTimeFormatter.ofPattern(pattern, locale))
    }
}

@Composable
private fun calendarWeekdayLabels(firstDayOfWeek: DayOfWeek): List<String> {
    val sundayFirst = stringArrayResource(R.array.calendar_weekdays_short).toList()
    val firstIndex = if (firstDayOfWeek == DayOfWeek.SUNDAY) 0 else firstDayOfWeek.value
    return sundayFirst.drop(firstIndex) + sundayFirst.take(firstIndex)
}

@Composable
private fun MemoSwipeRow(
    memo: Memo,
    mutating: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    SillageRecordSwipeRow(
        memo = memo,
        mutating = mutating,
        strings = SillageRecordSwipeRowStrings(
            row = SillageRecordRowStrings(
                blankRecord = stringResource(R.string.blank_record),
                entryDateLabel = localizedDate(memo.entryDate),
                moreActionsLabel = stringResource(R.string.action_more),
                savingDescription = stringResource(R.string.action_saving),
                favoritedStatus = stringResource(R.string.record_favorited),
                archivedStatus = stringResource(R.string.record_archived),
            ),
            swipeActions = SillageRecordSwipeActionStrings(
                favoriteAction = stringResource(R.string.action_favorite),
                unfavoriteAction = stringResource(R.string.action_unfavorite),
                archiveAction = stringResource(R.string.action_archive),
                restoreAction = stringResource(R.string.action_restore),
            ),
            quickActions = SillageRecordQuickActionsStrings(
                blankRecord = stringResource(R.string.blank_record),
                recordDescription = stringResource(
                    R.string.quick_actions_description,
                    localizedDate(memo.entryDate),
                ),
                editAction = stringResource(R.string.action_edit),
                editSupporting = stringResource(R.string.quick_edit_supporting),
                duplicateAction = stringResource(R.string.quick_copy_title),
                duplicateSupporting = stringResource(R.string.quick_copy_supporting),
                favoriteAction = stringResource(R.string.action_favorite),
                unfavoriteAction = stringResource(R.string.action_unfavorite),
                favoriteSupporting = stringResource(R.string.quick_favorite_supporting),
                unfavoriteToRecordsSupporting = stringResource(R.string.quick_unfavorite_to_records),
                unfavoriteToArchiveSupporting = stringResource(R.string.quick_unfavorite_to_archive),
                archiveAction = stringResource(R.string.action_archive),
                unarchiveAction = stringResource(R.string.action_unarchive),
                archiveSupporting = stringResource(R.string.quick_archive_supporting),
                unarchiveSupporting = stringResource(R.string.quick_unarchive_supporting),
                deleteAction = stringResource(R.string.action_delete),
                confirmDeleteAction = stringResource(R.string.action_confirm_delete),
                deleteSupporting = stringResource(R.string.quick_delete_supporting),
                confirmDeleteSupporting = stringResource(R.string.quick_delete_confirm_supporting),
            ),
        ),
        icons = SillageRecordSwipeRowIcons(
            favorite = Icons.Rounded.StarBorder,
            favorited = Icons.Rounded.Star,
            archive = Icons.Rounded.Archive,
            quickActions = SillageRecordQuickActionIcons(
                edit = Icons.Rounded.Edit,
                duplicate = Icons.Rounded.ContentCopy,
                favorite = Icons.Rounded.StarBorder,
                favorited = Icons.Rounded.Star,
                archive = Icons.Rounded.Archive,
                delete = Icons.Rounded.Delete,
            ),
        ),
        onClick = onClick,
        onEdit = onEdit,
        onDuplicate = onDuplicate,
        onToggleFavorite = onToggleFavorite,
        onToggleArchive = onToggleArchive,
        onDelete = onDelete,
    )
}
