package app.sillage.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class SillageSettingsLicensesDialogStrings(
    val title: String,
    val close: String,
)

@Composable
fun SillageSettingsLicensesDialog(
    notices: String,
    strings: SillageSettingsLicensesDialogStrings,
    onDismiss: () -> Unit,
) {
    val presentation = sillageSettingsLicensesDialogPresentation(
        notices = notices,
        strings = strings,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(presentation.title) },
        text = {
            SelectionContainer {
                Text(
                    text = presentation.notices,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(presentation.close)
            }
        },
    )
}

internal data class SillageSettingsLicensesDialogPresentation(
    val title: String,
    val notices: String,
    val close: String,
)

internal fun sillageSettingsLicensesDialogPresentation(
    notices: String,
    strings: SillageSettingsLicensesDialogStrings,
): SillageSettingsLicensesDialogPresentation {
    require(notices.isNotBlank()) { "License notices must not be blank" }
    return SillageSettingsLicensesDialogPresentation(
        title = strings.title,
        notices = notices,
        close = strings.close,
    )
}
