package app.sillage.ui.appshell

import app.sillage.core.application.preferences.ClientPreferenceValues
import app.sillage.core.application.preferences.normalizeLanguageMode
import app.sillage.core.application.preferences.normalizeThemeMode

/** Application-wide appearance state shared by native presentation hosts. */
data class AppAppearanceStateHolder(
    val themeMode: String = ClientPreferenceValues.THEME_LIGHT,
    val languageMode: String = ClientPreferenceValues.LANGUAGE_ZH_CN,
) {
    fun toggleTheme(): AppAppearanceStateHolder {
        return copy(
            themeMode = if (themeMode == ClientPreferenceValues.THEME_DARK) {
                ClientPreferenceValues.THEME_LIGHT
            } else {
                ClientPreferenceValues.THEME_DARK
            },
        )
    }

    fun setTheme(value: String): AppAppearanceStateHolder {
        return copy(themeMode = normalizeThemeMode(value))
    }

    fun setLanguage(value: String): AppAppearanceStateHolder {
        return copy(languageMode = normalizeLanguageMode(value))
    }

    companion object {
        fun hydrate(
            themeMode: String,
            languageMode: String,
        ): AppAppearanceStateHolder {
            return AppAppearanceStateHolder(
                themeMode = normalizeThemeMode(themeMode),
                languageMode = normalizeLanguageMode(languageMode),
            )
        }
    }
}
