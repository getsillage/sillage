package app.sillage.ui.application

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.sillage.core.domain.records.Memo
import app.sillage.features.records.MemoListFilter
import app.sillage.features.records.RecordsEditorActionContext
import app.sillage.ui.appshell.AppDestination
import app.sillage.ui.records.SillageRecentlyDeletedRecordRow
import app.sillage.ui.records.SillageRecentlyDeletedRecordStrings
import app.sillage.ui.records.SillageRecordDetailActionIcons
import app.sillage.ui.records.SillageRecordDetailActions
import app.sillage.ui.records.SillageRecordDetailCard
import app.sillage.ui.records.SillageRecordEditorActionIcons
import app.sillage.ui.records.SillageRecordEditorActionStrings
import app.sillage.ui.records.SillageRecordEditorActions
import app.sillage.ui.records.SillageRecordEmptyState
import app.sillage.ui.records.SillageRecordFilterTabs
import app.sillage.ui.records.SillageRecordRow
import app.sillage.ui.records.SillageRecordSearchBar
import kotlinx.coroutines.launch

private val RecordsListPaneWidth = 390.dp

@Composable
internal fun SillageRecordsWorkspace(
    controller: SillageNativeController,
    strings: SillageNativeStrings,
    wide: Boolean,
    onGuardedCloseEditor: () -> Unit,
    onGuardedAction: (() -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (wide) {
        Row(modifier = modifier.fillMaxSize()) {
            SillageRecordsListPane(
                controller = controller,
                strings = strings,
                onGuardedAction = onGuardedAction,
                modifier = Modifier
                    .width(RecordsListPaneWidth)
                    .fillMaxHeight(),
            )
            VerticalDivider()
            SillageRecordSecondaryPane(
                controller = controller,
                strings = strings,
                onGuardedAction = onGuardedAction,
                onGuardedCloseEditor = onGuardedCloseEditor,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    } else {
        when (controller.state.clientContext.screen) {
            AppDestination.Editor -> SillageRecordEditorPane(
                controller = controller,
                strings = strings,
                onClose = onGuardedCloseEditor,
                modifier = modifier,
            )
            AppDestination.MemoDetail -> SillageRecordDetailPane(
                controller = controller,
                strings = strings,
                showBack = true,
                modifier = modifier,
            )
            else -> SillageRecordsListPane(
                controller = controller,
                strings = strings,
                onGuardedAction = onGuardedAction,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun SillageRecordSecondaryPane(
    controller: SillageNativeController,
    strings: SillageNativeStrings,
    onGuardedAction: (() -> Unit) -> Unit,
    onGuardedCloseEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (controller.state.clientContext.screen) {
        AppDestination.Editor -> SillageRecordEditorPane(
            controller = controller,
            strings = strings,
            onClose = onGuardedCloseEditor,
            modifier = modifier,
        )
        AppDestination.MemoDetail -> SillageRecordDetailPane(
            controller = controller,
            strings = strings,
            showBack = false,
            modifier = modifier,
        )
        else -> SillageRecordEmptyState(
            text = strings.noSelection,
            icon = Icons.Outlined.Description,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SillageRecordsListPane(
    controller: SillageNativeController,
    strings: SillageNativeStrings,
    onGuardedAction: (() -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = controller.state
    val records = state.workspace.records
    val scope = rememberCoroutineScope()
    val visible = if (records.search.query.isBlank()) {
        records.records
    } else {
        records.search.currentResults().orEmpty()
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(strings.records)
                    Text(
                        strings.localMode,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            },
            actions = {
                FilledIconButton(
                    onClick = { onGuardedAction(controller::startNewRecord) },
                    enabled = state.storageAvailable && !state.busy,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = strings.newRecord)
                }
            },
        )
        SillageRecordFilterTabs(
            state = records,
            strings = strings.recordFilter,
            onSelect = { filter ->
                onGuardedAction { controller.selectFilter(filter) }
            },
        )
        SillageRecordSearchBar(
            state = records,
            strings = strings.recordSearch,
            searchIcon = Icons.Outlined.Search,
            clearIcon = Icons.Outlined.Close,
            onQueryChange = controller::updateSearchQuery,
            onClear = { controller.updateSearchQuery("") },
            onSearch = controller::searchRecords,
            modifier = Modifier.fillMaxWidth(),
        )
        HorizontalDivider()

        if (visible.isEmpty()) {
            val emptyMessage = if (records.search.query.isNotBlank() && !records.search.searching) {
                strings.emptyByFilter.getValue(records.filter)
            } else {
                strings.emptyByFilter.getValue(records.filter)
            }
            SillageRecordEmptyState(
                text = if (records.search.query.isBlank()) {
                    emptyMessage
                } else {
                    strings.noSearchResults
                },
                icon = if (records.search.query.isBlank()) {
                    Icons.Outlined.Description
                } else {
                    Icons.Outlined.Search
                },
                actionLabel = strings.newRecord.takeIf {
                    records.filter == MemoListFilter.Unarchived && records.search.query.isBlank()
                },
                onAction = ({ onGuardedAction(controller::startNewRecord) }).takeIf {
                    records.filter == MemoListFilter.Unarchived &&
                        records.search.query.isBlank() &&
                        state.storageAvailable
                },
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visible, key = Memo::id) { memo ->
                    if (records.filter == MemoListFilter.Deleted) {
                        SillageRecentlyDeletedRecordRow(
                            state = records,
                            memo = memo,
                            strings = SillageRecentlyDeletedRecordStrings(
                                blankRecord = strings.recordRow.blankRecord,
                                deletedAtLabel = memo.deletedAt.orEmpty(),
                                purgeSupporting = strings.purgeSupporting,
                                restoreAction = strings.restore,
                                deleteForeverAction = strings.purge,
                                confirmDeleteAction = strings.confirm,
                                cancelAction = strings.cancel,
                            ),
                            restoreIcon = Icons.Outlined.RestoreFromTrash,
                            purgeIcon = Icons.Outlined.DeleteForever,
                            onRestore = { scope.launch { controller.restoreRecord(memo) } },
                            onPurge = { scope.launch { controller.purgeRecord(memo) } },
                        )
                    } else {
                        SillageRecordRow(
                            memo = memo,
                            mutating = state.busy,
                            strings = strings.recordRow,
                            onClick = { onGuardedAction { controller.openRecord(memo) } },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SillageRecordDetailPane(
    controller: SillageNativeController,
    strings: SillageNativeStrings,
    showBack: Boolean,
    modifier: Modifier = Modifier,
) {
    val state = controller.state
    val memo = state.workspace.records.selection.selectedMemo
    val scope = rememberCoroutineScope()

    if (memo == null) {
        SillageRecordEmptyState(
            text = strings.noSelection,
            icon = Icons.Outlined.Description,
            modifier = modifier,
        )
        return
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(strings.recordDetails) },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = controller::navigateToRecords) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = strings.back)
                        }
                    }
                },
                actions = {
                    SillageRecordDetailActions(
                        state = state.workspace.records,
                        hostOperationBlocked = state.busy || !state.storageAvailable,
                        strings = strings.recordActions,
                        icons = SillageRecordDetailActionIcons(
                            edit = Icons.Outlined.Edit,
                            more = Icons.Outlined.MoreVert,
                            favorite = Icons.Outlined.StarBorder,
                            unfavorite = Icons.Outlined.Star,
                            archive = Icons.Outlined.Archive,
                            delete = Icons.Outlined.Delete,
                        ),
                        onEdit = controller::editSelectedRecord,
                        onToggleFavorite = {
                            scope.launch { controller.toggleSelectedFavorite() }
                        },
                        onToggleArchive = {
                            scope.launch { controller.toggleSelectedArchive() }
                        },
                        onDelete = { scope.launch { controller.deleteSelectedRecord() } },
                    )
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 800.dp)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(20.dp),
            ) {
                item {
                    SillageRecordDetailCard(
                        memo = memo,
                        strings = strings.recordDetail,
                    ) { record ->
                        Text(
                            record.content,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SillageRecordEditorPane(
    controller: SillageNativeController,
    strings: SillageNativeStrings,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = controller.state
    val records = state.workspace.records
    val scope = rememberCoroutineScope()
    val editingExisting = records.selection.selectedMemo != null
    val context = RecordsEditorActionContext(
        destinationAvailable = state.clientContext.screen == AppDestination.Editor,
        hostOperationInProgress = state.busy || !state.storageAvailable,
    )

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(if (editingExisting) strings.editorTitleEdit else strings.editorTitleNew)
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            if (editingExisting) {
                                Icons.AutoMirrored.Outlined.ArrowBack
                            } else {
                                Icons.Outlined.Close
                            },
                            contentDescription = if (editingExisting) strings.back else strings.close,
                        )
                    }
                },
                actions = {
                    SillageRecordEditorActions(
                        state = records,
                        context = context,
                        strings = SillageRecordEditorActionStrings(
                            saveContentDescription = strings.saveRecord,
                            savingContentDescription = strings.savingRecord,
                            attachmentUploadingContentDescription = strings.savingRecord,
                            moreContentDescription = strings.recordActions.moreContentDescription,
                            favoriteAction = strings.recordActions.favoriteAction,
                            unfavoriteAction = strings.recordActions.unfavoriteAction,
                            archiveAction = strings.recordActions.archiveAction,
                            unarchiveAction = strings.recordActions.unarchiveAction,
                            deleteAction = strings.recordActions.deleteAction,
                            deleteTitle = strings.recordActions.deleteTitle,
                            deleteSupporting = strings.recordActions.deleteSupporting,
                            confirmDeleteAction = strings.recordActions.confirmDeleteAction,
                            cancelAction = strings.recordActions.cancelAction,
                        ),
                        icons = SillageRecordEditorActionIcons(
                            save = Icons.Outlined.Save,
                            more = Icons.Outlined.MoreVert,
                            favorite = Icons.Outlined.StarBorder,
                            unfavorite = Icons.Outlined.Star,
                            archive = Icons.Outlined.Archive,
                            delete = Icons.Outlined.Delete,
                        ),
                        onSave = { scope.launch { controller.saveEditor() } },
                        onToggleFavorite = {
                            scope.launch { controller.toggleSelectedFavorite() }
                        },
                        onToggleArchive = {
                            scope.launch { controller.toggleSelectedArchive() }
                        },
                        onDelete = { scope.launch { controller.deleteSelectedRecord() } },
                    )
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 800.dp)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = records.editor.draftEntryDate,
                        onValueChange = controller::updateEditorEntryDate,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.busy && state.storageAvailable,
                        singleLine = true,
                        isError = state.editorValidationError != null,
                        label = { Text(strings.entryDate) },
                        placeholder = { Text(strings.entryDatePlaceholder) },
                        supportingText = state.editorValidationError?.let {
                            { Text(strings.invalidEntryDate) }
                        },
                    )
                }
                item {
                    OutlinedTextField(
                        value = records.editor.draftContent,
                        onValueChange = controller::updateEditorContent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 320.dp),
                        enabled = !state.busy && state.storageAvailable,
                        label = { Text(strings.content) },
                        placeholder = { Text(strings.contentPlaceholder) },
                        minLines = 12,
                    )
                }
            }
        }
    }
}
