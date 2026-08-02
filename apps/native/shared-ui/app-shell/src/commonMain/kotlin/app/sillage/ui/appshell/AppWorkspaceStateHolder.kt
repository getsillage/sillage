package app.sillage.ui.appshell

import app.sillage.core.domain.records.Memo
import app.sillage.features.ask.AskFeatureStateHolder
import app.sillage.features.records.RecordsFeatureStateHolder
import app.sillage.features.settings.AIProfileDraft
import app.sillage.features.settings.SettingsFeatureStateHolder

/**
 * Platform-neutral ownership for feature state that belongs to one client workspace.
 *
 * Authentication and synchronization remain independent because they coordinate
 * platform sessions and transport. Records, settings, and Ask are cleared and
 * seeded together whenever the active client workspace changes.
 */
data class AppWorkspaceStateHolder(
    val records: RecordsFeatureStateHolder = RecordsFeatureStateHolder(),
    val settings: SettingsFeatureStateHolder = SettingsFeatureStateHolder(),
    val ask: AskFeatureStateHolder = AskFeatureStateHolder(),
) {
    inline fun updateRecords(
        transform: (RecordsFeatureStateHolder) -> RecordsFeatureStateHolder,
    ): AppWorkspaceStateHolder = copy(records = transform(records))

    inline fun updateSettings(
        transform: (SettingsFeatureStateHolder) -> SettingsFeatureStateHolder,
    ): AppWorkspaceStateHolder = copy(settings = transform(settings))

    inline fun updateAsk(
        transform: (AskFeatureStateHolder) -> AskFeatureStateHolder,
    ): AppWorkspaceStateHolder = copy(ask = transform(ask))

    /** Clears all interactive feature ownership for a client-context change. */
    fun clearClientWorkspace(
        settingsProfiles: List<AIProfileDraft> = emptyList(),
        settingsAutoSummaryEnabled: Boolean = false,
        askInvalidateStream: Boolean = false,
        askInvalidateVariant: Boolean = false,
    ): AppWorkspaceStateHolder {
        return copy(
            records = records.clearInteractiveSurface(),
            settings = settings.clearWorkspace(
                profiles = settingsProfiles,
                autoSummaryEnabled = settingsAutoSummaryEnabled,
            ),
            ask = ask.clearWorkspace(
                invalidateStream = askInvalidateStream,
                invalidateVariant = askInvalidateVariant,
            ),
        )
    }

    /** Clears interactive ownership and seeds a locally backed offline workspace. */
    fun enterOfflineClientWorkspace(
        memos: List<Memo>,
        settingsProfiles: List<AIProfileDraft>,
        settingsAutoSummaryEnabled: Boolean,
    ): AppWorkspaceStateHolder {
        val cleared = clearClientWorkspace(
            settingsProfiles = settingsProfiles,
            settingsAutoSummaryEnabled = settingsAutoSummaryEnabled,
        )
        return cleared.copy(
            records = cleared.records.replaceVisibleRecords(memos),
        )
    }
}
