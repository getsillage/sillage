package app.sillage.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import app.sillage.ui.designsystem.SillageSettingsActionRow
import app.sillage.ui.designsystem.SillageSettingsSectionCard

data class SillageSettingsDataStrings(
    val sectionTitle: String,
    val exportTitle: String,
    val exportSupporting: String,
    val importTitle: String,
    val importSupporting: String,
)

@Composable
fun SillageSettingsDataSection(
    strings: SillageSettingsDataStrings,
    exportIcon: ImageVector,
    importIcon: ImageVector,
    enabled: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = sillageSettingsDataPresentation(strings = strings, enabled = enabled)

    SillageSettingsSectionCard(
        title = presentation.sectionTitle,
        modifier = modifier,
    ) {
        SillageSettingsActionRow(
            icon = exportIcon,
            title = presentation.exportTitle,
            supporting = presentation.exportSupporting,
            onClick = onExport,
            enabled = presentation.enabled,
        )
        SillageSettingsActionRow(
            icon = importIcon,
            title = presentation.importTitle,
            supporting = presentation.importSupporting,
            onClick = onImport,
            enabled = presentation.enabled,
            showDivider = true,
        )
    }
}

internal data class SillageSettingsDataPresentation(
    val sectionTitle: String,
    val exportTitle: String,
    val exportSupporting: String,
    val importTitle: String,
    val importSupporting: String,
    val enabled: Boolean,
)

internal fun sillageSettingsDataPresentation(
    strings: SillageSettingsDataStrings,
    enabled: Boolean,
) = SillageSettingsDataPresentation(
    sectionTitle = strings.sectionTitle,
    exportTitle = strings.exportTitle,
    exportSupporting = strings.exportSupporting,
    importTitle = strings.importTitle,
    importSupporting = strings.importSupporting,
    enabled = enabled,
)
