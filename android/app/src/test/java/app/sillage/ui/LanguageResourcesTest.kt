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
        assertEquals(
            "Connection successful (gpt-test)",
            context.localizedString(
                SessionStore.LANGUAGE_EN,
                R.string.notice_ai_test_success,
                "gpt-test",
            ),
        )
        assertEquals(
            "连接成功（gpt-test）",
            context.localizedString(
                SessionStore.LANGUAGE_ZH_CN,
                R.string.notice_ai_test_success,
                "gpt-test",
            ),
        )
        assertEquals(
            "Model list loaded.",
            context.localizedString(SessionStore.LANGUAGE_EN, R.string.notice_ai_models_loaded),
        )
        assertEquals(
            "已获取模型列表。",
            context.localizedString(SessionStore.LANGUAGE_ZH_CN, R.string.notice_ai_models_loaded),
        )
        assertEquals(
            "API protocol",
            context.localizedString(SessionStore.LANGUAGE_EN, R.string.settings_provider),
        )
        assertEquals(
            "接口协议",
            context.localizedString(SessionStore.LANGUAGE_ZH_CN, R.string.settings_provider),
        )
        assertEquals(
            "Anthropic-compatible API",
            context.localizedString(SessionStore.LANGUAGE_EN, R.string.settings_provider_anthropic_compatible),
        )
        assertEquals(
            "兼容 Anthropic 接口协议",
            context.localizedString(SessionStore.LANGUAGE_ZH_CN, R.string.settings_provider_anthropic_compatible),
        )
        assertEquals(
            "OpenAI-compatible API",
            context.localizedString(SessionStore.LANGUAGE_EN, R.string.settings_provider_openai_compatible),
        )
        assertEquals(
            "兼容 OpenAI 接口协议",
            context.localizedString(SessionStore.LANGUAGE_ZH_CN, R.string.settings_provider_openai_compatible),
        )
        assertEquals(
            "Signed in.",
            context.localizedString(SessionStore.LANGUAGE_EN, R.string.notice_signed_in),
        )
        assertEquals(
            "已登录。",
            context.localizedString(SessionStore.LANGUAGE_ZH_CN, R.string.notice_signed_in),
        )
        assertEquals(
            "This device's sign-in was cleared, but the server sign-out failed. Other sessions may still be active.",
            context.localizedString(SessionStore.LANGUAGE_EN, R.string.error_sign_out_local_only),
        )
        assertEquals(
            "已清除本机登录信息，但服务器退出失败；其他会话可能仍处于登录状态。",
            context.localizedString(SessionStore.LANGUAGE_ZH_CN, R.string.error_sign_out_local_only),
        )
        assertEquals(
            "Generation stopped. Any generated content has been kept.",
            context.localizedString(SessionStore.LANGUAGE_EN, R.string.notice_ask_generation_stopped),
        )
        assertEquals(
            "已停止生成，已生成内容会保留。",
            context.localizedString(SessionStore.LANGUAGE_ZH_CN, R.string.notice_ask_generation_stopped),
        )
    }

    @Test
    fun mainActivityStartsWithAppCompatTheme() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()

        assertNotNull(controller.get())

        controller.pause().stop().destroy()
    }
}
