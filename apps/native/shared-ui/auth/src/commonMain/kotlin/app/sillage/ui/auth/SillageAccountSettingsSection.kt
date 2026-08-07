package app.sillage.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import app.sillage.features.auth.AuthFeatureStateHolder
import app.sillage.ui.designsystem.SillageSettingsSectionCard

data class SillageAccountSettingsSectionStrings(
    val sectionTitle: String,
    val content: SillageAccountSettingsStrings,
)

@Composable
fun SillageAccountSettingsSection(
    state: AuthFeatureStateHolder,
    mutationBlocked: Boolean,
    strings: SillageAccountSettingsSectionStrings,
    signOutSupporting: String,
    signOutIcon: ImageVector,
    errorMessage: String? = null,
    onCurrentPasswordChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSavePassword: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = sillageAccountSettingsSectionPresentation(strings)

    SillageSettingsSectionCard(
        title = presentation.sectionTitle,
        modifier = modifier,
    ) {
        SillageAccountSettingsContent(
            state = state,
            mutationBlocked = mutationBlocked,
            strings = presentation.content,
            signOutSupporting = signOutSupporting,
            signOutIcon = signOutIcon,
            errorMessage = errorMessage,
            onCurrentPasswordChange = onCurrentPasswordChange,
            onNewPasswordChange = onNewPasswordChange,
            onConfirmPasswordChange = onConfirmPasswordChange,
            onSavePassword = onSavePassword,
            onSignOut = onSignOut,
        )
    }
}

internal data class SillageAccountSettingsSectionPresentation(
    val sectionTitle: String,
    val content: SillageAccountSettingsStrings,
)

internal fun sillageAccountSettingsSectionPresentation(
    strings: SillageAccountSettingsSectionStrings,
) = SillageAccountSettingsSectionPresentation(
    sectionTitle = strings.sectionTitle,
    content = strings.content,
)
