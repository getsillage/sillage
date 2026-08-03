package app.sillage.core.application.auth

import kotlin.coroutines.cancellation.CancellationException

enum class SignOutMode {
    Online,
    Offline,
}

sealed interface SignOutResult {
    data object SignedOut : SignOutResult

    data object OfflineSessionCleared : SignOutResult

    data object RemoteFailedLocalSessionCleared : SignOutResult
}

/**
 * A context-bound sign-out capability captured before asynchronous execution.
 *
 * Implementations must apply both operations only to the session that existed
 * when this capability was created. If another session replaces it, local clear
 * returns false and remote sign-out should fail instead of mutating the new one.
 */
interface CapturedSignOutSession {
    suspend fun signOutRemote()

    fun clearLocalSession(): Boolean
}

fun interface SignOutRepository {
    fun captureSession(): CapturedSignOutSession
}

class SignOutUseCase(
    private val repository: SignOutRepository,
) {
    fun prepare(mode: SignOutMode): PreparedSignOut {
        return PreparedSignOut(mode, repository.captureSession())
    }
}

class PreparedSignOut internal constructor(
    private val mode: SignOutMode,
    private val session: CapturedSignOutSession,
) {
    /**
     * Returns null when the captured session has already been replaced.
     * Cancellation still clears that captured session when possible, then is
     * rethrown so structured concurrency remains intact. A cleanup failure
     * takes precedence because callers must not report sign-out while durable
     * credentials may still remain.
     */
    suspend operator fun invoke(): SignOutResult? {
        if (mode == SignOutMode.Offline) {
            return if (session.clearLocalSession()) {
                SignOutResult.OfflineSessionCleared
            } else {
                null
            }
        }

        return try {
            session.signOutRemote()
            SignOutResult.SignedOut
        } catch (error: CancellationException) {
            session.clearLocalSession()
            throw error
        } catch (_: Throwable) {
            if (session.clearLocalSession()) {
                SignOutResult.RemoteFailedLocalSessionCleared
            } else {
                null
            }
        }
    }
}
