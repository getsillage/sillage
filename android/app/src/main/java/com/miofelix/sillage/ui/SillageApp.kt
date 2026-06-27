package com.miofelix.sillage.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miofelix.sillage.data.AIProfileDraft
import com.miofelix.sillage.data.AskConversation
import com.miofelix.sillage.data.AskMessage
import com.miofelix.sillage.data.AskPathEntry
import com.miofelix.sillage.data.AskSourceRef
import com.miofelix.sillage.data.MarkdownBlock
import com.miofelix.sillage.data.MarkdownBlockKind
import com.miofelix.sillage.data.MarkdownFormatStyle
import com.miofelix.sillage.data.Memo
import com.miofelix.sillage.data.MemoAI
import com.miofelix.sillage.data.SessionStore
import com.miofelix.sillage.data.adjacentMonth
import com.miofelix.sillage.data.askSourceLabel
import com.miofelix.sillage.data.buildAskActivePath
import com.miofelix.sillage.data.entriesByDate
import com.miofelix.sillage.data.entryDateCounts
import com.miofelix.sillage.data.excerpt
import com.miofelix.sillage.data.lastAssistantMessageId
import com.miofelix.sillage.data.memoMetadataLines
import com.miofelix.sillage.data.monthGrid
import com.miofelix.sillage.data.onThisDay
import com.miofelix.sillage.data.parseMarkdownPreview
import com.miofelix.sillage.data.yearsBetween
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
        supporting = "首次启动需要选择数据保存方式。之后会直接进入上次选择的模式，也可以在应用内切换。",
        state = state,
    ) {
        Button(
            onClick = viewModel::useOfflineMode,
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.OfflineBolt, contentDescription = null)
            Text("离线模式")
        }
        Text(
            "记录只保存在当前设备。适合先体验、无网络或不想配置服务器时使用。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            onClick = viewModel::chooseOnlineMode,
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.CloudSync, contentDescription = null)
            Text("在线模式")
        }
        Text(
            "连接自托管 Sillage 服务，支持登录、附件、跨设备同步和服务端 AI 能力。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
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
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sillage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        supporting,
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                        Text("记录", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (state.appMode == SessionStore.MODE_OFFLINE) {
                                "离线模式"
                            } else {
                                state.account?.displayName ?: state.baseUrl
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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
                    onMemoClick = viewModel::openMemoDetail,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainNavigationBar(state: SillageUiState, viewModel: SillageViewModel) {
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

@Composable
private fun MarkdownModeButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) {
            Text(label)
        }
    } else {
        TextButton(onClick = onClick) {
            Text(label)
        }
    }
}

@Composable
private fun MemoListView(
    visibleMemos: List<Memo>,
    showingSearchResults: Boolean,
    searching: Boolean,
    memories: List<Memo>,
    today: String,
    onMemoClick: (Memo) -> Unit,
) {
    if (searching && visibleMemos.isEmpty()) {
        EmptyState("正在搜索…")
        return
    }
    if (visibleMemos.isEmpty()) {
        EmptyState(if (showingSearchResults) "没有匹配的记录。" else "还没有记录。点右下角加号写第一条。")
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
            MemoRow(memo = memo, onClick = { onMemoClick(memo) })
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
private fun EmptyState(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OnThisDayCard(entries: List<Memo>, today: String, onMemoClick: (Memo) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "那年今日",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
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
            MemoRow(memo = memo, onClick = { viewModel.openMemoDetail(memo) })
        }
    }
}

@Composable
private fun CalendarHeader(state: SillageUiState, viewModel: SillageViewModel) {
    val previous = adjacentMonth(state.calendarYear, state.calendarMonth, -1)
    val next = adjacentMonth(state.calendarYear, state.calendarMonth, 1)
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { viewModel.changeCalendarMonth(-1) }) {
            Text("${previous.first}年${previous.second}月")
        }
        Text(
            "${state.calendarYear}年${state.calendarMonth}月",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = { viewModel.changeCalendarMonth(1) }) {
            Text("${next.first}年${next.second}月")
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
private fun MemoRow(memo: Memo, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                memo.content,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                if (memo.pinnedAt == null) memo.entryDate else "置顶 · ${memo.entryDate}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
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
                    Text("删除")
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
                    Text("删除")
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MarkdownModeButton("编辑", !preview) { onPreviewChange(false) }
            MarkdownModeButton("预览", preview) { onPreviewChange(true) }
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

@Composable
private fun MarkdownToolbar(onFormat: (MarkdownFormatStyle) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MarkdownToolButton("H") { onFormat(MarkdownFormatStyle.Heading) }
        MarkdownToolButton("B") { onFormat(MarkdownFormatStyle.Bold) }
        MarkdownToolButton("I") { onFormat(MarkdownFormatStyle.Italic) }
        MarkdownToolButton("`") { onFormat(MarkdownFormatStyle.Code) }
        MarkdownToolButton("列表") { onFormat(MarkdownFormatStyle.List) }
        MarkdownToolButton("引用") { onFormat(MarkdownFormatStyle.Quote) }
    }
}

@Composable
private fun MarkdownToolButton(label: String, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(label) })
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AskScreen(state: SillageUiState, viewModel: SillageViewModel) {
    var showConversations by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var composerFocused by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val entries = remember(state.askMessages, state.askHeadId) {
        buildAskActivePath(state.askMessages, state.askHeadId)
    }
    val latestAssistantId = remember(entries) {
        lastAssistantMessageId(entries)
    }
    val listItemCount = entries.size +
        (if (entries.isEmpty()) 1 else 0) +
        (if (state.askLiveUser != null) 1 else 0) +
        (if (state.askSending && state.askRegeneratingId.isBlank()) 1 else 0)
    LaunchedEffect(
        entries.size,
        state.askLiveUser?.id,
        state.askSending,
        state.askLiveAnswer.length,
    ) {
        if (listItemCount > 0) {
            listState.animateScrollToItem(listItemCount - 1)
        }
    }
    if (showConversations) {
        AskConversationSheet(
            state = state,
            viewModel = viewModel,
            onDismiss = { showConversations = false },
        )
    }
    if (showOptions) {
        AskOptionsSheet(
            state = state,
            viewModel = viewModel,
            onDismiss = { showOptions = false },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Ask", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            askContextLabel(state),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { showConversations = true }) {
                        Text("会话")
                    }
                    TextButton(onClick = { showOptions = true }) {
                        Text("上下文")
                    }
                    IconButton(onClick = viewModel::startNewAsk, enabled = !state.askSending) {
                        Icon(Icons.Rounded.Add, contentDescription = "新会话")
                    }
                },
            )
        },
        bottomBar = {
            if (!composerFocused) {
                MainNavigationBar(state = state, viewModel = viewModel)
            }
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
            if (state.askLoading && entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (entries.isEmpty()) {
                        item {
                            AskEmptyPrompt()
                        }
                    }
                    items(entries, key = { it.message.id }) { entry ->
                        AskMessageCard(
                            entry = entry,
                            canRegenerate = entry.message.id == latestAssistantId && !state.askSending,
                            regenerating = state.askRegeneratingId == entry.message.id,
                            savingDisabled = state.loading || state.askSending,
                            streamingText = if (state.askRegeneratingId == entry.message.id) state.askLiveAnswer else null,
                            onRegenerate = { viewModel.regenerateAskAnswer(entry.message.id) },
                            onSaveAsMemo = { viewModel.saveAskAnswerAsMemo(entry.message) },
                            onOpenSource = viewModel::openAskSourceMemo,
                            onSelectVariant = viewModel::selectAskVariant,
                        )
                    }
                    if (state.askLiveUser != null) {
                        item {
                            AskLiveUserCard(state.askLiveUser)
                        }
                    }
                    if (state.askSending && state.askRegeneratingId.isBlank()) {
                        item {
                            AskLiveAnswerCard(state.askLiveAnswer)
                        }
                    }
                }
            }
            AskComposer(
                state = state,
                viewModel = viewModel,
                onFocusChanged = { composerFocused = it },
            )
        }
    }
}

private fun askContextLabel(state: SillageUiState): String {
    val scope = when (state.askScope) {
        "recent_7_days" -> "最近 7 天"
        "all" -> "全部记录"
        else -> "最近 30 天"
    }
    val source = if (state.askSourceKind == "summaries") "记录总结" else "原始记录"
    return "$scope · $source"
}

@Composable
private fun AskEmptyPrompt() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "可以根据记录提问",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "例如「我最近在反复想些什么？」或「这周有什么值得继续做？」",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun AskComposer(
    state: SillageUiState,
    viewModel: SillageViewModel,
    onFocusChanged: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = state.askQuestion,
                onValueChange = viewModel::updateAskQuestion,
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { onFocusChanged(it.isFocused) },
                minLines = 1,
                maxLines = 3,
                placeholder = { Text("根据记录提问") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (!state.askSending && state.askQuestion.isNotBlank()) {
                            viewModel.sendAskQuestion()
                        }
                    },
                ),
            )
            if (state.askStreaming) {
                IconButton(
                    onClick = viewModel::stopAskStreaming,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(Icons.Rounded.StopCircle, contentDescription = "停止生成")
                }
            } else {
                FilledIconButton(
                    onClick = viewModel::sendAskQuestion,
                    enabled = !state.askSending && state.askQuestion.isNotBlank(),
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "发送")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AskConversationSheet(
    state: SillageUiState,
    viewModel: SillageViewModel,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "问答会话",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = viewModel::loadAskConversations, enabled = !state.askLoading) {
                    Text("刷新")
                }
            }
            AskConversationList(
                conversations = state.askConversations,
                activeId = state.activeAskId,
                onSelect = {
                    viewModel.selectAskConversation(it)
                    onDismiss()
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AskOptionsSheet(
    state: SillageUiState,
    viewModel: SillageViewModel,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "上下文",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            AskOptions(state, viewModel)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AskOptions(state: SillageUiState, viewModel: SillageViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "时间范围",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AskOptionButton("7 天", state.askScope == "recent_7_days") {
                viewModel.updateAskScope("recent_7_days")
            }
            AskOptionButton("30 天", state.askScope == "recent_30_days") {
                viewModel.updateAskScope("recent_30_days")
            }
            AskOptionButton("全部", state.askScope == "all") {
                viewModel.updateAskScope("all")
            }
        }
        Text(
            "来源",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AskOptionButton("原始记录", state.askSourceKind == "records") {
                viewModel.updateAskSourceKind("records")
            }
            AskOptionButton("记录总结", state.askSourceKind == "summaries") {
                viewModel.updateAskSourceKind("summaries")
            }
        }
    }
}

@Composable
private fun AskOptionButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) {
            Text(label)
        }
    } else {
        TextButton(onClick = onClick) {
            Text(label)
        }
    }
}

@Composable
private fun AskConversationList(
    conversations: List<AskConversation>,
    activeId: String,
    onSelect: (String) -> Unit,
) {
    if (conversations.isEmpty()) {
        Text(
            "暂无问答会话。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(conversations, key = { it.id }) { conversation ->
            TextButton(
                onClick = { onSelect(conversation.id) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (conversation.id == activeId) "当前 · ${conversation.title}" else conversation.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AskMessageCard(
    entry: AskPathEntry,
    canRegenerate: Boolean,
    regenerating: Boolean,
    savingDisabled: Boolean,
    streamingText: String?,
    onRegenerate: () -> Unit,
    onSaveAsMemo: () -> Unit,
    onOpenSource: (String) -> Unit,
    onSelectVariant: (String) -> Unit,
) {
    val message = entry.message
    val isAssistant = message.role == "assistant"
    val bubbleColor = if (isAssistant) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.primary
    }
    val textColor = if (isAssistant) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onPrimary
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isAssistant) Alignment.Start else Alignment.End,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(if (isAssistant) 1f else 0.86f),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    when {
                        streamingText != null && streamingText.isNotBlank() -> streamingText
                        regenerating -> "正在重新生成…"
                        else -> message.content
                    },
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (isAssistant && message.sourceRefs.isNotEmpty()) {
                    AskSourceRefs(
                        sources = message.sourceRefs,
                        onOpenSource = onOpenSource,
                    )
                }
                if (isAssistant) {
                    AskMessageActions(
                        entry = entry,
                        canRegenerate = canRegenerate,
                        regenerating = regenerating,
                        savingDisabled = savingDisabled,
                        onRegenerate = onRegenerate,
                        onSaveAsMemo = onSaveAsMemo,
                        onSelectVariant = onSelectVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AskSourceRefs(
    sources: List<AskSourceRef>,
    onOpenSource: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.height(32.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        ) {
            Text(
                "来源 ${sources.size}",
                style = MaterialTheme.typography.labelSmall,
            )
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "隐藏来源" else "显示来源",
                modifier = Modifier.size(16.dp),
            )
        }
        if (expanded) {
            sources.take(5).forEach { source ->
                TextButton(
                    onClick = { onOpenSource(source.memoId) },
                    enabled = source.memoId.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text(
                        askSourceLabel(source),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun AskLiveUserCard(message: AskMessage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.86f),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Text(
                message.content,
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun AskLiveAnswerCard(answer: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                answer.ifBlank { "正在思考…" },
                color = if (answer.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun AskMessageActions(
    entry: AskPathEntry,
    canRegenerate: Boolean,
    regenerating: Boolean,
    savingDisabled: Boolean,
    onRegenerate: () -> Unit,
    onSaveAsMemo: () -> Unit,
    onSelectVariant: (String) -> Unit,
) {
    val hasVariants = entry.variants.size > 1
    val canSave = entry.message.content.isNotBlank()
    if (!hasVariants && !canRegenerate && !regenerating && !canSave) {
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (hasVariants) {
            IconButton(
                onClick = {
                    val previous = entry.variants.getOrNull(entry.index - 1)
                    if (previous != null) {
                        onSelectVariant(previous.id)
                    }
                },
                enabled = entry.index > 0 && !regenerating,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "上一条")
            }
            Text(
                "${entry.index + 1}/${entry.variants.size}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            IconButton(
                onClick = {
                    val next = entry.variants.getOrNull(entry.index + 1)
                    if (next != null) {
                        onSelectVariant(next.id)
                    }
                },
                enabled = entry.index >= 0 && entry.index < entry.variants.lastIndex && !regenerating,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "下一条")
            }
        }
        if (canRegenerate || regenerating) {
            IconButton(
                onClick = onRegenerate,
                enabled = canRegenerate && !regenerating,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = if (regenerating) "生成中" else "重新生成")
            }
        }
        if (canSave) {
            IconButton(
                onClick = onSaveAsMemo,
                enabled = !savingDisabled && !regenerating,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(Icons.Rounded.Save, contentDescription = "存为记录")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AISettingsScreen(state: SillageUiState, viewModel: SillageViewModel) {
    var selectedAIProfileIndex by remember { mutableStateOf<Int?>(null) }
    val selectedIndex = selectedAIProfileIndex?.takeIf { it in state.aiProfiles.indices }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            viewModel.exportFullData(uri)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.importFullData(uri)
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
            )
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
            if (state.aiSettingsLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        SettingsSectionCard(title = "外观") {
                            SettingsActionRow(
                                title = if (state.themeMode == SessionStore.THEME_DARK) "浅色模式" else "深色模式",
                                supporting = "切换应用主题显示。",
                                onClick = viewModel::toggleThemeMode,
                            )
                        }
                    }
                    item {
                        SettingsSectionCard(title = "服务与同步") {
                            SettingsActionRow(
                                title = "刷新记录",
                                supporting = "重新读取当前模式下的记录列表。",
                                onClick = viewModel::refreshMemos,
                                enabled = !state.loading,
                            )
                            SettingsActionRow(
                                title = if (state.appMode == SessionStore.MODE_ONLINE) "当前：在线模式" else "切换到在线模式",
                                supporting = state.baseUrl.ifBlank { "未配置服务器地址" },
                                onClick = viewModel::useOnlineMode,
                                enabled = state.appMode != SessionStore.MODE_ONLINE,
                            )
                            SettingsActionRow(
                                title = if (state.appMode == SessionStore.MODE_OFFLINE) "当前：离线模式" else "切换到离线模式",
                                supporting = "记录保存在当前设备。",
                                onClick = viewModel::useOfflineMode,
                                enabled = state.appMode != SessionStore.MODE_OFFLINE,
                            )
                            if (state.appMode == SessionStore.MODE_ONLINE) {
                                SettingsActionRow(
                                    title = "服务器设置",
                                    supporting = "修改服务地址和重新连接。",
                                    onClick = viewModel::openServerSettings,
                                )
                                SettingsActionRow(
                                    title = "同步到本地",
                                    supporting = "把服务端数据保存到本机离线库。",
                                    onClick = viewModel::syncFromServer,
                                    enabled = !state.loading,
                                )
                                SettingsActionRow(
                                    title = "同步到云端",
                                    supporting = "把本机离线记录推送到服务端。",
                                    onClick = viewModel::syncToServer,
                                    enabled = !state.loading,
                                )
                                SettingsActionRow(
                                    title = "双向同步",
                                    supporting = "先推送本地更改，再拉取服务端数据。",
                                    onClick = viewModel::syncBothWays,
                                    enabled = !state.loading,
                                )
                            }
                        }
                    }
                    item {
                        SettingsSectionCard(title = "数据") {
                            SettingsActionRow(
                                title = "导出完整数据",
                                supporting = "导出记录、AI 设置和问答数据。",
                                onClick = { exportLauncher.launch("sillage-data.json") },
                                enabled = !state.loading,
                            )
                            SettingsActionRow(
                                title = "导入完整数据",
                                supporting = "从 JSON 文件恢复或合并数据。",
                                onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                                enabled = !state.loading,
                            )
                        }
                    }
                    if (state.appMode == SessionStore.MODE_ONLINE) {
                        item {
                            SettingsSectionCard(title = "账号") {
                                SettingsActionRow(
                                    title = "退出登录",
                                    supporting = state.account?.displayName ?: state.account?.username.orEmpty(),
                                    onClick = viewModel::signOut,
                                    enabled = !state.loading,
                                )
                            }
                        }
                    }
                    item {
                        AISettingsHeaderCard(
                            saving = state.aiSettingsSaving,
                            onAdd = {
                                selectedAIProfileIndex = state.aiProfiles.size
                                viewModel.addAIProfile()
                            },
                            onSave = viewModel::saveAISettings,
                        )
                    }
                    if (state.aiProfiles.isEmpty()) {
                        item {
                            EmptySettingsCard("还没有 AI 档案。可以在上方新增一个档案。")
                        }
                    } else {
                        items(state.aiProfiles.size, key = { index -> state.aiProfiles[index].id.ifBlank { "new-$index" } }) { index ->
                            val profile = state.aiProfiles[index]
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AIProfileSummaryCard(
                                    profile = profile,
                                    testResult = state.aiTestResults[profile.id],
                                    selected = selectedIndex == index,
                                    onClick = { selectedAIProfileIndex = index },
                                )
                                if (selectedIndex == index) {
                                    AIProfileDetailCard(
                                        index = index,
                                        profile = profile,
                                        testing = profile.id.isNotBlank() && state.aiTestingProfileId == profile.id,
                                        testResult = state.aiTestResults[profile.id],
                                        viewModel = viewModel,
                                        onClose = { selectedAIProfileIndex = null },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AISettingsHeaderCard(
    saving: Boolean,
    onAdd: () -> Unit,
    onSave: () -> Unit,
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
            Text(
                "AI 档案",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "管理总结和 Ask 使用的模型配置。密钥加密保存在本地服务端，不会回显。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAdd,
                    enabled = !saving,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("新增档案")
                }
                TextButton(
                    onClick = onSave,
                    enabled = !saving,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (saving) "保存中" else "保存 AI 设置")
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    supporting: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(title, textAlign = TextAlign.Start)
            if (supporting.isNotBlank()) {
                Text(
                    supporting,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}

@Composable
private fun EmptySettingsCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Text(
            text,
            modifier = Modifier.padding(14.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun AIProfileSummaryCard(
    profile: AIProfileDraft,
    testResult: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        profile.name.ifBlank { "未命名档案" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        profile.provider.ifBlank { "未设置 Provider" },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (profile.active) {
                    AssistChip(onClick = onClick, label = { Text("默认") })
                }
            }
            Text(
                profile.model.ifBlank { "未设置模型" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (profile.enabled) "已启用" else "已停用",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    if (profile.hasApiKey || profile.apiKeyInput.isNotBlank()) "有密钥" else "无密钥",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
                if (profile.keyUnavailable) {
                    Text(
                        "密钥异常",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            if (testResult != null) {
                Text(
                    testResult,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AIProfileDetailCard(
    index: Int,
    profile: AIProfileDraft,
    testing: Boolean,
    testResult: String?,
    viewModel: SillageViewModel,
    onClose: () -> Unit,
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "详细配置",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "修改当前档案后保存生效。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                TextButton(onClick = onClose) {
                    Text("收起")
                }
            }
            OutlinedTextField(
                value = profile.name,
                onValueChange = { viewModel.updateAIProfileName(index, it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("名称") },
            )
            OutlinedTextField(
                value = profile.provider,
                onValueChange = { viewModel.updateAIProfileProvider(index, it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Provider") },
                placeholder = { Text("anthropic / openai / workers-ai") },
            )
            OutlinedTextField(
                value = profile.baseUrl,
                onValueChange = { viewModel.updateAIProfileBaseUrl(index, it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Base URL") },
            )
            OutlinedTextField(
                value = profile.model,
                onValueChange = { viewModel.updateAIProfileModel(index, it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("模型") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = profile.temperature.toString(),
                    onValueChange = { viewModel.updateAIProfileTemperature(index, it) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("温度") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = profile.maxTokens.toString(),
                    onValueChange = { viewModel.updateAIProfileMaxTokens(index, it) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("最大 Tokens") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            OutlinedTextField(
                value = profile.apiKeyInput,
                onValueChange = { viewModel.updateAIProfileApiKey(index, it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("API 密钥") },
                placeholder = { Text(if (profile.hasApiKey) "已配置，留空保持不变" else "未配置") },
                visualTransformation = PasswordVisualTransformation(),
            )
            if (profile.keyUnavailable) {
                Text(
                    "当前密钥无法解密，请重新填写。",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            AISettingSwitch("启用", profile.enabled) { viewModel.toggleAIProfileEnabled(index) }
            AISettingSwitch("设为默认", profile.active) { viewModel.toggleAIProfileActive(index) }
            AISettingSwitch("新建记录后自动总结", profile.autoSummary) {
                viewModel.toggleAIProfileAutoSummary(index)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.testAIProfile(index) }, enabled = !testing) {
                    Text(if (testing) "测试中" else "测试连接")
                }
                TextButton(onClick = {
                    viewModel.removeAIProfile(index)
                    onClose()
                }) {
                    Text("删除")
                }
            }
            if (testResult != null) {
                Text(
                    testResult,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun AISettingSwitch(label: String, checked: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Switch(checked = checked, onCheckedChange = { onClick() })
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
private fun MessageBlock(error: String?, notice: String?, modifier: Modifier = Modifier) {
    val text = error ?: notice ?: return
    val color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        color = color,
        style = MaterialTheme.typography.bodyMedium,
    )
}
