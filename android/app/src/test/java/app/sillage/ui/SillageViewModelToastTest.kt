package app.sillage.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.sillage.R
import app.sillage.data.SessionStore
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun repeatedEmptyServerValidationProducesTwoToastEvents() = runBlocking {
        val viewModel = SillageViewModel(context)

        viewModel.saveServer()
        viewModel.saveServer()

        val events = withTimeout(1_000) {
            viewModel.toastEvents.take(2).toList()
        }
        assertEquals(listOf(1L, 2L), events.map(UiToastEvent::id))
        assertEquals(listOf(UiToastType.ERROR, UiToastType.ERROR), events.map(UiToastEvent::type))
        assertEquals(
            listOf("请先填写服务器地址。", "请先填写服务器地址。"),
            events.map(UiToastEvent::message),
        )
        assertEquals(SessionStore.LANGUAGE_ZH_CN, viewModel.state.value.languageMode)
        assertEquals(
            listOf(SessionStore.LANGUAGE_ZH_CN, SessionStore.LANGUAGE_ZH_CN),
            events.map(UiToastEvent::languageMode),
        )
    }

    @Test
    fun remoteSignOutFailureClearsLocalSessionAndReportsLocalOnlyError() = runBlocking {
        var localSessionCleared = false
        val feedback = performSignOut(
            offlineMode = false,
            remoteSignOut = { throw IllegalStateException("server unavailable") },
            clearLocalSession = { localSessionCleared = true },
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
            clearLocalSession = { localSessionCleared = true },
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
}
