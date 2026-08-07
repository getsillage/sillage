package app.sillage.core.application.preferences

/** Canonical client preference tokens shared by native hosts. */
object ClientPreferenceValues {
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    const val LANGUAGE_EN = "en"
    const val LANGUAGE_ZH_CN = "zh-CN"

    const val MODE_ONLINE = "online"
    const val MODE_OFFLINE = "offline"
}

/** Normalizes a typed or pasted service base URL. */
fun normalizeBaseUrl(value: String): String {
    val trimmed = value.trim().trimEnd('/')
    return when {
        trimmed.isBlank() -> ""
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        "://" in trimmed -> trimmed
        else -> "https://$trimmed"
    }
}

fun normalizeThemeMode(value: String): String {
    return if (value == ClientPreferenceValues.THEME_DARK) {
        ClientPreferenceValues.THEME_DARK
    } else {
        ClientPreferenceValues.THEME_LIGHT
    }
}

fun normalizeLanguageMode(value: String): String {
    return if (value.trim().lowercase().startsWith(ClientPreferenceValues.LANGUAGE_EN)) {
        ClientPreferenceValues.LANGUAGE_EN
    } else {
        ClientPreferenceValues.LANGUAGE_ZH_CN
    }
}

fun normalizeAppMode(value: String): String {
    return if (value == ClientPreferenceValues.MODE_OFFLINE) {
        ClientPreferenceValues.MODE_OFFLINE
    } else {
        ClientPreferenceValues.MODE_ONLINE
    }
}
