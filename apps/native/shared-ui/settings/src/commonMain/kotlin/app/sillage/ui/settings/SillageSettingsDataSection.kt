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
    onExport: (() -> Unit)?,
    onImport: (() -> Unit)?,
    modifier: Modifier = Modifier,
    leadingContent: (@Composable () -> Unit)? = null,
    exportEnabled: Boolean = enabled,
    importEnabled: Boolean = enabled,
) {
    val presentation = sillageSettingsDataPresentation(
        strings = strings,
        enabled = enabled,
        exportEnabled = exportEnabled,
        importEnabled = importEnabled,
    )

    SillageSettingsSectionCard(
        title = presentation.sectionTitle,
        modifier = modifier,
    ) {
        leadingContent?.invoke()
        onExport?.let { export ->
            SillageSettingsActionRow(
                icon = exportIcon,
                title = presentation.exportTitle,
                supporting = presentation.exportSupporting,
                onClick = export,
                enabled = presentation.exportEnabled,
                showDivider = leadingContent != null,
            )
        }
        onImport?.let { import ->
            SillageSettingsActionRow(
                icon = importIcon,
                title = presentation.importTitle,
                supporting = presentation.importSupporting,
                onClick = import,
                enabled = presentation.importEnabled,
                showDivider = leadingContent != null || onExport != null,
            )
        }
    }
}

internal data class SillageSettingsDataPresentation(
    val sectionTitle: String,
    val exportTitle: String,
    val exportSupporting: String,
    val importTitle: String,
    val importSupporting: String,
    val exportEnabled: Boolean,
    val importEnabled: Boolean,
)

internal fun sillageSettingsDataPresentation(
    strings: SillageSettingsDataStrings,
    enabled: Boolean,
    exportEnabled: Boolean = enabled,
    importEnabled: Boolean = enabled,
) = SillageSettingsDataPresentation(
    sectionTitle = strings.sectionTitle,
    exportTitle = strings.exportTitle,
    exportSupporting = strings.exportSupporting,
    importTitle = strings.importTitle,
    importSupporting = strings.importSupporting,
    exportEnabled = enabled && exportEnabled,
    importEnabled = enabled && importEnabled,
)
