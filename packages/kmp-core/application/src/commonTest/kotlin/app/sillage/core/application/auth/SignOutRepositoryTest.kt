package app.sillage.core.application.auth

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SignOutRepositoryTest {
    @Test
    fun onlineSignOutUsesCapturedRemoteSession() {
        val session = FakeCapturedSignOutSession()
        val prepared = SignOutUseCase(SignOutRepository { session }).prepare(SignOutMode.Online)

        assertEquals(SignOutResult.SignedOut, runSignOutSuspend { prepared() })
        assertTrue(session.remoteCalled)
        assertFalse(session.localClearCalled)
    }

    @Test
    fun offlineSignOutClearsOnlyCapturedLocalSession() {
        val session = FakeCapturedSignOutSession()
        val prepared = SignOutUseCase(SignOutRepository { session }).prepare(SignOutMode.Offline)

        assertEquals(SignOutResult.OfflineSessionCleared, runSignOutSuspend { prepared() })
        assertFalse(session.remoteCalled)
        assertTrue(session.localClearCalled)
    }

    @Test
    fun remoteFailureFallsBackToCapturedLocalSession() {
        val session = FakeCapturedSignOutSession(remoteFailure = IllegalStateException("unavailable"))
        val prepared = SignOutUseCase(SignOutRepository { session }).prepare(SignOutMode.Online)

        assertEquals(
            SignOutResult.RemoteFailedLocalSessionCleared,
            runSignOutSuspend { prepared() },
        )
        assertTrue(session.localClearCalled)
    }

    @Test
    fun replacedSessionProducesNoResult() {
        val session = FakeCapturedSignOutSession(
            remoteFailure = IllegalStateException("stale request"),
            localClearAccepted = false,
        )
        val prepared = SignOutUseCase(SignOutRepository { session }).prepare(SignOutMode.Online)

        assertNull(runSignOutSuspend { prepared() })
    }

    @Test
    fun cancellationClearsCapturedSessionAndIsRethrown() {
        val session = FakeCapturedSignOutSession(remoteFailure = CancellationException("cancelled"))
        val prepared = SignOutUseCase(SignOutRepository { session }).prepare(SignOutMode.Online)

        val failure = runCatching { runSignOutSuspend { prepared() } }.exceptionOrNull()

        assertTrue(session.localClearCalled)
        assertTrue(failure is CancellationException)
    }

    @Test
    fun cancellationCleanupFailureTakesPrecedenceOverFalseSignOut() {
        val cleanupFailure = IllegalStateException("secure storage unavailable")
        val session = FakeCapturedSignOutSession(
            remoteFailure = CancellationException("cancelled"),
            localClearFailure = cleanupFailure,
        )
        val prepared = SignOutUseCase(SignOutRepository { session }).prepare(SignOutMode.Online)

        val failure = runCatching { runSignOutSuspend { prepared() } }.exceptionOrNull()

        assertTrue(session.localClearCalled)
        assertTrue(failure === cleanupFailure)
    }
}

private class FakeCapturedSignOutSession(
    private val remoteFailure: Throwable? = null,
    private val localClearAccepted: Boolean = true,
    private val localClearFailure: Throwable? = null,
) : CapturedSignOutSession {
    var remoteCalled = false
    var localClearCalled = false

    override suspend fun signOutRemote() {
        remoteCalled = true
        remoteFailure?.let { throw it }
    }

    override fun clearLocalSession(): Boolean {
        localClearCalled = true
        localClearFailure?.let { throw it }
        return localClearAccepted
    }
}

private fun <T> runSignOutSuspend(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(
        object : Continuation<T> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<T>) {
                outcome = result
            }
        },
    )
    return checkNotNull(outcome) { "Test coroutine did not complete synchronously" }.getOrThrow()
}
