package app.sillage.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("sillage.session", Context.MODE_PRIVATE)
    private val securePrefs = SecurePreferences(prefs)

    fun baseUrl(): String = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL

    fun saveBaseUrl(value: String) {
        val normalized = normalizeBaseUrl(value)
        clearSecureSessionKeys(prefs.edit().putString(KEY_BASE_URL, normalized)).apply()
    }

    fun accessToken(): String? = securePrefs.getString(KEY_ACCESS_TOKEN, null)

    fun account(): Account? {
        val id = securePrefs.getString(KEY_ACCOUNT_ID, null) ?: return null
        val username = securePrefs.getString(KEY_USERNAME, null) ?: return null
        val displayName = securePrefs.getString(KEY_DISPLAY_NAME, null) ?: username
        return Account(id = id, username = username, displayName = displayName)
    }

    fun saveSession(session: AuthSession) {
        securePutAll(
            KEY_ACCESS_TOKEN to session.accessToken,
            KEY_EXPIRES_AT to session.expiresAt,
            KEY_ACCOUNT_ID to session.account.id,
            KEY_USERNAME to session.account.username,
            KEY_DISPLAY_NAME to session.account.displayName,
        ).apply()
    }

    fun clearSession() {
        clearSecureSessionKeys(prefs.edit()).apply()
    }

    fun cookieHeaders(): List<String> {
        val raw = securePrefs.getString(KEY_COOKIES, "[]") ?: "[]"
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
        securePrefs.putString(prefs.edit(), KEY_COOKIES, array.toString()).apply()
    }

    fun themeMode(): String = prefs.getString(KEY_THEME_MODE, THEME_LIGHT) ?: THEME_LIGHT

    fun saveThemeMode(value: String) {
        prefs.edit().putString(KEY_THEME_MODE, normalizeThemeMode(value)).apply()
    }

    fun languageMode(): String = prefs.getString(KEY_LANGUAGE_MODE, LANGUAGE_ZH_CN) ?: LANGUAGE_ZH_CN

    fun saveLanguageMode(value: String) {
        prefs.edit().putString(KEY_LANGUAGE_MODE, normalizeLanguageMode(value)).apply()
    }

    fun appMode(): String = prefs.getString(KEY_APP_MODE, MODE_ONLINE) ?: MODE_ONLINE

    fun hasAppModeSelection(): Boolean = prefs.getBoolean(KEY_APP_MODE_SELECTED, false)

    fun saveAppMode(value: String) {
        prefs.edit()
            .putString(KEY_APP_MODE, normalizeAppMode(value))
            .putBoolean(KEY_APP_MODE_SELECTED, true)
            .apply()
    }

    private fun securePutAll(vararg entries: Pair<String, String>): SharedPreferences.Editor {
        var editor = prefs.edit()
        for ((key, value) in entries) {
            editor = securePrefs.putString(editor, key, value)
        }
        return editor
    }

    private fun clearSecureSessionKeys(editor: SharedPreferences.Editor): SharedPreferences.Editor {
        var next = editor
        for (key in SECURE_SESSION_KEYS) {
            next = securePrefs.remove(next, key)
        }
        return next
    }

    companion object {
        const val DEFAULT_BASE_URL = ""
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val LANGUAGE_EN = "en"
        const val LANGUAGE_ZH_CN = "zh-CN"
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
        private const val KEY_LANGUAGE_MODE = "language_mode"
        private const val KEY_APP_MODE = "app_mode"
        private const val KEY_APP_MODE_SELECTED = "app_mode_selected"
        private val SECURE_SESSION_KEYS = listOf(
            KEY_ACCESS_TOKEN,
            KEY_EXPIRES_AT,
            KEY_ACCOUNT_ID,
            KEY_USERNAME,
            KEY_DISPLAY_NAME,
            KEY_COOKIES,
        )

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

        fun normalizeLanguageMode(value: String): String {
            return if (value.trim().lowercase().startsWith(LANGUAGE_EN)) LANGUAGE_EN else LANGUAGE_ZH_CN
        }

        fun normalizeAppMode(value: String): String {
            return if (value == MODE_OFFLINE) MODE_OFFLINE else MODE_ONLINE
        }
    }
}
