package app.sillage.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import app.sillage.ui.designsystem.SillageSettingsActionRow
import app.sillage.ui.designsystem.SillageSettingsInfoRow
import app.sillage.ui.designsystem.SillageSettingsSectionCard

data class SillageSettingsAboutStrings(
    val sectionTitle: String,
    val licensesTitle: String? = null,
    val licensesSupporting: String? = null,
) {
    init {
        require((licensesTitle == null) == (licensesSupporting == null)) {
            "License title and supporting text must be provided together"
        }
    }
}

data class SillageSettingsAboutValue(
    val label: String,
    val value: String,
)

@Composable
fun SillageSettingsAboutSection(
    strings: SillageSettingsAboutStrings,
    values: List<SillageSettingsAboutValue>,
    licensesIcon: ImageVector? = null,
    onOpenLicenses: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val presentation = sillageSettingsAboutPresentation(strings = strings, values = values)

    SillageSettingsSectionCard(
        title = presentation.sectionTitle,
        modifier = modifier,
    ) {
        presentation.values.forEachIndexed { index, value ->
            SillageSettingsInfoRow(
                label = value.label,
                value = value.value,
                showDivider = index > 0,
            )
        }
        val licensesTitle = presentation.licensesTitle
        val licensesSupporting = presentation.licensesSupporting
        if (
            licensesTitle != null &&
            licensesSupporting != null &&
            licensesIcon != null &&
            onOpenLicenses != null
        ) {
            SillageSettingsActionRow(
                icon = licensesIcon,
                title = licensesTitle,
                supporting = licensesSupporting,
                onClick = onOpenLicenses,
                showDivider = presentation.values.isNotEmpty(),
            )
        }
    }
}

internal data class SillageSettingsAboutPresentation(
    val sectionTitle: String,
    val values: List<SillageSettingsAboutValue>,
    val licensesTitle: String?,
    val licensesSupporting: String?,
)

internal fun sillageSettingsAboutPresentation(
    strings: SillageSettingsAboutStrings,
    values: List<SillageSettingsAboutValue>,
) = SillageSettingsAboutPresentation(
    sectionTitle = strings.sectionTitle,
    values = values.toList(),
    licensesTitle = strings.licensesTitle,
    licensesSupporting = strings.licensesSupporting,
)
