package app.sillage.ui.ask

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.sillage.core.domain.ask.AskMessage
import app.sillage.features.ask.AskFeatureStateHolder
import app.sillage.features.ask.AskPathEntry

@Composable
fun SillageAskMessageList(
    state: AskFeatureStateHolder,
    entries: List<AskPathEntry>,
    latestAssistantId: String?,
    hostActionsEnabled: Boolean,
    listState: LazyListState,
    loadErrorContent: @Composable LazyItemScope.(message: String) -> Unit,
    emptyContent: @Composable LazyItemScope.() -> Unit,
    messageContent: @Composable LazyItemScope.(item: SillageAskMessageListItem) -> Unit,
    liveUserContent: @Composable LazyItemScope.(message: AskMessage) -> Unit,
    liveAnswerContent: @Composable LazyItemScope.(answer: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = sillageAskMessageListPresentation(
        state = state,
        entries = entries,
        latestAssistantId = latestAssistantId,
        hostActionsEnabled = hostActionsEnabled,
    )
    if (presentation.initialLoading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presentation.loadErrorMessage?.let { message ->
            item(key = "ask-load-error") {
                loadErrorContent(message)
            }
        }
        if (presentation.showEmpty) {
            item(key = "ask-empty") {
                emptyContent()
            }
        }
        items(
            items = presentation.messages,
            key = { it.entry.message.id },
        ) { item ->
            messageContent(item)
        }
        presentation.liveUser?.let { message ->
            item(key = "ask-live-user-${message.id}") {
                liveUserContent(message)
            }
        }
        if (presentation.showLiveAnswer) {
            item(key = "ask-live-answer") {
                liveAnswerContent(presentation.liveAnswer)
            }
        }
    }
}

data class SillageAskMessageListItem(
    val entry: AskPathEntry,
    val canRegenerate: Boolean,
    val regenerating: Boolean,
    val variantChanging: Boolean,
    val savingDisabled: Boolean,
    val saving: Boolean,
    val sourceActionsEnabled: Boolean,
    val streamingText: String?,
)

internal data class SillageAskMessageListPresentation(
    val initialLoading: Boolean,
    val loadErrorMessage: String?,
    val showEmpty: Boolean,
    val messages: List<SillageAskMessageListItem>,
    val liveUser: AskMessage?,
    val showLiveAnswer: Boolean,
    val liveAnswer: String,
)

internal fun sillageAskMessageListPresentation(
    state: AskFeatureStateHolder,
    entries: List<AskPathEntry>,
    latestAssistantId: String?,
    hostActionsEnabled: Boolean,
): SillageAskMessageListPresentation {
    val initialLoading = state.loading && entries.isEmpty()
    val sourceActionsEnabled = hostActionsEnabled &&
        !state.sending &&
        !state.loading &&
        !state.variantLoading &&
        !state.sourceLoading
    return SillageAskMessageListPresentation(
        initialLoading = initialLoading,
        loadErrorMessage = state.loadErrorMessage,
        showEmpty = !initialLoading && entries.isEmpty() && state.loadErrorMessage == null,
        messages = entries.map { entry ->
            val regenerating = state.stream.regeneratingMessageId == entry.message.id
            SillageAskMessageListItem(
                entry = entry,
                canRegenerate = entry.message.id == latestAssistantId &&
                    !state.loading &&
                    !state.sending &&
                    !state.variantLoading &&
                    !state.sourceLoading,
                regenerating = regenerating,
                variantChanging = state.loading || state.variantLoading,
                savingDisabled = !hostActionsEnabled ||
                    state.loading ||
                    state.sending ||
                    state.variantLoading ||
                    state.sourceLoading ||
                    state.savingMessageId.isNotBlank(),
                saving = state.savingMessageId == entry.message.id,
                sourceActionsEnabled = sourceActionsEnabled,
                streamingText = if (regenerating) state.stream.liveAnswer else null,
            )
        },
        liveUser = state.stream.liveUser,
        showLiveAnswer = state.sending && state.stream.regeneratingMessageId.isBlank(),
        liveAnswer = state.stream.liveAnswer,
    )
}
