package app.sillage.ui.application

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import app.sillage.core.application.preferences.ClientPreferenceValues
import app.sillage.ui.designsystem.SillageDesignTheme

data class SillageNativeHostStrings(
    val fileMenu: String,
    val navigateMenu: String,
    val newRecord: String,
    val openDataLocation: String,
    val quit: String,
    val records: String,
    val settings: String,
)

fun sillageNativeHostStrings(languageMode: String): SillageNativeHostStrings {
    val strings = sillageNativeStrings(languageMode)
    val chinese = languageMode == ClientPreferenceValues.LANGUAGE_ZH_CN
    return SillageNativeHostStrings(
        fileMenu = if (chinese) "文件" else "File",
        navigateMenu = if (chinese) "导航" else "Navigate",
        newRecord = strings.newRecord,
        openDataLocation = strings.openDataLocation,
        quit = if (chinese) "退出" else "Quit",
        records = strings.records,
        settings = strings.settings,
    )
}

@Composable
fun SillageNativeDiscardChangesDialog(
    languageMode: String,
    themeMode: String,
    onDismissRequest: () -> Unit,
    onDiscard: () -> Unit,
) {
    val strings = sillageNativeStrings(languageMode)
    SillageDesignTheme(themeMode == ClientPreferenceValues.THEME_DARK) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(strings.discardTitle) },
            text = { Text(strings.discardSupporting) },
            confirmButton = {
                TextButton(onClick = onDiscard) {
                    Text(strings.discard)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(strings.cancel)
                }
            },
        )
    }
}
