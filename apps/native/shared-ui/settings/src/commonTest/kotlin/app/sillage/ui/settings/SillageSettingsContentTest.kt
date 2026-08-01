package app.sillage.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class SillageSettingsContentTest {
    @Test
    fun sectionOrderIncludesOptionalAccountBeforeAbout() {
        assertEquals(
            listOf(
                SillageSettingsOverviewKey,
                SillageSettingsAutoSummaryKey,
                SillageSettingsAppearanceKey,
                SillageSettingsServiceSyncKey,
                SillageSettingsDataKey,
                SillageSettingsAccountKey,
                SillageSettingsAboutKey,
                SillageSettingsProfilesKey,
            ),
            sillageSettingsSectionOrder(hasAccount = true),
        )
    }

    @Test
    fun sectionOrderOmitsAccountOffline() {
        assertEquals(
            listOf(
                SillageSettingsOverviewKey,
                SillageSettingsAutoSummaryKey,
                SillageSettingsAppearanceKey,
                SillageSettingsServiceSyncKey,
                SillageSettingsDataKey,
                SillageSettingsAboutKey,
                SillageSettingsProfilesKey,
            ),
            sillageSettingsSectionOrder(hasAccount = false),
        )
    }
}
