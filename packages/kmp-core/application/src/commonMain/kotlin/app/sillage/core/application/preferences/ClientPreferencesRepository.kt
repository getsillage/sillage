package app.sillage.core.application.preferences

data class ClientPreferences(
    val themeMode: String = ClientPreferenceValues.THEME_LIGHT,
    val languageMode: String = ClientPreferenceValues.LANGUAGE_ZH_CN,
    val serverBaseUrl: String = "",
)

/** Device-local client preferences without exposing a platform storage API. */
interface ClientPreferencesRepository {
    fun loadPreferences(): ClientPreferences

    fun savePreferences(preferences: ClientPreferences)
}
