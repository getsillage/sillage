package app.sillage.ui

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ApplicationProvider
import app.sillage.MainActivity
import app.sillage.R
import app.sillage.data.SessionStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LanguageResourcesTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clearPreferences() {
        context.getSharedPreferences("sillage.session", Context.MODE_PRIVATE).edit().clear().commit()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }

    @After
    fun resetLocales() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }

    @Test
    fun languagePreferenceDefaultsToChineseAndPersistsEnglish() {
        val store = SessionStore(context)

        assertEquals(SessionStore.LANGUAGE_ZH_CN, store.languageMode())

        store.saveLanguageMode(SessionStore.LANGUAGE_EN)

        assertEquals(SessionStore.LANGUAGE_EN, SessionStore(context).languageMode())
    }

    @Test
    fun localizedResourcesResolveBothSupportedLanguages() {
        assertEquals(
            "Choose how to use Sillage",
            context.localizedString(SessionStore.LANGUAGE_EN, R.string.mode_title),
        )
        assertEquals(
            "选择使用方式",
            context.localizedString(SessionStore.LANGUAGE_ZH_CN, R.string.mode_title),
        )
    }

    @Test
    fun mainActivityStartsWithAppCompatTheme() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()

        assertNotNull(controller.get())

        controller.pause().stop().destroy()
    }
}
