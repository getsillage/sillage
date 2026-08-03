package app.sillage.ui.application

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.sillage.core.application.preferences.ClientPreferenceValues
import app.sillage.features.auth.InstanceAuthenticationFailure
import app.sillage.features.auth.InstanceAuthenticationOperation
import app.sillage.ui.auth.SillageInitializeForm
import app.sillage.ui.auth.SillageInitializeFormStrings
import app.sillage.ui.auth.SillageLoginForm
import app.sillage.ui.auth.SillageLoginFormStrings
import app.sillage.ui.auth.SillagePasswordFieldStrings
import app.sillage.ui.auth.SillageServerForm
import app.sillage.ui.auth.SillageServerFormStrings
import app.sillage.ui.designsystem.SillageInlineError
import app.sillage.ui.designsystem.SillageSettingsActionRow
import app.sillage.ui.designsystem.SillageSettingsInfoRow
import app.sillage.ui.designsystem.SillageSettingsSectionCard
import app.sillage.ui.designsystem.SillageSettingsSwitchRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SillageNativeSettings(
    state: SillageNativeState,
    platform: SillageNativePlatform,
    strings: SillageNativeStrings,
    onDarkThemeChange: (Boolean) -> Unit,
    onLanguageChange: (String) -> Unit,
    onServerBaseUrlChange: (String) -> Unit,
    onCheckServer: () -> Unit,
    onAuthenticationUsernameChange: (String) -> Unit,
    onAuthenticationDisplayNameChange: (String) -> Unit,
    onAuthenticationPasswordChange: (String) -> Unit,
    onAuthenticate: () -> Unit,
    onSyncMemos: () -> Unit,
    onSignOut: () -> Unit,
    onExportBackup: (() -> Unit)?,
    onRestoreBackup: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var restoreConfirmationVisible by remember { mutableStateOf(false) }

    if (restoreConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { restoreConfirmationVisible = false },
            title = { Text(strings.restoreBackupTitle) },
            text = { Text(strings.restoreBackupWarning) },
            confirmButton = {
                TextButton(
                    onClick = {
                        restoreConfirmationVisible = false
                        onRestoreBackup?.invoke()
                    },
                ) {
                    Text(strings.restoreBackup)
                }
            },
            dismissButton = {
                TextButton(onClick = { restoreConfirmationVisible = false }) {
                    Text(strings.cancel)
                }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(strings.settings) })
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                item {
                    SillageSettingsSectionCard(strings.appearance) {
                        SillageSettingsSwitchRow(
                            icon = Icons.Outlined.NightsStay,
                            title = strings.darkTheme,
                            supporting = strings.darkThemeSupporting,
                            checked = state.appearance.themeMode == ClientPreferenceValues.THEME_DARK,
                            enabled = state.storageAvailable,
                            onCheckedChange = onDarkThemeChange,
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(strings.language)
                            FilterChip(
                                selected = state.appearance.languageMode ==
                                    ClientPreferenceValues.LANGUAGE_ZH_CN,
                                onClick = {
                                    onLanguageChange(ClientPreferenceValues.LANGUAGE_ZH_CN)
                                },
                                enabled = state.storageAvailable,
                                label = { Text(strings.simplifiedChinese) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            FilterChip(
                                selected = state.appearance.languageMode ==
                                    ClientPreferenceValues.LANGUAGE_EN,
                                onClick = { onLanguageChange(ClientPreferenceValues.LANGUAGE_EN) },
                                enabled = state.storageAvailable,
                                label = { Text(strings.english) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                item {
                    SillageSettingsSectionCard(strings.data) {
                        val openDataLocation = platform.openDataLocation
                        if (openDataLocation == null) {
                            SillageSettingsInfoRow(
                                label = strings.dataLocation,
                                value = platform.dataLocation,
                            )
                        } else {
                            SillageSettingsActionRow(
                                icon = Icons.Outlined.FolderOpen,
                                title = strings.openDataLocation,
                                supporting = platform.dataLocation,
                                onClick = { openDataLocation() },
                                enabled = !state.busy,
                            )
                        }
                        onExportBackup?.let { exportBackup ->
                            SillageSettingsActionRow(
                                icon = Icons.Outlined.UploadFile,
                                title = strings.exportBackup,
                                supporting = strings.exportBackupSupporting,
                                onClick = exportBackup,
                                enabled = state.storageAvailable && !state.busy,
                                showDivider = true,
                            )
                        }
                        onRestoreBackup?.let {
                            SillageSettingsActionRow(
                                icon = Icons.Outlined.FileDownload,
                                title = strings.restoreBackup,
                                supporting = strings.restoreBackupSupporting,
                                onClick = { restoreConfirmationVisible = true },
                                enabled = !state.busy,
                                showDivider = true,
                            )
                        }
                    }
                }
            item {
                SillageSettingsSectionCard(strings.service) {
                    val connection = state.serverConnection
                        SillageServerForm(
                            baseUrl = connection.baseUrl,
                            loading = connection.checking,
                            enabled = state.authentication.account == null &&
                                !state.authentication.loading,
                        strings = SillageServerFormStrings(
                            addressLabel = strings.serverAddress,
                            addressPlaceholder = strings.serverAddressPlaceholder,
                            submit = strings.checkServer,
                            submitting = strings.checkingServer,
                            useOffline = strings.offlineModeValue,
                        ),
                        connectIcon = Icons.Outlined.CloudSync,
                        onBaseUrlChange = onServerBaseUrlChange,
                        onSubmit = onCheckServer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                    if (connection.failed) {
                        SillageInlineError(
                            message = strings.serverConnectionFailed,
                            icon = Icons.Outlined.ErrorOutline,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                        )
                    }
                    connection.bootstrap?.let { bootstrap ->
                        SillageSettingsInfoRow(
                            label = strings.serverConnection,
                            value = strings.serverConnectionAvailable,
                            modifier = Modifier.semantics {
                                liveRegion = LiveRegionMode.Polite
                            },
                            showDivider = true,
                        )
                        SillageSettingsInfoRow(
                            label = strings.serverInitialization,
                            value = if (bootstrap.initialized) {
                                strings.serverInitialized
                            } else {
                                strings.serverNeedsInitialization
                            },
                            showDivider = true,
                        )
                        SillageSettingsInfoRow(
                            label = strings.serverVersion,
                            value = bootstrap.serverVersion.ifBlank { strings.unknownValue },
                            showDivider = true,
                        )
                            SillageSettingsInfoRow(
                                label = strings.apiVersion,
                                value = bootstrap.apiVersion.ifBlank { strings.unknownValue },
                                showDivider = true,
                        )
                        val authentication = state.authentication
                        val account = authentication.account
                        authentication.failure?.let { failure ->
                            SillageInlineError(
                                message = strings.authenticationFailure(failure),
                                icon = Icons.Outlined.ErrorOutline,
                                modifier = Modifier.padding(
                                    horizontal = 14.dp,
                                    vertical = 4.dp,
                                ),
                            )
                        }
                        if (account == null) {
                                SillageSettingsInfoRow(
                                    label = strings.accountSection,
                                    value = if (bootstrap.initialized) {
                                        strings.signInSupporting
                                    } else {
                                        strings.initializeAccountSupporting
                                    },
                                    showDivider = true,
                                )
                            val passwordStrings = SillagePasswordFieldStrings(
                                    label = strings.password,
                                    showPassword = strings.showPassword,
                                    hidePassword = strings.hidePassword,
                                )
                                if (bootstrap.initialized) {
                                    SillageLoginForm(
                                        state = authentication.form,
                                        loading = authentication.loading,
                                        strings = SillageLoginFormStrings(
                                            usernameLabel = strings.username,
                                            password = passwordStrings,
                                            submit = strings.signIn,
                                            submitting = strings.signingIn,
                                        ),
                                        showPasswordIcon = Icons.Outlined.Visibility,
                                        hidePasswordIcon = Icons.Outlined.VisibilityOff,
                                        onUsernameChange = onAuthenticationUsernameChange,
                                        onPasswordChange = onAuthenticationPasswordChange,
                                        onSubmit = onAuthenticate,
                                        modifier = Modifier.padding(
                                            horizontal = 14.dp,
                                            vertical = 12.dp,
                                        ),
                                    )
                                } else {
                                    SillageInitializeForm(
                                        state = authentication.form,
                                        loading = authentication.loading,
                                        strings = SillageInitializeFormStrings(
                                            usernameLabel = strings.username,
                                            displayNameLabel = strings.displayName,
                                            password = passwordStrings,
                                            submit = strings.initializeAccount,
                                            submitting = strings.initializingAccount,
                                        ),
                                        showPasswordIcon = Icons.Outlined.Visibility,
                                        hidePasswordIcon = Icons.Outlined.VisibilityOff,
                                        onUsernameChange = onAuthenticationUsernameChange,
                                        onDisplayNameChange = onAuthenticationDisplayNameChange,
                                        onPasswordChange = onAuthenticationPasswordChange,
                                        onSubmit = onAuthenticate,
                                        modifier = Modifier.padding(
                                            horizontal = 14.dp,
                                            vertical = 12.dp,
                                        ),
                                    )
                                }
                            } else {
                            SillageSettingsInfoRow(
                                label = strings.authenticatedAccount,
                                    value = account.displayName
                                        .takeIf(String::isNotBlank)
                                        ?.let { "$it (@${account.username})" }
                                        ?: account.username,
                                    modifier = Modifier.semantics {
                                        liveRegion = LiveRegionMode.Polite
                                    },
                                showDivider = true,
                            )
                            if (state.memoSyncSupported) {
                                SillageSettingsActionRow(
                                    icon = Icons.Outlined.CloudSync,
                                    title = strings.syncMemos,
                                    supporting = strings.syncMemosSupporting,
                    onClick = onSyncMemos,
                                    enabled = state.storageAvailable &&
                                        !state.busy &&
                                        connection.checkedBaseUrl != null &&
                                        !authentication.loading,
                                    showDivider = true,
                                )
                            }
                            SillageSettingsInfoRow(
                                label = strings.sessionPersistence,
                                value = if (platform.authenticationPersistsAcrossLaunches) {
                                    strings.sessionDeviceProtected
                                } else {
                                    strings.sessionMemoryOnly
                                },
                                showDivider = true,
                            )
                                SillageSettingsActionRow(
                                    icon = Icons.Outlined.Logout,
                                    title = if (
                                        authentication.operation ==
                                        InstanceAuthenticationOperation.SignOut
                                    ) {
                                        strings.signingOut
                                    } else {
                                        strings.signOut
                                    },
                                supporting = if (platform.authenticationPersistsAcrossLaunches) {
                                    strings.sessionDeviceProtected
                                } else {
                                    strings.sessionMemoryOnly
                                },
                                    onClick = onSignOut,
                                    enabled = !authentication.loading,
                                    showDivider = true,
                                )
                            }
                        }
                    SillageSettingsInfoRow(
                        label = strings.mode,
                        value = strings.offlineModeValue,
                        showDivider = true,
                    )
                        SillageSettingsInfoRow(
                            label = strings.platform,
                            value = platform.name,
                            showDivider = true,
                        )
                        SillageSettingsInfoRow(
                            label = strings.version,
                            value = platform.version,
                            showDivider = true,
                        )
                    }
                }
            }
        }
    }
}

private fun SillageNativeStrings.authenticationFailure(
    failure: InstanceAuthenticationFailure,
): String = when (failure) {
    InstanceAuthenticationFailure.RequiredFields -> authRequiredFields
    InstanceAuthenticationFailure.InvalidRequest -> authInvalidRequest
    InstanceAuthenticationFailure.InvalidCredentials -> authInvalidCredentials
    InstanceAuthenticationFailure.AlreadyInitialized -> authAlreadyInitialized
    InstanceAuthenticationFailure.RateLimited -> authRateLimited
    InstanceAuthenticationFailure.SessionExpired -> authSessionExpired
    InstanceAuthenticationFailure.ServerRejected -> authServerRejected
    InstanceAuthenticationFailure.InvalidResponse -> authInvalidResponse
    InstanceAuthenticationFailure.SecureStorageUnavailable -> authSecureStorageUnavailable
    InstanceAuthenticationFailure.Connection -> authConnectionFailed
}
