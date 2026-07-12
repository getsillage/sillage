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
import androidx.compose.ui.unit.dp
import app.sillage.data.AIProfileDraft
import app.sillage.data.SessionStore
import app.sillage.ui.SillageUiState
import app.sillage.ui.SillageViewModel
import app.sillage.ui.common.MessageBlock
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
                title = { Text("设置") },
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
            MessageBlock(
                error = state.error,
                notice = state.notice,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
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
                    item {
                        SettingsOverviewCard(state)
                    }
                    item {
                        SettingsSectionCard(title = "AI") {
                            SettingsSwitchRow(
                                icon = Icons.Rounded.AutoAwesome,
                                title = "自动总结",
                                supporting = "新建记录后自动生成摘要。",
                                checked = state.aiAutoSummary,
                                enabled = !state.aiAutoSummarySaving && !state.loading,
                                onCheckedChange = viewModel::setAISettingsAutoSummary,
                            )
                        }
                    }
                    item {
                        SettingsSectionCard(title = "外观") {
                            SettingsSwitchRow(
                                icon = Icons.Rounded.DarkMode,
                                title = "深色模式",
                                supporting = if (state.themeMode == SessionStore.THEME_DARK) {
                                    "当前使用深色主题。"
                                } else {
                                    "当前使用浅色主题。"
                                },
                                checked = state.themeMode == SessionStore.THEME_DARK,
                                enabled = !aiProfileOperationInProgress,
                                onCheckedChange = { viewModel.toggleThemeMode() },
                            )
                        }
                    }
                    item {
                        SettingsSectionCard(title = "服务与同步") {
                            SettingsActionRow(
                                icon = Icons.Rounded.Refresh,
                                title = "刷新记录",
                                supporting = "重新读取当前模式下的记录列表。",
                                onClick = viewModel::refreshMemos,
                                enabled = !state.loading,
                            )
                            SettingsActionRow(
                                icon = Icons.Rounded.CloudSync,
                                title = if (state.appMode == SessionStore.MODE_ONLINE) "当前：在线模式" else "切换到在线模式",
                                supporting = state.baseUrl.ifBlank { "未配置服务器地址" },
                                onClick = viewModel::useOnlineMode,
                                enabled = state.appMode != SessionStore.MODE_ONLINE && !aiProfileOperationInProgress,
                                selected = state.appMode == SessionStore.MODE_ONLINE,
                                showDivider = true,
                            )
                            SettingsActionRow(
                                icon = Icons.Rounded.Storage,
                                title = if (state.appMode == SessionStore.MODE_OFFLINE) "当前：离线模式" else "切换到离线模式",
                                supporting = "记录保存在当前设备。",
                                onClick = viewModel::useOfflineMode,
                                enabled = state.appMode != SessionStore.MODE_OFFLINE && !aiProfileOperationInProgress,
                                selected = state.appMode == SessionStore.MODE_OFFLINE,
                                showDivider = true,
                            )
                            if (state.appMode == SessionStore.MODE_ONLINE) {
                                SettingsActionRow(
                                    icon = Icons.Rounded.SettingsEthernet,
                                    title = "服务器设置",
                                    supporting = "修改服务地址和重新连接。",
                                    onClick = viewModel::openServerSettings,
                                    enabled = !aiProfileOperationInProgress,
                                    showDivider = true,
                                )
                                SettingsActionRow(
                                    icon = Icons.Rounded.Download,
                                    title = "同步到本地",
                                    supporting = "把服务端数据保存到本机离线库。",
                                    onClick = viewModel::syncFromServer,
                                    enabled = !aiProfileOperationInProgress,
                                    showDivider = true,
                                )
                                SettingsActionRow(
                                    icon = Icons.Rounded.UploadFile,
                                    title = "同步到云端",
                                    supporting = "把本机离线记录推送到服务端。",
                                    onClick = viewModel::syncToServer,
                                    enabled = !aiProfileOperationInProgress,
                                    showDivider = true,
                                )
                                SettingsActionRow(
                                    icon = Icons.Rounded.CloudSync,
                                    title = "双向同步",
                                    supporting = "先推送本地更改，再拉取服务端数据。",
                                    onClick = viewModel::syncBothWays,
                                    enabled = !aiProfileOperationInProgress,
                                    showDivider = true,
                                )
                            }
                        }
                    }
                    item {
                        SettingsSectionCard(title = "数据") {
                            SettingsActionRow(
                                icon = Icons.Rounded.Download,
                                title = "导出完整数据",
                                supporting = "导出记录、AI 设置和问答数据。",
                                onClick = { exportLauncher.launch("sillage-data.json") },
                                enabled = !aiProfileOperationInProgress,
                            )
                            SettingsActionRow(
                                icon = Icons.Rounded.UploadFile,
                                title = "导入完整数据",
                                supporting = "从 JSON 文件恢复或合并数据。",
                                onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                                enabled = !aiProfileOperationInProgress,
                                showDivider = true,
                            )
                        }
                    }
                    if (state.appMode == SessionStore.MODE_ONLINE) {
                        item {
                            SettingsSectionCard(title = "账号") {
                                SettingsActionRow(
                                    icon = Icons.AutoMirrored.Rounded.Logout,
                                    title = "退出登录",
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
                            EmptySettingsCard("还没有 AI 档案。可以在上方新增一个档案。")
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
                "当前状态",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OverviewItem(
                    label = if (state.appMode == SessionStore.MODE_ONLINE) "在线" else "离线",
                    value = if (state.appMode == SessionStore.MODE_ONLINE) {
                        state.baseUrl.ifBlank { "未配置" }
                    } else {
                        "${state.memos.size} 条记录"
                    },
                    modifier = Modifier.weight(1f),
                )
                OverviewItem(
                    label = "主题",
                    value = if (state.themeMode == SessionStore.THEME_DARK) "深色" else "浅色",
                    modifier = Modifier.weight(1f),
                )
                OverviewItem(
                    label = "AI",
                    value = if (state.aiAutoSummary) "自动总结" else "手动",
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
            "AI 档案",
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
                    "管理总结和问答使用的模型配置。密钥加密保存在本地服务端，不会回显。",
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
                        Text("新增档案")
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
                        Text(if (saving) "保存中" else "保存 AI 档案")
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
                        profile.name.ifBlank { "未命名档案" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        profile.provider.ifBlank { "未设置 Provider" },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (profile.active) {
                    AssistChip(
                        onClick = onConfigure,
                        label = { Text("默认") },
                        enabled = !saving,
                    )
                }
            }
            Text(
                profile.model.ifBlank { "未设置模型" },
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
                    if (profile.hasApiKey || profile.apiKeyInput.isNotBlank()) "有密钥" else "无密钥",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
                if (profile.keyUnavailable) {
                    Text(
                        "密钥异常",
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
                    Text("配置")
                }
                TextButton(
                    onClick = onSetDefault,
                    enabled = !profile.active && !saving,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(if (profile.active) "当前默认" else "设为默认")
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
                        "详细配置",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "修改当前档案后保存生效。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                TextButton(onClick = onClose) {
                    Text("收起")
                }
            }
            OutlinedTextField(
                value = profile.name,
                onValueChange = { viewModel.updateAIProfileName(index, it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("名称") },
                enabled = controlsEnabled,
            )
            OutlinedTextField(
                value = profile.provider,
                onValueChange = { viewModel.updateAIProfileProvider(index, it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Provider") },
                placeholder = { Text("anthropic / openai / workers-ai") },
                enabled = controlsEnabled,
            )
            OutlinedTextField(
                value = profile.baseUrl,
                onValueChange = { viewModel.updateAIProfileBaseUrl(index, it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Base URL") },
                enabled = controlsEnabled,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = profile.model,
                    onValueChange = { viewModel.updateAIProfileModel(index, it) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("模型") },
                    enabled = controlsEnabled,
                )
                TextButton(
                    onClick = { viewModel.loadAIModels(index) },
                    enabled = controlsEnabled,
                ) {
                    Text(if (loadingModels) "获取中" else "获取模型")
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
                    label = { Text("温度") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = controlsEnabled,
                )
                OutlinedTextField(
                    value = profile.maxTokensInput,
                    onValueChange = { viewModel.updateAIProfileMaxTokens(index, it) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("最大 Tokens") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = controlsEnabled,
                )
            }
            OutlinedTextField(
                value = profile.apiKeyInput,
                onValueChange = { viewModel.updateAIProfileApiKey(index, it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("API 密钥") },
                placeholder = { Text(if (profile.hasApiKey) "已配置，留空保持不变" else "未配置") },
                visualTransformation = PasswordVisualTransformation(),
                enabled = controlsEnabled,
            )
            if (profile.keyUnavailable) {
                Text(
                    "当前密钥无法解密，请重新填写。",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.testAIProfile(index) }, enabled = controlsEnabled) {
                    Text(if (testing) "测试中" else "测试连接")
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
                    Text(if (confirmingDelete) "确认删除" else "删除")
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
