package app.sillage.data

import app.sillage.core.application.auth.AuthSession
import app.sillage.core.application.auth.AuthenticationRepository
import app.sillage.core.application.auth.BootstrapInfo
import app.sillage.core.application.auth.ChangePasswordCommand
import app.sillage.core.application.auth.InitializeAccountCommand
import app.sillage.core.application.auth.InstanceBootstrapRepository
import app.sillage.core.application.auth.SignInCommand
import app.sillage.core.domain.auth.Account

class RemoteAuthenticationRepository(
    private val api: SillageApi,
) : AuthenticationRepository, InstanceBootstrapRepository {
    override suspend fun load(baseUrl: String): BootstrapInfo = api.bootstrap(baseUrl)

    override suspend fun initialize(command: InitializeAccountCommand): AuthSession {
        return api.initialize(command.username, command.displayName, command.password)
    }

    override suspend fun signIn(command: SignInCommand): AuthSession {
        return api.signIn(command.username, command.password)
    }

    override suspend fun currentAccount(): Account = api.me()

    override suspend fun changePassword(command: ChangePasswordCommand): AuthSession {
        return api.changePassword(command.currentPassword, command.newPassword)
    }
}
