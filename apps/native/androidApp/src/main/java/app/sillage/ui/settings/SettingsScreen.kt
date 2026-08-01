package app.sillage.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
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
import app.sillage.ui.auth.SillageAccountSettingsContent
import app.sillage.ui.auth.SillageAccountSettingsStrings
import app.sillage.ui.designsystem.SillageErrorCard
import app.sillage.ui.designsystem.SillageSettingsActionRow
import app.sillage.ui.designsystem.SillageSettingsInfoRow
import app.sillage.ui.designsystem.SillageSettingsSectionCard
import app.sillage.ui.designsystem.SillageSettingsSwitchRow
import app.sillage.ui.designsystem.applySillageHeadingSemantics
import app.sillage.ui.hasClientContextOperationInProgress
import app.sillage.ui.navigation.MainNavigationBar
import app.sillage.ui.settings.SillageAIProfileDetailStrings
import app.sillage.ui.settings.SillageAIProfileSummaryStrings
import app.sillage.ui.settings.SillageAIProfilesEditorStrings
import app.sillage.ui.settings.SillageAIProfilesHeaderStrings
import app.sillage.ui.settings.SillageSettingsLanguageOption
import app.sillage.ui.settings.SillageSettingsLanguageRow
import app.sillage.ui.settings.SillageSettingsLanguageStrings
import app.sillage.ui.settings.SillageSettingsOverviewCard
import app.sillage.ui.settings.SillageSettingsOverviewItem
import app.sillage.ui.settings.rememberSillageAIProfilesEditorState
import app.sillage.ui.settings.sillageAIProfilesEditorItems

internal const val SETTINGS_SCREEN_TEST_TAG = "settings-screen"
internal const val SETTINGS_LIST_TEST_TAG = "settings-list"

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
            if (state.aiSettingsLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(SETTINGS_LIST_TEST_TAG),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    state.aiSettingsLoadError?.let { message ->
                        item(key = "ai-settings-load-error") {
                            SillageErrorCard(
                                message = message,
                                actionLabel = stringResource(R.string.action_retry),
                                actionIcon = Icons.Rounded.Refresh,
                                onAction = viewModel::loadAISettings,
                            )
                        }
                    }
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
        SillageSettingsSectionCard(title = stringResource(R.string.settings_section_ai)) {
            SillageSettingsSwitchRow(
                                icon = Icons.Rounded.AutoAwesome,
                                title = stringResource(R.string.settings_auto_summary),
                                supporting = stringResource(R.string.settings_auto_summary_supporting),
                                checked = state.aiAutoSummary,
                                enabled = !state.aiAutoSummarySaving &&
                                    !state.aiSettingsSaving &&
                                    !state.loading,
                                onCheckedChange = viewModel::setAISettingsAutoSummary,
                            )
                        }
                    }
                    item {
        SillageSettingsSectionCard(title = stringResource(R.string.settings_section_appearance)) {
            SillageSettingsSwitchRow(
                                icon = Icons.Rounded.DarkMode,
                                title = stringResource(R.string.settings_dark_mode),
                                supporting = if (state.themeMode == SessionStore.THEME_DARK) {
                                    stringResource(R.string.settings_dark_mode_on)
                                } else {
                                    stringResource(R.string.settings_dark_mode_off)
                                },
                                checked = state.themeMode == SessionStore.THEME_DARK,
                                enabled = !aiProfileOperationInProgress,
                                onCheckedChange = { viewModel.toggleThemeMode() },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 50.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                            SillageSettingsLanguageRow(
                                selectedLanguage = state.languageMode,
                                options = listOf(
                                    SillageSettingsLanguageOption(
                                        value = SessionStore.LANGUAGE_ZH_CN,
                                        label = stringResource(R.string.language_chinese),
                                    ),
                                    SillageSettingsLanguageOption(
                                        value = SessionStore.LANGUAGE_EN,
                                        label = stringResource(R.string.language_english),
                                    ),
                                ),
                                strings = SillageSettingsLanguageStrings(
                                    title = stringResource(R.string.settings_language),
                                    supporting = stringResource(R.string.settings_language_supporting),
                                ),
                                icon = Icons.Rounded.Language,
                                enabled = !aiProfileOperationInProgress,
                                onLanguageChange = viewModel::setLanguageMode,
                            )
                        }
                    }
                    item {
        SillageSettingsSectionCard(title = stringResource(R.string.settings_section_service_sync)) {
            SillageSettingsActionRow(
                                icon = Icons.Rounded.Refresh,
                                title = stringResource(R.string.settings_refresh_records),
                                supporting = stringResource(R.string.settings_refresh_records_supporting),
                                onClick = viewModel::refreshMemos,
                                enabled = !state.loading,
                            )
            SillageSettingsActionRow(
                                icon = Icons.Rounded.CloudSync,
                                title = stringResource(
                                    if (state.appMode == SessionStore.MODE_ONLINE) R.string.settings_online_current else R.string.settings_online_switch,
                                ),
                                supporting = state.baseUrl.ifBlank { stringResource(R.string.settings_server_not_configured) },
                                onClick = viewModel::useOnlineMode,
                                enabled = state.appMode != SessionStore.MODE_ONLINE && !clientContextChangeBlocked,
                                selected = state.appMode == SessionStore.MODE_ONLINE,
                                showDivider = true,
                            )
            SillageSettingsActionRow(
                                icon = Icons.Rounded.Storage,
                                title = stringResource(
                                    if (state.appMode == SessionStore.MODE_OFFLINE) R.string.settings_offline_current else R.string.settings_offline_switch,
                                ),
                                supporting = stringResource(R.string.settings_offline_supporting),
                                onClick = viewModel::useOfflineMode,
                                enabled = state.appMode != SessionStore.MODE_OFFLINE && !clientContextChangeBlocked,
                                selected = state.appMode == SessionStore.MODE_OFFLINE,
                                showDivider = true,
                            )
                            if (state.appMode == SessionStore.MODE_ONLINE) {
            SillageSettingsActionRow(
                                    icon = Icons.Rounded.SettingsEthernet,
                                    title = stringResource(R.string.settings_server),
                                    supporting = stringResource(R.string.settings_server_supporting),
                                    onClick = viewModel::openServerSettings,
                                    enabled = !clientContextChangeBlocked,
                                    showDivider = true,
                                )
            SillageSettingsActionRow(
                                    icon = Icons.Rounded.Download,
                                    title = stringResource(R.string.settings_sync_local),
                                    supporting = stringResource(R.string.settings_sync_local_supporting),
                                    onClick = viewModel::syncFromServer,
                                    enabled = !clientContextChangeBlocked,
                                    showDivider = true,
                                )
            SillageSettingsActionRow(
                                    icon = Icons.Rounded.UploadFile,
                                    title = stringResource(R.string.settings_sync_cloud),
                                    supporting = stringResource(R.string.settings_sync_cloud_supporting),
                                    onClick = viewModel::syncToServer,
                                    enabled = !clientContextChangeBlocked,
                                    showDivider = true,
                                )
            SillageSettingsActionRow(
                                    icon = Icons.Rounded.CloudSync,
                                    title = stringResource(R.string.settings_sync_both),
                                    supporting = stringResource(R.string.settings_sync_both_supporting),
                                    onClick = viewModel::syncBothWays,
                                    enabled = !clientContextChangeBlocked,
                                    showDivider = true,
                                )
                            }
                        }
                    }
                    item {
        SillageSettingsSectionCard(title = stringResource(R.string.settings_section_data)) {
            SillageSettingsActionRow(
                                icon = Icons.Rounded.Download,
                                title = stringResource(R.string.settings_export),
                                supporting = stringResource(R.string.settings_export_supporting),
                                onClick = { exportLauncher.launch("sillage-data.json") },
                                enabled = !clientContextChangeBlocked,
                            )
            SillageSettingsActionRow(
                                icon = Icons.Rounded.UploadFile,
                                title = stringResource(R.string.settings_import),
                                supporting = stringResource(R.string.settings_import_supporting),
                                onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                                enabled = !clientContextChangeBlocked,
                                showDivider = true,
                            )
                        }
                    }
                    if (state.appMode == SessionStore.MODE_ONLINE) {
                        item {
        SillageSettingsSectionCard(title = stringResource(R.string.settings_section_account)) {
                                SillageAccountSettingsContent(
                                    state = state.auth,
                                    mutationBlocked = clientContextChangeBlocked,
                                    strings = SillageAccountSettingsStrings(
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
                    }
                    item {
        SillageSettingsSectionCard(title = stringResource(R.string.settings_section_about)) {
                            val unavailable = stringResource(R.string.settings_value_unavailable)
            SillageSettingsInfoRow(
                                label = stringResource(R.string.settings_app_version),
                                value = stringResource(
                                    R.string.settings_app_version_value,
                                    BuildConfig.VERSION_NAME,
                                    BuildConfig.VERSION_CODE,
                                ),
                            )
            SillageSettingsInfoRow(
                                label = stringResource(R.string.settings_server_version),
                                value = state.serverVersion.ifBlank { unavailable },
                                showDivider = true,
                            )
                        SillageSettingsInfoRow(
                                label = stringResource(R.string.settings_server_revision),
                                value = state.serverRevision.ifBlank { unavailable },
                                showDivider = true,
                            )
                        SillageSettingsInfoRow(
                                label = stringResource(R.string.settings_api_version),
                                value = state.apiVersion.ifBlank { unavailable },
                                showDivider = true,
                            )
                        SillageSettingsInfoRow(
                                label = stringResource(R.string.settings_minimum_android_version_code),
                                value = state.minimumAndroidVersionCode.takeIf { it > 0 }?.toString() ?: unavailable,
                                showDivider = true,
                            )
                        SillageSettingsActionRow(
                                icon = Icons.Rounded.Info,
                                title = stringResource(R.string.settings_open_source_licenses),
                                supporting = stringResource(R.string.settings_open_source_licenses_supporting),
                                onClick = { showOpenSourceLicenses = true },
                                showDivider = true,
                            )
                        }
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
