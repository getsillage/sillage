package com.miofelix.sillage.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miofelix.sillage.data.AIProfileDraft
import com.miofelix.sillage.data.AskConversation
import com.miofelix.sillage.data.AskMessage
import com.miofelix.sillage.data.AskPathEntry
import com.miofelix.sillage.data.Memo
import com.miofelix.sillage.data.MemoAI
import com.miofelix.sillage.data.buildAskActivePath
import com.miofelix.sillage.data.lastAssistantMessageId

@Composable
fun SillageApp(viewModel: SillageViewModel) {
    val state by viewModel.state.collectAsState()
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (state.screen) {
            Screen.Loading -> LoadingScreen()
            Screen.Server -> ServerScreen(state, viewModel)
            Screen.Initialize -> InitializeScreen(state, viewModel)
            Screen.Login -> LoginScreen(state, viewModel)
            Screen.Memos -> MemoListScreen(state, viewModel)
            Screen.Editor -> MemoEditorScreen(state, viewModel)
            Screen.AISettings -> AISettingsScreen(state, viewModel)
            Screen.Ask -> AskScreen(state, viewModel)
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
    ) {
        OutlinedTextField(
            value = state.baseUrl,
            onValueChange = viewModel::updateBaseUrl,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("服务器地址") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        Button(
            onClick = viewModel::saveServer,
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("记录", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            state.account?.displayName ?: state.baseUrl,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::refreshMemos, enabled = !state.loading) {
                        Text("刷新")
                    }
                    TextButton(onClick = viewModel::openAISettings) {
                        Text("AI")
                    }
                    TextButton(onClick = viewModel::openAsk) {
                        Text("Ask")
                    }
                    TextButton(onClick = viewModel::openServerSettings) {
                        Text("服务器")
                    }
                    TextButton(onClick = viewModel::signOut) {
                        Text("退出")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::startNewMemo) {
                Text("+", style = MaterialTheme.typography.headlineSmall)
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
            SearchBlock(state = state, viewModel = viewModel)
            if (state.loading && state.memos.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.searching && visibleMemos.isEmpty()) {
                EmptyState("正在搜索…")
            } else if (visibleMemos.isEmpty()) {
                EmptyState(if (showingSearchResults) "没有匹配的记录。" else "还没有记录。点右下角加号写第一条。")
            } else {
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
                    }
                    items(visibleMemos, key = { it.id }) { memo ->
                        MemoRow(memo = memo, onClick = { viewModel.editMemo(memo) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBlock(state: SillageUiState, viewModel: SillageViewModel) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = viewModel::updateSearchQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("搜索记录") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.searchMemos() }),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = viewModel::searchMemos,
                enabled = !state.searching && state.searchQuery.isNotBlank(),
            ) {
                Text(if (state.searching) "搜索中" else "搜索")
            }
            TextButton(
                onClick = viewModel::clearSearch,
                enabled = state.searchQuery.isNotBlank() || state.searchResults != null,
            ) {
                Text("清除")
            }
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
private fun MemoEditorScreen(state: SillageUiState, viewModel: SillageViewModel) {
    var menuExpanded by remember { mutableStateOf(false) }
    val attachmentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        viewModel.uploadAttachments(uris)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.selectedMemo == null) "新建记录" else "编辑记录") },
                navigationIcon = {
                    TextButton(onClick = viewModel::closeEditor) {
                        Text("返回")
                    }
                },
                actions = {
                    val selected = state.selectedMemo
                    if (selected != null) {
                        Box {
                            TextButton(onClick = { menuExpanded = true }, enabled = !state.loading) {
                                Text("更多")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (selected.pinnedAt == null) "置顶" else "取消置顶") },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.toggleSelectedMemoPinned()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (selected.archivedAt == null) "归档" else "取消归档") },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.toggleSelectedMemoArchived()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("删除") },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.deleteSelectedMemo()
                                    },
                                )
                            }
                        }
                    }
                    TextButton(onClick = viewModel::saveMemo, enabled = !state.loading) {
                        Text(if (state.loading) "保存中" else "保存")
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
            OutlinedTextField(
                value = state.draftEntryDate,
                onValueChange = viewModel::updateDraftEntryDate,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("日期 YYYY-MM-DD") },
            )
            OutlinedTextField(
                value = state.draftContent,
                onValueChange = viewModel::updateDraftContent,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = { Text("内容") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { attachmentLauncher.launch("*/*") },
                    enabled = !state.uploadingAttachment,
                ) {
                    Text(if (state.uploadingAttachment) "上传中" else "附件")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AskScreen(state: SillageUiState, viewModel: SillageViewModel) {
    val entries = remember(state.askMessages, state.askHeadId) {
        buildAskActivePath(state.askMessages, state.askHeadId)
    }
    val latestAssistantId = remember(entries) {
        lastAssistantMessageId(entries)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ask") },
                navigationIcon = {
                    TextButton(onClick = viewModel::closeAsk) {
                        Text("返回")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::startNewAsk, enabled = !state.askSending) {
                        Text("新会话")
                    }
                    TextButton(onClick = viewModel::loadAskConversations, enabled = !state.askLoading) {
                        Text("刷新")
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MessageBlock(state.error, state.notice)
            AskOptions(state, viewModel)
            AskConversationList(state.askConversations, state.activeAskId, viewModel)
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
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (entries.isEmpty()) {
                        item {
                            Text(
                                "可以根据记录提问，例如「我最近在反复想些什么？」",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    items(entries, key = { it.message.id }) { entry ->
                        AskMessageCard(
                            entry = entry,
                            canRegenerate = entry.message.id == latestAssistantId && !state.askSending,
                            regenerating = state.askRegeneratingId == entry.message.id,
                            onRegenerate = { viewModel.regenerateAskAnswer(entry.message.id) },
                            onSelectVariant = viewModel::selectAskVariant,
                        )
                    }
                    if (state.askSending && state.askRegeneratingId.isBlank()) {
                        item {
                            Text(
                                "正在生成回答…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
            OutlinedTextField(
                value = state.askQuestion,
                onValueChange = viewModel::updateAskQuestion,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                label = { Text("根据记录提问") },
            )
            Button(
                onClick = viewModel::sendAskQuestion,
                enabled = !state.askSending,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.askSending) "生成中" else "发送")
            }
        }
    }
}

@Composable
private fun AskOptions(state: SillageUiState, viewModel: SillageViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    viewModel: SillageViewModel,
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
            .height(112.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(conversations, key = { it.id }) { conversation ->
            TextButton(
                onClick = { viewModel.selectAskConversation(conversation.id) },
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
    onRegenerate: () -> Unit,
    onSelectVariant: (String) -> Unit,
) {
    val message = entry.message
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
                if (message.role == "assistant") "回答" else "问题",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                if (regenerating) "正在重新生成…" else message.content,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (message.sourceRefs.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "来源",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    message.sourceRefs.take(5).forEach { source ->
                        Text(
                            "${source.entryDate} · ${source.excerpt}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (message.role == "assistant") {
                AskMessageActions(
                    entry = entry,
                    canRegenerate = canRegenerate,
                    regenerating = regenerating,
                    onRegenerate = onRegenerate,
                    onSelectVariant = onSelectVariant,
                )
            }
        }
    }
}

@Composable
private fun AskMessageActions(
    entry: AskPathEntry,
    canRegenerate: Boolean,
    regenerating: Boolean,
    onRegenerate: () -> Unit,
    onSelectVariant: (String) -> Unit,
) {
    val hasVariants = entry.variants.size > 1
    if (!hasVariants && !canRegenerate && !regenerating) {
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (hasVariants) {
            TextButton(
                onClick = {
                    val previous = entry.variants.getOrNull(entry.index - 1)
                    if (previous != null) {
                        onSelectVariant(previous.id)
                    }
                },
                enabled = entry.index > 0 && !regenerating,
            ) {
                Text("上一条")
            }
            Text(
                "${entry.index + 1}/${entry.variants.size}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            TextButton(
                onClick = {
                    val next = entry.variants.getOrNull(entry.index + 1)
                    if (next != null) {
                        onSelectVariant(next.id)
                    }
                },
                enabled = entry.index >= 0 && entry.index < entry.variants.lastIndex && !regenerating,
            ) {
                Text("下一条")
            }
        }
        if (canRegenerate || regenerating) {
            TextButton(onClick = onRegenerate, enabled = canRegenerate && !regenerating) {
                Text(if (regenerating) "生成中" else "重新生成")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AISettingsScreen(state: SillageUiState, viewModel: SillageViewModel) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 设置") },
                navigationIcon = {
                    TextButton(onClick = viewModel::closeAISettings) {
                        Text("返回")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::addAIProfile, enabled = !state.aiSettingsSaving) {
                        Text("新增")
                    }
                    TextButton(onClick = viewModel::saveAISettings, enabled = !state.aiSettingsSaving) {
                        Text(if (state.aiSettingsSaving) "保存中" else "保存")
                    }
                },
            )
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
            } else if (state.aiProfiles.isEmpty()) {
                EmptyState("还没有 AI 档案。点击右上角新增。")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            "密钥加密保存在本地服务端，不会回显。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    items(state.aiProfiles.size, key = { index -> state.aiProfiles[index].id.ifBlank { "new-$index" } }) { index ->
                        AIProfileCard(
                            index = index,
                            profile = state.aiProfiles[index],
                            testing = state.aiTestingProfileId == state.aiProfiles[index].id,
                            testResult = state.aiTestResults[state.aiProfiles[index].id],
                            viewModel = viewModel,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AIProfileCard(
    index: Int,
    profile: AIProfileDraft,
    testing: Boolean,
    testResult: String?,
    viewModel: SillageViewModel,
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
                TextButton(onClick = { viewModel.removeAIProfile(index) }) {
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
