package app.sillage.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sillage.R
import app.sillage.ui.SillageUiState
import app.sillage.ui.SillageViewModel

@Composable
internal fun ModeSelectionScreen(state: SillageUiState, viewModel: SillageViewModel) {
    AuthScaffold(
        title = stringResource(R.string.mode_title),
        supporting = stringResource(R.string.mode_supporting),
        state = state,
        onLanguageToggle = viewModel::toggleLanguageMode,
    ) {
        SillageModeOptionCard(
            icon = Icons.Rounded.OfflineBolt,
            trailingIcon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            title = stringResource(R.string.mode_offline),
            supporting = stringResource(R.string.mode_offline_supporting),
            iconContainer = MaterialTheme.colorScheme.secondaryContainer,
            iconContent = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = viewModel::useOfflineMode,
            enabled = !state.loading,
        )
        SillageModeOptionCard(
            icon = Icons.Rounded.CloudSync,
            trailingIcon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            title = stringResource(R.string.mode_online),
            supporting = stringResource(R.string.mode_online_supporting),
            iconContainer = MaterialTheme.colorScheme.primaryContainer,
            iconContent = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = viewModel::chooseOnlineMode,
            enabled = !state.loading,
        )
    }
}

@Composable
internal fun ServerScreen(state: SillageUiState, viewModel: SillageViewModel) {
    BackHandler(enabled = !state.loading, onBack = viewModel::cancelServerConnection)
    AuthScaffold(
        title = stringResource(R.string.server_title),
        supporting = stringResource(R.string.server_supporting),
        state = state,
        onLanguageToggle = viewModel::toggleLanguageMode,
        trailing = {
            TextButton(onClick = viewModel::cancelServerConnection, enabled = !state.loading) {
                Text(stringResource(if (state.serverReturnScreen != null) R.string.action_back else R.string.action_cancel))
            }
        },
    ) {
        SillageServerForm(
            baseUrl = state.baseUrl,
            loading = state.loading,
            strings = SillageServerFormStrings(
                addressLabel = stringResource(R.string.server_address),
                addressPlaceholder = stringResource(R.string.server_address_placeholder),
                submit = stringResource(R.string.server_save_connect),
                submitting = stringResource(R.string.server_connecting),
                useOffline = stringResource(R.string.server_use_offline),
            ),
            connectIcon = Icons.Rounded.CloudSync,
            offlineIcon = Icons.Rounded.OfflineBolt,
            onBaseUrlChange = viewModel::updateBaseUrl,
            onSubmit = viewModel::saveServer,
            onUseOffline = viewModel::useOfflineMode,
        )
    }
}

@Composable
internal fun InitializeScreen(state: SillageUiState, viewModel: SillageViewModel) {
    AuthScaffold(
        title = stringResource(R.string.initialize_title),
        supporting = stringResource(R.string.initialize_supporting),
        state = state,
        onLanguageToggle = viewModel::toggleLanguageMode,
        trailing = {
            TextButton(onClick = viewModel::openServerSettings, enabled = !state.loading) {
                Text(stringResource(R.string.server_label))
            }
        },
    ) {
        SillageInitializeForm(
            state = state.auth,
            loading = state.loading,
            strings = SillageInitializeFormStrings(
                usernameLabel = stringResource(R.string.account_username),
                displayNameLabel = stringResource(R.string.account_display_name),
                password = SillagePasswordFieldStrings(
                    label = stringResource(R.string.account_password),
                    showPassword = stringResource(R.string.account_show_password),
                    hidePassword = stringResource(R.string.account_hide_password),
                ),
                submit = stringResource(R.string.account_create_enter),
                submitting = stringResource(R.string.account_creating),
            ),
            showPasswordIcon = Icons.Rounded.Visibility,
            hidePasswordIcon = Icons.Rounded.VisibilityOff,
            onUsernameChange = viewModel::updateUsername,
            onDisplayNameChange = viewModel::updateDisplayName,
            onPasswordChange = viewModel::updatePassword,
            onSubmit = viewModel::initialize,
        )
    }
}

@Composable
internal fun LoginScreen(state: SillageUiState, viewModel: SillageViewModel) {
    AuthScaffold(
        title = stringResource(R.string.login_title),
        supporting = stringResource(R.string.login_supporting),
        state = state,
        onLanguageToggle = viewModel::toggleLanguageMode,
        trailing = {
            TextButton(onClick = viewModel::openServerSettings, enabled = !state.loading) {
                Text(stringResource(R.string.server_label))
            }
        },
    ) {
        SillageLoginForm(
            state = state.auth,
            loading = state.loading,
            strings = SillageLoginFormStrings(
                usernameLabel = stringResource(R.string.account_username),
                password = SillagePasswordFieldStrings(
                    label = stringResource(R.string.account_password),
                    showPassword = stringResource(R.string.account_show_password),
                    hidePassword = stringResource(R.string.account_hide_password),
                ),
                submit = stringResource(R.string.login_action),
                submitting = stringResource(R.string.login_signing_in),
            ),
            showPasswordIcon = Icons.Rounded.Visibility,
            hidePasswordIcon = Icons.Rounded.VisibilityOff,
            onUsernameChange = viewModel::updateUsername,
            onPasswordChange = viewModel::updatePassword,
            onSubmit = viewModel::signIn,
        )
    }
}

@Composable
private fun AuthScaffold(
    title: String,
    supporting: String,
    state: SillageUiState,
    onLanguageToggle: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    SillageAuthScaffold(
        title = title,
        supporting = supporting,
        errorMessage = state.authError,
        errorIcon = Icons.Rounded.ErrorOutline,
        trailing = trailing,
        header = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = colorResource(R.color.ic_launcher_background),
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.brand_tagline),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onLanguageToggle, enabled = !state.loading) {
                    Icon(
                        Icons.Rounded.Language,
                        contentDescription = stringResource(
                            if (state.languageMode == app.sillage.data.SessionStore.LANGUAGE_ZH_CN) {
                                R.string.language_switch_to_english
                            } else {
                                R.string.language_switch_to_chinese
                            },
                        ),
                    )
                }
            }
        },
        content = content,
    )
}
