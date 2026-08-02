package app.sillage.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.sillage.features.sync.SyncFeatureStateHolder

data class SillageSyncConflictStrings(
    val title: String,
    val supporting: String,
    val localLabel: String,
    val serverLabel: String,
    val emptyLocal: String,
    val emptyServer: String,
    val keepLocal: String,
    val takeServer: String,
    val dismiss: String,
)

@Composable
fun SillageSyncConflictDialog(
    state: SyncFeatureStateHolder,
    strings: SillageSyncConflictStrings,
    onKeepLocal: (String) -> Unit,
    onTakeServer: (String) -> Unit,
    onDismiss: (String) -> Unit,
) {
    val presentation = remember(state, strings) {
        sillageSyncConflictPresentation(state, strings)
    } ?: return

    AlertDialog(
        onDismissRequest = { onDismiss(presentation.resourceId) },
        title = { Text(strings.title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(strings.supporting)
                Text(strings.localLabel, fontWeight = FontWeight.SemiBold)
                Text(presentation.localPreview)
                Spacer(modifier = Modifier.height(4.dp))
                Text(strings.serverLabel, fontWeight = FontWeight.SemiBold)
                Text(presentation.serverPreview)
            }
        },
        confirmButton = {
            TextButton(onClick = { onKeepLocal(presentation.resourceId) }) {
                Text(strings.keepLocal)
            }
        },
        dismissButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { onTakeServer(presentation.resourceId) }) {
                    Text(strings.takeServer)
                }
                TextButton(onClick = { onDismiss(presentation.resourceId) }) {
                    Text(strings.dismiss)
                }
            }
        },
    )
}

internal data class SillageSyncConflictPresentation(
    val resourceId: String,
    val localPreview: String,
    val serverPreview: String,
)

internal fun sillageSyncConflictPresentation(
    state: SyncFeatureStateHolder,
    strings: SillageSyncConflictStrings,
): SillageSyncConflictPresentation? {
    val item = state.items.firstOrNull() ?: return null
    return SillageSyncConflictPresentation(
        resourceId = item.conflict.resourceId,
        localPreview = item.localMemo?.content.orEmpty()
            .trim()
            .ifBlank { strings.emptyLocal }
            .take(SYNC_CONFLICT_PREVIEW_LIMIT),
        serverPreview = item.conflict.serverMemo?.content.orEmpty()
            .trim()
            .ifBlank { strings.emptyServer }
            .take(SYNC_CONFLICT_PREVIEW_LIMIT),
    )
}

private const val SYNC_CONFLICT_PREVIEW_LIMIT = 800
