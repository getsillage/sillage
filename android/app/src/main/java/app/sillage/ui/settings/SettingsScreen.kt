package app.sillage.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.sillage.data.AIProfileDraft
import app.sillage.R
import app.sillage.data.SessionStore
import app.sillage.ui.SillageUiState
import app.sillage.ui.SillageViewModel
import app.sillage.ui.navigation.MainNavigationBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AISettingsScreen(state: SillageUiState, viewModel: SillageViewModel) {
    var selectedAIProfileIndex by remember { mutableStateOf<Int?>(null) }
    val selectedIndex = selectedAIProfileIndex?.takeIf { it in state.aiProfiles.indices }
    val aiProfileOperationInProgress = state.aiSettingsSaving ||
        state.aiTestingProfileId.isNotBlank() ||
        state.aiLoadingModelsProfileId.isNotBlank() ||
        state.loading
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
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    state.aiSettingsLoadError?.let { message ->
                        item(key = "ai-settings-load-error") {
                            SettingsLoadErrorCard(
                                message = message,
                                onRetry = viewModel::loadAISettings,
                            )
                        }
                    }
                    item {
                        SettingsOverviewCard(state)
                    }
                    item {
                        SettingsSectionCard(title = stringResource(R.string.settings_section_ai)) {
                            SettingsSwitchRow(
                                icon = Icons.Rounded.AutoAwesome,
                                title = stringResource(R.string.settings_auto_summary),
                                supporting = stringResource(R.string.settings_auto_summary_supporting),
                                checked = state.aiAutoSummary,
                                enabled = !state.aiAutoSummarySaving && !state.loading,
                                onCheckedChange = viewModel::setAISettingsAutoSummary,
                            )
                        }
                    }
                    item {
                        SettingsSectionCard(title = stringResource(R.string.settings_section_appearance)) {
                            SettingsSwitchRow(
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
                        SettingsSectionCard(title = stringResource(R.string.settings_section_service_sync)) {
                            SettingsActionRow(
                                icon = Icons.Rounded.Refresh,
                                title = stringResource(R.string.settings_refresh_records),
                                supporting = stringResource(R.string.settings_refresh_records_supporting),
                                onClick = viewModel::refreshMemos,
                                enabled = !state.loading,
                            )
                            SettingsActionRow(
                                icon = Icons.Rounded.CloudSync,
                                title = stringResource(
                                    if (state.appMode == SessionStore.MODE_ONLINE) R.string.settings_online_current else R.string.settings_online_switch,
                                ),
                                supporting = state.baseUrl.ifBlank { stringResource(R.string.settings_server_not_configured) },
                                onClick = viewModel::useOnlineMode,
                                enabled = state.appMode != SessionStore.MODE_ONLINE && !aiProfileOperationInProgress,
                                selected = state.appMode == SessionStore.MODE_ONLINE,
                                showDivider = true,
                            )
                            SettingsActionRow(
                                icon = Icons.Rounded.Storage,
                                title = stringResource(
                                    if (state.appMode == SessionStore.MODE_OFFLINE) R.string.settings_offline_current else R.string.settings_offline_switch,
                                ),
                                supporting = stringResource(R.string.settings_offline_supporting),
                                onClick = viewModel::useOfflineMode,
                                enabled = state.appMode != SessionStore.MODE_OFFLINE && !aiProfileOperationInProgress,
                                selected = state.appMode == SessionStore.MODE_OFFLINE,
                                showDivider = true,
                            )
                            if (state.appMode == SessionStore.MODE_ONLINE) {
                                SettingsActionRow(
                                    icon = Icons.Rounded.SettingsEthernet,
                                    title = stringResource(R.string.settings_server),
                                    supporting = stringResource(R.string.settings_server_supporting),
                                    onClick = viewModel::openServerSettings,
                                    enabled = !aiProfileOperationInProgress,
                                    showDivider = true,
                                )
                                SettingsActionRow(
                                    icon = Icons.Rounded.Download,
                                    title = stringResource(R.string.settings_sync_local),
                                    supporting = stringResource(R.string.settings_sync_local_supporting),
                                    onClick = viewModel::syncFromServer,
                                    enabled = !aiProfileOperationInProgress,
                                    showDivider = true,
                                )
                                SettingsActionRow(
                                    icon = Icons.Rounded.UploadFile,
                                    title = stringResource(R.string.settings_sync_cloud),
                                    supporting = stringResource(R.string.settings_sync_cloud_supporting),
                                    onClick = viewModel::syncToServer,
                                    enabled = !aiProfileOperationInProgress,
                                    showDivider = true,
                                )
                                SettingsActionRow(
                                    icon = Icons.Rounded.CloudSync,
                                    title = stringResource(R.string.settings_sync_both),
                                    supporting = stringResource(R.string.settings_sync_both_supporting),
                                    onClick = viewModel::syncBothWays,
                                    enabled = !aiProfileOperationInProgress,
                                    showDivider = true,
                                )
                            }
                        }
                    }
                    item {
                        SettingsSectionCard(title = stringResource(R.string.settings_section_data)) {
                            SettingsActionRow(
                                icon = Icons.Rounded.Download,
                                title = stringResource(R.string.settings_export),
                                supporting = stringResource(R.string.settings_export_supporting),
                                onClick = { exportLauncher.launch("sillage-data.json") },
                                enabled = !aiProfileOperationInProgress,
                            )
                            SettingsActionRow(
                                icon = Icons.Rounded.UploadFile,
                                title = stringResource(R.string.settings_import),
                                supporting = stringResource(R.string.settings_import_supporting),
                                onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                                enabled = !aiProfileOperationInProgress,
                                showDivider = true,
                            )
                        }
                    }
                    if (state.appMode == SessionStore.MODE_ONLINE) {
                        item {
                            SettingsSectionCard(title = stringResource(R.string.settings_section_account)) {
                                SettingsActionRow(
                                    icon = Icons.AutoMirrored.Rounded.Logout,
                                    title = stringResource(R.string.settings_sign_out),
                                    supporting = state.account?.displayName ?: state.account?.username.orEmpty(),
                                    onClick = viewModel::signOut,
                                    enabled = !aiProfileOperationInProgress,
                                )
                            }
                        }
                    }
                    item {
                        AISettingsHeaderCard(
                            saving = state.aiSettingsSaving,
                            actionsEnabled = !aiProfileOperationInProgress,
                            onAdd = {
                                selectedAIProfileIndex = state.aiProfiles.size
                                viewModel.addAIProfile()
                            },
                            onSave = viewModel::saveAIProfiles,
                        )
                    }
                    if (state.aiProfiles.isEmpty()) {
                        item {
                            EmptySettingsCard(stringResource(R.string.settings_no_ai_profiles))
                        }
                    } else {
                        items(state.aiProfiles.size, key = { index -> state.aiProfiles[index].id.ifBlank { "new-$index" } }) { index ->
                            val profile = state.aiProfiles[index]
                            val profileKey = profile.id.ifBlank { "new-$index" }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AIProfileSummaryCard(
                                    profile = profile,
                                    testResult = state.aiTestResults[profileKey],
                                    selected = selectedIndex == index,
                                    saving = aiProfileOperationInProgress,
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
                                        saving = aiProfileOperationInProgress,
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
}

@Composable
private fun SettingsLoadErrorCard(message: String, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = onRetry,
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.action_retry))
            }
        }
    }
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
    actionsEnabled: Boolean,
    onAdd: () -> Unit,
    onSave: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.settings_ai_profiles),
            modifier = Modifier.padding(horizontal = 4.dp),
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
                        enabled = actionsEnabled,
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
                        enabled = actionsEnabled,
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

@Composable
private fun SettingsSectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            modifier = Modifier.padding(horizontal = 4.dp),
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
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    supporting: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selected: Boolean = false,
    showDivider: Boolean = false,
) {
    val titleColor = if (enabled || selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    val supportingColor = if (enabled || selected) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }
    Column {
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 50.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            color = if (selected) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 68.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (selected) MaterialTheme.colorScheme.primary else supportingColor,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        title,
                        color = titleColor,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (supporting.isNotBlank()) {
                        Text(
                            supporting,
                            color = supportingColor,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    supporting: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val titleColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    val supportingColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .heightIn(min = 68.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = supportingColor,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, color = titleColor, style = MaterialTheme.typography.bodyMedium)
            Text(
                supporting,
                color = supportingColor,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
        )
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
private fun EmptySettingsCard(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text,
            modifier = Modifier.padding(14.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun AIProfileSummaryCard(
    profile: AIProfileDraft,
    testResult: String?,
    selected: Boolean,
    saving: Boolean,
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
                        profile.provider.ifBlank { stringResource(R.string.settings_provider_unset) },
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
                        enabled = !saving,
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
                    enabled = !saving,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.action_configure))
                }
                TextButton(
                    onClick = onSetDefault,
                    enabled = !profile.active && !saving,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(if (profile.active) R.string.settings_default_current else R.string.settings_set_default))
                }
            }
        }
    }
}

@Composable
private fun AIProfileDetailCard(
    index: Int,
    profile: AIProfileDraft,
    testing: Boolean,
    loadingModels: Boolean,
    modelOptions: List<String>,
    testResult: String?,
    saving: Boolean,
    viewModel: SillageViewModel,
    onClose: () -> Unit,
) {
    var confirmingDelete by remember(profile.id, index) { mutableStateOf(false) }
    val controlsEnabled = !saving && !testing && !loadingModels
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
            OutlinedTextField(
                value = profile.provider,
                onValueChange = { viewModel.updateAIProfileProvider(index, it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.settings_provider)) },
                placeholder = { Text(stringResource(R.string.settings_provider_placeholder)) },
                enabled = controlsEnabled,
            )
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                            viewModel.removeAIProfile(index)
                            onClose()
                        } else {
                            confirmingDelete = true
                        }
                    },
                    enabled = controlsEnabled,
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
