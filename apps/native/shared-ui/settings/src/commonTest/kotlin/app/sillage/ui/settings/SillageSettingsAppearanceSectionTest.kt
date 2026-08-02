package app.sillage.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SillageSettingsAppearanceSectionTest {
    private val strings = SillageSettingsAppearanceStrings(
        sectionTitle = "Appearance",
        darkModeTitle = "Dark mode",
        darkModeOn = "Dark mode is on",
        darkModeOff = "Dark mode is off",
        language = SillageSettingsLanguageStrings(
            title = "Language",
            supporting = "Choose the interface language.",
        ),
    )

    @Test
    fun presentationSelectsThemeSupportingText() {
        val dark = sillageSettingsAppearancePresentation(
            darkMode = true,
            strings = strings,
            enabled = true,
        )
        val light = sillageSettingsAppearancePresentation(
            darkMode = false,
            strings = strings,
            enabled = false,
        )

        assertEquals("Dark mode is on", dark.darkModeSupporting)
        assertTrue(dark.darkMode)
        assertTrue(dark.enabled)
        assertEquals("Dark mode is off", light.darkModeSupporting)
        assertFalse(light.darkMode)
        assertFalse(light.enabled)
    }
}
