package app.sillage.ui.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sillage.ui.designsystem.applySillageStatusSemantics

@Composable
fun SillageRecordSearchStatus(
    summary: String,
    completionEventId: Long,
    icon: ImageVector,
    onAnnounce: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var observedCompletionEventId by remember {
        mutableLongStateOf(completionEventId)
    }

    LaunchedEffect(completionEventId, summary) {
        if (sillageRecordSearchStatusShouldAnnounce(observedCompletionEventId, completionEventId)) {
            observedCompletionEventId = completionEventId
            onAnnounce(summary)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clearAndSetSemantics { applySillageStatusSemantics(summary) },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            summary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun sillageRecordSearchStatusShouldAnnounce(
    observedCompletionEventId: Long,
    completionEventId: Long,
): Boolean = observedCompletionEventId != completionEventId
