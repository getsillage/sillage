package app.sillage.ui.ask

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.sillage.features.ask.AskFeatureStateHolder
import app.sillage.features.ask.AskPathEntry

@Composable
fun SillageAskAutoFollow(
    state: AskFeatureStateHolder,
    entries: List<AskPathEntry>,
    listState: LazyListState,
) {
    val isUserDragging by listState.interactionSource.collectIsDraggedAsState()
    val thresholdPx = with(LocalDensity.current) { AUTO_FOLLOW_THRESHOLD.roundToPx() }
    var autoFollow by remember(state.activeConversationId) { mutableStateOf(true) }
    val listItemCount = sillageAskMessageListItemCount(state, entries)

    LaunchedEffect(isUserDragging) {
        if (isUserDragging) autoFollow = false
    }
    LaunchedEffect(listState, isUserDragging, autoFollow, thresholdPx) {
        if (!isUserDragging && !autoFollow) {
            snapshotFlow { listState.isNearSillageAskBottom(thresholdPx) }
                .collect { nearBottom ->
                    if (nearBottom) autoFollow = true
                }
        }
    }
    LaunchedEffect(state.sending) {
        if (state.sending && listItemCount > 0) {
            autoFollow = true
            withFrameNanos { }
            listState.scrollToSillageAskBottom()
        }
    }
    LaunchedEffect(
        listItemCount,
        entries.lastOrNull()?.message?.id,
        state.stream.liveAnswer.length,
        autoFollow,
        isUserDragging,
    ) {
        if (!state.loading && listItemCount > 0 && autoFollow && !isUserDragging) {
            withFrameNanos { }
            listState.scrollToSillageAskBottom()
        }
    }
}

internal fun sillageAskMessageListItemCount(
    state: AskFeatureStateHolder,
    entries: List<AskPathEntry>,
): Int {
    if (state.loading && entries.isEmpty()) return 0

    return entries.size +
        (if (state.loadErrorMessage != null || entries.isEmpty()) 1 else 0) +
        (if (state.stream.liveUser != null) 1 else 0) +
        (if (state.sending && state.stream.regeneratingMessageId.isBlank()) 1 else 0)
}

private fun LazyListState.isNearSillageAskBottom(thresholdPx: Int): Boolean {
    val layout = layoutInfo
    val lastVisibleItem = layout.visibleItemsInfo.lastOrNull()
    return isSillageAskListNearBottom(
        lastVisibleIndex = lastVisibleItem?.index,
        totalItemsCount = layout.totalItemsCount,
        lastVisibleEnd = lastVisibleItem?.let { it.offset + it.size },
        viewportEnd = layout.viewportEndOffset,
        thresholdPx = thresholdPx,
    )
}

private suspend fun LazyListState.scrollToSillageAskBottom() {
    val totalItemsCount = layoutInfo.totalItemsCount
    if (totalItemsCount <= 0) return

    val lastIndex = totalItemsCount - 1
    if (layoutInfo.visibleItemsInfo.none { it.index == lastIndex }) {
        scrollToItem(lastIndex)
    }
    val layout = layoutInfo
    val lastItem = layout.visibleItemsInfo.lastOrNull { it.index == lastIndex } ?: return
    val remainingDistance = lastItem.offset + lastItem.size - layout.viewportEndOffset
    if (remainingDistance > 0) scrollBy(remainingDistance.toFloat())
}

internal fun isSillageAskListNearBottom(
    lastVisibleIndex: Int?,
    totalItemsCount: Int,
    lastVisibleEnd: Int?,
    viewportEnd: Int,
    thresholdPx: Int,
): Boolean {
    if (totalItemsCount <= 0 ||
        lastVisibleIndex != totalItemsCount - 1 ||
        lastVisibleEnd == null
    ) {
        return false
    }
    return lastVisibleEnd - viewportEnd <= thresholdPx.coerceAtLeast(0)
}

private val AUTO_FOLLOW_THRESHOLD = 96.dp
