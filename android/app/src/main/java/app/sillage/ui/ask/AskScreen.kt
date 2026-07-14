package app.sillage.ui.ask

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sillage.data.AskConversation
import app.sillage.data.AskMessage
import app.sillage.data.AskPathEntry
import app.sillage.data.AskSourceRef
import app.sillage.data.buildAskActivePath
import app.sillage.data.lastAssistantMessageId
import app.sillage.R
import app.sillage.ui.SillageUiState
import app.sillage.ui.SillageViewModel
import app.sillage.ui.applyHeadingSemantics
import app.sillage.ui.localizedDate
import app.sillage.ui.navigation.MainNavigationBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskScreen(state: SillageUiState, viewModel: SillageViewModel) {
    var showConversations by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    val view = LocalView.current
    val completedDescription = stringResource(R.string.ask_answer_complete)
    var observedCompletionEventId by remember(state.askScreenSessionId) {
        mutableLongStateOf(state.askCompletionEventId)
    }
    val listState = rememberLazyListState()
    val isUserDragging by listState.interactionSource.collectIsDraggedAsState()
    val autoFollowThresholdPx = with(LocalDensity.current) { 96.dp.roundToPx() }
    var autoFollow by remember(state.activeAskId) { mutableStateOf(true) }
    val entries = remember(state.askMessages, state.askHeadId) {
        buildAskActivePath(state.askMessages, state.askHeadId)
    }
    val latestAssistantId = remember(entries) {
        lastAssistantMessageId(entries)
    }
    val contextControlsEnabled = !state.askLoading &&
        !state.askSending &&
        !state.askVariantLoading &&
        !state.askSourceLoading
    val listItemCount = entries.size +
        (if (entries.isEmpty()) 1 else 0) +
        (if (state.askLiveUser != null) 1 else 0) +
        (if (state.askSending && state.askRegeneratingId.isBlank()) 1 else 0)
    LaunchedEffect(isUserDragging) {
        if (isUserDragging) {
            autoFollow = false
        }
    }
    LaunchedEffect(listState, isUserDragging, autoFollow, autoFollowThresholdPx) {
        if (!isUserDragging && !autoFollow) {
            snapshotFlow { listState.isNearAskBottom(autoFollowThresholdPx) }
                .collect { nearBottom ->
                    if (nearBottom) {
                        autoFollow = true
                    }
                }
        }
    }
    LaunchedEffect(state.askSending) {
        if (state.askSending && listItemCount > 0) {
            autoFollow = true
            withFrameNanos { }
            listState.scrollToAskBottom()
        }
    }
    LaunchedEffect(
        state.askScreenSessionId,
        state.askCompletionEventId,
        completedDescription,
        view,
    ) {
        if (observedCompletionEventId != state.askCompletionEventId) {
            observedCompletionEventId = state.askCompletionEventId
            view.announceForAccessibility(completedDescription)
        }
    }
    LaunchedEffect(
        listItemCount,
        entries.lastOrNull()?.message?.id,
        state.askLiveAnswer.length,
        autoFollow,
        isUserDragging,
    ) {
        if (!state.askLoading && listItemCount > 0 && autoFollow && !isUserDragging) {
            withFrameNanos { }
            listState.scrollToAskBottom()
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
            enabled = contextControlsEnabled,
            onDismiss = { showOptions = false },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.ask_title),
                                modifier = Modifier.semantics { applyHeadingSemantics() },
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                askContextLabel(state),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                            if (state.askSavingMessageId.isNotBlank()) {
                                val savingDescription = stringResource(R.string.action_saving)
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .semantics { contentDescription = savingDescription },
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showConversations = true },
                        enabled = contextControlsEnabled,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.List, contentDescription = stringResource(R.string.ask_conversations_description))
                    }
                    IconButton(
                        onClick = { showOptions = true },
                        enabled = contextControlsEnabled,
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = stringResource(R.string.ask_context_description))
                    }
                    IconButton(
                        onClick = viewModel::startNewAsk,
                        enabled = contextControlsEnabled,
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.ask_new_conversation_description))
                    }
                },
            )
        },
        bottomBar = {
            MainNavigationBar(state = state, viewModel = viewModel)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
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
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.askLoadError?.let { message ->
                        item(key = "ask-load-error") {
                            AskLoadErrorCard(
                                message = message,
                                onRetry = viewModel::retryAskLoad,
                            )
                        }
                    }
                    if (entries.isEmpty() && state.askLoadError == null) {
                        item {
                            AskEmptyPrompt()
                        }
                    }
                    items(entries, key = { it.message.id }) { entry ->
                        AskMessageCard(
                            entry = entry,
                            canRegenerate = entry.message.id == latestAssistantId &&
                                !state.askLoading &&
                                !state.askSending &&
                                !state.askVariantLoading &&
                                !state.askSourceLoading,
                            regenerating = state.askRegeneratingId == entry.message.id,
                            variantChanging = state.askLoading || state.askVariantLoading,
                            savingDisabled = state.loading ||
                                state.askLoading ||
                                state.askSending ||
                                state.askVariantLoading ||
                                state.askSourceLoading ||
                                state.askSavingMessageId.isNotBlank(),
                            saving = state.askSavingMessageId == entry.message.id,
                            sourceActionsEnabled = !state.loading &&
                                !state.askSending &&
                                !state.askLoading &&
                                !state.askVariantLoading &&
                                !state.askSourceLoading,
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
            )
        }
    }
}

private fun LazyListState.isNearAskBottom(thresholdPx: Int): Boolean {
    val layout = layoutInfo
    val lastVisibleItem = layout.visibleItemsInfo.lastOrNull()
    return isAskListNearBottom(
        lastVisibleIndex = lastVisibleItem?.index,
        totalItemsCount = layout.totalItemsCount,
        lastVisibleEnd = lastVisibleItem?.let { it.offset + it.size },
        viewportEnd = layout.viewportEndOffset,
        thresholdPx = thresholdPx,
    )
}

private suspend fun LazyListState.scrollToAskBottom() {
    val totalItemsCount = layoutInfo.totalItemsCount
    if (totalItemsCount <= 0) {
        return
    }
    val lastIndex = totalItemsCount - 1
    if (layoutInfo.visibleItemsInfo.none { it.index == lastIndex }) {
        scrollToItem(lastIndex)
    }
    val layout = layoutInfo
    val lastItem = layout.visibleItemsInfo.lastOrNull { it.index == lastIndex } ?: return
    val remainingDistance = lastItem.offset + lastItem.size - layout.viewportEndOffset
    if (remainingDistance > 0) {
        scrollBy(remainingDistance.toFloat())
    }
}

internal fun isAskListNearBottom(
    lastVisibleIndex: Int?,
    totalItemsCount: Int,
    lastVisibleEnd: Int?,
    viewportEnd: Int,
    thresholdPx: Int,
): Boolean {
    if (totalItemsCount <= 0 || lastVisibleIndex != totalItemsCount - 1 || lastVisibleEnd == null) {
        return false
    }
    return lastVisibleEnd - viewportEnd <= thresholdPx.coerceAtLeast(0)
}

@Composable
private fun askContextLabel(state: SillageUiState): String {
    val scope = when (state.askScope) {
        "recent_7_days" -> stringResource(R.string.ask_scope_7_days)
        "all" -> stringResource(R.string.ask_scope_all)
        else -> stringResource(R.string.ask_scope_30_days)
    }
    val source = stringResource(
        if (state.askSourceKind == "summaries") R.string.ask_source_summaries else R.string.ask_source_records,
    )
    return stringResource(R.string.ask_record_context_summary, scope, source)
}

@Composable
private fun AskLoadErrorCard(message: String, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = onRetry,
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

@Composable
private fun AskEmptyPrompt() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                    }
                }
                Text(
                    stringResource(R.string.ask_prompt_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                stringResource(R.string.ask_prompt_example),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun AskComposer(
    state: SillageUiState,
    viewModel: SillageViewModel,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 10.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    askContextLabel(state),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    pluralStringResource(
                        R.plurals.quantity_characters,
                        state.askQuestion.trim().length,
                        state.askQuestion.trim().length,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = state.askQuestion,
                    onValueChange = viewModel::updateAskQuestion,
                    modifier = Modifier.weight(1f),
                    minLines = 1,
                    maxLines = 3,
                    label = { Text(stringResource(R.string.ask_question_label)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (
                                !state.askLoading &&
                                !state.askSending &&
                                !state.askVariantLoading &&
                                !state.askSourceLoading &&
                                state.askQuestion.isNotBlank()
                            ) {
                                viewModel.sendAskQuestion()
                            }
                        },
                    ),
                )
                if (state.askSending) {
                    FilledIconButton(
                        onClick = viewModel::stopAskStreaming,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Rounded.StopCircle, contentDescription = stringResource(R.string.ask_stop_generation))
                    }
                } else {
                    FilledIconButton(
                        onClick = viewModel::sendAskQuestion,
                        enabled = !state.askSending &&
                            !state.askLoading &&
                            !state.askVariantLoading &&
                            !state.askSourceLoading &&
                            state.askQuestion.isNotBlank(),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = stringResource(R.string.ask_send))
                    }
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
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.ask_conversations_title),
                    modifier = Modifier
                        .weight(1f)
                        .semantics { applyHeadingSemantics() },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(
                    onClick = viewModel::loadAskConversations,
                    enabled = !state.askLoading &&
                        !state.askSending &&
                        !state.askVariantLoading &&
                        state.askSavingMessageId.isBlank(),
                ) {
                    Text(stringResource(R.string.action_refresh))
                }
            }
            AskConversationList(
                conversations = state.askConversations,
                activeId = state.activeAskId,
                enabled = !state.askLoading &&
                    !state.askSending &&
                    !state.askVariantLoading &&
                    !state.askSourceLoading,
                onSelect = {
                    viewModel.selectAskConversation(it)
                    onDismiss()
                },
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AskOptionsSheet(
    state: SillageUiState,
    viewModel: SillageViewModel,
    enabled: Boolean,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.ask_context_title),
                modifier = Modifier.semantics { applyHeadingSemantics() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            AskOptions(state, viewModel, enabled)
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun AskOptions(
    state: SillageUiState,
    viewModel: SillageViewModel,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.ask_time_range),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AskOptionButton(stringResource(R.string.ask_scope_7_days_short), state.askScope == "recent_7_days", enabled) {
                viewModel.updateAskScope("recent_7_days")
            }
            AskOptionButton(stringResource(R.string.ask_scope_30_days_short), state.askScope == "recent_30_days", enabled) {
                viewModel.updateAskScope("recent_30_days")
            }
            AskOptionButton(stringResource(R.string.ask_scope_all_short), state.askScope == "all", enabled) {
                viewModel.updateAskScope("all")
            }
        }
        Text(
            stringResource(R.string.ask_source_title),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AskOptionButton(stringResource(R.string.ask_source_records), state.askSourceKind == "records", enabled) {
                viewModel.updateAskSourceKind("records")
            }
            AskOptionButton(stringResource(R.string.ask_source_summaries), state.askSourceKind == "summaries", enabled) {
                viewModel.updateAskSourceKind("summaries")
            }
        }
    }
}

@Composable
private fun AskOptionButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
    )
}

@Composable
private fun AskConversationList(
    conversations: List<AskConversation>,
    activeId: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    if (conversations.isEmpty()) {
        Text(
            stringResource(R.string.ask_no_conversations),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(conversations, key = { it.id }) { conversation ->
            Card(
                onClick = { onSelect(conversation.id) },
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (conversation.id == activeId) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                ),
                border = BorderStroke(
                    1.dp,
                    if (conversation.id == activeId) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
            ) {
                Text(
                    if (conversation.id == activeId) {
                        stringResource(
                            R.string.ask_current_conversation,
                            conversation.title.ifBlank { stringResource(R.string.ask_untitled_conversation) },
                        )
                    } else {
                        conversation.title.ifBlank { stringResource(R.string.ask_untitled_conversation) }
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodySmall,
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
    variantChanging: Boolean,
    savingDisabled: Boolean,
    saving: Boolean,
    sourceActionsEnabled: Boolean,
    streamingText: String?,
    onRegenerate: () -> Unit,
    onSaveAsMemo: () -> Unit,
    onOpenSource: (String) -> Unit,
    onSelectVariant: (String) -> Unit,
) {
    val message = entry.message
    val isAssistant = message.role == "assistant"
    val displayedContent = when {
        streamingText != null && streamingText.isNotBlank() -> streamingText
        regenerating -> stringResource(R.string.ask_regenerating)
        else -> message.content
    }
    val messageDescription = askMessageDescription(isAssistant, displayedContent)
    val bubbleColor = if (isAssistant) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val textColor = if (isAssistant) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isAssistant) Alignment.Start else Alignment.End,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(if (isAssistant) 0.94f else 0.84f),
            shape = RoundedCornerShape(
                topStart = 8.dp,
                topEnd = 8.dp,
                bottomEnd = if (isAssistant) 8.dp else 2.dp,
                bottomStart = if (isAssistant) 2.dp else 8.dp,
            ),
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            border = BorderStroke(
                1.dp,
                if (isAssistant) {
                    MaterialTheme.colorScheme.outlineVariant
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                },
            ),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    displayedContent,
                    modifier = Modifier.clearAndSetSemantics {
                        applyAskMessageSemantics(messageDescription)
                    },
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (isAssistant && message.sourceRefs.isNotEmpty()) {
                    AskSourceRefs(
                        sources = message.sourceRefs,
                        enabled = sourceActionsEnabled,
                        onOpenSource = onOpenSource,
                    )
                }
                if (isAssistant) {
                    AskMessageActions(
                        entry = entry,
                        canRegenerate = canRegenerate,
                        regenerating = regenerating,
                        variantChanging = variantChanging,
                        savingDisabled = savingDisabled,
                        saving = saving,
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
    enabled: Boolean,
    onOpenSource: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.height(48.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        ) {
            Text(
                pluralStringResource(R.plurals.quantity_sources, sources.size, sources.size),
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = stringResource(if (expanded) R.string.ask_hide_sources else R.string.ask_show_sources),
                modifier = Modifier.size(16.dp),
            )
        }
        if (expanded) {
            sources.take(5).forEach { source ->
                TextButton(
                    onClick = { onOpenSource(source.memoId) },
                    enabled = enabled && source.memoId.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                ) {
                    Text(
                        stringResource(R.string.quantity_joiner, localizedDate(source.entryDate), source.excerpt),
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
    val messageDescription = askMessageDescription(isAssistant = false, content = message.content)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.84f),
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)),
        ) {
            Text(
                message.content,
                modifier = Modifier
                    .clearAndSetSemantics { applyAskMessageSemantics(messageDescription) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun AskLiveAnswerCard(answer: String) {
    val displayedContent = answer.ifBlank { stringResource(R.string.ask_thinking) }
    val messageDescription = askMessageDescription(isAssistant = true, content = displayedContent)
    Card(
        modifier = Modifier.fillMaxWidth(0.94f),
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 2.dp, bottomEnd = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                displayedContent,
                modifier = Modifier.clearAndSetSemantics {
                    applyAskMessageSemantics(messageDescription)
                },
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
    variantChanging: Boolean,
    savingDisabled: Boolean,
    saving: Boolean,
    onRegenerate: () -> Unit,
    onSaveAsMemo: () -> Unit,
    onSelectVariant: (String) -> Unit,
) {
    val hasVariants = entry.variants.size > 1
    val canSave = entry.message.content.isNotBlank()
    if (!hasVariants && !canRegenerate && !regenerating && !canSave) {
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (hasVariants) {
            val variantPosition = stringResource(
                R.string.ask_variant_position,
                entry.index + 1,
                entry.variants.size,
            )
            IconButton(
                onClick = {
                    val previous = entry.variants.getOrNull(entry.index - 1)
                    if (previous != null) {
                        onSelectVariant(previous.id)
                    }
                },
                enabled = entry.index > 0 && !regenerating && !variantChanging,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.ask_previous_variant),
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                stringResource(R.string.ask_variant_counter, entry.index + 1, entry.variants.size),
                modifier = Modifier.clearAndSetSemantics {
                    applyAskVariantSemantics(variantPosition)
                },
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
                enabled = entry.index >= 0 &&
                    entry.index < entry.variants.lastIndex &&
                    !regenerating &&
                    !variantChanging,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.ask_next_variant),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (canRegenerate || regenerating) {
            IconButton(
                onClick = onRegenerate,
                enabled = canRegenerate && !regenerating,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = stringResource(if (regenerating) R.string.ask_generating else R.string.ask_regenerate),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (canSave) {
            IconButton(
                onClick = onSaveAsMemo,
                enabled = !savingDisabled && !regenerating,
                modifier = Modifier.size(48.dp),
            ) {
                if (saving) {
                    val savingDescription = stringResource(R.string.action_saving)
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(20.dp)
                            .semantics { contentDescription = savingDescription },
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Rounded.Save,
                        contentDescription = stringResource(R.string.ask_save_as_record),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun askMessageDescription(isAssistant: Boolean, content: String): String {
    val speaker = stringResource(if (isAssistant) R.string.app_name else R.string.ask_speaker_you)
    return stringResource(R.string.ask_message_description, speaker, content)
}

internal fun SemanticsPropertyReceiver.applyAskMessageSemantics(description: String) {
    contentDescription = description
}

internal fun SemanticsPropertyReceiver.applyAskVariantSemantics(description: String) {
    contentDescription = description
    liveRegion = LiveRegionMode.Polite
}
