package app.sillage.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.sillage.BuildConfig
import app.sillage.R
import app.sillage.data.SessionStore
import app.sillage.ui.SillageUiState
import app.sillage.ui.SillageViewModel
import app.sillage.ui.auth.SillageAccountSettingsStrings
import app.sillage.ui.auth.SillageAccountSettingsSection
import app.sillage.ui.auth.SillageAccountSettingsSectionStrings
import app.sillage.ui.designsystem.applySillageHeadingSemantics
import app.sillage.ui.hasClientContextOperationInProgress
import app.sillage.ui.navigation.MainNavigationBar
import app.sillage.ui.settings.SillageAIAutoSummarySection
import app.sillage.ui.settings.SillageAIAutoSummaryStrings
import app.sillage.ui.settings.SillageAIProfileDetailStrings
import app.sillage.ui.settings.SillageAIProfileSummaryStrings
import app.sillage.ui.settings.SillageAIProfilesEditorStrings
import app.sillage.ui.settings.SillageAIProfilesHeaderStrings
import app.sillage.ui.settings.SillageSettingsAboutSection
import app.sillage.ui.settings.SillageSettingsAboutStrings
import app.sillage.ui.settings.SillageSettingsAboutValue
import app.sillage.ui.settings.SillageSettingsAppearanceSection
import app.sillage.ui.settings.SillageSettingsAppearanceStrings
import app.sillage.ui.settings.SillageSettingsDataSection
import app.sillage.ui.settings.SillageSettingsDataStrings
import app.sillage.ui.settings.SillageSettingsLanguageOption
import app.sillage.ui.settings.SillageSettingsLanguageStrings
import app.sillage.ui.settings.SillageSettingsList
import app.sillage.ui.settings.SillageSettingsOverviewCard
import app.sillage.ui.settings.SillageSettingsOverviewItem
import app.sillage.ui.settings.SillageSettingsServiceSyncIcons
import app.sillage.ui.settings.SillageSettingsServiceSyncSection
import app.sillage.ui.settings.SillageSettingsServiceSyncStrings
import app.sillage.ui.settings.rememberSillageAIProfilesEditorState
import app.sillage.ui.settings.sillageAIProfilesEditorItems

internal const val SETTINGS_SCREEN_TEST_TAG = "settings-screen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AISettingsScreen(state: SillageUiState, viewModel: SillageViewModel) {
    var showOpenSourceLicenses by remember { mutableStateOf(false) }
    val aiProfilesEditorState = rememberSillageAIProfilesEditorState()
    val aiProfileOperationInProgress = state.aiSettingsSaving ||
        state.aiTestingProfileId.isNotBlank() ||
        state.aiLoadingModelsProfileId.isNotBlank() ||
        state.loading
    val aiProfileMutationBlocked = aiProfileOperationInProgress || state.aiAutoSummarySaving
    val clientContextChangeBlocked = state.hasClientContextOperationInProgress()
    val aiProfilesEditorStrings = SillageAIProfilesEditorStrings(
        header = SillageAIProfilesHeaderStrings(
            title = stringResource(R.string.settings_ai_profiles),
            supporting = stringResource(R.string.settings_ai_profiles_supporting),
            newProfile = stringResource(R.string.action_new),
            saving = stringResource(R.string.action_saving),
            save = stringResource(R.string.action_save),
        ),
        empty = stringResource(R.string.settings_no_ai_profiles),
        summary = SillageAIProfileSummaryStrings(
            unnamedProfile = stringResource(R.string.settings_profile_unnamed),
            anthropicCompatible = stringResource(
                R.string.settings_provider_anthropic_compatible,
            ),
            openAICompatible = stringResource(R.string.settings_provider_openai_compatible),
            defaultProfile = stringResource(R.string.settings_default),
            modelUnset = stringResource(R.string.settings_model_unset),
            keyPresent = stringResource(R.string.settings_key_present),
            keyMissing = stringResource(R.string.settings_key_missing),
            keyError = stringResource(R.string.settings_key_error),
            configure = stringResource(R.string.action_configure),
            currentDefault = stringResource(R.string.settings_default_current),
            setDefault = stringResource(R.string.settings_set_default),
        ),
        detail = SillageAIProfileDetailStrings(
            title = stringResource(R.string.settings_profile_details),
            supporting = stringResource(R.string.settings_profile_details_supporting),
            collapse = stringResource(R.string.action_collapse),
            nameLabel = stringResource(R.string.settings_profile_name),
            providerLabel = stringResource(R.string.settings_provider),
            anthropicCompatible = stringResource(
                R.string.settings_provider_anthropic_compatible,
            ),
            openAICompatible = stringResource(R.string.settings_provider_openai_compatible),
            baseUrlLabel = stringResource(R.string.settings_base_url),
            modelLabel = stringResource(R.string.settings_model),
            modelsLoading = stringResource(R.string.settings_models_loading),
            getModels = stringResource(R.string.settings_models_get),
            temperatureLabel = stringResource(R.string.settings_temperature),
            maxTokensLabel = stringResource(R.string.settings_max_tokens),
            apiKeyLabel = stringResource(R.string.settings_api_key),
            keepApiKey = stringResource(R.string.settings_key_keep),
            apiKeyNotConfigured = stringResource(R.string.settings_key_not_configured),
            keyDecryptError = stringResource(R.string.settings_key_decrypt_error),
            testing = stringResource(R.string.settings_test_testing),
            testConnection = stringResource(R.string.settings_test_connection),
            confirmDelete = stringResource(R.string.action_confirm_delete),
            delete = stringResource(R.string.action_delete),
        ),
    )
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            viewModel.exportFullData(uri)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.importFullData(uri)
        }
    }
    Scaffold(
        modifier = Modifier.testTag(SETTINGS_SCREEN_TEST_TAG),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_title),
                        modifier = Modifier.semantics { applySillageHeadingSemantics() },
                    )
                },
            )
        },
        bottomBar = {
            MainNavigationBar(state = state, viewModel = viewModel)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SillageSettingsList(
                loading = state.aiSettingsLoading,
                errorMessage = state.aiSettingsLoadError,
                retryLabel = stringResource(R.string.action_retry),
                retryIcon = Icons.Rounded.Refresh,
                onRetry = viewModel::loadAISettings,
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                val online = state.appMode == SessionStore.MODE_ONLINE
                SillageSettingsOverviewCard(
                    title = stringResource(R.string.settings_status_title),
                    items = listOf(
                        SillageSettingsOverviewItem(
                            label = stringResource(
                                if (online) R.string.status_online else R.string.status_offline,
                            ),
                            value = if (online) {
                                state.baseUrl.ifBlank {
                                    stringResource(R.string.settings_not_configured)
                                }
                            } else {
                                pluralStringResource(
                                    R.plurals.quantity_records,
                                    state.memos.size,
                                    state.memos.size,
                                )
                            },
                        ),
                        SillageSettingsOverviewItem(
                            label = stringResource(R.string.settings_theme_label),
                            value = stringResource(
                                if (state.themeMode == SessionStore.THEME_DARK) {
                                    R.string.settings_theme_dark
                                } else {
                                    R.string.settings_theme_light
                                },
                            ),
                        ),
                        SillageSettingsOverviewItem(
                            label = stringResource(R.string.settings_section_ai),
                            value = stringResource(
                                if (state.aiAutoSummary) {
                                    R.string.settings_auto_summary
                                } else {
                                    R.string.settings_summary_manual
                                },
                            ),
                        ),
                    ),
                )
            }
            item {
                SillageAIAutoSummarySection(
                    state = state.settings,
                    strings = SillageAIAutoSummaryStrings(
                        sectionTitle = stringResource(R.string.settings_section_ai),
                        title = stringResource(R.string.settings_auto_summary),
                        supporting = stringResource(R.string.settings_auto_summary_supporting),
                    ),
                    icon = Icons.Rounded.AutoAwesome,
                    operationBlocked = state.loading,
                    onCheckedChange = viewModel::setAISettingsAutoSummary,
                )
            }
            item {
                SillageSettingsAppearanceSection(
                    darkMode = state.themeMode == SessionStore.THEME_DARK,
                    selectedLanguage = state.languageMode,
                    languageOptions = listOf(
                        SillageSettingsLanguageOption(
                            value = SessionStore.LANGUAGE_ZH_CN,
                            label = stringResource(R.string.language_chinese),
                        ),
                        SillageSettingsLanguageOption(
                            value = SessionStore.LANGUAGE_EN,
                            label = stringResource(R.string.language_english),
                        ),
                    ),
                    strings = SillageSettingsAppearanceStrings(
                        sectionTitle = stringResource(R.string.settings_section_appearance),
                        darkModeTitle = stringResource(R.string.settings_dark_mode),
                        darkModeOn = stringResource(R.string.settings_dark_mode_on),
                        darkModeOff = stringResource(R.string.settings_dark_mode_off),
                        language = SillageSettingsLanguageStrings(
                            title = stringResource(R.string.settings_language),
                            supporting = stringResource(
                                R.string.settings_language_supporting,
                            ),
                        ),
                    ),
                    darkModeIcon = Icons.Rounded.DarkMode,
                    languageIcon = Icons.Rounded.Language,
                    enabled = !aiProfileOperationInProgress,
                    onDarkModeChange = { viewModel.toggleThemeMode() },
                    onLanguageChange = viewModel::setLanguageMode,
                )
            }
            item {
                SillageSettingsServiceSyncSection(
                    online = state.appMode == SessionStore.MODE_ONLINE,
                    baseUrl = state.baseUrl,
                    strings = SillageSettingsServiceSyncStrings(
                        sectionTitle = stringResource(R.string.settings_section_service_sync),
                        refreshTitle = stringResource(R.string.settings_refresh_records),
                        refreshSupporting = stringResource(
                            R.string.settings_refresh_records_supporting,
                        ),
                        onlineCurrent = stringResource(R.string.settings_online_current),
                        onlineSwitch = stringResource(R.string.settings_online_switch),
                        serverNotConfigured = stringResource(
                            R.string.settings_server_not_configured,
                        ),
                        offlineCurrent = stringResource(R.string.settings_offline_current),
                        offlineSwitch = stringResource(R.string.settings_offline_switch),
                        offlineSupporting = stringResource(R.string.settings_offline_supporting),
                        serverTitle = stringResource(R.string.settings_server),
                        serverSupporting = stringResource(R.string.settings_server_supporting),
                        syncLocalTitle = stringResource(R.string.settings_sync_local),
                        syncLocalSupporting = stringResource(
                            R.string.settings_sync_local_supporting,
                        ),
                        syncCloudTitle = stringResource(R.string.settings_sync_cloud),
                        syncCloudSupporting = stringResource(
                            R.string.settings_sync_cloud_supporting,
                        ),
                        syncBothTitle = stringResource(R.string.settings_sync_both),
                        syncBothSupporting = stringResource(
                            R.string.settings_sync_both_supporting,
                        ),
                    ),
                    icons = SillageSettingsServiceSyncIcons(
                        refresh = Icons.Rounded.Refresh,
                        online = Icons.Rounded.CloudSync,
                        offline = Icons.Rounded.Storage,
                        server = Icons.Rounded.SettingsEthernet,
                        syncLocal = Icons.Rounded.Download,
                        syncCloud = Icons.Rounded.UploadFile,
                        syncBoth = Icons.Rounded.CloudSync,
                    ),
                    loading = state.loading,
                    clientContextBlocked = clientContextChangeBlocked,
                    onRefresh = viewModel::refreshMemos,
                    onUseOnline = viewModel::useOnlineMode,
                    onUseOffline = viewModel::useOfflineMode,
                    onOpenServer = viewModel::openServerSettings,
                    onSyncLocal = viewModel::syncFromServer,
                    onSyncCloud = viewModel::syncToServer,
                    onSyncBoth = viewModel::syncBothWays,
                )
            }
            item {
                SillageSettingsDataSection(
                    strings = SillageSettingsDataStrings(
                        sectionTitle = stringResource(R.string.settings_section_data),
                        exportTitle = stringResource(R.string.settings_export),
                        exportSupporting = stringResource(R.string.settings_export_supporting),
                        importTitle = stringResource(R.string.settings_import),
                        importSupporting = stringResource(R.string.settings_import_supporting),
                    ),
                    exportIcon = Icons.Rounded.Download,
                    importIcon = Icons.Rounded.UploadFile,
                    enabled = !clientContextChangeBlocked,
                    onExport = { exportLauncher.launch("sillage-data.json") },
                    onImport = {
                        importLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                    },
                )
            }
            if (state.appMode == SessionStore.MODE_ONLINE) {
                item {
                    SillageAccountSettingsSection(
                        state = state.auth,
                        mutationBlocked = clientContextChangeBlocked,
                        strings = SillageAccountSettingsSectionStrings(
                            sectionTitle = stringResource(R.string.settings_section_account),
                            content = SillageAccountSettingsStrings(
                                changePasswordTitle = stringResource(R.string.settings_change_password),
                                changePasswordSupporting = stringResource(
                                    R.string.settings_change_password_supporting,
                                    ),
                                    currentPasswordLabel = stringResource(R.string.settings_current_password),
                                    newPasswordLabel = stringResource(R.string.settings_new_password),
                                    confirmPasswordLabel = stringResource(R.string.settings_confirm_password),
                                savePassword = stringResource(R.string.settings_save_password),
                                signOut = stringResource(R.string.settings_sign_out),
                            ),
                        ),
                        signOutSupporting = state.account?.displayName
                            ?: state.account?.username.orEmpty(),
                        signOutIcon = Icons.AutoMirrored.Rounded.Logout,
                        onCurrentPasswordChange = viewModel::updateCurrentPassword,
                        onNewPasswordChange = viewModel::updateNewPassword,
                        onConfirmPasswordChange = viewModel::updateConfirmPassword,
                        onSavePassword = viewModel::changePassword,
                        onSignOut = viewModel::signOut,
                    )
                }
            }
            item {
                val unavailable = stringResource(R.string.settings_value_unavailable)
                SillageSettingsAboutSection(
                    strings = SillageSettingsAboutStrings(
                        sectionTitle = stringResource(R.string.settings_section_about),
                        licensesTitle = stringResource(R.string.settings_open_source_licenses),
                        licensesSupporting = stringResource(
                            R.string.settings_open_source_licenses_supporting,
                        ),
                    ),
                    values = listOf(
                        SillageSettingsAboutValue(
                            label = stringResource(R.string.settings_app_version),
                            value = stringResource(
                                R.string.settings_app_version_value,
                                BuildConfig.VERSION_NAME,
                                BuildConfig.VERSION_CODE,
                            ),
                        ),
                        SillageSettingsAboutValue(
                            label = stringResource(R.string.settings_server_version),
                            value = state.serverVersion.ifBlank { unavailable },
                        ),
                        SillageSettingsAboutValue(
                            label = stringResource(R.string.settings_server_revision),
                            value = state.serverRevision.ifBlank { unavailable },
                        ),
                        SillageSettingsAboutValue(
                            label = stringResource(R.string.settings_api_version),
                            value = state.apiVersion.ifBlank { unavailable },
                        ),
                        SillageSettingsAboutValue(
                            label = stringResource(
                                R.string.settings_minimum_android_version_code,
                            ),
                            value = state.minimumAndroidVersionCode
                                .takeIf { it > 0 }
                                ?.toString()
                                ?: unavailable,
                        ),
                    ),
                    licensesIcon = Icons.Rounded.Info,
                    onOpenLicenses = { showOpenSourceLicenses = true },
                )
            }
            sillageAIProfilesEditorItems(
                state = state.settings,
                editorState = aiProfilesEditorState,
                strings = aiProfilesEditorStrings,
                addIcon = Icons.Rounded.Add,
                saveIcon = Icons.Rounded.Save,
                editingBlocked = aiProfileOperationInProgress,
                mutationBlocked = aiProfileMutationBlocked,
                onAdd = viewModel::addAIProfile,
                onSave = viewModel::saveAIProfiles,
                onSetDefault = viewModel::setAIProfileDefault,
                onNameChange = viewModel::updateAIProfileName,
                onProviderChange = viewModel::updateAIProfileProvider,
                onBaseUrlChange = viewModel::updateAIProfileBaseUrl,
                onModelChange = viewModel::updateAIProfileModel,
                onLoadModels = viewModel::loadAIModels,
                onTemperatureChange = viewModel::updateAIProfileTemperature,
                onMaxTokensChange = viewModel::updateAIProfileMaxTokens,
                onApiKeyChange = viewModel::updateAIProfileApiKey,
                onTestConnection = viewModel::testAIProfile,
                onDelete = viewModel::removeAIProfile,
            )
            }
        }
    }
    if (showOpenSourceLicenses) {
        OpenSourceLicensesDialog(onDismiss = { showOpenSourceLicenses = false })
    }
}

@Composable
private fun OpenSourceLicensesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val notices = remember(context) {
        context.resources.openRawResource(R.raw.third_party_notices)
            .bufferedReader()
            .use { it.readText() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_open_source_licenses)) },
        text = {
            SelectionContainer {
                Text(
                    text = notices,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}
