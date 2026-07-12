package app.sillage

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.core.os.LocaleListCompat
import app.sillage.data.SessionStore
import app.sillage.ui.SillageApp
import app.sillage.ui.SillageViewModel
import app.sillage.ui.theme.SillageTheme

class MainActivity : AppCompatActivity() {
    private val viewModel: SillageViewModel by viewModels {
        SillageViewModel.Factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val effectiveLanguage = applyStoredLanguage()
        super.onCreate(savedInstanceState)
        viewModel.setLanguageMode(effectiveLanguage)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
        )
        setContent {
            val state by viewModel.state.collectAsState()
            LaunchedEffect(state.languageMode) {
                applyLanguage(state.languageMode)
            }
            SillageTheme(darkTheme = state.themeMode == SessionStore.THEME_DARK) {
                SillageApp(viewModel = viewModel)
            }
        }
    }

    private fun applyStoredLanguage(): String {
        val sessionStore = SessionStore(applicationContext)
        val platformTags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val platformLanguage = platformTags
            .split(',')
            .firstOrNull()
            ?.let(SessionStore::normalizeLanguageMode)
        val language = if (platformTags.isNotBlank()) {
            platformLanguage ?: sessionStore.languageMode()
        } else {
            sessionStore.languageMode()
        }
        sessionStore.saveLanguageMode(language)
        applyLanguage(language)
        return language
    }

    private fun applyLanguage(language: String) {
        val normalized = SessionStore.normalizeLanguageMode(language)
        val locales = LocaleListCompat.forLanguageTags(normalized)
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != locales.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }
}
