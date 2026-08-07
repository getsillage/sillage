package app.sillage.core.application.preferences

import kotlin.test.Test
import kotlin.test.assertEquals

class ClientPreferenceValuesTest {
    @Test
    fun normalizeBaseUrlAddsHttpsAndTrimsSlash() {
        assertEquals("", normalizeBaseUrl("  "))
        assertEquals("https://example.com", normalizeBaseUrl("example.com/"))
        assertEquals("http://localhost:8080", normalizeBaseUrl("http://localhost:8080/"))
        assertEquals("https://a.example", normalizeBaseUrl(" https://a.example/ "))
        assertEquals("ftp://example.com", normalizeBaseUrl("ftp://example.com/"))
    }

    @Test
    fun normalizeThemeLanguageAndAppModeUseCanonicalTokens() {
        assertEquals(ClientPreferenceValues.THEME_DARK, normalizeThemeMode("dark"))
        assertEquals(ClientPreferenceValues.THEME_LIGHT, normalizeThemeMode("weird"))
        assertEquals(ClientPreferenceValues.LANGUAGE_EN, normalizeLanguageMode("en-US"))
        assertEquals(ClientPreferenceValues.LANGUAGE_ZH_CN, normalizeLanguageMode("zh"))
        assertEquals(ClientPreferenceValues.MODE_OFFLINE, normalizeAppMode("offline"))
        assertEquals(ClientPreferenceValues.MODE_ONLINE, normalizeAppMode("online"))
    }
}
