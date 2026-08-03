package app.sillage.ui.application

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import app.sillage.ui.auth.SillageAccountSettingsSection
import app.sillage.ui.auth.SillageAccountSettingsSectionStrings
import app.sillage.ui.auth.SillageAccountSettingsStrings
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
import app.sillage.ui.settings.SillageSettingsAboutSection
import app.sillage.ui.settings.SillageSettingsAboutStrings
import app.sillage.ui.settings.SillageSettingsAboutValue
import app.sillage.ui.settings.SillageSettingsAppearanceSection
import app.sillage.ui.settings.SillageSettingsAppearanceStrings
import app.sillage.ui.settings.SillageSettingsDataSection
import app.sillage.ui.settings.SillageSettingsDataStrings
import app.sillage.ui.settings.SillageSettingsLanguageOption
import app.sillage.ui.settings.SillageSettingsLanguageStrings
import app.sillage.ui.settings.SillageSettingsLicensesDialog
import app.sillage.ui.settings.SillageSettingsLicensesDialogStrings

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
    onAuthenticationCurrentPasswordChange: (String) -> Unit,
    onAuthenticationNewPasswordChange: (String) -> Unit,
    onAuthenticationConfirmPasswordChange: (String) -> Unit,
    onAuthenticate: () -> Unit,
    onChangePassword: () -> Unit,
    onSyncMemos: () -> Unit,
    onSignOut: () -> Unit,
    onExportBackup: (() -> Unit)?,
    onRestoreBackup: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val presentation = sillageNativeSettingsPresentation(strings = strings, platform = platform)
    val thirdPartyNotices = platform.thirdPartyNotices?.takeIf(String::isNotBlank)
    var restoreConfirmationVisible by remember { mutableStateOf(false) }
    var licensesVisible by remember { mutableStateOf(false) }

    if (licensesVisible && thirdPartyNotices != null) {
        SillageSettingsLicensesDialog(
            notices = thirdPartyNotices,
            strings = SillageSettingsLicensesDialogStrings(
                title = strings.openSourceLicenses,
                close = strings.close,
            ),
            onDismiss = { licensesVisible = false },
        )
    }

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
                    SillageSettingsAppearanceSection(
                        darkMode = state.appearance.themeMode == ClientPreferenceValues.THEME_DARK,
                        selectedLanguage = state.appearance.languageMode,
                        languageOptions = presentation.languageOptions,
                        strings = presentation.appearanceStrings,
                        darkModeIcon = Icons.Outlined.NightsStay,
                        languageIcon = Icons.Outlined.Language,
                        enabled = state.storageAvailable,
                        onDarkModeChange = onDarkThemeChange,
                        onLanguageChange = onLanguageChange,
                    )
                }
                item {
                    SillageSettingsDataSection(
                        strings = presentation.dataStrings,
                        exportIcon = Icons.Outlined.UploadFile,
                        importIcon = Icons.Outlined.FileDownload,
                        enabled = !state.busy,
                        exportEnabled = state.storageAvailable,
                        onExport = onExportBackup,
                        onImport = onRestoreBackup?.let {
                            { restoreConfirmationVisible = true }
                        },
                        leadingContent = {
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
                        },
                    )
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
                        if (account == null) {
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
                        }
                        }
                    }
                }
                item {
                    SillageSettingsAboutSection(
                        strings = presentation.aboutStrings,
                        values = presentation.aboutValues,
                        licensesIcon = Icons.Outlined.Info,
                        onOpenLicenses = thirdPartyNotices?.let {
                            { licensesVisible = true }
                        },
                    )
                }
                state.authentication.account?.let { account ->
                    item {
                        SillageAccountSettingsSection(
                            state = state.authentication.form,
                            mutationBlocked = state.busy || state.authentication.loading,
                            strings = SillageAccountSettingsSectionStrings(
                                sectionTitle = strings.accountSection,
                                content = SillageAccountSettingsStrings(
                                    changePasswordTitle = strings.changePassword,
                                    changePasswordSupporting = strings.changePasswordSupporting,
                                    currentPasswordLabel = strings.currentPassword,
                                    newPasswordLabel = strings.newPassword,
                                    confirmPasswordLabel = strings.confirmPassword,
                                    savePassword = strings.savePassword,
                                    signOut = if (
                                        state.authentication.operation ==
                                        InstanceAuthenticationOperation.SignOut
                                    ) {
                                        strings.signingOut
                                    } else {
                                        strings.signOut
                                    },
                                ),
                            ),
                            signOutSupporting = account.displayName
                                .takeIf(String::isNotBlank)
                                ?.let { "$it (@${account.username})" }
                                ?: account.username,
                            signOutIcon = Icons.AutoMirrored.Outlined.Logout,
                            errorMessage = state.authentication.failure?.let { failure ->
                                strings.authenticationFailure(failure, passwordChange = true)
                            },
                            onCurrentPasswordChange = onAuthenticationCurrentPasswordChange,
                            onNewPasswordChange = onAuthenticationNewPasswordChange,
                            onConfirmPasswordChange = onAuthenticationConfirmPasswordChange,
                            onSavePassword = onChangePassword,
                            onSignOut = onSignOut,
                        )
                    }
                }
            }
        }
    }
}

internal data class SillageNativeSettingsPresentation(
    val appearanceStrings: SillageSettingsAppearanceStrings,
    val languageOptions: List<SillageSettingsLanguageOption>,
    val dataStrings: SillageSettingsDataStrings,
    val aboutStrings: SillageSettingsAboutStrings,
    val aboutValues: List<SillageSettingsAboutValue>,
)

internal fun sillageNativeSettingsPresentation(
    strings: SillageNativeStrings,
    platform: SillageNativePlatform,
) = SillageNativeSettingsPresentation(
    appearanceStrings = SillageSettingsAppearanceStrings(
        sectionTitle = strings.appearance,
        darkModeTitle = strings.darkTheme,
        darkModeOn = strings.darkThemeSupporting,
        darkModeOff = strings.darkThemeSupporting,
        language = SillageSettingsLanguageStrings(
            title = strings.language,
            supporting = strings.languageSupporting,
        ),
    ),
    languageOptions = listOf(
        SillageSettingsLanguageOption(
            value = ClientPreferenceValues.LANGUAGE_ZH_CN,
            label = strings.simplifiedChinese,
        ),
        SillageSettingsLanguageOption(
            value = ClientPreferenceValues.LANGUAGE_EN,
            label = strings.english,
        ),
    ),
    dataStrings = SillageSettingsDataStrings(
        sectionTitle = strings.data,
        exportTitle = strings.exportBackup,
        exportSupporting = strings.exportBackupSupporting,
        importTitle = strings.restoreBackup,
        importSupporting = strings.restoreBackupSupporting,
    ),
    aboutStrings = SillageSettingsAboutStrings(
        sectionTitle = strings.about,
        licensesTitle = platform.thirdPartyNotices
            ?.takeIf(String::isNotBlank)
            ?.let { strings.openSourceLicenses },
        licensesSupporting = platform.thirdPartyNotices
            ?.takeIf(String::isNotBlank)
            ?.let { strings.openSourceLicensesSupporting },
    ),
    aboutValues = listOf(
        SillageSettingsAboutValue(label = strings.mode, value = strings.offlineModeValue),
        SillageSettingsAboutValue(label = strings.platform, value = platform.name),
        SillageSettingsAboutValue(label = strings.version, value = platform.version),
    ),
)

private fun SillageNativeStrings.authenticationFailure(
    failure: InstanceAuthenticationFailure,
    passwordChange: Boolean = false,
): String = when (failure) {
    InstanceAuthenticationFailure.RequiredFields -> {
        if (passwordChange) passwordChangeRequiredFields else authRequiredFields
    }
    InstanceAuthenticationFailure.PasswordConfirmationMismatch -> passwordChangeConfirmationMismatch
    InstanceAuthenticationFailure.PasswordUnchanged -> passwordChangeUnchanged
    InstanceAuthenticationFailure.InvalidRequest -> authInvalidRequest
    InstanceAuthenticationFailure.InvalidCredentials -> {
        if (passwordChange) passwordChangeInvalidCurrent else authInvalidCredentials
    }
    InstanceAuthenticationFailure.AlreadyInitialized -> authAlreadyInitialized
    InstanceAuthenticationFailure.RateLimited -> authRateLimited
    InstanceAuthenticationFailure.SessionExpired -> authSessionExpired
    InstanceAuthenticationFailure.ServerRejected -> authServerRejected
    InstanceAuthenticationFailure.InvalidResponse -> authInvalidResponse
    InstanceAuthenticationFailure.SecureStorageUnavailable -> authSecureStorageUnavailable
    InstanceAuthenticationFailure.Connection -> authConnectionFailed
}
