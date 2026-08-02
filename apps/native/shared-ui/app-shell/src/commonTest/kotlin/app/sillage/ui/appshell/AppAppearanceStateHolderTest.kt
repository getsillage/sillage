package app.sillage.ui.appshell

import app.sillage.core.application.preferences.ClientPreferenceValues
import kotlin.test.Test
import kotlin.test.assertEquals

class AppAppearanceStateHolderTest {
    @Test
    fun hydrateNormalizesPersistedValues() {
        val state = AppAppearanceStateHolder.hydrate(
            themeMode = "unexpected",
            languageMode = "en-US",
        )

        assertEquals(ClientPreferenceValues.THEME_LIGHT, state.themeMode)
        assertEquals(ClientPreferenceValues.LANGUAGE_EN, state.languageMode)
    }

    @Test
    fun toggleThemePreservesLanguage() {
        val state = AppAppearanceStateHolder(
            themeMode = ClientPreferenceValues.THEME_LIGHT,
            languageMode = ClientPreferenceValues.LANGUAGE_EN,
        )

        val dark = state.toggleTheme()
        val light = dark.toggleTheme()

        assertEquals(ClientPreferenceValues.THEME_DARK, dark.themeMode)
        assertEquals(ClientPreferenceValues.LANGUAGE_EN, dark.languageMode)
        assertEquals(ClientPreferenceValues.THEME_LIGHT, light.themeMode)
    }

    @Test
    fun settersNormalizeOnlyTheirOwnedValue() {
        val initial = AppAppearanceStateHolder(
            themeMode = ClientPreferenceValues.THEME_DARK,
            languageMode = ClientPreferenceValues.LANGUAGE_ZH_CN,
        )

        val english = initial.setLanguage("en-GB")
        val light = english.setTheme("unsupported")

        assertEquals(ClientPreferenceValues.THEME_DARK, english.themeMode)
        assertEquals(ClientPreferenceValues.LANGUAGE_EN, english.languageMode)
        assertEquals(ClientPreferenceValues.THEME_LIGHT, light.themeMode)
        assertEquals(ClientPreferenceValues.LANGUAGE_EN, light.languageMode)
    }
}
