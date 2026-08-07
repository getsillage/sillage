package app.sillage.core.application.auth

import app.sillage.core.domain.auth.Account
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AuthenticationRepositoryTest {
    @Test
    fun focusedUseCasesDelegateSharedCommands() {
        val repository = CapturingAuthenticationRepository()
        val initialize = InitializeAccountCommand("owner", "Owner", "password")
        val signIn = SignInCommand("owner", "password")
        val changePassword = ChangePasswordCommand("password", "new-password")

        runAuthSuspend { InitializeAccountUseCase(repository)(initialize) }
        runAuthSuspend { SignInUseCase(repository)(signIn) }
        val account = runAuthSuspend { GetCurrentAccountUseCase(repository)() }
        runAuthSuspend { ChangePasswordUseCase(repository)(changePassword) }

        assertEquals(
            listOf(initialize, signIn, "current-account", changePassword),
            repository.operations,
        )
        assertEquals(repository.account, account)
    }

    @Test
    fun authenticationCommandsRedactPasswordsFromDiagnostics() {
        val initialize = InitializeAccountCommand("owner", "Owner", "private-initialize")
        val signIn = SignInCommand("owner", "private-sign-in")
        val changePassword = ChangePasswordCommand("private-current", "private-next")

        assertEquals(false, initialize.toString().contains("private-initialize"))
        assertEquals(false, signIn.toString().contains("private-sign-in"))
        assertEquals(false, changePassword.toString().contains("private-current"))
        assertEquals(false, changePassword.toString().contains("private-next"))
    }

    @Test
    fun bootstrapRejectsBlankUrlBeforeAdapterCall() {
        var called = false
        val repository = InstanceBootstrapRepository {
            called = true
            BootstrapInfo(false, "", "", "", 0)
        }

        assertFailsWith<IllegalArgumentException> {
            runAuthSuspend { LoadInstanceBootstrapUseCase(repository)(" ") }
        }
        assertEquals(false, called)
    }

    private class CapturingAuthenticationRepository : AuthenticationRepository {
        val account = Account("account-1", "owner", "Owner")
        val operations = mutableListOf<Any>()

        override suspend fun initialize(command: InitializeAccountCommand): AuthSession {
            operations += command
            return session()
        }

        override suspend fun signIn(command: SignInCommand): AuthSession {
            operations += command
            return session()
        }

        override suspend fun currentAccount(): Account {
            operations += "current-account"
            return account
        }

        override suspend fun changePassword(command: ChangePasswordCommand): AuthSession {
            operations += command
            return session()
        }

        private fun session(): AuthSession = AuthSession(
            account = account,
            accessToken = "token",
            expiresAt = "2026-08-02T00:00:00Z",
        )
    }
}

private fun <T> runAuthSuspend(block: suspend () -> T): T {
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
