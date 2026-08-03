package app.sillage.features.auth

import app.sillage.core.domain.auth.Account

enum class InstanceAuthenticationOperation {
    Restore,
    Initialize,
    SignIn,
    SignOut,
}

enum class InstanceAuthenticationFailure {
    RequiredFields,
    InvalidRequest,
    InvalidCredentials,
    AlreadyInitialized,
    RateLimited,
    SessionExpired,
    ServerRejected,
    InvalidResponse,
    SecureStorageUnavailable,
    Connection,
}

data class InstanceAuthenticationContext(
    val baseUrl: String,
    val initialized: Boolean,
    val clientContextGeneration: Long,
)

data class InstanceAuthenticationRequest(
    val requestId: Long,
    val operation: InstanceAuthenticationOperation,
    val baseUrl: String,
    val clientContextGeneration: Long,
    val username: String = "",
    val displayName: String = "",
    val password: String = "",
    val accountId: String = "",
) {
    override fun toString(): String {
        return "InstanceAuthenticationRequest(" +
            "requestId=$requestId, operation=$operation, baseUrl=$baseUrl, " +
            "clientContextGeneration=$clientContextGeneration, username=$username, " +
            "displayName=$displayName, password=<redacted>, accountId=$accountId)"
    }
}

/** Owns native authentication form data, request identity, and in-memory account state. */
data class InstanceAuthenticationStateHolder(
    val form: AuthFeatureStateHolder = AuthFeatureStateHolder(),
    val loading: Boolean = false,
    val requestId: Long = 0,
    val operation: InstanceAuthenticationOperation? = null,
    val account: Account? = null,
    val failure: InstanceAuthenticationFailure? = null,
) {
    fun updateUsername(value: String): InstanceAuthenticationStateHolder {
        return updateForm(form.updateUsername(value))
    }

    fun updateDisplayName(value: String): InstanceAuthenticationStateHolder {
        return updateForm(form.updateDisplayName(value))
    }

    fun updatePassword(value: String): InstanceAuthenticationStateHolder {
        return updateForm(form.updatePassword(value))
    }

    fun validation(context: InstanceAuthenticationContext): InstanceAuthenticationFailure? {
        return if (
            context.baseUrl.isBlank() ||
            form.username.isBlank() ||
            form.password.isBlank()
        ) {
            InstanceAuthenticationFailure.RequiredFields
        } else {
            null
        }
    }

    fun nextAuthenticationRequest(
        context: InstanceAuthenticationContext,
    ): InstanceAuthenticationRequest? {
        if (loading || account != null || validation(context) != null) return null
        return InstanceAuthenticationRequest(
            requestId = requestId + 1,
            operation = if (context.initialized) {
                InstanceAuthenticationOperation.SignIn
            } else {
                InstanceAuthenticationOperation.Initialize
            },
            baseUrl = context.baseUrl,
            clientContextGeneration = context.clientContextGeneration,
            username = form.username.trim(),
            displayName = form.displayName.trim(),
            password = form.password,
        )
    }

    fun nextRestoreRequest(
        context: InstanceAuthenticationContext,
    ): InstanceAuthenticationRequest? {
        if (
            loading ||
            account != null ||
            context.baseUrl.isBlank() ||
            !context.initialized
        ) {
            return null
        }
        return InstanceAuthenticationRequest(
            requestId = requestId + 1,
            operation = InstanceAuthenticationOperation.Restore,
            baseUrl = context.baseUrl,
            clientContextGeneration = context.clientContextGeneration,
        )
    }

    fun nextSignOutRequest(
        context: InstanceAuthenticationContext,
    ): InstanceAuthenticationRequest? {
        val currentAccount = account ?: return null
        if (loading || context.baseUrl.isBlank()) return null
        return InstanceAuthenticationRequest(
            requestId = requestId + 1,
            operation = InstanceAuthenticationOperation.SignOut,
            baseUrl = context.baseUrl,
            clientContextGeneration = context.clientContextGeneration,
            accountId = currentAccount.id,
        )
    }

    fun begin(
        request: InstanceAuthenticationRequest,
        context: InstanceAuthenticationContext,
    ): InstanceAuthenticationStateHolder? {
        val expected = when (request.operation) {
            InstanceAuthenticationOperation.Restore -> nextRestoreRequest(context)
            InstanceAuthenticationOperation.Initialize,
            InstanceAuthenticationOperation.SignIn,
            -> nextAuthenticationRequest(context)
            InstanceAuthenticationOperation.SignOut -> nextSignOutRequest(context)
        }
        if (expected != request) return null
        return copy(
            loading = true,
            requestId = request.requestId,
            operation = request.operation,
            failure = null,
        )
    }

    fun completeRestoreWithoutSession(
        request: InstanceAuthenticationRequest,
        context: InstanceAuthenticationContext,
    ): InstanceAuthenticationStateHolder? {
        if (!owns(request, context) || request.operation != InstanceAuthenticationOperation.Restore) {
            return null
        }
        return copy(
            loading = false,
            operation = null,
            failure = null,
        )
    }

    fun completeAuthentication(
        request: InstanceAuthenticationRequest,
        context: InstanceAuthenticationContext,
        account: Account,
    ): InstanceAuthenticationStateHolder? {
        if (!owns(request, context)) return null
        if (request.operation == InstanceAuthenticationOperation.SignOut) return null
        return copy(
            form = form.clearPrimaryCredentials(clearDisplayName = true),
            loading = false,
            operation = null,
            account = account,
            failure = null,
        )
    }

    fun completeSignOut(
        request: InstanceAuthenticationRequest,
        context: InstanceAuthenticationContext,
    ): InstanceAuthenticationStateHolder? {
        if (!owns(request, context) || request.operation != InstanceAuthenticationOperation.SignOut) {
            return null
        }
        return copy(
            form = AuthFeatureStateHolder(),
            loading = false,
            operation = null,
            account = null,
            failure = null,
        )
    }

    fun fail(
        request: InstanceAuthenticationRequest,
        context: InstanceAuthenticationContext,
        failure: InstanceAuthenticationFailure,
    ): InstanceAuthenticationStateHolder? {
        if (!owns(request, context)) return null
        return copy(
            loading = false,
            operation = null,
            failure = failure,
        )
    }

    fun showValidationFailure(
        context: InstanceAuthenticationContext,
    ): InstanceAuthenticationStateHolder {
        return copy(failure = validation(context))
    }

    fun cancel(
        request: InstanceAuthenticationRequest,
        context: InstanceAuthenticationContext,
    ): InstanceAuthenticationStateHolder? {
        if (!owns(request, context)) return null
        return copy(
            loading = false,
            requestId = requestId + 1,
            operation = null,
            failure = null,
        )
    }

    fun resetForServerChange(): InstanceAuthenticationStateHolder {
        return InstanceAuthenticationStateHolder(requestId = requestId + if (loading) 1 else 0)
    }

    private fun updateForm(nextForm: AuthFeatureStateHolder): InstanceAuthenticationStateHolder {
        return copy(
            form = nextForm,
            loading = false,
            requestId = requestId + if (loading) 1 else 0,
            operation = null,
            failure = null,
        )
    }

    private fun owns(
        request: InstanceAuthenticationRequest,
        context: InstanceAuthenticationContext,
    ): Boolean {
        return loading &&
            requestId == request.requestId &&
            operation == request.operation &&
            request.baseUrl == context.baseUrl &&
            request.clientContextGeneration == context.clientContextGeneration &&
            (request.operation != InstanceAuthenticationOperation.SignOut ||
                account?.id == request.accountId)
    }
}
