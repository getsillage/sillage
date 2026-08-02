package app.sillage.ui.memos

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import app.sillage.data.SessionStore
import app.sillage.R
import app.sillage.ui.SillageUiState
import app.sillage.ui.SillageViewModel
import app.sillage.ui.canRunMemoEditorAction
import app.sillage.ui.memoEditorActionContext
import app.sillage.ui.records.SillageRecordEditorActionIcons
import app.sillage.ui.records.SillageRecordEditorActionStrings
import app.sillage.ui.records.SillageRecordEditorActions
import app.sillage.ui.records.SillageRecordEditorContent
import app.sillage.ui.records.SillageRecordEditorContentIcons
import app.sillage.ui.records.SillageRecordEditorContentStrings
import app.sillage.ui.records.SillageRecordEditorDiscardStrings
import app.sillage.ui.records.SillageRecordEditorBackAction
import app.sillage.ui.records.SillageRecordEditorScreenChromeStrings
import app.sillage.ui.records.SillageRecordEditorTopBarTitle
import app.sillage.ui.records.rememberSillageRecordEditorCloseRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemoEditorScreen(state: SillageUiState, viewModel: SillageViewModel) {
    var showDatePicker by remember { mutableStateOf(false) }
    val editorActionContext = state.memoEditorActionContext()
    val editorActionsEnabled = state.canRunMemoEditorAction()
    val requestCloseEditor = rememberSillageRecordEditorCloseRequest(
        state = state.records,
        context = editorActionContext,
        strings = SillageRecordEditorDiscardStrings(
            title = stringResource(R.string.discard_changes_title),
            supporting = stringResource(R.string.discard_changes_supporting),
            discardAction = stringResource(R.string.discard_changes_action),
            continueEditingAction = stringResource(R.string.continue_editing),
        ),
        onClose = viewModel::closeEditor,
    )
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
    val screenChromeStrings = SillageRecordEditorScreenChromeStrings(
        newRecordTitle = stringResource(R.string.editor_new_title),
        editRecordTitle = stringResource(R.string.editor_edit_title),
        backContentDescription = stringResource(R.string.action_back),
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    SillageRecordEditorTopBarTitle(
                        state = state.records,
                        context = editorActionContext,
                        strings = screenChromeStrings,
                    )
                },
                navigationIcon = {
                    SillageRecordEditorBackAction(
                        state = state.records,
                        context = editorActionContext,
                        strings = screenChromeStrings,
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        onBack = requestCloseEditor,
                    )
                },
                actions = {
                    SillageRecordEditorActions(
                        state = state.records,
                        context = editorActionContext,
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
        SillageRecordEditorContent(
            state = state.records,
            context = editorActionContext,
            showAttachmentAction = state.appMode == SessionStore.MODE_ONLINE,
            strings = SillageRecordEditorContentStrings(
                entryDateLabel = stringResource(R.string.editor_date),
                entryDatePlaceholder = stringResource(R.string.editor_date_placeholder),
                pickDateContentDescription = stringResource(R.string.editor_pick_date),
                favoritedStatus = stringResource(R.string.record_favorited),
                archivedStatus = stringResource(R.string.record_archived),
                addAttachmentAction = stringResource(R.string.editor_add_attachment),
                uploadingAttachmentAction = stringResource(R.string.editor_uploading),
            ),
            icons = SillageRecordEditorContentIcons(
                pickDate = Icons.Rounded.CalendarMonth,
                addAttachment = Icons.Rounded.AttachFile,
            ),
            onEntryDateChange = viewModel::updateDraftEntryDate,
            onPickDate = { showDatePicker = true },
            onAddAttachment = { attachmentLauncher.launch("*/*") },
            editorContent = { editorModifier, editorHeight, actionsEnabled ->
                MarkdownEditorSection(
                    content = state.draftContent,
                    baseUrl = state.baseUrl,
                    openingAttachmentPath = state.openingAttachmentPath,
                    preview = state.markdownPreview,
                    enabled = actionsEnabled,
                    onContentChange = viewModel::updateDraftContent,
                    onPreviewChange = viewModel::updateMarkdownPreview,
                    onFormat = viewModel::appendMarkdownFormat,
                    onOpenAttachment = viewModel::openProtectedAttachment,
                    modifier = editorModifier.height(editorHeight),
                )
            },
            summaryContent = { summaryModifier, actionsEnabled ->
                MemoSummarySection(
                    summary = state.selectedSummary,
                    loading = state.summaryLoading,
                    actionEnabled = actionsEnabled,
                    onGenerate = viewModel::summarizeSelectedMemo,
                    modifier = summaryModifier,
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Scaffold already applies insets via padding; without
                // consuming them imePadding stacks a blank gap over the keyboard.
                .consumeWindowInsets(padding)
                .imePadding(),
        )
    }
}
