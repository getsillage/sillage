package app.sillage.core.application.auth

import app.sillage.core.domain.auth.Account

data class InitializeAccountCommand(
    val username: String,
    val displayName: String,
    val password: String,
)

data class SignInCommand(
    val username: String,
    val password: String,
)

data class ChangePasswordCommand(
    val currentPassword: String,
    val newPassword: String,
)

fun interface InstanceBootstrapRepository {
    suspend fun load(baseUrl: String): BootstrapInfo
}

interface AuthenticationRepository {
    suspend fun initialize(command: InitializeAccountCommand): AuthSession

    suspend fun signIn(command: SignInCommand): AuthSession

    suspend fun currentAccount(): Account

    suspend fun changePassword(command: ChangePasswordCommand): AuthSession
}

enum class AuthenticationFailureReason {
    InvalidRequest,
    InvalidCredentials,
    AlreadyInitialized,
    RateLimited,
    SessionExpired,
    ServerRejected,
    InvalidResponse,
}

class AuthenticationFailureException(
    val reason: AuthenticationFailureReason,
) : IllegalStateException("The Sillage authentication request failed: ${reason.name}.")

interface InstanceAuthenticationRepository : AuthenticationRepository, SignOutRepository

fun interface InstanceAuthenticationRepositoryFactory {
    fun create(baseUrl: String): InstanceAuthenticationRepository
}

class LoadInstanceBootstrapUseCase(
    private val repository: InstanceBootstrapRepository,
) {
    suspend operator fun invoke(baseUrl: String): BootstrapInfo {
        require(baseUrl.isNotBlank()) { "baseUrl must not be blank" }
        return repository.load(baseUrl)
    }
}

class InitializeAccountUseCase(
    private val repository: AuthenticationRepository,
) {
    suspend operator fun invoke(command: InitializeAccountCommand): AuthSession {
        return repository.initialize(command)
    }
}

class SignInUseCase(
    private val repository: AuthenticationRepository,
) {
    suspend operator fun invoke(command: SignInCommand): AuthSession {
        return repository.signIn(command)
    }
}

class GetCurrentAccountUseCase(
    private val repository: AuthenticationRepository,
) {
    suspend operator fun invoke(): Account = repository.currentAccount()
}

class ChangePasswordUseCase(
    private val repository: AuthenticationRepository,
) {
    suspend operator fun invoke(command: ChangePasswordCommand): AuthSession {
        return repository.changePassword(command)
    }
}
