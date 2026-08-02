package app.sillage.ui.memos

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import app.sillage.data.SessionStore
import app.sillage.R
import app.sillage.ui.SillageUiState
import app.sillage.ui.SillageViewModel
import app.sillage.ui.designsystem.applySillageHeadingSemantics
import app.sillage.ui.canRunMemoEditorAction
import app.sillage.ui.hasUnsavedMemoDraft
import app.sillage.ui.isMemoMutationInProgress
import app.sillage.ui.records.SillageRecordEditorActionIcons
import app.sillage.ui.records.SillageRecordEditorActionStrings
import app.sillage.ui.records.SillageRecordEditorActions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemoEditorScreen(state: SillageUiState, viewModel: SillageViewModel) {
    var confirmDiscard by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val memoMutationInProgress = state.selectedMemo?.id?.let(state::isMemoMutationInProgress) == true
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
        } else {
            viewModel.notifyMemoEditorBackBlocked()
        }
    }
    val attachmentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        viewModel.uploadAttachments(uris)
    }
    if (showDatePicker) {
        val initialMillis = runCatching { LocalDate.parse(state.draftEntryDate.trim()) }
            .getOrElse { LocalDate.now() }
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            viewModel.updateDraftEntryDate(
                                Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString(),
                            )
                        }
                        showDatePicker = false
                    },
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.discard_changes_title)) },
            text = { Text(stringResource(R.string.discard_changes_supporting)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        viewModel.closeEditor()
                    },
                    enabled = editorActionsEnabled,
                ) {
                    Text(stringResource(R.string.discard_changes_action), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text(stringResource(R.string.continue_editing))
                }
            },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (state.selectedMemo == null) R.string.editor_new_title else R.string.editor_edit_title),
                    modifier = Modifier.semantics { applySillageHeadingSemantics() },
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = requestCloseEditor,
                        enabled = editorActionsEnabled,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    SillageRecordEditorActions(
                        memo = state.selectedMemo,
                        actionsEnabled = editorActionsEnabled,
                        saving = state.loading || memoMutationInProgress,
                        uploadingAttachment = state.uploadingAttachment,
                        deleteDismissEnabled = !state.loading,
                        strings = SillageRecordEditorActionStrings(
                            saveContentDescription = stringResource(R.string.action_save),
                            savingContentDescription = stringResource(R.string.action_saving),
                            attachmentUploadingContentDescription = stringResource(
                                R.string.editor_attachment_uploading,
                            ),
                            moreContentDescription = stringResource(R.string.action_more),
                            favoriteAction = stringResource(R.string.action_favorite),
                            unfavoriteAction = stringResource(R.string.action_unfavorite),
                            archiveAction = stringResource(R.string.action_archive),
                            unarchiveAction = stringResource(R.string.action_unarchive),
                            deleteAction = stringResource(R.string.action_delete),
                            deleteTitle = stringResource(R.string.delete_record_title),
                            deleteSupporting = if (state.appMode == SessionStore.MODE_OFFLINE) {
                                stringResource(R.string.delete_record_offline_supporting)
                            } else {
                                stringResource(R.string.delete_record_online_supporting)
                            },
                            confirmDeleteAction = stringResource(R.string.action_confirm_delete),
                            cancelAction = stringResource(R.string.action_cancel),
                        ),
                        icons = SillageRecordEditorActionIcons(
                            save = Icons.Rounded.Check,
                            more = Icons.Rounded.MoreVert,
                            favorite = Icons.Rounded.StarBorder,
                            unfavorite = Icons.Rounded.Star,
                            archive = Icons.Rounded.Archive,
                            delete = Icons.Rounded.Delete,
                        ),
                        onSave = viewModel::saveMemo,
                        onToggleFavorite = viewModel::toggleSelectedMemoFavorited,
                        onToggleArchive = viewModel::toggleSelectedMemoArchived,
                        onDelete = viewModel::deleteSelectedMemo,
                    )
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Scaffold already applies these insets via padding; without
                // consuming them imePadding stacks a blank gap over the keyboard.
                .consumeWindowInsets(padding)
                .imePadding(),
        ) {
            val editorHeight = (maxHeight * 0.6f).coerceIn(320.dp, 560.dp)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 760.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MemoStatusLine(state.selectedMemo)
                        OutlinedTextField(
                            value = state.draftEntryDate,
                            onValueChange = viewModel::updateDraftEntryDate,
                            enabled = editorActionsEnabled,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(stringResource(R.string.editor_date)) },
                            placeholder = { Text(stringResource(R.string.editor_date_placeholder)) },
                            trailingIcon = {
                                IconButton(
                                    onClick = { showDatePicker = true },
                                    enabled = editorActionsEnabled,
                                ) {
                                    Icon(
                                        Icons.Rounded.CalendarMonth,
                                        contentDescription = stringResource(R.string.editor_pick_date),
                                    )
                                }
                            },
                        )
                    }
                }
                item {
                    MarkdownEditorSection(
                        content = state.draftContent,
                        baseUrl = state.baseUrl,
                        openingAttachmentPath = state.openingAttachmentPath,
                        preview = state.markdownPreview,
                        enabled = editorActionsEnabled,
                        onContentChange = viewModel::updateDraftContent,
                        onPreviewChange = viewModel::updateMarkdownPreview,
                        onFormat = viewModel::appendMarkdownFormat,
                        onOpenAttachment = viewModel::openProtectedAttachment,
                        modifier = Modifier
                            .widthIn(max = 760.dp)
                            .fillMaxWidth()
                            .height(editorHeight),
                    )
                }
                if (state.appMode == SessionStore.MODE_ONLINE) {
                    item {
                        Row(
                            modifier = Modifier
                                .widthIn(max = 760.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(
                                onClick = { attachmentLauncher.launch("*/*") },
                                enabled = editorActionsEnabled,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .widthIn(min = 112.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.AttachFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(if (state.uploadingAttachment) R.string.editor_uploading else R.string.editor_add_attachment))
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
                            modifier = Modifier
                                .widthIn(max = 760.dp)
                                .fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
