package app.sillage.ui.records

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

data class SillageRecordEditorDiscardStrings(
    val title: String,
    val supporting: String,
    val discardAction: String,
    val continueEditingAction: String,
)

@Composable
fun rememberSillageRecordEditorCloseRequest(
    hasUnsavedChanges: Boolean,
    discardEnabled: Boolean,
    strings: SillageRecordEditorDiscardStrings,
    onClose: () -> Unit,
): () -> Unit {
    var confirmDiscard by remember { mutableStateOf(false) }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(strings.title) },
            text = { Text(strings.supporting) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        onClose()
                    },
                    enabled = discardEnabled,
                ) {
                    Text(strings.discardAction, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text(strings.continueEditingAction)
                }
            },
        )
    }

    return {
        when (sillageRecordEditorCloseRequest(hasUnsavedChanges)) {
            SillageRecordEditorCloseRequest.Close -> onClose()
            SillageRecordEditorCloseRequest.ConfirmDiscard -> confirmDiscard = true
        }
    }
}

internal enum class SillageRecordEditorCloseRequest {
    Close,
    ConfirmDiscard,
}

internal fun sillageRecordEditorCloseRequest(
    hasUnsavedChanges: Boolean,
): SillageRecordEditorCloseRequest = if (hasUnsavedChanges) {
    SillageRecordEditorCloseRequest.ConfirmDiscard
} else {
    SillageRecordEditorCloseRequest.Close
}
