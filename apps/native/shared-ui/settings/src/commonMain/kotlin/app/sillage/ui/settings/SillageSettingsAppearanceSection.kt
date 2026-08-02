package app.sillage.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.sillage.ui.designsystem.SillageSettingsSectionCard
import app.sillage.ui.designsystem.SillageSettingsSwitchRow

data class SillageSettingsAppearanceStrings(
    val sectionTitle: String,
    val darkModeTitle: String,
    val darkModeOn: String,
    val darkModeOff: String,
    val language: SillageSettingsLanguageStrings,
)

@Composable
fun SillageSettingsAppearanceSection(
    darkMode: Boolean,
    selectedLanguage: String,
    languageOptions: List<SillageSettingsLanguageOption>,
    strings: SillageSettingsAppearanceStrings,
    darkModeIcon: ImageVector,
    languageIcon: ImageVector,
    enabled: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    onLanguageChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = sillageSettingsAppearancePresentation(
        darkMode = darkMode,
        strings = strings,
        enabled = enabled,
    )

    SillageSettingsSectionCard(
        title = presentation.sectionTitle,
        modifier = modifier,
    ) {
        SillageSettingsSwitchRow(
            icon = darkModeIcon,
            title = presentation.darkModeTitle,
            supporting = presentation.darkModeSupporting,
            checked = presentation.darkMode,
            enabled = presentation.enabled,
            onCheckedChange = onDarkModeChange,
        )
        HorizontalDivider(
            modifier = Modifier.padding(start = 50.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        SillageSettingsLanguageRow(
            selectedLanguage = selectedLanguage,
            options = languageOptions,
            strings = strings.language,
            icon = languageIcon,
            enabled = presentation.enabled,
            onLanguageChange = onLanguageChange,
        )
    }
}

internal data class SillageSettingsAppearancePresentation(
    val sectionTitle: String,
    val darkModeTitle: String,
    val darkModeSupporting: String,
    val darkMode: Boolean,
    val enabled: Boolean,
)

internal fun sillageSettingsAppearancePresentation(
    darkMode: Boolean,
    strings: SillageSettingsAppearanceStrings,
    enabled: Boolean,
) = SillageSettingsAppearancePresentation(
    sectionTitle = strings.sectionTitle,
    darkModeTitle = strings.darkModeTitle,
    darkModeSupporting = if (darkMode) strings.darkModeOn else strings.darkModeOff,
    darkMode = darkMode,
    enabled = enabled,
)
