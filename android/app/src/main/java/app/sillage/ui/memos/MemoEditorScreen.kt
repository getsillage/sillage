package app.sillage.ui.memos

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.sillage.data.SessionStore
import app.sillage.ui.SillageUiState
import app.sillage.ui.SillageViewModel
import app.sillage.ui.canRunMemoEditorAction
import app.sillage.ui.common.MessageBlock
import app.sillage.ui.hasUnsavedMemoDraft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemoEditorScreen(state: SillageUiState, viewModel: SillageViewModel) {
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val editorActionsEnabled = state.canRunMemoEditorAction()
    val requestCloseEditor: () -> Unit = {
        if (state.hasUnsavedMemoDraft()) {
            confirmDiscard = true
        } else {
            viewModel.closeEditor()
        }
    }
    BackHandler {
        if (editorActionsEnabled) {
            requestCloseEditor()
        }
    }
    val attachmentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        viewModel.uploadAttachments(uris)
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("放弃未保存的修改？") },
            text = { Text("返回后，本次修改不会保存。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        viewModel.closeEditor()
                    },
                    enabled = editorActionsEnabled,
                ) {
                    Text("放弃修改", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text("继续编辑")
                }
            },
        )
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
                        "删除后会从当前列表移除，并同步到服务器。"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteSelectedMemo()
                    },
                    enabled = editorActionsEnabled,
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
                    IconButton(
                        onClick = requestCloseEditor,
                        enabled = editorActionsEnabled,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    val selected = state.selectedMemo
                    IconButton(onClick = viewModel::saveMemo, enabled = editorActionsEnabled) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = when {
                                state.uploadingAttachment -> "附件上传中"
                                state.loading -> "保存中"
                                else -> "保存"
                            },
                        )
                    }
                    if (selected != null) {
                        Box {
                            IconButton(onClick = { menuExpanded = true }, enabled = editorActionsEnabled) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "更多操作")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (selected.favoritedAt == null) "收藏" else "取消收藏") },
                                    leadingIcon = {
                                        Icon(
                                            if (selected.favoritedAt == null) Icons.Rounded.StarBorder else Icons.Rounded.Star,
                                            contentDescription = null,
                                        )
                                    },
                                    enabled = editorActionsEnabled,
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.toggleSelectedMemoFavorited()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (selected.archivedAt == null) "归档" else "取消归档") },
                                    leadingIcon = { Icon(Icons.Rounded.Archive, contentDescription = null) },
                                    enabled = editorActionsEnabled,
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.toggleSelectedMemoArchived()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("删除") },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                                    enabled = editorActionsEnabled,
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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            val editorHeight = (maxHeight * 0.6f).coerceIn(320.dp, 560.dp)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    }
                }
                item {
                    MarkdownEditorSection(
                        content = state.draftContent,
                        baseUrl = state.baseUrl,
                        openingAttachmentPath = state.openingAttachmentPath,
                        preview = state.markdownPreview,
                        onContentChange = viewModel::updateDraftContent,
                        onPreviewChange = viewModel::updateMarkdownPreview,
                        onFormat = viewModel::appendMarkdownFormat,
                        onOpenAttachment = viewModel::openProtectedAttachment,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(editorHeight),
                    )
                }
                if (state.appMode == SessionStore.MODE_ONLINE) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = { attachmentLauncher.launch("*/*") },
                                enabled = editorActionsEnabled,
                            ) {
                                Icon(Icons.Rounded.AttachFile, contentDescription = null)
                                Text(if (state.uploadingAttachment) "上传中" else "附件")
                            }
                        }
                    }
                }
                if (state.selectedMemo != null) {
                    item {
                        MemoSummarySection(
                            summary = state.selectedSummary,
                            loading = state.summaryLoading,
                            actionEnabled = editorActionsEnabled,
                            onGenerate = viewModel::summarizeSelectedMemo,
                        )
                    }
                }
            }
        }
    }
}
