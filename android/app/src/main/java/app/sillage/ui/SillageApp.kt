package app.sillage.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.sillage.data.MarkdownBlock
import app.sillage.data.MarkdownBlockKind
import app.sillage.data.MarkdownFormatStyle
import app.sillage.data.Memo
import app.sillage.data.MemoAI
import app.sillage.data.SessionStore
import app.sillage.data.adjacentMonth
import app.sillage.data.entriesByDate
import app.sillage.data.entryDateCounts
import app.sillage.data.excerpt
import app.sillage.data.memoMetadataLines
import app.sillage.data.monthGrid
import app.sillage.data.onThisDay
import app.sillage.data.parseMarkdownPreview
import app.sillage.data.yearsBetween
import java.time.LocalDate

@Composable
fun SillageApp(viewModel: SillageViewModel) {
    val state by viewModel.state.collectAsState()
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (state.screen) {
            Screen.Loading -> LoadingScreen()
            Screen.ModeSelection -> ModeSelectionScreen(state, viewModel)
            Screen.Server -> ServerScreen(state, viewModel)
            Screen.Initialize -> InitializeScreen(state, viewModel)
            Screen.Login -> LoginScreen(state, viewModel)
            Screen.Memos -> MemoListScreen(state, viewModel)
            Screen.MemoDetail -> MemoDetailScreen(state, viewModel)
            Screen.Editor -> MemoEditorScreen(state, viewModel)
            Screen.AISettings -> AISettingsScreen(state, viewModel)
            Screen.Ask -> AskScreen(state, viewModel)
        }
    }
}

@Composable
private fun ModeSelectionScreen(state: SillageUiState, viewModel: SillageViewModel) {
    AuthScaffold(
        title = "选择使用方式",
        supporting = "之后可以在设置里切换。",
        state = state,
    ) {
        ModeOptionCard(
            icon = Icons.Rounded.OfflineBolt,
            title = "离线模式",
            supporting = "记录只保存在当前设备。",
            onClick = viewModel::useOfflineMode,
            enabled = !state.loading,
        )
        ModeOptionCard(
            icon = Icons.Rounded.CloudSync,
            title = "在线模式",
            supporting = "连接自托管服务，同步附件和 AI 能力。",
            onClick = viewModel::chooseOnlineMode,
            enabled = !state.loading,
        )
    }
}

@Composable
private fun ModeOptionCard(
    icon: ImageVector,
    title: String,
    supporting: String,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    ElevatedCard(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    supporting,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ServerScreen(state: SillageUiState, viewModel: SillageViewModel) {
    AuthScaffold(
        title = "连接 Sillage",
        supporting = "填写后端服务地址。模拟器访问本机服务可使用 http://10.0.2.2:5231。",
        state = state,
        trailing = {
            if (state.serverReturnScreen != null) {
                TextButton(onClick = viewModel::closeServerSettings) {
                    Text("返回")
                }
            }
        },
    ) {
        OutlinedTextField(
            value = state.baseUrl,
            onValueChange = viewModel::updateBaseUrl,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("服务器地址") },
            placeholder = { Text("https://example.com 或 192.168.1.10:5231") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        Button(
            onClick = viewModel::saveServer,
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.CloudSync, contentDescription = null)
            Text(if (state.loading) "连接中" else "保存并连接")
        }
    }
}

@Composable
private fun InitializeScreen(state: SillageUiState, viewModel: SillageViewModel) {
    AuthScaffold(
        title = "创建唯一账号",
        supporting = "这是你的私密记录空间，初始化后不允许创建第二个账号。",
        state = state,
        trailing = {
            TextButton(onClick = viewModel::openServerSettings) {
                Text("服务器")
            }
        },
    ) {
        OutlinedTextField(
            value = state.username,
            onValueChange = viewModel::updateUsername,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("账号") },
        )
        OutlinedTextField(
            value = state.displayName,
            onValueChange = viewModel::updateDisplayName,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("显示名") },
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::updatePassword,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("密码") },
            visualTransformation = PasswordVisualTransformation(),
        )
        Button(
            onClick = viewModel::initialize,
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.loading) "创建中" else "创建并进入")
        }
    }
}

@Composable
private fun LoginScreen(state: SillageUiState, viewModel: SillageViewModel) {
    AuthScaffold(
        title = "登录 Sillage",
        supporting = "登录后可查看和编辑你的记录。",
        state = state,
        trailing = {
            TextButton(onClick = viewModel::openServerSettings) {
                Text("服务器")
            }
        },
    ) {
        OutlinedTextField(
            value = state.username,
            onValueChange = viewModel::updateUsername,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("账号") },
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::updatePassword,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("密码") },
            visualTransformation = PasswordVisualTransformation(),
        )
        Button(
            onClick = viewModel::signIn,
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.loading) "登录中" else "登录")
        }
    }
}

@Composable
private fun AuthScaffold(
    title: String,
    supporting: String,
    state: SillageUiState,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Sillage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                supporting,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    trailing?.invoke()
                }
                MessageBlock(state.error, state.notice)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    content()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoListScreen(state: SillageUiState, viewModel: SillageViewModel) {
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
                            if (state.memoViewMode == MemoViewMode.Calendar) "日历" else "记录",
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
                    IconButton(onClick = viewModel::refreshMemos, enabled = !state.loading) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新记录")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::startNewMemo) {
                Icon(Icons.Rounded.Add, contentDescription = "新建记录")
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
            MessageBlock(
                error = state.error,
                notice = state.notice,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (state.memoViewMode == MemoViewMode.List) {
                SearchBlock(state = state, viewModel = viewModel)
                SearchStatusBlock(state = state)
            }
            if (state.loading && state.memos.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.memoViewMode == MemoViewMode.Calendar) {
                CalendarMemoView(state = state, viewModel = viewModel)
            } else {
                MemoListView(
                    visibleMemos = visibleMemos,
                    showingSearchResults = showingSearchResults,
                    searching = state.searching,
                    memories = memories,
                    today = today,
                    hasMore = !showingSearchResults && state.memoNextCursor.isNotBlank(),
                    loadingMore = state.loadingMoreMemos,
                    onLoadMore = viewModel::loadMoreMemos,
                    onMemoClick = viewModel::openMemoDetail,
                    onMemoEdit = viewModel::editMemo,
                    onMemoDuplicate = viewModel::duplicateMemoDraft,
                    onMemoTogglePin = viewModel::toggleMemoPinned,
                    onMemoToggleArchive = viewModel::toggleMemoArchived,
                    onMemoDelete = viewModel::deleteMemo,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigationBar(state: SillageUiState, viewModel: SillageViewModel) {
    NavigationBar {
        NavigationBarItem(
            selected = state.screen == Screen.Memos && state.memoViewMode == MemoViewMode.List,
            onClick = { viewModel.updateMemoViewMode(MemoViewMode.List) },
            icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
            label = { Text("记录") },
        )
        NavigationBarItem(
            selected = state.screen == Screen.Memos && state.memoViewMode == MemoViewMode.Calendar,
            onClick = { viewModel.updateMemoViewMode(MemoViewMode.Calendar) },
            icon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
            label = { Text("日历") },
        )
        NavigationBarItem(
            selected = state.screen == Screen.Ask,
            onClick = viewModel::openAsk,
            icon = { Icon(Icons.Rounded.QuestionAnswer, contentDescription = null) },
            label = { Text("Ask") },
        )
        NavigationBarItem(
            selected = state.screen == Screen.AISettings,
            onClick = viewModel::openAISettings,
            icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
            label = { Text("设置") },
        )
    }
}

private fun memoListSubtitle(state: SillageUiState): String {
    val mode = if (state.appMode == SessionStore.MODE_OFFLINE) {
        "离线"
    } else {
        state.account?.displayName ?: state.baseUrl.ifBlank { "在线" }
    }
    return "$mode · ${state.memos.size} 条记录"
}

@Composable
private fun SearchStatusBlock(state: SillageUiState) {
    val query = state.searchQuery.trim()
    val results = state.searchResults
    if (query.isBlank() || results == null) {
        return
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "“$query” · ${results.size} 条结果",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MarkdownModeButton(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
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
    onLoadMore: () -> Unit,
    onMemoClick: (Memo) -> Unit,
    onMemoEdit: (Memo) -> Unit,
    onMemoDuplicate: (Memo) -> Unit,
    onMemoTogglePin: (Memo) -> Unit,
    onMemoToggleArchive: (Memo) -> Unit,
    onMemoDelete: (Memo) -> Unit,
) {
    if (searching && visibleMemos.isEmpty()) {
        EmptyState("正在搜索…", Icons.Rounded.Search)
        return
    }
    if (visibleMemos.isEmpty()) {
        EmptyState(
            if (showingSearchResults) "没有匹配的记录。" else "还没有记录。点右下角加号写第一条。",
            if (showingSearchResults) Icons.Rounded.Search else Icons.Rounded.Edit,
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showingSearchResults) {
            item {
                Text(
                    "搜索结果",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        } else if (memories.isNotEmpty()) {
            item {
                OnThisDayCard(entries = memories, today = today, onMemoClick = onMemoClick)
            }
        }
        items(visibleMemos, key = { it.id }) { memo ->
            MemoSwipeRow(
                memo = memo,
                onClick = { onMemoClick(memo) },
                onEdit = { onMemoEdit(memo) },
                onDuplicate = { onMemoDuplicate(memo) },
                onTogglePin = { onMemoTogglePin(memo) },
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
                    Text(if (loadingMore) "正在加载…" else "加载更多")
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
            placeholder = { Text("搜索记录") },
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
                        Icon(Icons.Rounded.Close, contentDescription = "清除搜索")
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
            Icon(Icons.Rounded.Search, contentDescription = "搜索")
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
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(28.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                Text(
                    "那年今日",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            entries.forEach { memo ->
                Text(
                    "${yearsBetween(memo.entryDate, today)}年前 · ${excerpt(memo.content, 56).ifBlank { "空白记录" }}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onMemoClick(memo) }
                        .padding(vertical = 4.dp),
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
    val weeks = remember(state.calendarYear, state.calendarMonth) {
        monthGrid(state.calendarYear, state.calendarMonth)
    }
    val counts = remember(state.memos) { entryDateCounts(state.memos) }
    val selectedEntries = remember(state.memos, state.selectedCalendarDate) {
        state.selectedCalendarDate?.let { entriesByDate(state.memos, it) }.orEmpty()
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
                onSelectDate = viewModel::selectCalendarDate,
            )
        }
        item {
            Text(
                state.selectedCalendarDate ?: "选择一天查看当天记录。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (state.selectedCalendarDate != null && selectedEntries.isEmpty()) {
            item {
                EmptyCalendarSelection()
            }
        }
        items(selectedEntries, key = { it.id }) { memo ->
            MemoSwipeRow(
                memo = memo,
                onClick = { viewModel.openMemoDetail(memo) },
                onEdit = { viewModel.editMemo(memo) },
                onDuplicate = { viewModel.duplicateMemoDraft(memo) },
                onTogglePin = { viewModel.toggleMemoPinned(memo) },
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = { viewModel.changeCalendarMonth(-1) }) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = "${previous.first}年${previous.second}月",
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "${state.calendarYear}年${state.calendarMonth}月",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                "按日期回看记录",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        IconButton(onClick = { viewModel.changeCalendarMonth(1) }) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "${next.first}年${next.second}月",
            )
        }
    }
}

@Composable
private fun CalendarGrid(
    weeks: List<List<String?>>,
    counts: Map<String, Int>,
    today: String,
    selectedDate: String?,
    onSelectDate: (String) -> Unit,
) {
    val weekdays = listOf("日", "一", "二", "三", "四", "五", "六")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
    val color = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        count > 0 -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    Card(
        modifier = modifier
            .height(48.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = color),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                date.takeLast(2).toInt().toString(),
                fontWeight = if (isToday || selected) FontWeight.SemiBold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (count > 0) {
                Text(
                    count.toString(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun EmptyCalendarSelection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Text(
            "这一天没有记录。",
            modifier = Modifier.padding(14.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun MemoSwipeRow(
    memo: Memo,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var showActions by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { direction ->
            when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> onTogglePin()
                SwipeToDismissBoxValue.EndToStart -> onToggleArchive()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.34f },
    )
    if (showActions) {
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
            onTogglePin = {
                showActions = false
                onTogglePin()
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
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            MemoSwipeBackground(
                targetValue = dismissState.targetValue,
                memo = memo,
            )
        },
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
    ) {
        MemoRow(
            memo = memo,
            onClick = onClick,
            onLongClick = { showActions = true },
        )
    }
}

@Composable
private fun MemoSwipeBackground(targetValue: SwipeToDismissBoxValue, memo: Memo) {
    val isPin = targetValue == SwipeToDismissBoxValue.StartToEnd
    val active = targetValue != SwipeToDismissBoxValue.Settled
    val color = when {
        !active -> MaterialTheme.colorScheme.surfaceContainer
        isPin -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val alignment = if (isPin) Alignment.CenterStart else Alignment.CenterEnd
    val icon = if (isPin) Icons.Rounded.PushPin else Icons.Rounded.Archive
    val label = if (isPin) {
        if (memo.pinnedAt == null) "置顶" else "取消置顶"
    } else {
        if (memo.archivedAt == null) "归档" else "取消归档"
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color, RoundedCornerShape(8.dp))
            .padding(horizontal = 18.dp),
        contentAlignment = alignment,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
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
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmingDelete by remember(memo.id) { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                excerpt(memo.content, 64).ifBlank { "空白记录" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${memo.entryDate} · 快捷操作",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            QuickActionRow(
                icon = Icons.Rounded.Edit,
                title = "编辑",
                supporting = "直接进入编辑器。",
                onClick = onEdit,
            )
            QuickActionRow(
                icon = Icons.Rounded.ContentCopy,
                title = "复制为新记录",
                supporting = "保留正文，日期使用今天。",
                onClick = onDuplicate,
            )
            QuickActionRow(
                icon = Icons.Rounded.PushPin,
                title = if (memo.pinnedAt == null) "置顶" else "取消置顶",
                supporting = if (memo.pinnedAt == null) "保留在列表顶部。" else "恢复到时间顺序。",
                onClick = onTogglePin,
            )
            QuickActionRow(
                icon = Icons.Rounded.Archive,
                title = if (memo.archivedAt == null) "归档" else "取消归档",
                supporting = if (memo.archivedAt == null) "从主列表移除。" else "回到主列表。",
                onClick = onToggleArchive,
            )
            QuickActionRow(
                icon = Icons.Rounded.Delete,
                title = if (confirmingDelete) "确认删除" else "删除",
                supporting = if (confirmingDelete) "此操作会从当前列表移除。" else "从当前列表移除。",
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
private fun QuickActionRow(
    icon: ImageVector,
    title: String,
    supporting: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = RoundedCornerShape(8.dp),
                color = if (destructive) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = if (destructive) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                }
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemoRow(memo: Memo, onClick: () -> Unit, onLongClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                memo.content.ifBlank { "空白记录" },
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
                    memo.entryDate,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
                MemoStatusPills(memo)
            }
        }
    }
}

@Composable
private fun MemoStatusPills(memo: Memo?) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (memo?.pinnedAt != null) {
            StatusPill("置顶")
        }
        if (memo?.archivedAt != null) {
            StatusPill("归档")
        }
    }
}

@Composable
private fun StatusPill(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoDetailScreen(state: SillageUiState, viewModel: SillageViewModel) {
    val memo = state.selectedMemo
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete && memo != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除记录？") },
            text = { Text("删除后会从当前列表移除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteSelectedMemo()
                    },
                    enabled = !state.loading,
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }, enabled = !state.loading) {
                    Text("取消")
                }
            },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("记录详情", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = viewModel::closeMemoDetail) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::editSelectedMemo,
                        enabled = memo != null && !state.loading,
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = "编辑记录")
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }, enabled = memo != null && !state.loading) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "更多操作")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            if (memo != null) {
                                DropdownMenuItem(
                                    text = { Text(if (memo.pinnedAt == null) "置顶" else "取消置顶") },
                                    leadingIcon = { Icon(Icons.Rounded.PushPin, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.toggleSelectedMemoPinned()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (memo.archivedAt == null) "归档" else "取消归档") },
                                    leadingIcon = { Icon(Icons.Rounded.Archive, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.toggleSelectedMemoArchived()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("删除") },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        confirmDelete = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (memo == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("这条记录不存在或已被删除。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                MessageBlock(state.error, state.notice)
            }
            item {
                MemoDetailCard(memo)
            }
            item {
                MemoInsightStrip(memo)
            }
            item {
                MemoSummarySection(
                    summary = state.selectedSummary,
                    loading = state.summaryLoading,
                    onGenerate = viewModel::summarizeSelectedMemo,
                )
            }
            item {
                MemoMetadataBlock(memo)
            }
        }
    }
}

@Composable
private fun MemoInsightStrip(memo: Memo) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MemoInsightChip(
            icon = Icons.Rounded.History,
            label = "创建",
            value = compactDateTime(memo.createdAt),
            modifier = Modifier.weight(1f),
        )
        MemoInsightChip(
            icon = Icons.Rounded.Update,
            label = "更新",
            value = compactDateTime(memo.updatedAt),
            modifier = Modifier.weight(1f),
        )
        MemoInsightChip(
            icon = Icons.Rounded.Edit,
            label = "版本",
            value = memo.version.toString(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MemoInsightChip(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
            Text(
                value.ifBlank { "-" },
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun compactDateTime(value: String): String {
    if (value.length < 10) {
        return value
    }
    val date = value.take(10)
    val time = value.substringAfter('T', "").take(5)
    return if (time.isBlank()) date else "$date $time"
}

@Composable
private fun MemoDetailCard(memo: Memo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    memo.entryDate,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
                MemoStatusLine(memo)
            }
            if (memo.content.trim().isBlank()) {
                Text(
                    "空白记录",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                MarkdownContent(memo.content)
            }
        }
    }
}

@Composable
private fun MarkdownContent(content: String) {
    val blocks = remember(content) { parseMarkdownPreview(content) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (blocks.isEmpty()) {
            Text(
                content,
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            blocks.forEach { block ->
                MarkdownPreviewBlock(block)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoEditorScreen(state: SillageUiState, viewModel: SillageViewModel) {
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val attachmentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        viewModel.uploadAttachments(uris)
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除记录？") },
            text = {
                Text(
                    if (state.appMode == SessionStore.MODE_OFFLINE) {
                        "删除后会从离线列表移除。"
                    } else {
                        "删除后会从当前列表移除，并同步为服务端 tombstone。"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteSelectedMemo()
                    },
                    enabled = !state.loading,
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }, enabled = !state.loading) {
                    Text("取消")
                }
            },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.selectedMemo == null) "新建记录" else "编辑记录") },
                navigationIcon = {
                    IconButton(onClick = viewModel::closeEditor) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    val selected = state.selectedMemo
                    IconButton(onClick = viewModel::saveMemo, enabled = !state.loading) {
                        Icon(Icons.Rounded.Check, contentDescription = if (state.loading) "保存中" else "保存")
                    }
                    if (selected != null) {
                        Box {
                            IconButton(onClick = { menuExpanded = true }, enabled = !state.loading) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "更多操作")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (selected.pinnedAt == null) "置顶" else "取消置顶") },
                                    leadingIcon = { Icon(Icons.Rounded.PushPin, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.toggleSelectedMemoPinned()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (selected.archivedAt == null) "归档" else "取消归档") },
                                    leadingIcon = { Icon(Icons.Rounded.Archive, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.toggleSelectedMemoArchived()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("删除") },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        confirmDelete = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MessageBlock(state.error, state.notice)
            MemoStatusLine(state.selectedMemo)
            MemoMetadataBlock(state.selectedMemo)
            OutlinedTextField(
                value = state.draftEntryDate,
                onValueChange = viewModel::updateDraftEntryDate,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("日期 YYYY-MM-DD") },
            )
            MarkdownEditorSection(
                content = state.draftContent,
                preview = state.markdownPreview,
                onContentChange = viewModel::updateDraftContent,
                onPreviewChange = viewModel::updateMarkdownPreview,
                onFormat = viewModel::appendMarkdownFormat,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            if (state.appMode == SessionStore.MODE_ONLINE) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { attachmentLauncher.launch("*/*") },
                        enabled = !state.uploadingAttachment,
                    ) {
                        Icon(Icons.Rounded.AttachFile, contentDescription = null)
                        Text(if (state.uploadingAttachment) "上传中" else "附件")
                    }
                }
            }
            if (state.selectedMemo != null) {
                MemoSummarySection(
                    summary = state.selectedSummary,
                    loading = state.summaryLoading,
                    onGenerate = viewModel::summarizeSelectedMemo,
                )
            }
        }
    }
}

@Composable
private fun MarkdownEditorSection(
    content: String,
    preview: Boolean,
    onContentChange: (String) -> Unit,
    onPreviewChange: (Boolean) -> Unit,
    onFormat: (MarkdownFormatStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MarkdownModeButton("编辑", !preview) { onPreviewChange(false) }
                MarkdownModeButton("预览", preview) { onPreviewChange(true) }
            }
            Text(
                markdownDraftStats(content),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
        MarkdownToolbar(onFormat)
        if (preview) {
            MarkdownPreview(
                content = content,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        } else {
            OutlinedTextField(
                value = content,
                onValueChange = onContentChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = { Text("内容") },
                placeholder = { Text("写下想记录的内容…") },
            )
        }
    }
}

private fun markdownDraftStats(content: String): String {
    val characters = content.trim().length
    val lines = if (content.isBlank()) 0 else content.lines().size
    return "$characters 字 · $lines 行"
}

@Composable
private fun MarkdownToolbar(onFormat: (MarkdownFormatStyle) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MarkdownToolButton(Icons.Rounded.Title, "标题") { onFormat(MarkdownFormatStyle.Heading) }
        MarkdownToolButton(Icons.Rounded.FormatBold, "加粗") { onFormat(MarkdownFormatStyle.Bold) }
        MarkdownToolButton(Icons.Rounded.FormatItalic, "斜体") { onFormat(MarkdownFormatStyle.Italic) }
        MarkdownToolButton(Icons.Rounded.Code, "代码") { onFormat(MarkdownFormatStyle.Code) }
        MarkdownToolButton(Icons.AutoMirrored.Rounded.FormatListBulleted, "列表") { onFormat(MarkdownFormatStyle.List) }
        MarkdownToolButton(Icons.Rounded.FormatQuote, "引用") { onFormat(MarkdownFormatStyle.Quote) }
    }
}

@Composable
private fun MarkdownToolButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        },
    )
}

@Composable
private fun MarkdownPreview(content: String, modifier: Modifier = Modifier) {
    val blocks = remember(content) { parseMarkdownPreview(content) }
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        if (blocks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(14.dp), contentAlignment = Alignment.Center) {
                Text(
                    "没有可预览的内容",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(blocks) { block ->
                    MarkdownPreviewBlock(block)
                }
            }
        }
    }
}

@Composable
private fun MarkdownPreviewBlock(block: MarkdownBlock) {
    when (block.kind) {
        MarkdownBlockKind.Heading -> Text(
            block.text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        MarkdownBlockKind.Quote -> Text(
            "｜ ${block.text}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        MarkdownBlockKind.ListItem -> Text(
            "• ${block.text}",
            style = MaterialTheme.typography.bodyMedium,
        )
        MarkdownBlockKind.Code -> Text(
            block.text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        MarkdownBlockKind.Link -> Text(
            "${block.text} · ${block.url.orEmpty()}",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
        )
        MarkdownBlockKind.Image -> Text(
            "图片：${block.text} · ${block.url.orEmpty()}",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
        )
        MarkdownBlockKind.Paragraph -> Text(
            block.text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun MemoStatusLine(memo: Memo?) {
    val flags = listOfNotNull(
        if (memo?.pinnedAt != null) "置顶" else null,
        if (memo?.archivedAt != null) "已归档" else null,
    )
    if (flags.isEmpty()) {
        return
    }
    Text(
        flags.joinToString(" · "),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium,
    )
}

@Composable
private fun MemoMetadataBlock(memo: Memo?) {
    val lines = memoMetadataLines(memo)
    if (lines.isEmpty()) {
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lines.forEach { line ->
            Text(
                line,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun MemoSummarySection(
    summary: MemoAI?,
    loading: Boolean,
    onGenerate: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "总结",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onGenerate, enabled = !loading) {
                    Text(
                        when {
                            loading && summary == null -> "读取中"
                            loading -> "总结中"
                            summary == null -> "生成总结"
                            else -> "重新总结"
                        },
                    )
                }
            }
            val body = summary?.summary?.takeIf { it.isNotBlank() }
            if (body != null) {
                Text(
                    body,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                SummaryMeta(summary)
            } else {
                Text(
                    if (loading) "正在读取总结…" else "让 AI 基于这条记录生成一段简短的总结。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SummaryMeta(summary: MemoAI) {
    val model = listOf(summary.provider, summary.model)
        .filter { it.isNotBlank() }
        .joinToString(" / ")
    if (model.isBlank() && summary.totalTokens == 0L) {
        return
    }
    val text = buildString {
        if (model.isNotBlank()) {
            append(model)
        }
        if (summary.totalTokens > 0) {
            if (isNotEmpty()) {
                append(" · ")
            }
            append(summary.totalTokens)
            append(" tokens")
        }
    }
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium,
    )
}

@Composable
fun MessageBlock(error: String?, notice: String?, modifier: Modifier = Modifier) {
    val text = error ?: notice ?: return
    val isError = error != null
    val container = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val content = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
