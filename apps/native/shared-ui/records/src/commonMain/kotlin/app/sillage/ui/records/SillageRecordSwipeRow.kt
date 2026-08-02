package app.sillage.ui.records

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.sillage.core.domain.records.Memo
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

data class SillageRecordSwipeRowStrings(
    val row: SillageRecordRowStrings,
    val swipeActions: SillageRecordSwipeActionStrings,
    val quickActions: SillageRecordQuickActionsStrings,
)

data class SillageRecordSwipeRowIcons(
    val favorite: ImageVector,
    val favorited: ImageVector,
    val archive: ImageVector,
    val quickActions: SillageRecordQuickActionIcons,
)

@Composable
fun SillageRecordSwipeRow(
    memo: Memo,
    mutating: Boolean,
    strings: SillageRecordSwipeRowStrings,
    icons: SillageRecordSwipeRowIcons,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showActions by remember { mutableStateOf(false) }
    val actionWidth = 92.dp
    val actionWidthPx = with(LocalDensity.current) { actionWidth.toPx() }
    val coroutineScope = rememberCoroutineScope()
    var offsetX by remember(memo.id) { mutableStateOf(0f) }
    val dragState = rememberDraggableState { delta ->
        offsetX = sillageRecordSwipeDraggedOffset(offsetX, delta, actionWidthPx)
    }

    fun animateOffsetTo(target: Float, after: (() -> Unit)? = null) {
        coroutineScope.launch {
            val animation = Animatable(offsetX)
            animation.animateTo(target) {
                offsetX = value
            }
            offsetX = target
            after?.invoke()
        }
    }

    fun closeActions() {
        animateOffsetTo(0f)
    }

    fun settleActions() {
        animateOffsetTo(sillageRecordSwipeSettleTarget(offsetX, actionWidthPx))
    }

    fun runAction(action: () -> Unit) {
        animateOffsetTo(0f, action)
    }

    LaunchedEffect(mutating) {
        if (mutating) {
            showActions = false
            offsetX = 0f
        }
    }

    if (showActions && !mutating) {
        SillageRecordQuickActionsSheet(
            memo = memo,
            strings = strings.quickActions,
            icons = icons.quickActions,
            onDismiss = { showActions = false },
            onEdit = {
                showActions = false
                onEdit()
            },
            onDuplicate = {
                showActions = false
                onDuplicate()
            },
            onToggleFavorite = {
                showActions = false
                onToggleFavorite()
            },
            onToggleArchive = {
                showActions = false
                onToggleArchive()
            },
            onDelete = {
                showActions = false
                onDelete()
            },
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 92.dp),
    ) {
        SillageRecordSwipeActionPane(
            memo = memo,
            actionWidth = actionWidth,
            revealedOffset = offsetX,
            strings = strings.swipeActions,
            favoriteIcon = icons.favorite,
            favoritedIcon = icons.favorited,
            archiveIcon = icons.archive,
            onToggleFavorite = { runAction(onToggleFavorite) },
            onToggleArchive = { runAction(onToggleArchive) },
            enabled = !mutating,
            modifier = Modifier.matchParentSize(),
        )
        SillageRecordRow(
            memo = memo,
            strings = strings.row,
            modifier = Modifier
                .heightIn(min = 92.dp)
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = dragState,
                    enabled = !mutating,
                    onDragStopped = { settleActions() },
                ),
            mutating = mutating,
            onClick = {
                if (offsetX != 0f) {
                    closeActions()
                } else {
                    onClick()
                }
            },
            onLongClick = if (mutating) null else { { showActions = true } },
        )
    }
}

internal fun sillageRecordSwipeDraggedOffset(
    currentOffset: Float,
    delta: Float,
    actionWidthPx: Float,
): Float = (currentOffset + delta).coerceIn(-actionWidthPx, actionWidthPx)

internal fun sillageRecordSwipeSettleTarget(
    offset: Float,
    actionWidthPx: Float,
): Float {
    val settleThreshold = actionWidthPx * 0.56f
    return when {
        offset > settleThreshold -> actionWidthPx
        offset < -settleThreshold -> -actionWidthPx
        else -> 0f
    }
}
