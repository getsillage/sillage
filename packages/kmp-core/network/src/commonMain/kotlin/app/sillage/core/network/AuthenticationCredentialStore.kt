package app.sillage.core.network

/**
 * Platform security boundary for the one refresh credential needed to resume a native session.
 *
 * Implementations must use an operating-system credential vault when
 * [persistsAcrossLaunches] is true. Plaintext files, preferences, command-line
 * arguments, environment variables, and portable backups are forbidden.
 */
interface AuthenticationCredentialStore {
    val persistsAcrossLaunches: Boolean

    fun read(baseUrl: String): AuthenticationCredentialReadResult

    fun write(baseUrl: String, refreshCookie: String)

    fun delete(baseUrl: String)
}

sealed interface AuthenticationCredentialReadResult {
    data object Missing : AuthenticationCredentialReadResult

    data class Available(
        val refreshCookie: String,
    ) : AuthenticationCredentialReadResult {
        override fun toString(): String {
            return "AuthenticationCredentialReadResult.Available(refreshCookie=<redacted>)"
        }
    }
}

class AuthenticationCredentialStoreException : IllegalStateException(
    "The Sillage authentication credential store is unavailable.",
)

/** Safe default for hosts that have not yet supplied an OS credential-vault adapter. */
object MemoryOnlyAuthenticationCredentialStore : AuthenticationCredentialStore {
    override val persistsAcrossLaunches: Boolean = false

    override fun read(baseUrl: String): AuthenticationCredentialReadResult {
        return AuthenticationCredentialReadResult.Missing
    }

    override fun write(baseUrl: String, refreshCookie: String) = Unit

    override fun delete(baseUrl: String) = Unit
}
