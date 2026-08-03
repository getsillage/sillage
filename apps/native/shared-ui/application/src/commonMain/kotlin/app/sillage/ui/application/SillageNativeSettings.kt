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
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.UploadFile
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
import androidx.compose.ui.unit.dp
import app.sillage.core.application.preferences.ClientPreferenceValues
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
                        SillageSettingsInfoRow(
                            label = strings.mode,
                            value = strings.offlineModeValue,
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
