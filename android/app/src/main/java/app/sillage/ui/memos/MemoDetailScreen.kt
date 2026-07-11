package app.sillage.ui.memos

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sillage.data.MarkdownLinkTarget
import app.sillage.data.Memo
import app.sillage.data.MemoAI
import app.sillage.data.memoMetadataLines
import app.sillage.data.memoSummarySourceCount
import app.sillage.ui.SillageUiState
import app.sillage.ui.SillageViewModel
import app.sillage.ui.common.MessageBlock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemoDetailScreen(state: SillageUiState, viewModel: SillageViewModel) {
    val memo = state.selectedMemo
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    BackHandler(onBack = viewModel::closeMemoDetail)
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
                                    text = { Text(if (memo.favoritedAt == null) "收藏" else "取消收藏") },
                                    leadingIcon = {
                                        Icon(
                                            if (memo.favoritedAt == null) Icons.Rounded.StarBorder else Icons.Rounded.Star,
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.toggleSelectedMemoFavorited()
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
                MemoDetailCard(
                    memo = memo,
                    baseUrl = state.baseUrl,
                    openingAttachmentPath = state.openingAttachmentPath,
                    onOpenAttachment = viewModel::openProtectedAttachment,
                )
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
private fun MemoDetailCard(
    memo: Memo,
    baseUrl: String,
    openingAttachmentPath: String?,
    onOpenAttachment: (MarkdownLinkTarget.ProtectedAttachment) -> Unit,
) {
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
                MarkdownContent(
                    content = memo.content,
                    baseUrl = baseUrl,
                    openingAttachmentPath = openingAttachmentPath,
                    onOpenAttachment = onOpenAttachment,
                )
            }
        }
    }
}

@Composable
internal fun MemoStatusLine(memo: Memo?) {
    val flags = listOfNotNull(
        if (memo?.favoritedAt != null) "已收藏" else null,
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
internal fun MemoMetadataBlock(memo: Memo?) {
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
internal fun MemoSummarySection(
    summary: MemoAI?,
    loading: Boolean,
    actionEnabled: Boolean = true,
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
                TextButton(onClick = onGenerate, enabled = actionEnabled && !loading) {
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
    val sourceCount = memoSummarySourceCount(summary.sourceMemoIds)
    val model = listOf(summary.provider, summary.model)
        .filter { it.isNotBlank() }
        .joinToString(" / ")
    val technicalDetails = buildList {
        if (model.isNotBlank()) {
            add(model)
        }
        if (summary.totalTokens > 0) {
            add("${summary.totalTokens} tokens")
        }
    }.joinToString(" · ")
    if (sourceCount == null && technicalDetails.isBlank()) {
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (sourceCount != null) {
            Text(
                "基于 $sourceCount 条记录生成",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (technicalDetails.isNotBlank()) {
            Text(
                technicalDetails,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
