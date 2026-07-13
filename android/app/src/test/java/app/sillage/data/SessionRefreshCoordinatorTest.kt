package app.sillage.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class SessionRefreshCoordinatorTest {
    @Test
    fun concurrentUnauthorizedRequestsShareOneSuccessfulRefresh() = runBlocking {
        var session = testSession(accessToken = "expired")
        var refreshCalls = 0
        var clearCalls = 0
        val releaseRefresh = CompletableDeferred<Unit>()
        val coordinator = SessionRefreshCoordinator(
            currentSession = { session },
            clearSession = { context, expectedToken ->
                if (session.matches(context, expectedToken)) {
                    clearCalls += 1
                    session = session.copy(
                        contextGeneration = context.contextGeneration + 1,
                        accessToken = null,
                    )
                    true
                } else {
                    false
                }
            },
            refresh = { _ ->
                refreshCalls += 1
                releaseRefresh.await()
                session = session.copy(accessToken = "fresh")
            },
        )

        val first = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.refreshAfterUnauthorized(TEST_CONTEXT, "expired")
        }
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.refreshAfterUnauthorized(TEST_CONTEXT, "expired")
        }

        assertFalse(first.isCompleted)
        assertFalse(second.isCompleted)
        releaseRefresh.complete(Unit)
        first.await()
        second.await()

        assertEquals(1, refreshCalls)
        assertEquals(0, clearCalls)
        assertEquals("fresh", session.accessToken)
    }

    @Test
    fun failedSharedRefreshClearsTheExpiredSessionOnlyOnce() = runBlocking {
        var session = testSession(accessToken = "expired")
        var refreshCalls = 0
        var clearCalls = 0
        val releaseRefresh = CompletableDeferred<Unit>()
        val coordinator = SessionRefreshCoordinator(
            currentSession = { session },
            clearSession = { context, expectedToken ->
                if (session.matches(context, expectedToken)) {
                    clearCalls += 1
                    session = session.copy(
                        contextGeneration = context.contextGeneration + 1,
                        accessToken = null,
                    )
                    true
                } else {
                    false
                }
            },
            refresh = { _ ->
                refreshCalls += 1
                releaseRefresh.await()
                throw ApiException("刷新失败")
            },
        )

        val first = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching {
                coordinator.refreshAfterUnauthorized(TEST_CONTEXT, "expired")
            }.exceptionOrNull()
        }
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching {
                coordinator.refreshAfterUnauthorized(TEST_CONTEXT, "expired")
            }.exceptionOrNull()
        }
        releaseRefresh.complete(Unit)

        assertNotNull(first.await())
        assertNotNull(second.await())
        assertEquals(1, refreshCalls)
        assertEquals(1, clearCalls)
        assertEquals(null, session.accessToken)
    }

    @Test
    fun failedOldRefreshDoesNotClearANewerSession() = runBlocking {
        var session = testSession(accessToken = "expired")
        var clearCalls = 0
        val coordinator = SessionRefreshCoordinator(
            currentSession = { session },
            clearSession = { context, expectedToken ->
                if (session.matches(context, expectedToken)) {
                    clearCalls += 1
                    session = session.copy(
                        contextGeneration = context.contextGeneration + 1,
                        accessToken = null,
                    )
                    true
                } else {
                    false
                }
            },
            refresh = { _ ->
                session = session.copy(accessToken = "new-session")
                throw ApiException("旧刷新失败")
            },
        )

        val failure = runCatching {
            coordinator.refreshAfterUnauthorized(TEST_CONTEXT, "expired")
        }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(0, clearCalls)
        assertEquals("new-session", session.accessToken)
    }

    @Test
    fun staleContextDoesNotRefreshOrClearCurrentSession() = runBlocking {
        var refreshCalls = 0
        var clearCalls = 0
        val coordinator = SessionRefreshCoordinator(
            currentSession = { testSession(contextGeneration = 2, accessToken = "same-token") },
            clearSession = { _, _ ->
                clearCalls += 1
                true
            },
            refresh = { _ -> refreshCalls += 1 },
        )

        val failure = runCatching {
            coordinator.refreshAfterUnauthorized(TEST_CONTEXT, "same-token")
        }.exceptionOrNull()

        assertEquals("服务器配置已更改", failure?.message)
        assertEquals(0, refreshCalls)
        assertEquals(0, clearCalls)
    }

    @Test
    fun sessionExclusiveOperationFromAnotherCoordinatorWaitsForRefresh() = runBlocking {
        var session = testSession(accessToken = "expired")
        val refreshStarted = CompletableDeferred<Unit>()
        val releaseRefresh = CompletableDeferred<Unit>()
        val exclusiveStarted = CompletableDeferred<Unit>()
        val coordinator = SessionRefreshCoordinator(
            currentSession = { session },
            clearSession = { _, _ -> false },
            refresh = { _ ->
                refreshStarted.complete(Unit)
                releaseRefresh.await()
                session = session.copy(accessToken = "fresh")
            },
        )
        val anotherCoordinator = SessionRefreshCoordinator(
            currentSession = { session },
            clearSession = { _, _ -> false },
            refresh = { error("unexpected refresh") },
        )

        val refresh = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.refreshAfterUnauthorized(TEST_CONTEXT, "expired")
        }
        refreshStarted.await()
        val exclusive = async(start = CoroutineStart.UNDISPATCHED) {
            anotherCoordinator.runSessionExclusive { exclusiveStarted.complete(Unit) }
        }
        yield()
        assertFalse(exclusiveStarted.isCompleted)

        releaseRefresh.complete(Unit)
        refresh.await()
        exclusive.await()
        assertEquals("fresh", session.accessToken)
        assertEquals(Unit, exclusiveStarted.await())
    }

    private fun testSession(
        contextGeneration: Long = TEST_CONTEXT.contextGeneration,
        accessToken: String?,
    ) = ClientSessionSnapshot(
        baseUrl = TEST_CONTEXT.baseUrl,
        contextGeneration = contextGeneration,
        accessToken = accessToken,
    )

    private fun ClientSessionSnapshot.matches(
        context: ClientRequestContext,
        expectedToken: String,
    ): Boolean {
        return baseUrl == context.baseUrl &&
            contextGeneration == context.contextGeneration &&
            accessToken == expectedToken
    }

    private companion object {
        val TEST_CONTEXT = ClientRequestContext(
            baseUrl = "https://example.test/tenant",
            contextGeneration = 1,
            serverBaseKey = "https://example.test/tenant",
        )
    }
}
