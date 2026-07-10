package app.sillage.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class SessionRefreshCoordinatorTest {
    @Test
    fun concurrentUnauthorizedRequestsShareOneSuccessfulRefresh() = runBlocking {
        var token: String? = "expired"
        var refreshCalls = 0
        var clearCalls = 0
        val releaseRefresh = CompletableDeferred<Unit>()
        val coordinator = SessionRefreshCoordinator(
            currentAccessToken = { token },
            clearSession = {
                clearCalls += 1
                token = null
            },
            refresh = {
                refreshCalls += 1
                releaseRefresh.await()
                token = "fresh"
            },
        )

        val first = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.refreshAfterUnauthorized("expired")
        }
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.refreshAfterUnauthorized("expired")
        }

        assertFalse(first.isCompleted)
        assertFalse(second.isCompleted)
        releaseRefresh.complete(Unit)
        first.await()
        second.await()

        assertEquals(1, refreshCalls)
        assertEquals(0, clearCalls)
        assertEquals("fresh", token)
    }

    @Test
    fun failedSharedRefreshClearsTheExpiredSessionOnlyOnce() = runBlocking {
        var token: String? = "expired"
        var refreshCalls = 0
        var clearCalls = 0
        val releaseRefresh = CompletableDeferred<Unit>()
        val coordinator = SessionRefreshCoordinator(
            currentAccessToken = { token },
            clearSession = {
                clearCalls += 1
                token = null
            },
            refresh = {
                refreshCalls += 1
                releaseRefresh.await()
                throw ApiException("刷新失败")
            },
        )

        val first = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { coordinator.refreshAfterUnauthorized("expired") }.exceptionOrNull()
        }
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { coordinator.refreshAfterUnauthorized("expired") }.exceptionOrNull()
        }
        releaseRefresh.complete(Unit)

        assertNotNull(first.await())
        assertNotNull(second.await())
        assertEquals(1, refreshCalls)
        assertEquals(1, clearCalls)
        assertEquals(null, token)
    }

    @Test
    fun failedOldRefreshDoesNotClearANewerSession() = runBlocking {
        var token: String? = "expired"
        var clearCalls = 0
        val coordinator = SessionRefreshCoordinator(
            currentAccessToken = { token },
            clearSession = {
                clearCalls += 1
                token = null
            },
            refresh = {
                token = "new-session"
                throw ApiException("旧刷新失败")
            },
        )

        val failure = runCatching {
            coordinator.refreshAfterUnauthorized("expired")
        }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(0, clearCalls)
        assertEquals("new-session", token)
    }
}
