package app.sillage.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import app.sillage.features.settings.SettingsFeatureStateHolder
import app.sillage.ui.designsystem.SillageSettingsSectionCard
import app.sillage.ui.designsystem.SillageSettingsSwitchRow

data class SillageAIAutoSummaryStrings(
    val sectionTitle: String,
    val title: String,
    val supporting: String,
)

@Composable
fun SillageAIAutoSummarySection(
    state: SettingsFeatureStateHolder,
    strings: SillageAIAutoSummaryStrings,
    icon: ImageVector,
    operationBlocked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = sillageAIAutoSummaryPresentation(
        state = state,
        strings = strings,
        operationBlocked = operationBlocked,
    )

    SillageSettingsSectionCard(
        title = presentation.sectionTitle,
        modifier = modifier,
    ) {
        SillageSettingsSwitchRow(
            icon = icon,
            title = presentation.title,
            supporting = presentation.supporting,
            checked = presentation.checked,
            enabled = presentation.enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

internal data class SillageAIAutoSummaryPresentation(
    val sectionTitle: String,
    val title: String,
    val supporting: String,
    val checked: Boolean,
    val enabled: Boolean,
)

internal fun sillageAIAutoSummaryPresentation(
    state: SettingsFeatureStateHolder,
    strings: SillageAIAutoSummaryStrings,
    operationBlocked: Boolean,
) = SillageAIAutoSummaryPresentation(
    sectionTitle = strings.sectionTitle,
    title = strings.title,
    supporting = strings.supporting,
    checked = state.autoSummaryEnabled,
    enabled = !operationBlocked && !state.autoSummarySaving && !state.profilesSaving,
)
