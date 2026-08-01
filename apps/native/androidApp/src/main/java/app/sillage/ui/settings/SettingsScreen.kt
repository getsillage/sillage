package app.sillage.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.sillage.BuildConfig
import app.sillage.R
import app.sillage.features.settings.AIProfileDraft
import app.sillage.features.settings.editorKey
import app.sillage.data.SessionStore
import app.sillage.ui.SillageUiState
import app.sillage.ui.SillageViewModel
import app.sillage.ui.auth.SillageAccountSettingsContent
import app.sillage.ui.auth.SillageAccountSettingsStrings
import app.sillage.ui.designsystem.SillageErrorCard
import app.sillage.ui.designsystem.SillageSettingsActionRow
import app.sillage.ui.designsystem.SillageSettingsEmptyCard
import app.sillage.ui.designsystem.SillageSettingsInfoRow
import app.sillage.ui.designsystem.SillageSettingsSectionCard
import app.sillage.ui.designsystem.SillageSettingsSwitchRow
import app.sillage.ui.designsystem.applySillageHeadingSemantics
import app.sillage.ui.hasClientContextOperationInProgress
import app.sillage.ui.navigation.MainNavigationBar

private const val AI_PROVIDER_ANTHROPIC = "anthropic"
private const val AI_PROVIDER_OPENAI = "openai"
private val AI_PROVIDER_OPTIONS = listOf(AI_PROVIDER_ANTHROPIC, AI_PROVIDER_OPENAI)
internal const val SETTINGS_SCREEN_TEST_TAG = "settings-screen"
internal const val SETTINGS_LIST_TEST_TAG = "settings-list"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AISettingsScreen(state: SillageUiState, viewModel: SillageViewModel) {
    var selectedAIProfileIndex by remember { mutableStateOf<Int?>(null) }
    var showOpenSourceLicenses by remember { mutableStateOf(false) }
    val selectedIndex = selectedAIProfileIndex?.takeIf { it in state.aiProfiles.indices }
    val aiProfileOperationInProgress = state.aiSettingsSaving ||
        state.aiTestingProfileId.isNotBlank() ||
        state.aiLoadingModelsProfileId.isNotBlank() ||
        state.loading
    val aiProfileMutationBlocked = aiProfileOperationInProgress || state.aiAutoSummarySaving
    val clientContextChangeBlocked = state.hasClientContextOperationInProgress()
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
                        SettingsOverviewCard(state)
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
                            SettingsLanguageRow(
                                languageMode = state.languageMode,
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
                    item {
                        AISettingsHeaderCard(
                            saving = state.aiSettingsSaving,
                            addEnabled = !aiProfileOperationInProgress,
                            saveEnabled = !aiProfileMutationBlocked,
                            onAdd = {
                                selectedAIProfileIndex = state.aiProfiles.size
                                viewModel.addAIProfile()
                            },
                            onSave = viewModel::saveAIProfiles,
                        )
                    }
                    if (state.aiProfiles.isEmpty()) {
                        item {
                        SillageSettingsEmptyCard(stringResource(R.string.settings_no_ai_profiles))
                        }
                    } else {
                        items(state.aiProfiles.size, key = { index -> state.aiProfiles[index].editorKey(index) }) { index ->
                            val profile = state.aiProfiles[index]
                            val profileKey = profile.editorKey(index)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AIProfileSummaryCard(
                                    profile = profile,
                                    testResult = state.aiTestResults[profileKey],
                                    selected = selectedIndex == index,
                                    editingBlocked = aiProfileOperationInProgress,
                                    mutationBlocked = aiProfileMutationBlocked,
                                    onConfigure = { selectedAIProfileIndex = index },
                                    onSetDefault = { viewModel.setAIProfileDefault(index) },
                                )
                                if (selectedIndex == index) {
                                    AIProfileDetailCard(
                                        index = index,
                                        profile = profile,
                                        testing = state.aiTestingProfileId == profileKey,
                                        loadingModels = state.aiLoadingModelsProfileId == profileKey,
                                        modelOptions = state.aiModelResults[profileKey].orEmpty(),
                                        testResult = state.aiTestResults[profileKey],
                                        editingBlocked = aiProfileOperationInProgress,
                                        mutationBlocked = aiProfileMutationBlocked,
                                        viewModel = viewModel,
                                        onClose = { selectedAIProfileIndex = null },
                                    )
                                }
                            }
                        }
                    }
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

@Composable
private fun SettingsOverviewCard(state: SillageUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.settings_status_title),
                modifier = Modifier.semantics { applySillageHeadingSemantics() },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OverviewItem(
                    label = stringResource(if (state.appMode == SessionStore.MODE_ONLINE) R.string.status_online else R.string.status_offline),
                    value = if (state.appMode == SessionStore.MODE_ONLINE) {
                        state.baseUrl.ifBlank { stringResource(R.string.settings_not_configured) }
                    } else {
                        pluralStringResource(R.plurals.quantity_records, state.memos.size, state.memos.size)
                    },
                    modifier = Modifier.weight(1f),
                )
                OverviewItem(
                    label = stringResource(R.string.settings_theme_label),
                    value = stringResource(if (state.themeMode == SessionStore.THEME_DARK) R.string.settings_theme_dark else R.string.settings_theme_light),
                    modifier = Modifier.weight(1f),
                )
                OverviewItem(
                    label = stringResource(R.string.settings_section_ai),
                    value = stringResource(if (state.aiAutoSummary) R.string.settings_auto_summary else R.string.settings_summary_manual),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun OverviewItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AISettingsHeaderCard(
    saving: Boolean,
    addEnabled: Boolean,
    saveEnabled: Boolean,
    onAdd: () -> Unit,
    onSave: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.settings_ai_profiles),
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .semantics { applySillageHeadingSemantics() },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.settings_ai_profiles_supporting),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onAdd,
                        enabled = addEnabled,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.action_new))
                    }
                    Button(
                        onClick = onSave,
                        enabled = saveEnabled,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                    ) {
                        if (saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = LocalContentColor.current,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(if (saving) R.string.action_saving else R.string.action_save))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsLanguageRow(
    languageMode: String,
    enabled: Boolean,
    onLanguageChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Language,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                },
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.settings_language),
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.settings_language_supporting),
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        val languages = listOf(
            SessionStore.LANGUAGE_ZH_CN to stringResource(R.string.language_chinese),
            SessionStore.LANGUAGE_EN to stringResource(R.string.language_english),
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 34.dp, top = 10.dp),
        ) {
            languages.forEachIndexed { index, (language, label) ->
                SegmentedButton(
                    selected = languageMode == language,
                    onClick = { onLanguageChange(language) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = languages.size),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp),
                    label = {
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                )
            }
        }
    }
}

@Composable
private fun aiProviderProtocolLabel(provider: String): String {
    val label = if (provider.equals(AI_PROVIDER_ANTHROPIC, ignoreCase = true)) {
        R.string.settings_provider_anthropic_compatible
    } else {
        R.string.settings_provider_openai_compatible
    }
    return stringResource(label)
}

@Composable
private fun AIProfileSummaryCard(
    profile: AIProfileDraft,
    testResult: String?,
    selected: Boolean,
    editingBlocked: Boolean,
    mutationBlocked: Boolean,
    onConfigure: () -> Unit,
    onSetDefault: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        profile.name.ifBlank { stringResource(R.string.settings_profile_unnamed) },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        aiProviderProtocolLabel(profile.provider),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (profile.active) {
                    AssistChip(
                        onClick = onConfigure,
                        label = { Text(stringResource(R.string.settings_default)) },
                        enabled = !editingBlocked,
                    )
                }
            }
            Text(
                profile.model.ifBlank { stringResource(R.string.settings_model_unset) },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(
                        if (profile.hasApiKey || profile.apiKeyInput.isNotBlank()) R.string.settings_key_present else R.string.settings_key_missing,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
                if (profile.keyUnavailable) {
                    Text(
                        stringResource(R.string.settings_key_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            if (testResult != null) {
                Text(
                    testResult,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onConfigure,
                    enabled = !editingBlocked,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.action_configure))
                }
                TextButton(
                    onClick = onSetDefault,
                    enabled = !profile.active && !mutationBlocked,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(if (profile.active) R.string.settings_default_current else R.string.settings_set_default))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AIProfileDetailCard(
    index: Int,
    profile: AIProfileDraft,
    testing: Boolean,
    loadingModels: Boolean,
    modelOptions: List<String>,
    testResult: String?,
    editingBlocked: Boolean,
    mutationBlocked: Boolean,
    viewModel: SillageViewModel,
    onClose: () -> Unit,
) {
    val profileKey = profile.editorKey(index)
    var confirmingDelete by remember(profileKey) { mutableStateOf(false) }
    var providerMenuExpanded by remember(profileKey) { mutableStateOf(false) }
    val controlsEnabled = !editingBlocked && !testing && !loadingModels
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_profile_details),
                        modifier = Modifier.semantics { applySillageHeadingSemantics() },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.settings_profile_details_supporting),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                TextButton(onClick = onClose) {
                    Text(stringResource(R.string.action_collapse))
                }
            }
            OutlinedTextField(
                value = profile.name,
                onValueChange = { viewModel.updateAIProfileName(index, it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.settings_profile_name)) },
                enabled = controlsEnabled,
            )
            ExposedDropdownMenuBox(
                expanded = providerMenuExpanded,
                onExpandedChange = { expanded ->
                    providerMenuExpanded = controlsEnabled && expanded
                },
            ) {
                OutlinedTextField(
                    value = aiProviderProtocolLabel(profile.provider),
                    onValueChange = {},
                    modifier = Modifier
                        .menuAnchor(
                            type = MenuAnchorType.PrimaryNotEditable,
                            enabled = controlsEnabled,
                        )
                        .fillMaxWidth(),
                    readOnly = true,
                    singleLine = true,
                    label = { Text(stringResource(R.string.settings_provider)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerMenuExpanded)
                    },
                    enabled = controlsEnabled,
                )
                ExposedDropdownMenu(
                    expanded = providerMenuExpanded,
                    onDismissRequest = { providerMenuExpanded = false },
                ) {
                    AI_PROVIDER_OPTIONS.forEach { provider ->
                        DropdownMenuItem(
                            text = { Text(aiProviderProtocolLabel(provider)) },
                            enabled = controlsEnabled,
                            onClick = {
                                viewModel.updateAIProfileProvider(index, provider)
                                providerMenuExpanded = false
                            },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = profile.baseUrl,
                onValueChange = { viewModel.updateAIProfileBaseUrl(index, it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.settings_base_url)) },
                enabled = controlsEnabled,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = profile.model,
                    onValueChange = { viewModel.updateAIProfileModel(index, it) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(stringResource(R.string.settings_model)) },
                    enabled = controlsEnabled,
                )
                TextButton(
                    onClick = { viewModel.loadAIModels(index) },
                    enabled = controlsEnabled,
                ) {
                    Text(stringResource(if (loadingModels) R.string.settings_models_loading else R.string.settings_models_get))
                }
            }
            if (modelOptions.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    modelOptions.forEach { model ->
                        AssistChip(
                            onClick = { viewModel.updateAIProfileModel(index, model) },
                            label = { Text(model, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            enabled = controlsEnabled,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = profile.temperatureInput,
                    onValueChange = { viewModel.updateAIProfileTemperature(index, it) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(stringResource(R.string.settings_temperature)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = controlsEnabled,
                )
                OutlinedTextField(
                    value = profile.maxTokensInput,
                    onValueChange = { viewModel.updateAIProfileMaxTokens(index, it) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(stringResource(R.string.settings_max_tokens)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = controlsEnabled,
                )
            }
            OutlinedTextField(
                value = profile.apiKeyInput,
                onValueChange = { viewModel.updateAIProfileApiKey(index, it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.settings_api_key)) },
                placeholder = {
                    Text(stringResource(if (profile.hasApiKey) R.string.settings_key_keep else R.string.settings_key_not_configured))
                },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = controlsEnabled,
            )
            if (profile.keyUnavailable) {
                Text(
                    stringResource(R.string.settings_key_decrypt_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.testAIProfile(index) }, enabled = controlsEnabled) {
                    Text(stringResource(if (testing) R.string.settings_test_testing else R.string.settings_test_connection))
                }
                TextButton(
                    onClick = {
                        if (confirmingDelete) {
                            confirmingDelete = false
                            if (viewModel.removeAIProfile(index)) {
                                onClose()
                            }
                        } else {
                            confirmingDelete = true
                        }
                    },
                    enabled = controlsEnabled && !mutationBlocked,
                ) {
                    Text(stringResource(if (confirmingDelete) R.string.action_confirm_delete else R.string.action_delete))
                }
            }
            if (testResult != null) {
                Text(
                    testResult,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
