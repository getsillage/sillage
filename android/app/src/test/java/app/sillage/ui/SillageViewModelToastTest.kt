package app.sillage.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.sillage.R
import app.sillage.data.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SillageViewModelToastTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clearPreferences() {
        context.getSharedPreferences("sillage.session", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun emptyServerValidationStaysInTheFormWithoutDuplicateToastEvents() = runBlocking {
        val viewModel = SillageViewModel(context)

        viewModel.saveServer()
        viewModel.saveServer()

        assertEquals("请先填写服务器地址。", viewModel.state.value.authError)
        assertNull(withTimeoutOrNull(100) { viewModel.toastEvents.first() })
        assertEquals(SessionStore.LANGUAGE_ZH_CN, viewModel.state.value.languageMode)

        viewModel.setLanguageMode(SessionStore.LANGUAGE_EN)

        assertEquals(
            "Enter a server address first.",
            viewModel.state.value.authError,
        )

        viewModel.updateBaseUrl("https://example.com")

        assertNull(viewModel.state.value.authError)
        assertNull(viewModel.state.value.authErrorResourceId)
    }

    @Test
    fun switchingFromOfflineSettingsRoutesConnectionFailureToServerForm() {
        val sessionStore = SessionStore(context)
        sessionStore.saveBaseUrl("http://localhost:99999")
        sessionStore.saveAppMode(SessionStore.MODE_OFFLINE)
        val viewModel = SillageViewModel(context)
        viewModel.openAISettings()

        viewModel.useOnlineMode()

        assertEquals(Screen.Server, viewModel.state.value.screen)
        assertFalse(viewModel.state.value.loading)
        assertTrue(viewModel.state.value.authError?.isNotBlank() == true)
        assertNull(viewModel.state.value.authErrorResourceId)

        val originalError = viewModel.state.value.authError
        viewModel.setLanguageMode(SessionStore.LANGUAGE_EN)

        assertEquals(originalError, viewModel.state.value.authError)
        assertNull(viewModel.state.value.authErrorResourceId)
    }

    @Test
    fun authenticationErrorsKeepResourcesForLanguageChanges() {
        assertEquals(
            R.string.error_auth_invalid_credentials,
            readableErrorResourceId("账号或密码不正确", SessionStore.LANGUAGE_ZH_CN),
        )
        assertEquals(
            "Incorrect account or password.",
            context.localizedString(
                SessionStore.LANGUAGE_EN,
                requireNotNull(
                    readableErrorResourceId(
                        "账号或密码不正确",
                        SessionStore.LANGUAGE_ZH_CN,
                    ),
                ),
            ),
        )
        assertEquals(
            R.string.error_auth_rate_limited,
            readableErrorResourceId("尝试次数太多，请稍后再试", SessionStore.LANGUAGE_ZH_CN),
        )
        assertEquals(
            R.string.error_auth_already_initialized,
            readableErrorResourceId("这个实例已经初始化", SessionStore.LANGUAGE_ZH_CN),
        )
        assertEquals(
            R.string.error_auth_refresh_failed,
            readableErrorResourceId("刷新登录状态失败", SessionStore.LANGUAGE_ZH_CN),
        )
        assertEquals(
            R.string.error_request_failed,
            readableErrorResourceId("请求失败", SessionStore.LANGUAGE_ZH_CN),
        )
        assertNull(
            readableErrorResourceId("connection refused", SessionStore.LANGUAGE_EN),
        )
    }

    @Test
    fun remoteSignOutFailureClearsLocalSessionAndReportsLocalOnlyError() = runBlocking {
        var localSessionCleared = false
        val feedback = performSignOut(
            offlineMode = false,
            remoteSignOut = { throw IllegalStateException("server unavailable") },
            clearLocalSession = {
                localSessionCleared = true
                true
            },
        )

        assertTrue(localSessionCleared)
        assertEquals(
            SignOutFeedback(
                noticeResourceId = null,
                errorResourceId = R.string.error_sign_out_local_only,
            ),
            feedback,
        )
    }

    @Test
    fun offlineSignOutSkipsRemoteCallAndStillClearsLocalSession() = runBlocking {
        var remoteSignOutCalled = false
        var localSessionCleared = false

        val feedback = performSignOut(
            offlineMode = true,
            remoteSignOut = { remoteSignOutCalled = true },
            clearLocalSession = {
                localSessionCleared = true
                true
            },
        )

        assertFalse(remoteSignOutCalled)
        assertTrue(localSessionCleared)
        assertEquals(
            SignOutFeedback(
                noticeResourceId = R.string.notice_online_session_cleared,
                errorResourceId = null,
            ),
            feedback,
        )
    }

    @Test
    fun staleSignOutDoesNotReportSuccessWhenConditionalClearIsRejected() = runBlocking {
        val feedback = performSignOut(
            offlineMode = false,
            remoteSignOut = { throw IllegalStateException("old request failed") },
            clearLocalSession = { false },
        )

        assertNull(feedback)
    }

    @Test
    fun signOutCancellationIsRethrownAfterConditionalLocalClear() = runBlocking {
        var localSessionCleared = false

        val failure = runCatching {
            performSignOut(
                offlineMode = false,
                remoteSignOut = { throw CancellationException("cancelled") },
                clearLocalSession = {
                    localSessionCleared = true
                    true
                },
            )
        }.exceptionOrNull()

        assertTrue(localSessionCleared)
        assertTrue(failure is CancellationException)
    }
}
