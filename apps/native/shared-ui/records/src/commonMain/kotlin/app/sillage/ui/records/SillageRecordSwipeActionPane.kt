package app.sillage.ui.records

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.sillage.core.domain.records.Memo

data class SillageRecordSwipeActionStrings(
    val favoriteAction: String,
    val unfavoriteAction: String,
    val archiveAction: String,
    val restoreAction: String,
)

@Composable
fun SillageRecordSwipeActionPane(
    memo: Memo,
    actionWidth: Dp,
    revealedOffset: Float,
    strings: SillageRecordSwipeActionStrings,
    favoriteIcon: ImageVector,
    favoritedIcon: ImageVector,
    archiveIcon: ImageVector,
    onToggleFavorite: () -> Unit,
    onToggleArchive: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val presentation = sillageRecordSwipeActionPresentation(memo, revealedOffset, strings)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        SillageSwipeActionButton(
            icon = if (presentation.favoriteActive) favoritedIcon else favoriteIcon,
            label = presentation.favoriteLabel,
            visible = presentation.favoriteVisible,
            enabled = enabled,
            color = MaterialTheme.colorScheme.primaryContainer,
            onClick = onToggleFavorite,
            modifier = Modifier
                .fillMaxHeight()
                .width(actionWidth),
        )
        SillageSwipeActionButton(
            icon = archiveIcon,
            label = presentation.archiveLabel,
            visible = presentation.archiveVisible,
            enabled = enabled,
            color = MaterialTheme.colorScheme.secondaryContainer,
            onClick = onToggleArchive,
            modifier = Modifier
                .fillMaxHeight()
                .width(actionWidth),
        )
    }
}

@Composable
private fun SillageSwipeActionButton(
    icon: ImageVector,
    label: String,
    visible: Boolean,
    enabled: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (visible) color else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    if (!visible) {
                        invisibleToUser()
                    }
                }
                .clickable(enabled = visible && enabled, onClick = onClick)
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

internal data class SillageRecordSwipeActionPresentation(
    val favoriteLabel: String,
    val archiveLabel: String,
    val favoriteActive: Boolean,
    val favoriteVisible: Boolean,
    val archiveVisible: Boolean,
)

internal fun sillageRecordSwipeActionPresentation(
    memo: Memo,
    revealedOffset: Float,
    strings: SillageRecordSwipeActionStrings,
): SillageRecordSwipeActionPresentation = SillageRecordSwipeActionPresentation(
    favoriteLabel = if (memo.favoritedAt == null) strings.favoriteAction else strings.unfavoriteAction,
    archiveLabel = if (memo.archivedAt == null) strings.archiveAction else strings.restoreAction,
    favoriteActive = memo.favoritedAt != null,
    favoriteVisible = revealedOffset > 0f,
    archiveVisible = revealedOffset < 0f,
)
