package app.sillage.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.sillage.core.domain.records.Memo
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SillageApiTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private lateinit var oldServer: MockWebServer
    private lateinit var newServer: MockWebServer

    @Before
    fun setUp() {
        context.getSharedPreferences("sillage.session", Context.MODE_PRIVATE).edit().clear().commit()
        oldServer = MockWebServer()
        newServer = MockWebServer()
    }

    @Test
    fun bootstrapParsesReleaseCompatibilityMetadata() = runBlocking {
        oldServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"initialized":true,"serverVersion":"0.3.0","serverRevision":"abc123","apiVersion":"v1","minimumAndroidVersionCode":12}""",
                ),
        )
        val sessionStore = SessionStore(context).apply {
            saveBaseUrl(oldServer.url("/").toString())
        }

        val bootstrap = SillageApi(sessionStore).bootstrap()

        assertTrue(bootstrap.initialized)
        assertEquals("0.3.0", bootstrap.serverVersion)
        assertEquals("abc123", bootstrap.serverRevision)
        assertEquals("v1", bootstrap.apiVersion)
        assertEquals(12, bootstrap.minimumAndroidVersionCode)
        assertEquals("/api/v1/auth/bootstrap", oldServer.takeRequest().path)
    }

    @Test
    fun recentlyDeletedAndLifecycleRequestsUseCanonicalRoutes() = runBlocking {
        oldServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"memos":[],"nextCursor":""}"""),
        )
        oldServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"memo":${memoJson(deleted = false, purged = false)}}"""),
        )
        oldServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"memo":${memoJson(deleted = true, purged = true)}}"""),
        )
        val sessionStore = SessionStore(context).apply {
            saveBaseUrl(oldServer.url("/").toString())
        }
        setLegacyAccessToken("token")
        val api = SillageApi(sessionStore)
        val deletedMemo = testMemo(deletedAt = "2026-07-30T01:00:00Z")

        api.listMemos(archived = null, deleted = true)
        api.restoreMemo(deletedMemo)
        api.purgeMemo(deletedMemo)

        val listRequest = oldServer.takeRequest()
        assertEquals("/api/v1/memos?limit=200&favorited=false&deleted=true", listRequest.path)
        val restoreRequest = oldServer.takeRequest()
        assertEquals("POST", restoreRequest.method)
        assertEquals("/api/v1/memos/memo-1:restore", restoreRequest.path)
        assertEquals(4L, JSONObject(restoreRequest.body.readUtf8()).getLong("expectedVersion"))
        val purgeRequest = oldServer.takeRequest()
        assertEquals("POST", purgeRequest.method)
        assertEquals("/api/v1/memos/memo-1:purge", purgeRequest.path)
        assertEquals(4L, JSONObject(purgeRequest.body.readUtf8()).getLong("expectedVersion"))
    }

    @Test
    fun pushMemosPreservesConflictDetailsForUiResolution() = runBlocking {
        oldServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    JSONObject()
                        .put(
                            "results",
                            JSONArray().put(
                                JSONObject()
                                    .put("status", "conflict")
                                    .put("mutationId", "mutation-conflict")
                                    .put("resourceId", "memo-1")
                                    .put("clientVersion", 4)
                                    .put("serverVersion", 5)
                                    .put(
                                        "serverResource",
                                        JSONObject(memoJson(deleted = false, purged = false))
                                            .put("version", 5)
                                            .put("content", "服务端并发版本"),
                                    ),
                            ),
                        )
                        .toString(),
                ),
        )
        val sessionStore = SessionStore(context).apply {
            saveBaseUrl(oldServer.url("/").toString())
        }
        setLegacyAccessToken("token")

        val summary = SillageApi(sessionStore).pushMemos(
            listOf(
                PendingMemoSync(
                    memo = testMemo(),
                    baseVersion = 3,
                    mutationId = "mutation-conflict",
                    action = "update",
                ),
            ),
        )

        assertEquals(0, summary.applied)
        assertEquals(1, summary.conflict)
        assertEquals(0, summary.rejected)
        val conflict = summary.conflictMemoSyncs.single()
        assertEquals("mutation-conflict", conflict.mutationId)
        assertEquals("memo-1", conflict.resourceId)
        assertEquals(4L, conflict.clientVersion)
        assertEquals(5L, conflict.serverVersion)
        assertEquals("服务端并发版本", conflict.serverMemo?.content)
    }

    @After
    fun tearDown() {
        oldServer.shutdown()
        newServer.shutdown()
    }

    @Test
    fun authenticatedRequestSnapshotsTokenBeforeIoDispatch() = runBlocking {
        oldServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "sillage_refresh=old-cookie; Path=/; HttpOnly")
                .setBody("""{"memos":[],"nextCursor":""}"""),
        )
        val sessionStore = SessionStore(context).apply {
            saveBaseUrl(oldServer.url("/").toString())
        }
        setLegacyAccessToken("old-token")
        val dispatcher = PausedDispatcher()
        val api = SillageApi(sessionStore, dispatcher)

        val request = launch(start = CoroutineStart.UNDISPATCHED) {
            api.listMemos()
        }
        try {
            assertTrue(dispatcher.hasTasks())

            sessionStore.saveBaseUrl(newServer.url("/").toString())
            setLegacyAccessToken("new-token")
            setLegacyCookies("sillage_refresh=new-cookie; path=/; httponly")
            dispatcher.runNext()
            withTimeout(5_000) { request.join() }
        } finally {
            request.cancel()
            dispatcher.runAll()
        }

        val recorded = oldServer.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull(recorded)
        assertEquals("Bearer old-token", recorded?.getHeader("Authorization"))
        assertEquals(null, recorded?.getHeader("Cookie"))
        assertEquals(0, newServer.requestCount)
        assertTrue(sessionStore.cookieHeaders().any { "new-cookie" in it })
        assertTrue(sessionStore.cookieHeaders().none { "old-cookie" in it })

        newServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"memos":[],"nextCursor":""}"""),
        )
        SillageApi(sessionStore).listMemos()
        val newRequest = newServer.takeRequest(1, TimeUnit.SECONDS)
        assertEquals("sillage_refresh=new-cookie", newRequest?.getHeader("Cookie"))
    }

    @Test
    fun retryDoesNotSendNewServerTokenToOriginalServer() = runBlocking {
        oldServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"message":"unauthorized"}"""),
        )
        val sessionStore = SessionStore(context).apply {
            saveBaseUrl(oldServer.url("/").toString())
        }
        setLegacyAccessToken("old-token")
        val dispatcher = PausedDispatcher()
        val api = SillageApi(sessionStore, dispatcher)
        var failure: Throwable? = null

        val request = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                api.listMemos()
            } catch (error: Throwable) {
                failure = error
            }
        }
        try {
            assertTrue(dispatcher.hasTasks())

            sessionStore.saveBaseUrl(newServer.url("/").toString())
            setLegacyAccessToken("new-token")
            dispatcher.runNext()
            withTimeout(5_000) { request.join() }
        } finally {
            request.cancel()
            dispatcher.runAll()
        }

        val recorded = oldServer.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull(recorded)
        assertEquals("Bearer old-token", recorded?.getHeader("Authorization"))
        assertEquals(1, oldServer.requestCount)
        assertEquals(0, newServer.requestCount)
        assertEquals("服务器配置已更改", failure?.message)
    }

    @Test
    fun retryUsesRefreshedTokenOnTheSameServer() = runBlocking {
        oldServer.enqueue(MockResponse().setResponseCode(401))
        oldServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"memos":[],"nextCursor":""}"""),
        )
        val sessionStore = SessionStore(context).apply {
            saveBaseUrl(oldServer.url("/").toString())
        }
        setLegacyAccessToken("old-token")
        val dispatcher = PausedDispatcher()
        val api = SillageApi(sessionStore, dispatcher)

        val request = launch(start = CoroutineStart.UNDISPATCHED) {
            api.listMemos()
        }
        try {
            assertTrue(dispatcher.hasTasks())

            setLegacyAccessToken("refreshed-token")
            dispatcher.runNext()
            withTimeout(5_000) { request.join() }
        } finally {
            request.cancel()
            dispatcher.runAll()
        }

        val first = oldServer.takeRequest(1, TimeUnit.SECONDS)
        val retry = oldServer.takeRequest(1, TimeUnit.SECONDS)
        assertEquals("Bearer old-token", first?.getHeader("Authorization"))
        assertEquals("Bearer refreshed-token", retry?.getHeader("Authorization"))
        assertEquals(2, oldServer.requestCount)
    }

    @Test
    fun nestedBaseRequestDoesNotUseRootSessionAfterBasePathChanges() = runBlocking {
        oldServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "sillage_refresh=stale-tenant-cookie; Path=/; HttpOnly")
                .setBody("""{"memos":[],"nextCursor":""}"""),
        )
        val sessionStore = SessionStore(context).apply {
            saveBaseUrl(oldServer.url("/tenant").toString())
        }
        setLegacyAccessToken("tenant-token")
        setLegacyCookies("sillage_refresh=tenant-cookie; path=/; httponly")
        val dispatcher = PausedDispatcher()
        val api = SillageApi(sessionStore, dispatcher)

        val request = launch(start = CoroutineStart.UNDISPATCHED) {
            api.listMemos()
        }
        try {
            assertTrue(dispatcher.hasTasks())

            sessionStore.saveBaseUrl(oldServer.url("/").toString())
            setLegacyAccessToken("root-token")
            setLegacyCookies("sillage_refresh=root-cookie; path=/; httponly")
            dispatcher.runNext()
            withTimeout(5_000) { request.join() }
        } finally {
            request.cancel()
            dispatcher.runAll()
        }

        val staleRequest = oldServer.takeRequest(1, TimeUnit.SECONDS)
        assertEquals(
            "/tenant/api/v1/memos?limit=200&archived=false&favorited=false&deleted=false",
            staleRequest?.path,
        )
        assertEquals("Bearer tenant-token", staleRequest?.getHeader("Authorization"))
        assertEquals(null, staleRequest?.getHeader("Cookie"))
        assertTrue(sessionStore.cookieHeaders().any { "root-cookie" in it })
        assertTrue(sessionStore.cookieHeaders().none { "stale-tenant-cookie" in it })

        oldServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"memos":[],"nextCursor":""}"""),
        )
        SillageApi(sessionStore).listMemos()
        val rootRequest = oldServer.takeRequest(1, TimeUnit.SECONDS)
        assertEquals(
            "/api/v1/memos?limit=200&archived=false&favorited=false&deleted=false",
            rootRequest?.path,
        )
        assertEquals("sillage_refresh=root-cookie", rootRequest?.getHeader("Cookie"))
    }

    @Test
    fun staleUnauthorizedResponseCannotRefreshAfterReturningToSameBase() = runBlocking {
        oldServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .addHeader("Set-Cookie", "sillage_refresh=stale-cookie; Path=/; HttpOnly")
                .setBody("""{"message":"unauthorized"}"""),
        )
        val serverA = oldServer.url("/tenant").toString()
        val sessionStore = SessionStore(context).apply {
            saveBaseUrl(serverA)
        }
        setLegacyAccessToken("old-token")
        setLegacyCookies("sillage_refresh=old-cookie; path=/; httponly")
        val dispatcher = PausedDispatcher()
        val api = SillageApi(sessionStore, dispatcher)
        var failure: Throwable? = null

        val request = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                api.listMemos()
            } catch (error: Throwable) {
                failure = error
            }
        }
        try {
            assertTrue(dispatcher.hasTasks())

            sessionStore.saveBaseUrl(newServer.url("/").toString())
            sessionStore.saveBaseUrl(serverA)
            setLegacyAccessToken("old-token")
            setLegacyCookies("sillage_refresh=new-cookie; path=/; httponly")
            dispatcher.runNext()
            withTimeout(5_000) { request.join() }
        } finally {
            request.cancel()
            dispatcher.runAll()
        }

        val staleRequest = oldServer.takeRequest(1, TimeUnit.SECONDS)
        assertEquals("Bearer old-token", staleRequest?.getHeader("Authorization"))
        assertEquals(null, staleRequest?.getHeader("Cookie"))
        assertEquals("服务器配置已更改", failure?.message)
        assertEquals(1, oldServer.requestCount)
        assertEquals(0, newServer.requestCount)
        assertTrue(sessionStore.cookieHeaders().any { "new-cookie" in it })
        assertTrue(sessionStore.cookieHeaders().none { "stale-cookie" in it })
    }

    @Test
    fun authenticatedRedirectCannotChangeConfiguredBasePath() = runBlocking {
        oldServer.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", oldServer.url("/tenant/child/api/v1/collect")),
        )
        oldServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"memos":[],"nextCursor":""}"""),
        )
        val sessionStore = SessionStore(context).apply {
            saveBaseUrl(oldServer.url("/tenant").toString())
        }
        setLegacyAccessToken("tenant-token")

        val failure = runCatching { SillageApi(sessionStore).listMemos() }.exceptionOrNull()

        assertEquals("服务器配置已更改", failure?.message)
        val initialRequest = oldServer.takeRequest(1, TimeUnit.SECONDS)
        assertEquals(
            "/tenant/api/v1/memos?limit=200&archived=false&favorited=false&deleted=false",
            initialRequest?.path,
        )
        assertEquals("Bearer tenant-token", initialRequest?.getHeader("Authorization"))
        assertEquals(1, oldServer.requestCount)
    }

    @Test
    fun lateRefreshCannotRestoreSessionAfterContextChanges() = runBlocking {
        val refreshStarted = CountDownLatch(1)
        val releaseRefresh = CountDownLatch(1)
        oldServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path?.substringBefore('?')) {
                    "/api/v1/memos" -> MockResponse().setResponseCode(401)
                    "/api/v1/auth/refresh" -> {
                        refreshStarted.countDown()
                        releaseRefresh.await(5, TimeUnit.SECONDS)
                        MockResponse()
                            .setResponseCode(200)
                            .addHeader("Set-Cookie", "sillage_refresh=late-cookie; Path=/; HttpOnly")
                            .setBody(
                                """{"account":{"id":"old-account","username":"old","displayName":"Old"},"accessToken":"late-token","expiresAt":"2099-01-01T00:00:00Z"}""",
                            )
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val sessionStore = SessionStore(context).apply {
            saveBaseUrl(oldServer.url("/").toString())
        }
        setLegacySession("old-account", "old", "Old", "old-token")
        setLegacyCookies("sillage_refresh=old-cookie; path=/; httponly")
        val request = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { SillageApi(sessionStore).listMemos() }.exceptionOrNull()
        }

        try {
            val didStartRefresh = refreshStarted.await(5, TimeUnit.SECONDS)
            assertTrue(
                "refresh did not start; requestCount=${oldServer.requestCount}",
                didStartRefresh,
            )
            sessionStore.saveBaseUrl(newServer.url("/").toString())
            setLegacySession("new-account", "new", "New", "new-token")
            setLegacyCookies("sillage_refresh=new-cookie; path=/; httponly")
            releaseRefresh.countDown()

            val failure = withTimeout(5_000) { request.await() }
            assertEquals("服务器配置已更改", failure?.message)
        } finally {
            releaseRefresh.countDown()
        }

        assertEquals("new-token", sessionStore.accessToken())
        assertEquals("new-account", sessionStore.account()?.id)
        assertTrue(sessionStore.cookieHeaders().any { "new-cookie" in it })
        assertTrue(sessionStore.cookieHeaders().none { "late-cookie" in it })
        assertEquals(2, oldServer.requestCount)
        assertEquals(0, newServer.requestCount)
    }

    @Test
    fun cancellingAuthCallReleasesSharedSessionMutex() = runBlocking {
        oldServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        oldServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":{"message":"invalid credentials"}}"""),
        )
        val sessionStore = SessionStore(context).apply {
            saveBaseUrl(oldServer.url("/").toString())
        }
        val firstApi = SillageApi(sessionStore)
        val secondApi = SillageApi(SessionStore(context))

        val first = launch(start = CoroutineStart.UNDISPATCHED) {
            firstApi.signIn("user", "password")
        }
        val firstRequest = oldServer.takeRequest(5, TimeUnit.SECONDS)
        assertEquals("/api/v1/auth/signin", firstRequest?.path)

        withTimeout(5_000) { first.cancelAndJoin() }
        val secondFailure = withTimeout(5_000) {
            runCatching { secondApi.signIn("user", "wrong") }.exceptionOrNull()
        }

        assertEquals("invalid credentials", secondFailure?.message)
        val secondRequest = oldServer.takeRequest(1, TimeUnit.SECONDS)
        assertEquals("/api/v1/auth/signin", secondRequest?.path)
        assertEquals(2, oldServer.requestCount)
    }

    private fun testMemo(deletedAt: String? = null): Memo {
        return Memo(
            id = "memo-1",
            content = "正文",
            entryDate = "2026-07-30",
            version = 4,
            createdAt = "2026-07-30T00:00:00Z",
            updatedAt = "2026-07-30T01:00:00Z",
            favoritedAt = null,
            archivedAt = null,
            deletedAt = deletedAt,
        )
    }

    private fun memoJson(deleted: Boolean, purged: Boolean): String {
        return JSONObject()
            .put("id", "memo-1")
            .put("content", if (purged) "" else "正文")
            .put("entryDate", if (purged) "1970-01-01" else "2026-07-30")
            .put("version", if (purged) 5 else 4)
            .put("createdAt", "2026-07-30T00:00:00Z")
            .put("updatedAt", "2026-07-30T02:00:00Z")
            .put("favoritedAt", JSONObject.NULL)
            .put("archivedAt", JSONObject.NULL)
            .put("deletedAt", if (deleted) "2026-07-30T01:00:00Z" else JSONObject.NULL)
            .put("purgedAt", if (purged) "2026-07-30T02:00:00Z" else JSONObject.NULL)
            .toString()
    }

    private fun setLegacyAccessToken(token: String) {
        context.getSharedPreferences("sillage.session", Context.MODE_PRIVATE)
            .edit()
            .putString("access_token", token)
            .commit()
    }

    private fun setLegacySession(
        accountId: String,
        username: String,
        displayName: String,
        accessToken: String,
    ) {
        context.getSharedPreferences("sillage.session", Context.MODE_PRIVATE)
            .edit()
            .putString("access_token", accessToken)
            .putString("expires_at", "2099-01-01T00:00:00Z")
            .putString("account_id", accountId)
            .putString("username", username)
            .putString("display_name", displayName)
            .commit()
    }

    private fun setLegacyCookies(vararg headers: String) {
        val cookies = JSONArray()
        headers.forEach(cookies::put)
        context.getSharedPreferences("sillage.session", Context.MODE_PRIVATE)
            .edit()
            .putString("cookies", cookies.toString())
            .commit()
    }
}

private class PausedDispatcher : CoroutineDispatcher() {
    private val tasks = ConcurrentLinkedQueue<Runnable>()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        tasks.add(block)
    }

    fun hasTasks(): Boolean = tasks.isNotEmpty()

    fun runNext() {
        requireNotNull(tasks.poll()).run()
    }

    fun runAll() {
        while (tasks.isNotEmpty()) {
            runNext()
        }
    }
}
