package app.sillage.ui.ask

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sillage.core.domain.ask.AskMessage
import app.sillage.data.AskPathEntry
import app.sillage.core.domain.ask.AskSourceRef
import app.sillage.data.MarkdownLinkTarget
import app.sillage.data.buildAskActivePath
import app.sillage.data.lastAssistantMessageId
import app.sillage.R
import app.sillage.ui.SillageUiState
import app.sillage.ui.SillageViewModel
import app.sillage.ui.designsystem.SillageErrorCard
import app.sillage.ui.memos.MarkdownContent
import app.sillage.ui.localizedDate
import app.sillage.ui.navigation.MainNavigationBar
import app.sillage.ui.designsystem.applySillageHeadingSemantics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskScreen(state: SillageUiState, viewModel: SillageViewModel) {
    var showConversations by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    val view = LocalView.current
    val completedDescription = stringResource(R.string.ask_answer_complete)
    val contextStrings = SillageAskContextStrings(
        recentSevenDays = stringResource(R.string.ask_scope_7_days),
        recentThirtyDays = stringResource(R.string.ask_scope_30_days),
        allTime = stringResource(R.string.ask_scope_all),
        recordsSource = stringResource(R.string.ask_source_records),
        summariesSource = stringResource(R.string.ask_source_summaries),
    )
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
        SillageAskConversationSheet(
            state = state.ask,
            strings = SillageAskConversationStrings(
                title = stringResource(R.string.ask_conversations_title),
                refreshAction = stringResource(R.string.action_refresh),
                emptyConversations = stringResource(R.string.ask_no_conversations),
                untitledConversation = stringResource(R.string.ask_untitled_conversation),
            ),
            onRefresh = viewModel::loadAskConversations,
            onSelect = viewModel::selectAskConversation,
            onDismiss = { showConversations = false },
            currentConversationLabel = { title ->
                stringResource(R.string.ask_current_conversation, title)
            },
        )
    }
    if (showOptions) {
        SillageAskOptionsSheet(
            state = state.ask,
            enabled = contextControlsEnabled,
            strings = SillageAskOptionsStrings(
                title = stringResource(R.string.ask_context_title),
                timeRangeLabel = stringResource(R.string.ask_time_range),
                recentSevenDaysAction = stringResource(R.string.ask_scope_7_days_short),
                recentThirtyDaysAction = stringResource(R.string.ask_scope_30_days_short),
                allTimeAction = stringResource(R.string.ask_scope_all_short),
                sourceLabel = stringResource(R.string.ask_source_title),
                recordsSourceAction = stringResource(R.string.ask_source_records),
                summariesSourceAction = stringResource(R.string.ask_source_summaries),
            ),
            onDismiss = { showOptions = false },
            onContextScopeChange = viewModel::updateAskScope,
            onSourceKindChange = viewModel::updateAskSourceKind,
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
                        modifier = Modifier.semantics { applySillageHeadingSemantics() },
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                SillageAskContextLabel(
                                    state = state.ask,
                                    strings = contextStrings,
                                    contextSummary = { scope, source ->
                                        stringResource(R.string.ask_record_context_summary, scope, source)
                                    },
                                ),
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
                        SillageErrorCard(
                            message = message,
                            actionLabel = stringResource(R.string.action_retry),
                            actionIcon = Icons.Rounded.Refresh,
                            onAction = viewModel::retryAskLoad,
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
                            baseUrl = state.baseUrl,
                            openingAttachmentPath = state.openingAttachmentPath,
                            onRegenerate = { viewModel.regenerateAskAnswer(entry.message.id) },
                            onSaveAsMemo = { viewModel.saveAskAnswerAsMemo(entry.message) },
                            onOpenSource = viewModel::openAskSourceMemo,
                            onSelectVariant = viewModel::selectAskVariant,
                            onOpenAttachment = viewModel::openProtectedAttachment,
                        )
                    }
                    state.askLiveUser?.let { liveUser ->
                        item {
                            AskLiveUserCard(liveUser)
                        }
                    }
                    if (state.askSending && state.askRegeneratingId.isBlank()) {
                        item {
                            AskLiveAnswerCard(state.askLiveAnswer)
                        }
                    }
                }
            }
            SillageAskComposer(
                state = state.ask,
                strings = SillageAskComposerStrings(
                    context = contextStrings,
                    questionLabel = stringResource(R.string.ask_question_label),
                    sendContentDescription = stringResource(R.string.ask_send),
                    stopContentDescription = stringResource(R.string.ask_stop_generation),
                ),
                icons = SillageAskComposerIcons(
                    send = Icons.AutoMirrored.Rounded.Send,
                    stop = Icons.Rounded.StopCircle,
                ),
                onQuestionChange = viewModel::updateAskQuestion,
                onSend = viewModel::sendAskQuestion,
                onStop = viewModel::stopAskStreaming,
                contextSummary = { scope, source ->
                    stringResource(R.string.ask_record_context_summary, scope, source)
                },
                characterCount = { count ->
                    pluralStringResource(R.plurals.quantity_characters, count, count)
                },
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
private fun AskMessageCard(
    entry: AskPathEntry,
    canRegenerate: Boolean,
    regenerating: Boolean,
    variantChanging: Boolean,
    savingDisabled: Boolean,
    saving: Boolean,
    sourceActionsEnabled: Boolean,
    streamingText: String?,
    baseUrl: String,
    openingAttachmentPath: String?,
    onRegenerate: () -> Unit,
    onSaveAsMemo: () -> Unit,
    onOpenSource: (String) -> Unit,
    onSelectVariant: (String) -> Unit,
    onOpenAttachment: (MarkdownLinkTarget.ProtectedAttachment) -> Unit,
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
                // Final assistant answers render as Markdown like record bodies;
                // streaming/regenerating text stays plain to avoid re-parsing
                // partial syntax on every delta.
                if (isAssistant && streamingText == null && !regenerating) {
                    Box(
                        modifier = Modifier.clearAndSetSemantics {
                            applyAskMessageSemantics(messageDescription)
                        },
                    ) {
                        MarkdownContent(
                            content = displayedContent,
                            baseUrl = baseUrl,
                            openingAttachmentPath = openingAttachmentPath,
                            onOpenAttachment = onOpenAttachment,
                        )
                    }
                } else {
                    Text(
                        displayedContent,
                        modifier = Modifier.clearAndSetSemantics {
                            applyAskMessageSemantics(messageDescription)
                        },
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
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
