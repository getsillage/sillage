package app.sillage.ui.memos

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.sillage.data.Memo
import app.sillage.data.MemoListFilter
import app.sillage.data.SessionStore
import app.sillage.data.adjacentMonth
import app.sillage.data.calendarMemoCoverage
import app.sillage.data.entriesByDate
import app.sillage.data.entryDateCounts
import app.sillage.data.excerpt
import app.sillage.data.monthGrid
import app.sillage.data.onThisDay
import app.sillage.data.yearsBetween
import app.sillage.R
import app.sillage.ui.MemoListLoadStatus
import app.sillage.ui.MemoViewMode
import app.sillage.ui.SillageUiState
import app.sillage.ui.SillageViewModel
import app.sillage.ui.navigation.MainNavigationBar
import app.sillage.ui.shouldShowMemoListLoadFailure
import app.sillage.ui.shouldShowMemoSearchFailure
import app.sillage.ui.localizedDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemoListScreen(state: SillageUiState, viewModel: SillageViewModel) {
    val visibleMemos = state.searchResults ?: state.memos
    val showingSearchResults = state.searchResults != null
    val today = remember { LocalDate.now().toString() }
    val memories = remember(state.memos, today) { onThisDay(state.memos, today) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(if (state.memoViewMode == MemoViewMode.Calendar) R.string.nav_calendar else R.string.records_title),
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
            FloatingActionButton(onClick = viewModel::startNewMemo) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.records_new))
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
                MemoListFilterTabs(
                    selected = state.memoListFilter,
                    onSelect = viewModel::updateMemoListFilter,
                )
                SearchBlock(state = state, viewModel = viewModel)
                SearchStatusBlock(state = state)
            }
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
                EmptyState(stringResource(R.string.records_load_failed), Icons.Rounded.Refresh)
            } else if (state.shouldShowMemoSearchFailure()) {
                EmptyState(stringResource(R.string.records_search_failed), Icons.Rounded.Refresh)
            } else {
                MemoListView(
                    visibleMemos = visibleMemos,
                    showingSearchResults = showingSearchResults,
                    searching = state.searching,
                    memories = memories,
                    today = today,
                    hasMore = !showingSearchResults && state.memoNextCursor.isNotBlank(),
                    loadingMore = state.loadingMoreMemos,
                    memoMutationIds = state.memoMutationIds,
                    onLoadMore = viewModel::loadMoreMemos,
                    onMemoClick = viewModel::openMemoDetail,
                    onMemoEdit = viewModel::editMemo,
                    onMemoDuplicate = viewModel::duplicateMemoDraft,
                    onMemoToggleFavorite = viewModel::toggleMemoFavorited,
                    onMemoToggleArchive = viewModel::toggleMemoArchived,
                    onMemoDelete = viewModel::deleteMemo,
                    filter = state.memoListFilter,
                )
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
private fun MemoListFilterTabs(
    selected: MemoListFilter,
    onSelect: (MemoListFilter) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    ) {
        Row(modifier = Modifier.selectableGroup()) {
            MemoListFilter.entries.forEach { filter ->
                val isSelected = selected == filter
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .selectable(
                            selected = isSelected,
                            onClick = { onSelect(filter) },
                            role = Role.Tab,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 3.dp)
                            .height(36.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                when (filter) {
                                    MemoListFilter.Unarchived -> stringResource(R.string.filter_unarchived)
                                    MemoListFilter.Archived -> stringResource(R.string.filter_archived)
                                    MemoListFilter.Favorited -> stringResource(R.string.filter_favorited)
                                },
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchStatusBlock(state: SillageUiState) {
    val query = state.searchQuery.trim()
    val results = state.searchResults
    if (query.isBlank() || results == null) {
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(
                R.string.search_results_summary,
                query,
                pluralStringResource(R.plurals.quantity_results, results.size, results.size),
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
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
    memoMutationIds: Set<String>,
    onLoadMore: () -> Unit,
    onMemoClick: (Memo) -> Unit,
    onMemoEdit: (Memo) -> Unit,
    onMemoDuplicate: (Memo) -> Unit,
    onMemoToggleFavorite: (Memo) -> Unit,
    onMemoToggleArchive: (Memo) -> Unit,
    onMemoDelete: (Memo) -> Unit,
    filter: MemoListFilter,
) {
    if (searching && visibleMemos.isEmpty()) {
        EmptyState(stringResource(R.string.searching), Icons.Rounded.Search)
        return
    }
    if (visibleMemos.isEmpty()) {
        EmptyState(
            if (showingSearchResults) {
                stringResource(R.string.search_no_matches)
            } else {
                when (filter) {
                    MemoListFilter.Unarchived -> stringResource(R.string.empty_unarchived)
                    MemoListFilter.Archived -> stringResource(R.string.empty_archived)
                    MemoListFilter.Favorited -> stringResource(R.string.empty_favorited)
                }
            },
            if (showingSearchResults) Icons.Rounded.Search else Icons.Rounded.Edit,
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!showingSearchResults && memories.isNotEmpty()) {
            item {
                OnThisDayCard(entries = memories, today = today, onMemoClick = onMemoClick)
            }
        }
        items(visibleMemos, key = { it.id }) { memo ->
            MemoSwipeRow(
                memo = memo,
                mutating = memo.id in memoMutationIds,
                onClick = { onMemoClick(memo) },
                onEdit = { onMemoEdit(memo) },
                onDuplicate = { onMemoDuplicate(memo) },
                onToggleFavorite = { onMemoToggleFavorite(memo) },
                onToggleArchive = { onMemoToggleArchive(memo) },
                onDelete = { onMemoDelete(memo) },
            )
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
private fun SearchBlock(state: SillageUiState, viewModel: SillageViewModel) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = viewModel::updateSearchQuery,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_records)) },
            leadingIcon = {
                if (state.searching) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                }
            },
            trailingIcon = {
                if (state.searchQuery.isNotBlank() || state.searchResults != null) {
                    IconButton(onClick = viewModel::clearSearch) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.search_clear))
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.searchMemos() }),
        )
        FilledIconButton(
            onClick = viewModel::searchMemos,
            enabled = !state.searching && state.searchQuery.isNotBlank(),
        ) {
            Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.action_search))
        }
    }
}

@Composable
private fun EmptyState(text: String, icon: ImageVector? = null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (icon != null) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null)
                    }
                }
            }
            Text(
                text,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun OnThisDayCard(entries: List<Memo>, today: String, onMemoClick: (Memo) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
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
                            Icons.Rounded.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    stringResource(R.string.on_this_day),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            entries.forEachIndexed { index, memo ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
                Text(
                    stringResource(
                        R.string.years_ago_record,
                        pluralStringResource(
                            R.plurals.quantity_years_ago,
                            yearsBetween(memo.entryDate, today),
                            yearsBetween(memo.entryDate, today),
                        ),
                        excerpt(memo.content, 56).ifBlank { stringResource(R.string.blank_record) },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onMemoClick(memo) }
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
            CalendarGrid(
                weeks = weeks,
                counts = counts,
                today = today,
                selectedDate = state.selectedCalendarDate,
                firstDayOfWeek = firstDayOfWeek,
                onSelectDate = viewModel::selectCalendarDate,
            )
        }
        if (coverage.hasMoreOlderRecords) {
            item {
                CalendarCoverageNotice(
                    loadedCount = state.memos.size,
                    currentMonthMayBeIncomplete = coverage.currentMonthMayBeIncomplete,
                    loading = state.loadingMoreMemos,
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
                EmptyCalendarSelection(mayBeIncomplete = coverage.currentMonthMayBeIncomplete)
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
private fun CalendarCoverageNotice(
    loadedCount: Int,
    currentMonthMayBeIncomplete: Boolean,
    loading: Boolean,
    onLoadMore: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (currentMonthMayBeIncomplete) {
                    stringResource(
                        R.string.calendar_partial_month,
                        pluralStringResource(R.plurals.quantity_records, loadedCount, loadedCount),
                    )
                } else {
                    stringResource(R.string.calendar_complete_month)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                onClick = onLoadMore,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                }
                Text(stringResource(if (loading) R.string.calendar_loading_earlier else R.string.calendar_load_earlier))
            }
        }
    }
}

@Composable
private fun CalendarHeader(state: SillageUiState, viewModel: SillageViewModel) {
    val previous = adjacentMonth(state.calendarYear, state.calendarMonth, -1)
    val next = adjacentMonth(state.calendarYear, state.calendarMonth, 1)
    val previousLabel = localizedMonth(previous.first, previous.second)
    val currentLabel = localizedMonth(state.calendarYear, state.calendarMonth)
    val nextLabel = localizedMonth(next.first, next.second)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = { viewModel.changeCalendarMonth(-1) }) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = previousLabel,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                currentLabel,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.calendar_browse_by_date),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        IconButton(onClick = { viewModel.changeCalendarMonth(1) }) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = nextLabel,
            )
        }
    }
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
private fun CalendarGrid(
    weeks: List<List<String?>>,
    counts: Map<String, Int>,
    today: String,
    selectedDate: String?,
    firstDayOfWeek: DayOfWeek,
    onSelectDate: (String) -> Unit,
) {
    val sundayFirst = stringArrayResource(R.array.calendar_weekdays_short).toList()
    val firstIndex = if (firstDayOfWeek == DayOfWeek.SUNDAY) 0 else firstDayOfWeek.value
    val weekdays = sundayFirst.drop(firstIndex) + sundayFirst.take(firstIndex)
    Column(
        modifier = Modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            weekdays.forEach { day ->
                Text(
                    day,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
        weeks.forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { date ->
                    if (date == null) {
                        Spacer(modifier = Modifier.weight(1f).height(44.dp))
                    } else {
                        CalendarDayCell(
                            date = date,
                            count = counts[date] ?: 0,
                            isToday = date == today,
                            selected = date == selectedDate,
                            onClick = { onSelectDate(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    date: String,
    count: Int,
    isToday: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recordCount = pluralStringResource(R.plurals.quantity_records, count, count)
    val description = stringResource(
        if (isToday) R.string.calendar_day_today_description else R.string.calendar_day_description,
        localizedDate(date),
        recordCount,
    )
    val color = when {
        selected -> MaterialTheme.colorScheme.surfaceContainerHighest
        count > 0 -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> Color.Transparent
    }
    val border = when {
        selected -> BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant)
        isToday -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        else -> null
    }
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics { applyCalendarDaySemantics(description, selected) },
        shape = RoundedCornerShape(8.dp),
        color = color,
        border = border,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clearAndSetSemantics { }
                .padding(vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                date.takeLast(2).toInt().toString(),
                fontWeight = if (isToday || selected) FontWeight.SemiBold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (count > 0) count.toString() else " ",
                color = if (count > 0) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    Color.Transparent
                },
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

internal fun SemanticsPropertyReceiver.applyCalendarDaySemantics(
    description: String,
    isSelected: Boolean,
) {
    contentDescription = description
    selected = isSelected
}

@Composable
private fun EmptyCalendarSelection(mayBeIncomplete: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Text(
            if (mayBeIncomplete) {
                stringResource(R.string.calendar_day_maybe_incomplete)
            } else {
                stringResource(R.string.calendar_day_empty)
            },
            modifier = Modifier.padding(14.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
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
    var showActions by remember { mutableStateOf(false) }
    val actionWidth = 92.dp
    val actionWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { actionWidth.toPx() }
    val settleThreshold = actionWidthPx * 0.56f
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    var offsetX by remember(memo.id) { mutableStateOf(0f) }
    val dragState = rememberDraggableState { delta ->
        offsetX = (offsetX + delta).coerceIn(-actionWidthPx, actionWidthPx)
    }
    fun animateOffsetTo(target: Float, after: (() -> Unit)? = null) {
        coroutineScope.launch {
            val animation = Animatable(offsetX)
            animation.animateTo(target) {
                offsetX = value
            }
            offsetX = target
            after?.invoke()
        }
    }
    fun closeActions() {
        animateOffsetTo(0f)
    }
    fun settleActions() {
        val target = when {
            offsetX > settleThreshold -> actionWidthPx
            offsetX < -settleThreshold -> -actionWidthPx
            else -> 0f
        }
        animateOffsetTo(target)
    }
    fun runAction(action: () -> Unit) {
        animateOffsetTo(0f, action)
    }
    LaunchedEffect(mutating) {
        if (mutating) {
            showActions = false
            offsetX = 0f
        }
    }
    if (showActions && !mutating) {
        MemoQuickActionsSheet(
            memo = memo,
            onDismiss = { showActions = false },
            onEdit = {
                showActions = false
                onEdit()
            },
            onDuplicate = {
                showActions = false
                onDuplicate()
            },
            onToggleFavorite = {
                showActions = false
                onToggleFavorite()
            },
            onToggleArchive = {
                showActions = false
                onToggleArchive()
            },
            onDelete = {
                showActions = false
                onDelete()
            },
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 92.dp),
    ) {
        MemoSwipeActionPane(
            memo = memo,
            actionWidth = actionWidth,
            revealedOffset = offsetX,
            onToggleFavorite = { runAction(onToggleFavorite) },
            onToggleArchive = { runAction(onToggleArchive) },
            enabled = !mutating,
            modifier = Modifier.matchParentSize(),
        )
        MemoRow(
            memo = memo,
            modifier = Modifier
                .heightIn(min = 92.dp)
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = dragState,
                    enabled = !mutating,
                    onDragStopped = { settleActions() },
                ),
            mutating = mutating,
            onClick = {
                if (offsetX != 0f) {
                    closeActions()
                } else {
                    onClick()
                }
            },
            onLongClick = if (mutating) null else { { showActions = true } },
        )
    }
}

@Composable
private fun MemoSwipeActionPane(
    memo: Memo,
    actionWidth: androidx.compose.ui.unit.Dp,
    revealedOffset: Float,
    onToggleFavorite: () -> Unit,
    onToggleArchive: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        SwipeActionButton(
            icon = if (memo.favoritedAt == null) Icons.Rounded.StarBorder else Icons.Rounded.Star,
            label = stringResource(if (memo.favoritedAt == null) R.string.action_favorite else R.string.action_unfavorite),
            visible = revealedOffset > 0f,
            enabled = enabled,
            color = MaterialTheme.colorScheme.primaryContainer,
            onClick = onToggleFavorite,
            modifier = Modifier
                .fillMaxHeight()
                .width(actionWidth),
        )
        SwipeActionButton(
            icon = Icons.Rounded.Archive,
            label = stringResource(if (memo.archivedAt == null) R.string.action_archive else R.string.action_restore),
            visible = revealedOffset < 0f,
            enabled = enabled,
            color = MaterialTheme.colorScheme.secondaryContainer,
            onClick = onToggleArchive,
            modifier = Modifier
                .fillMaxHeight()
                .width(actionWidth),
        )
    }
}

@Composable
private fun SwipeActionButton(
    icon: ImageVector,
    label: String,
    visible: Boolean,
    enabled: Boolean,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (visible) color else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    if (!visible) {
                        invisibleToUser()
                    }
                }
                .clickable(enabled = visible && enabled, onClick = onClick)
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoQuickActionsSheet(
    memo: Memo,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmingDelete by remember(memo.id) { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Text(
                excerpt(memo.content, 64).ifBlank { stringResource(R.string.blank_record) },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.quick_actions_description, localizedDate(memo.entryDate)),
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            QuickActionRow(
                icon = Icons.Rounded.Edit,
                title = stringResource(R.string.action_edit),
                supporting = stringResource(R.string.quick_edit_supporting),
                onClick = onEdit,
            )
            QuickActionDivider()
            QuickActionRow(
                icon = Icons.Rounded.ContentCopy,
                title = stringResource(R.string.quick_copy_title),
                supporting = stringResource(R.string.quick_copy_supporting),
                onClick = onDuplicate,
            )
            QuickActionDivider()
            QuickActionRow(
                icon = if (memo.favoritedAt == null) Icons.Rounded.StarBorder else Icons.Rounded.Star,
                title = stringResource(if (memo.favoritedAt == null) R.string.action_favorite else R.string.action_unfavorite),
                supporting = if (memo.favoritedAt == null) {
                    stringResource(R.string.quick_favorite_supporting)
                } else if (memo.archivedAt == null) {
                    stringResource(R.string.quick_unfavorite_to_records)
                } else {
                    stringResource(R.string.quick_unfavorite_to_archive)
                },
                onClick = onToggleFavorite,
            )
            QuickActionDivider()
            QuickActionRow(
                icon = Icons.Rounded.Archive,
                title = stringResource(if (memo.archivedAt == null) R.string.action_archive else R.string.action_unarchive),
                supporting = stringResource(if (memo.archivedAt == null) R.string.quick_archive_supporting else R.string.quick_unarchive_supporting),
                onClick = onToggleArchive,
            )
            QuickActionDivider()
            QuickActionRow(
                icon = Icons.Rounded.Delete,
                title = stringResource(if (confirmingDelete) R.string.action_confirm_delete else R.string.action_delete),
                supporting = stringResource(
                    if (confirmingDelete) R.string.quick_delete_confirm_supporting else R.string.quick_delete_supporting,
                ),
                destructive = true,
                onClick = {
                    if (confirmingDelete) {
                        onDelete()
                    } else {
                        confirmingDelete = true
                    }
                },
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun QuickActionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 48.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
    )
}

@Composable
private fun QuickActionRow(
    icon: ImageVector,
    title: String,
    supporting: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(34.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = if (destructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                supporting,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemoRow(
    memo: Memo,
    mutating: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val moreActionsLabel = if (onLongClick == null) null else stringResource(R.string.action_more)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClickLabel = moreActionsLabel,
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
                memo.content.ifBlank { stringResource(R.string.blank_record) },
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
                    localizedDate(memo.entryDate),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
                Box(
                    modifier = Modifier.size(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (mutating) {
                        val savingDescription = stringResource(R.string.action_saving)
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(16.dp)
                                .semantics { contentDescription = savingDescription },
                            strokeWidth = 2.dp,
                        )
                    }
                }
                MemoStatusLine(memo)
            }
        }
    }
}
