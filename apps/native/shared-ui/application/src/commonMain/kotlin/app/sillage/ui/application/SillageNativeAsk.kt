package app.sillage.ui.application

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.sillage.features.ask.AskPathEntry
import app.sillage.features.ask.buildAskActivePath
import app.sillage.features.ask.lastAssistantMessageId
import app.sillage.ui.ask.SillageAskAutoFollow
import app.sillage.ui.ask.SillageAskComposer
import app.sillage.ui.ask.SillageAskComposerIcons
import app.sillage.ui.ask.SillageAskComposerStrings
import app.sillage.ui.ask.SillageAskContextStrings
import app.sillage.ui.ask.SillageAskConversationSheet
import app.sillage.ui.ask.SillageAskConversationStrings
import app.sillage.ui.ask.SillageAskEmptyPrompt
import app.sillage.ui.ask.SillageAskEmptyPromptStrings
import app.sillage.ui.ask.SillageAskLiveAnswerCard
import app.sillage.ui.ask.SillageAskLiveUserCard
import app.sillage.ui.ask.SillageAskMessageActionIcons
import app.sillage.ui.ask.SillageAskMessageActionStrings
import app.sillage.ui.ask.SillageAskMessageActions
import app.sillage.ui.ask.SillageAskMessageCard
import app.sillage.ui.ask.SillageAskMessageList
import app.sillage.ui.ask.SillageAskOptionsSheet
import app.sillage.ui.ask.SillageAskOptionsStrings
import app.sillage.ui.ask.SillageAskSourceReferenceIcons
import app.sillage.ui.ask.SillageAskSourceReferenceStrings
import app.sillage.ui.ask.SillageAskSourceReferences
import app.sillage.ui.ask.SillageAskTopBarActions
import app.sillage.ui.ask.SillageAskTopBarIcons
import app.sillage.ui.ask.SillageAskTopBarStrings
import app.sillage.ui.ask.SillageAskTopBarTitle
import app.sillage.ui.ask.sillageAskContextControlsEnabled
import app.sillage.ui.designsystem.SillageErrorCard
import app.sillage.ui.designsystem.SillageInlineError
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SillageNativeAsk(
    controller: SillageNativeController,
    strings: SillageNativeAskStrings,
    modifier: Modifier = Modifier,
) {
    val state = controller.state
    val ask = state.workspace.ask
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var showConversations by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var streamJob by remember { mutableStateOf<Job?>(null) }
    var observedCompletionEventId by remember(ask.screenSessionId) {
        mutableLongStateOf(ask.stream.completionEventId)
    }
    var completionAnnouncement by remember(ask.screenSessionId) { mutableStateOf<String?>(null) }
    val entries = remember(ask.messages, ask.headMessageId) {
        buildAskActivePath(ask.messages, ask.headMessageId)
    }
    val latestAssistantId = remember(entries) { lastAssistantMessageId(entries) }
    val contextStrings = remember(strings) {
        SillageAskContextStrings(
            recentSevenDays = strings.recentSevenDays,
            recentThirtyDays = strings.recentThirtyDays,
            allTime = strings.allTime,
            recordsSource = strings.recordsSource,
            summariesSource = strings.summariesSource,
        )
    }
    val topBarStrings = remember(strings) {
        SillageAskTopBarStrings(
            title = strings.title,
            savingContentDescription = strings.savingAnswerDescription,
            conversationsContentDescription = strings.conversationsDescription,
            contextContentDescription = strings.contextDescription,
            newConversationContentDescription = strings.newConversationDescription,
        )
    }

    fun launchStream(operation: suspend () -> Unit) {
        if (streamJob?.isActive == true) return
        val job = scope.launch { operation() }
        streamJob = job
        job.invokeOnCompletion {
            if (streamJob === job) streamJob = null
        }
    }

    LaunchedEffect(controller) {
        controller.loadAskConversations()
    }
    LaunchedEffect(ask.stream.completionEventId) {
        if (observedCompletionEventId != ask.stream.completionEventId) {
            observedCompletionEventId = ask.stream.completionEventId
            completionAnnouncement = strings.answerCompleteDescription
        }
    }
    DisposableEffect(controller) {
        onDispose { streamJob?.cancel() }
    }
    SillageAskAutoFollow(
        state = ask,
        entries = entries,
        listState = listState,
    )

    if (showConversations) {
        SillageAskConversationSheet(
            state = ask,
            strings = SillageAskConversationStrings(
                title = strings.conversationsTitle,
                refreshAction = strings.refresh,
                emptyConversations = strings.noConversations,
                untitledConversation = strings.untitledConversation,
            ),
            onRefresh = { scope.launch { controller.loadAskConversations() } },
            onSelect = { conversationId ->
                scope.launch { controller.selectAskConversation(conversationId) }
            },
            onDismiss = { showConversations = false },
            currentConversationLabel = { title -> strings.currentConversation(title) },
        )
    }
    if (showOptions) {
        SillageAskOptionsSheet(
            state = ask,
            enabled = sillageAskContextControlsEnabled(ask) && !state.busy,
            strings = SillageAskOptionsStrings(
                title = strings.contextTitle,
                timeRangeLabel = strings.timeRange,
                recentSevenDaysAction = strings.recentSevenDaysShort,
                recentThirtyDaysAction = strings.recentThirtyDaysShort,
                allTimeAction = strings.allTimeShort,
                sourceLabel = strings.sourceTitle,
                recordsSourceAction = strings.recordsSource,
                summariesSourceAction = strings.summariesSource,
            ),
            onDismiss = { showOptions = false },
            onContextScopeChange = controller::updateAskContextScope,
            onSourceKindChange = controller::updateAskSourceKind,
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    SillageAskTopBarTitle(
                        state = ask,
                        strings = topBarStrings,
                        contextStrings = contextStrings,
                        contextSummary = { scope, source ->
                            strings.contextSummary(scope, source)
                        },
                    )
                },
                actions = {
                    SillageAskTopBarActions(
                        state = ask,
                        strings = topBarStrings,
                        icons = SillageAskTopBarIcons(
                            conversations = Icons.AutoMirrored.Outlined.List,
                            context = Icons.Outlined.Tune,
                            newConversation = Icons.Outlined.Add,
                        ),
                        onShowConversations = { showConversations = true },
                        onShowContext = { showOptions = true },
                        onNewConversation = controller::startNewAskConversation,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            completionAnnouncement?.let { announcement ->
                Box(
                    modifier = Modifier
                        .size(1.dp)
                        .semantics {
                            contentDescription = announcement
                            liveRegion = LiveRegionMode.Polite
                        },
                )
            }
            state.askFailure
                ?.takeIf { it != SillageNativeAskFailure.LoadFailed }
                ?.let { failure ->
                    SillageInlineError(
                        message = strings.failureMessage(failure),
                        icon = Icons.Outlined.ErrorOutline,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            SillageAskMessageList(
                state = ask,
                entries = entries,
                latestAssistantId = latestAssistantId,
                hostActionsEnabled = state.storageAvailable && !state.busy,
                listState = listState,
                modifier = Modifier.weight(1f),
                loadErrorContent = {
                    SillageErrorCard(
                        message = strings.loadFailed,
                        actionLabel = strings.retry,
                        actionIcon = Icons.Outlined.Refresh,
                        onAction = { scope.launch { controller.retryAskLoad() } },
                    )
                },
                emptyContent = {
                    SillageAskEmptyPrompt(
                        strings = SillageAskEmptyPromptStrings(
                            title = strings.promptTitle,
                            example = strings.promptExample,
                        ),
                        icon = Icons.Outlined.AutoAwesome,
                    )
                },
                messageContent = { item ->
                    SillageNativeAskMessage(
                        entry = item.entry,
                        canRegenerate = item.canRegenerate,
                        regenerating = item.regenerating,
                        variantChanging = item.variantChanging,
                        savingDisabled = item.savingDisabled,
                        saving = item.saving,
                        sourceActionsEnabled = item.sourceActionsEnabled,
                        streamingText = item.streamingText,
                        strings = strings,
                        onRegenerate = {
                            launchStream {
                                controller.regenerateAskAnswer(item.entry.message.id)
                            }
                        },
                        onSave = {
                            scope.launch { controller.saveAskAnswerAsRecord(item.entry.message) }
                        },
                        onOpenSource = controller::openAskSource,
                        onSelectVariant = { messageId ->
                            scope.launch { controller.selectAskVariant(messageId) }
                        },
                    )
                },
                liveUserContent = { message ->
                    SillageAskLiveUserCard(
                        message = message,
                        messageDescription = { content ->
                            strings.messageDescription(false, content)
                        },
                    )
                },
                liveAnswerContent = { answer ->
                    SillageAskLiveAnswerCard(
                        answer = answer,
                        thinking = strings.thinking,
                        messageDescription = { content ->
                            strings.messageDescription(true, content)
                        },
                    )
                },
            )
            SillageAskComposer(
                state = ask,
                strings = SillageAskComposerStrings(
                    context = contextStrings,
                    questionLabel = strings.questionLabel,
                    sendContentDescription = strings.sendDescription,
                    stopContentDescription = strings.stopDescription,
                ),
                icons = SillageAskComposerIcons(
                    send = Icons.AutoMirrored.Outlined.Send,
                    stop = Icons.Outlined.StopCircle,
                ),
                onQuestionChange = controller::updateAskQuestion,
                onSend = { launchStream { controller.sendAskQuestion() } },
                onStop = {
                    controller.stopAskStreaming()
                    streamJob?.cancel()
                },
                contextSummary = { scope, source -> strings.contextSummary(scope, source) },
                characterCount = { count -> strings.characterCount(count) },
            )
        }
    }
}

@Composable
private fun SillageNativeAskMessage(
    entry: AskPathEntry,
    canRegenerate: Boolean,
    regenerating: Boolean,
    variantChanging: Boolean,
    savingDisabled: Boolean,
    saving: Boolean,
    sourceActionsEnabled: Boolean,
    streamingText: String?,
    strings: SillageNativeAskStrings,
    onRegenerate: () -> Unit,
    onSave: () -> Unit,
    onOpenSource: (String) -> Unit,
    onSelectVariant: (String) -> Unit,
) {
    SillageAskMessageCard(
        message = entry.message,
        streamingText = streamingText,
        regenerating = regenerating,
        regeneratingText = strings.regenerating,
        messageDescription = { isAssistant, content ->
            strings.messageDescription(isAssistant, content)
        },
        finalAssistantContent = { content -> Text(content) },
        sourceContent = {
            SillageAskSourceReferences(
                sources = entry.message.sourceRefs,
                enabled = sourceActionsEnabled,
                strings = SillageAskSourceReferenceStrings(
                    sourceCount = { count -> strings.sourceCount(count) },
                    sourceLabel = { source -> strings.sourceLabel(source) },
                    showSourcesContentDescription = strings.showSourcesDescription,
                    hideSourcesContentDescription = strings.hideSourcesDescription,
                ),
                icons = SillageAskSourceReferenceIcons(
                    expand = Icons.Outlined.ExpandMore,
                    collapse = Icons.Outlined.ExpandLess,
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
                    previousVariantContentDescription = strings.previousVariantDescription,
                    nextVariantContentDescription = strings.nextVariantDescription,
                    variantCounter = { position, total ->
                        strings.variantCounter(position, total)
                    },
                    variantPositionDescription = { position, total ->
                        strings.variantPositionDescription(position, total)
                    },
                    regenerateContentDescription = strings.regenerateDescription,
                    generatingContentDescription = strings.generatingDescription,
                    saveContentDescription = strings.saveAsRecordDescription,
                    savingContentDescription = strings.savingDescription,
                ),
                icons = SillageAskMessageActionIcons(
                    previousVariant = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    nextVariant = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    regenerate = Icons.Outlined.Refresh,
                    save = Icons.Outlined.Save,
                ),
                onRegenerate = onRegenerate,
                onSave = onSave,
                onSelectVariant = onSelectVariant,
            )
        },
    )
}

private fun SillageNativeAskStrings.failureMessage(failure: SillageNativeAskFailure): String {
    return when (failure) {
        SillageNativeAskFailure.AuthenticationRequired -> authenticationRequired
        SillageNativeAskFailure.QuestionRequired -> questionRequired
        SillageNativeAskFailure.LoadFailed -> loadFailed
        SillageNativeAskFailure.SendFailed -> sendFailed
        SillageNativeAskFailure.VariantFailed -> variantFailed
        SillageNativeAskFailure.SourceUnavailable -> sourceUnavailable
        SillageNativeAskFailure.SaveFailed -> saveFailed
    }
}
