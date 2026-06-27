package app.sillage.data

import android.content.Context
import org.json.JSONArray

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("sillage.session", Context.MODE_PRIVATE)

    fun baseUrl(): String = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL

    fun saveBaseUrl(value: String) {
        val normalized = normalizeBaseUrl(value)
        prefs.edit()
            .putString(KEY_BASE_URL, normalized)
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_ACCOUNT_ID)
            .remove(KEY_USERNAME)
            .remove(KEY_DISPLAY_NAME)
            .remove(KEY_COOKIES)
            .apply()
    }

    fun accessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun account(): Account? {
        val id = prefs.getString(KEY_ACCOUNT_ID, null) ?: return null
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val displayName = prefs.getString(KEY_DISPLAY_NAME, null) ?: username
        return Account(id = id, username = username, displayName = displayName)
    }

    fun saveSession(session: AuthSession) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, session.accessToken)
            .putString(KEY_EXPIRES_AT, session.expiresAt)
            .putString(KEY_ACCOUNT_ID, session.account.id)
            .putString(KEY_USERNAME, session.account.username)
            .putString(KEY_DISPLAY_NAME, session.account.displayName)
            .apply()
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_ACCOUNT_ID)
            .remove(KEY_USERNAME)
            .remove(KEY_DISPLAY_NAME)
            .remove(KEY_COOKIES)
            .apply()
    }

    fun cookieHeaders(): List<String> {
        val raw = prefs.getString(KEY_COOKIES, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index)
                if (value.isNotBlank()) {
                    add(value)
                }
            }
        }
    }

    fun saveCookieHeaders(headers: List<String>) {
        val array = JSONArray()
        headers.distinct().forEach { array.put(it) }
        prefs.edit().putString(KEY_COOKIES, array.toString()).apply()
    }

    fun themeMode(): String = prefs.getString(KEY_THEME_MODE, THEME_LIGHT) ?: THEME_LIGHT

    fun saveThemeMode(value: String) {
        prefs.edit().putString(KEY_THEME_MODE, normalizeThemeMode(value)).apply()
    }

    fun appMode(): String = prefs.getString(KEY_APP_MODE, MODE_ONLINE) ?: MODE_ONLINE

    fun hasAppModeSelection(): Boolean = prefs.getBoolean(KEY_APP_MODE_SELECTED, false)

    fun saveAppMode(value: String) {
        prefs.edit()
            .putString(KEY_APP_MODE, normalizeAppMode(value))
            .putBoolean(KEY_APP_MODE_SELECTED, true)
            .apply()
    }

    companion object {
        const val DEFAULT_BASE_URL = ""
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val MODE_ONLINE = "online"
        const val MODE_OFFLINE = "offline"

        private const val KEY_BASE_URL = "base_url"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_ACCOUNT_ID = "account_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_COOKIES = "cookies"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_APP_MODE = "app_mode"
        private const val KEY_APP_MODE_SELECTED = "app_mode_selected"

        fun normalizeBaseUrl(value: String): String {
            val trimmed = value.trim().trimEnd('/')
            return when {
                trimmed.isBlank() -> ""
                trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
                else -> "http://$trimmed"
            }
        }

        fun normalizeThemeMode(value: String): String {
            return if (value == THEME_DARK) THEME_DARK else THEME_LIGHT
        }

        fun normalizeAppMode(value: String): String {
            return if (value == MODE_OFFLINE) MODE_OFFLINE else MODE_ONLINE
        }
    }
}
