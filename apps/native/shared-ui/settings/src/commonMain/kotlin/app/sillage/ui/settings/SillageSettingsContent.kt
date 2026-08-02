package app.sillage.ui.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

internal const val SillageSettingsOverviewKey = "settings-overview"
internal const val SillageSettingsAutoSummaryKey = "settings-auto-summary"
internal const val SillageSettingsAppearanceKey = "settings-appearance"
internal const val SillageSettingsServiceSyncKey = "settings-service-sync"
internal const val SillageSettingsDataKey = "settings-data"
internal const val SillageSettingsAccountKey = "settings-account"
internal const val SillageSettingsAboutKey = "settings-about"
internal const val SillageSettingsProfilesKey = "settings-profiles"

@Composable
fun SillageSettingsContent(
    loading: Boolean,
    errorMessage: String?,
    retryLabel: String,
    retryIcon: ImageVector,
    onRetry: () -> Unit,
    overview: @Composable () -> Unit,
    autoSummary: @Composable () -> Unit,
    appearance: @Composable () -> Unit,
    serviceSync: @Composable () -> Unit,
    data: @Composable () -> Unit,
    account: (@Composable () -> Unit)?,
    about: @Composable () -> Unit,
    profileItems: LazyListScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    SillageSettingsList(
        loading = loading,
        errorMessage = errorMessage,
        retryLabel = retryLabel,
        retryIcon = retryIcon,
        onRetry = onRetry,
        modifier = modifier,
    ) {
        item(key = SillageSettingsOverviewKey) { overview() }
        item(key = SillageSettingsAutoSummaryKey) { autoSummary() }
        item(key = SillageSettingsAppearanceKey) { appearance() }
        item(key = SillageSettingsServiceSyncKey) { serviceSync() }
        item(key = SillageSettingsDataKey) { data() }
        account?.let { content ->
            item(key = SillageSettingsAccountKey) { content() }
        }
        item(key = SillageSettingsAboutKey) { about() }
        profileItems()
    }
}

internal fun sillageSettingsSectionOrder(hasAccount: Boolean): List<String> = buildList {
    add(SillageSettingsOverviewKey)
    add(SillageSettingsAutoSummaryKey)
    add(SillageSettingsAppearanceKey)
    add(SillageSettingsServiceSyncKey)
    add(SillageSettingsDataKey)
    if (hasAccount) add(SillageSettingsAccountKey)
    add(SillageSettingsAboutKey)
    add(SillageSettingsProfilesKey)
}
