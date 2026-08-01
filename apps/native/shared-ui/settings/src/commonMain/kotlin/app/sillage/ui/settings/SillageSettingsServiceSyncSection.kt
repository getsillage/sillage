package app.sillage.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import app.sillage.ui.designsystem.SillageSettingsActionRow
import app.sillage.ui.designsystem.SillageSettingsSectionCard

data class SillageSettingsServiceSyncStrings(
    val sectionTitle: String,
    val refreshTitle: String,
    val refreshSupporting: String,
    val onlineCurrent: String,
    val onlineSwitch: String,
    val serverNotConfigured: String,
    val offlineCurrent: String,
    val offlineSwitch: String,
    val offlineSupporting: String,
    val serverTitle: String,
    val serverSupporting: String,
    val syncLocalTitle: String,
    val syncLocalSupporting: String,
    val syncCloudTitle: String,
    val syncCloudSupporting: String,
    val syncBothTitle: String,
    val syncBothSupporting: String,
)

data class SillageSettingsServiceSyncIcons(
    val refresh: ImageVector,
    val online: ImageVector,
    val offline: ImageVector,
    val server: ImageVector,
    val syncLocal: ImageVector,
    val syncCloud: ImageVector,
    val syncBoth: ImageVector,
)

@Composable
fun SillageSettingsServiceSyncSection(
    online: Boolean,
    baseUrl: String,
    strings: SillageSettingsServiceSyncStrings,
    icons: SillageSettingsServiceSyncIcons,
    loading: Boolean,
    clientContextBlocked: Boolean,
    onRefresh: () -> Unit,
    onUseOnline: () -> Unit,
    onUseOffline: () -> Unit,
    onOpenServer: () -> Unit,
    onSyncLocal: () -> Unit,
    onSyncCloud: () -> Unit,
    onSyncBoth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = sillageSettingsServiceSyncPresentation(
        online = online,
        baseUrl = baseUrl,
        strings = strings,
        loading = loading,
        clientContextBlocked = clientContextBlocked,
    )

    SillageSettingsSectionCard(
        title = presentation.sectionTitle,
        modifier = modifier,
    ) {
        SillageSettingsActionRow(
            icon = icons.refresh,
            title = presentation.refreshTitle,
            supporting = presentation.refreshSupporting,
            onClick = onRefresh,
            enabled = presentation.refreshEnabled,
        )
        SillageSettingsActionRow(
            icon = icons.online,
            title = presentation.onlineTitle,
            supporting = presentation.onlineSupporting,
            onClick = onUseOnline,
            enabled = presentation.onlineEnabled,
            selected = presentation.online,
            showDivider = true,
        )
        SillageSettingsActionRow(
            icon = icons.offline,
            title = presentation.offlineTitle,
            supporting = presentation.offlineSupporting,
            onClick = onUseOffline,
            enabled = presentation.offlineEnabled,
            selected = !presentation.online,
            showDivider = true,
        )
        if (presentation.online) {
            SillageSettingsActionRow(
                icon = icons.server,
                title = presentation.serverTitle,
                supporting = presentation.serverSupporting,
                onClick = onOpenServer,
                enabled = presentation.contextActionsEnabled,
                showDivider = true,
            )
            SillageSettingsActionRow(
                icon = icons.syncLocal,
                title = presentation.syncLocalTitle,
                supporting = presentation.syncLocalSupporting,
                onClick = onSyncLocal,
                enabled = presentation.contextActionsEnabled,
                showDivider = true,
            )
            SillageSettingsActionRow(
                icon = icons.syncCloud,
                title = presentation.syncCloudTitle,
                supporting = presentation.syncCloudSupporting,
                onClick = onSyncCloud,
                enabled = presentation.contextActionsEnabled,
                showDivider = true,
            )
            SillageSettingsActionRow(
                icon = icons.syncBoth,
                title = presentation.syncBothTitle,
                supporting = presentation.syncBothSupporting,
                onClick = onSyncBoth,
                enabled = presentation.contextActionsEnabled,
                showDivider = true,
            )
        }
    }
}

internal data class SillageSettingsServiceSyncPresentation(
    val sectionTitle: String,
    val refreshTitle: String,
    val refreshSupporting: String,
    val refreshEnabled: Boolean,
    val online: Boolean,
    val onlineTitle: String,
    val onlineSupporting: String,
    val onlineEnabled: Boolean,
    val offlineTitle: String,
    val offlineSupporting: String,
    val offlineEnabled: Boolean,
    val serverTitle: String,
    val serverSupporting: String,
    val syncLocalTitle: String,
    val syncLocalSupporting: String,
    val syncCloudTitle: String,
    val syncCloudSupporting: String,
    val syncBothTitle: String,
    val syncBothSupporting: String,
    val contextActionsEnabled: Boolean,
)

internal fun sillageSettingsServiceSyncPresentation(
    online: Boolean,
    baseUrl: String,
    strings: SillageSettingsServiceSyncStrings,
    loading: Boolean,
    clientContextBlocked: Boolean,
) = SillageSettingsServiceSyncPresentation(
    sectionTitle = strings.sectionTitle,
    refreshTitle = strings.refreshTitle,
    refreshSupporting = strings.refreshSupporting,
    refreshEnabled = !loading,
    online = online,
    onlineTitle = if (online) strings.onlineCurrent else strings.onlineSwitch,
    onlineSupporting = baseUrl.ifBlank { strings.serverNotConfigured },
    onlineEnabled = !online && !clientContextBlocked,
    offlineTitle = if (online) strings.offlineSwitch else strings.offlineCurrent,
    offlineSupporting = strings.offlineSupporting,
    offlineEnabled = online && !clientContextBlocked,
    serverTitle = strings.serverTitle,
    serverSupporting = strings.serverSupporting,
    syncLocalTitle = strings.syncLocalTitle,
    syncLocalSupporting = strings.syncLocalSupporting,
    syncCloudTitle = strings.syncCloudTitle,
    syncCloudSupporting = strings.syncCloudSupporting,
    syncBothTitle = strings.syncBothTitle,
    syncBothSupporting = strings.syncBothSupporting,
    contextActionsEnabled = !clientContextBlocked,
)
