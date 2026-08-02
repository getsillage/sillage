package app.sillage.ui.memos

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.sillage.core.domain.records.Memo
import app.sillage.core.domain.records.MemoAI
import app.sillage.data.memoSummarySourceCount
import app.sillage.R
import app.sillage.ui.SillageUiState
import app.sillage.ui.SillageViewModel
import app.sillage.ui.designsystem.applySillageHeadingSemantics
import app.sillage.ui.localizedDate
import app.sillage.ui.localizedTimestamp
import app.sillage.ui.records.SillageRecordDetailCard
import app.sillage.ui.records.SillageRecordDetailContent
import app.sillage.ui.records.SillageRecordDetailStrings
import app.sillage.ui.records.SillageRecordMetadataBlock
import app.sillage.ui.records.SillageRecordStatusLine
import app.sillage.ui.records.SillageRecordSummarySection
import app.sillage.ui.records.SillageRecordSummaryStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemoDetailScreen(state: SillageUiState, viewModel: SillageViewModel) {
    val memo = state.selectedMemo
    val memoMutating = memo?.id?.let(state.memoMutationIds::contains) == true
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(memoMutating) {
        if (memoMutating) {
            menuExpanded = false
            confirmDelete = false
        }
    }
    BackHandler(onBack = viewModel::closeMemoDetail)
    if (confirmDelete && memo != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_record_title)) },
            text = { Text(stringResource(R.string.delete_record_supporting)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteSelectedMemo()
                    },
                    enabled = !state.loading && !memoMutating,
                ) {
                    Text(stringResource(R.string.action_confirm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }, enabled = !state.loading) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.record_detail_title),
                modifier = Modifier.semantics { applySillageHeadingSemantics() },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = viewModel::closeMemoDetail) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::editSelectedMemo,
                        enabled = memo != null && !state.loading && !memoMutating,
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.record_edit_description))
                    }
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            enabled = memo != null && !state.loading && !memoMutating,
                        ) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_more))
                        }
                        DropdownMenu(
                            expanded = menuExpanded && !memoMutating,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            if (memo != null) {
                                DropdownMenuItem(
                                    text = {
                                        Text(stringResource(if (memo.favoritedAt == null) R.string.action_favorite else R.string.action_unfavorite))
                                    },
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
                                    enabled = !state.loading && !memoMutating,
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(stringResource(if (memo.archivedAt == null) R.string.action_archive else R.string.action_unarchive))
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.Archive, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.toggleSelectedMemoArchived()
                                    },
                                    enabled = !state.loading && !memoMutating,
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_delete)) },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        confirmDelete = true
                                    },
                                    enabled = !state.loading && !memoMutating,
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        SillageRecordDetailContent(
            memo = memo,
            missingRecord = stringResource(R.string.record_missing),
            recordContent = { record, itemModifier ->
                SillageRecordDetailCard(
                    memo = record,
                    strings = SillageRecordDetailStrings(
                        entryDateLabel = localizedDate(record.entryDate),
                        blankRecord = stringResource(R.string.blank_record),
                        favoritedStatus = stringResource(R.string.record_favorited),
                        archivedStatus = stringResource(R.string.record_archived),
                    ),
                    modifier = itemModifier,
                ) { contentMemo ->
                    MarkdownContent(
                        content = contentMemo.content,
                        baseUrl = state.baseUrl,
                        openingAttachmentPath = state.openingAttachmentPath,
                        onOpenAttachment = viewModel::openProtectedAttachment,
                    )
                }
            },
            summaryContent = { _, itemModifier ->
                MemoSummarySection(
                    summary = state.selectedSummary,
                    loading = state.summaryLoading,
                    onGenerate = viewModel::summarizeSelectedMemo,
                    modifier = itemModifier,
                )
            },
            metadataContent = { record, itemModifier ->
                val updatedTimestamp = localizedTimestamp(record.updatedAt)
                SillageRecordMetadataBlock(
                    memo = record,
                    createdLabel = stringResource(
                        R.string.metadata_created,
                        localizedTimestamp(record.createdAt),
                    ),
                    updatedLabel = { revisions ->
                        stringResource(
                            R.string.metadata_updated,
                            updatedTimestamp,
                            pluralStringResource(
                                R.plurals.quantity_revisions,
                                revisions,
                                revisions,
                            ),
                        )
                    },
                    modifier = itemModifier,
                )
            },
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
internal fun MemoStatusLine(memo: Memo?) {
    SillageRecordStatusLine(
        memo = memo,
        favoritedStatus = stringResource(R.string.record_favorited),
        archivedStatus = stringResource(R.string.record_archived),
    )
}

@Composable
internal fun MemoSummarySection(
    summary: MemoAI?,
    loading: Boolean,
    modifier: Modifier = Modifier,
    actionEnabled: Boolean = true,
    onGenerate: () -> Unit,
) {
    SillageRecordSummarySection(
        summary = summary,
        loading = loading,
        strings = SillageRecordSummaryStrings(
            title = stringResource(R.string.summary_title),
            readingAction = stringResource(R.string.summary_reading),
            generatingAction = stringResource(R.string.summary_generating),
            generateAction = stringResource(R.string.summary_generate),
            regenerateAction = stringResource(R.string.summary_regenerate),
            loadingBody = stringResource(R.string.summary_loading_body),
            emptyBody = stringResource(R.string.summary_empty_body),
        ),
        sourceRecordsLabel = memoSummarySourceLabel(summary),
        tokenCountLabel = memoSummaryTokenLabel(summary),
        actionEnabled = actionEnabled,
        onGenerate = onGenerate,
        modifier = modifier,
    )
}

@Composable
private fun memoSummarySourceLabel(summary: MemoAI?): String? {
    val sourceCount = summary?.let { memoSummarySourceCount(it.sourceMemoIds) } ?: return null
    return pluralStringResource(R.plurals.quantity_source_records, sourceCount, sourceCount)
}

@Composable
private fun memoSummaryTokenLabel(summary: MemoAI?): String? {
    val tokenCount = summary?.totalTokens?.takeIf { it > 0 } ?: return null
    return pluralStringResource(R.plurals.quantity_tokens, tokenCount.toInt(), tokenCount)
}
