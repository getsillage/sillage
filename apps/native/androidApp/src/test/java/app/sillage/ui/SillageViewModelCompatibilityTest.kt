package app.sillage.ui

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.sillage.BuildConfig
import app.sillage.data.SessionStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SillageViewModelCompatibilityTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        context.getSharedPreferences("sillage.session", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("sillage.local_data", Context.MODE_PRIVATE).edit().clear().commit()
        server = MockWebServer()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun incompatibleAndroidBuildBlocksOnlineBootstrapButKeepsOfflineModeAvailable() = runBlocking {
        val requiredVersion = BuildConfig.VERSION_CODE + 1
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"initialized":true,"serverVersion":"0.3.0","serverRevision":"revision","apiVersion":"v1","minimumAndroidVersionCode":$requiredVersion}""",
                ),
        )
        SessionStore(context).apply {
            saveBaseUrl(server.url("/").toString())
            saveAppMode(SessionStore.MODE_ONLINE)
        }

        val viewModel = SillageViewModel(context)
        val blocked = withTimeout(5_000) {
            while (!viewModel.state.value.androidUpdateRequired || viewModel.state.value.loading) {
                shadowOf(Looper.getMainLooper()).idle()
                delay(10)
            }
            viewModel.state.value
        }

        assertEquals(Screen.Server, blocked.clientContext.screen)
        assertEquals("0.3.0", blocked.serverVersion)
        assertEquals("revision", blocked.serverRevision)
        assertEquals("v1", blocked.apiVersion)
        assertEquals(requiredVersion, blocked.minimumAndroidVersionCode)
        assertTrue(blocked.authError?.contains(requiredVersion.toString()) == true)
        assertEquals(1, server.requestCount)

        viewModel.useOfflineMode()
        assertEquals(SessionStore.MODE_OFFLINE, viewModel.state.value.clientContext.appMode)
        assertEquals(Screen.Memos, viewModel.state.value.clientContext.screen)
        assertFalse(viewModel.state.value.loading)

        viewModel.syncFromServer()
        assertEquals("同步需要在线模式。", viewModel.state.value.error)
    }
}
