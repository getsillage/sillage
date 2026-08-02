package app.sillage.ui.ask

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sillage.features.ask.AskPathEntry
import app.sillage.data.MarkdownLinkTarget
import app.sillage.features.ask.buildAskActivePath
import app.sillage.features.ask.lastAssistantMessageId
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
            SillageAskMessageList(
                state = state.ask,
                entries = entries,
                latestAssistantId = latestAssistantId,
                hostActionsEnabled = !state.loading,
                listState = listState,
                modifier = Modifier.weight(1f),
                loadErrorContent = { message ->
                    SillageErrorCard(
                        message = message,
                        actionLabel = stringResource(R.string.action_retry),
                        actionIcon = Icons.Rounded.Refresh,
                        onAction = viewModel::retryAskLoad,
                    )
                },
                emptyContent = {
                    SillageAskEmptyPrompt(
                        strings = SillageAskEmptyPromptStrings(
                            title = stringResource(R.string.ask_prompt_title),
                            example = stringResource(R.string.ask_prompt_example),
                        ),
                        icon = Icons.Rounded.AutoAwesome,
                    )
                },
                messageContent = { item ->
                    AskMessageCard(
                        entry = item.entry,
                        canRegenerate = item.canRegenerate,
                        regenerating = item.regenerating,
                        variantChanging = item.variantChanging,
                        savingDisabled = item.savingDisabled,
                        saving = item.saving,
                        sourceActionsEnabled = item.sourceActionsEnabled,
                        streamingText = item.streamingText,
                        baseUrl = state.baseUrl,
                        openingAttachmentPath = state.openingAttachmentPath,
                        onRegenerate = {
                            viewModel.regenerateAskAnswer(item.entry.message.id)
                        },
                        onSaveAsMemo = {
                            viewModel.saveAskAnswerAsMemo(item.entry.message)
                        },
                        onOpenSource = viewModel::openAskSourceMemo,
                        onSelectVariant = viewModel::selectAskVariant,
                        onOpenAttachment = viewModel::openProtectedAttachment,
                    )
                },
                liveUserContent = { message ->
                    SillageAskLiveUserCard(
                        message = message,
                        messageDescription = { content ->
                            askMessageDescription(isAssistant = false, content = content)
                        },
                    )
                },
                liveAnswerContent = { answer ->
                    SillageAskLiveAnswerCard(
                        answer = answer,
                        thinking = stringResource(R.string.ask_thinking),
                        messageDescription = { content ->
                            askMessageDescription(isAssistant = true, content = content)
                        },
                    )
                },
            )

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
    SillageAskMessageCard(
        message = entry.message,
        streamingText = streamingText,
        regenerating = regenerating,
        regeneratingText = stringResource(R.string.ask_regenerating),
        messageDescription = { isAssistant, content ->
            askMessageDescription(isAssistant, content)
        },
        finalAssistantContent = { content ->
            // Final assistant answers render Markdown like record bodies; the
            // shared card keeps streaming/regenerating text plain.
            MarkdownContent(
                content = content,
                baseUrl = baseUrl,
                openingAttachmentPath = openingAttachmentPath,
                onOpenAttachment = onOpenAttachment,
            )
        },
        sourceContent = {
            SillageAskSourceReferences(
                sources = entry.message.sourceRefs,
                enabled = sourceActionsEnabled,
                strings = SillageAskSourceReferenceStrings(
                    sourceCount = { count ->
                        pluralStringResource(R.plurals.quantity_sources, count, count)
                    },
                    sourceLabel = { source ->
                        stringResource(
                            R.string.quantity_joiner,
                            localizedDate(source.entryDate),
                            source.excerpt,
                        )
                    },
                    showSourcesContentDescription = stringResource(
                        R.string.ask_show_sources,
                    ),
                    hideSourcesContentDescription = stringResource(
                        R.string.ask_hide_sources,
                    ),
                ),
                icons = SillageAskSourceReferenceIcons(
                    expand = Icons.Rounded.ExpandMore,
                    collapse = Icons.Rounded.ExpandLess,
                ),
                onOpenSource = onOpenSource,
            )
        },
        actionContent = {
            SillageAskMessageActions(
                message = entry.message,
                variants = entry.variants,
                selectedIndex = entry.index,
                canRegenerate = canRegenerate,
                regenerating = regenerating,
                variantChanging = variantChanging,
                savingDisabled = savingDisabled,
                saving = saving,
                strings = SillageAskMessageActionStrings(
                    previousVariantContentDescription = stringResource(
                        R.string.ask_previous_variant,
                    ),
                    nextVariantContentDescription = stringResource(
                        R.string.ask_next_variant,
                    ),
                    variantCounter = { position, total ->
                        stringResource(R.string.ask_variant_counter, position, total)
                    },
                    variantPositionDescription = { position, total ->
                        stringResource(R.string.ask_variant_position, position, total)
                    },
                    regenerateContentDescription = stringResource(R.string.ask_regenerate),
                    generatingContentDescription = stringResource(R.string.ask_generating),
                    saveContentDescription = stringResource(R.string.ask_save_as_record),
                    savingContentDescription = stringResource(R.string.action_saving),
                ),
                icons = SillageAskMessageActionIcons(
                    previousVariant = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    nextVariant = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    regenerate = Icons.Rounded.Refresh,
                    save = Icons.Rounded.Save,
                ),
                onRegenerate = onRegenerate,
                onSave = onSaveAsMemo,
                onSelectVariant = onSelectVariant,
            )
        },
    )
}

@Composable
private fun askMessageDescription(isAssistant: Boolean, content: String): String {
    val speaker = stringResource(if (isAssistant) R.string.app_name else R.string.ask_speaker_you)
    return stringResource(R.string.ask_message_description, speaker, content)
}
