package app.sillage.features.auth

enum class PasswordChangeValidation {
    RequiredFields,
    ConfirmationMismatch,
    Unchanged,
}

data class PasswordChangeContext(
    val appMode: String,
    val clientContextGeneration: Long,
    val online: Boolean,
    val anotherOperationInProgress: Boolean,
)

data class PasswordChangeRequest(
    val requestId: Long,
    val currentPassword: String,
    val newPassword: String,
    val appMode: String,
    val clientContextGeneration: Long,
) {
    override fun toString(): String {
        return "PasswordChangeRequest(" +
            "requestId=$requestId, currentPassword=<redacted>, newPassword=<redacted>, " +
            "appMode=$appMode, clientContextGeneration=$clientContextGeneration)"
    }
}

/** Owns authentication forms and password-change request lifecycle. */
data class AuthenticationStateHolder(
    val username: String = "",
    val displayName: String = "",
    val password: String = "",
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val passwordChanging: Boolean = false,
    val passwordChangeRequestId: Long = 0,
) {
    fun updateUsername(value: String): AuthenticationStateHolder = copy(username = value)

    fun updateDisplayName(value: String): AuthenticationStateHolder = copy(displayName = value)

    fun updatePassword(value: String): AuthenticationStateHolder = copy(password = value)

    fun updateCurrentPassword(value: String): AuthenticationStateHolder = copy(currentPassword = value)

    fun updateNewPassword(value: String): AuthenticationStateHolder = copy(newPassword = value)

    fun updateConfirmPassword(value: String): AuthenticationStateHolder = copy(confirmPassword = value)

    fun passwordChangeValidation(): PasswordChangeValidation? = when {
        currentPassword.isBlank() || newPassword.isBlank() -> PasswordChangeValidation.RequiredFields
        newPassword != confirmPassword -> PasswordChangeValidation.ConfirmationMismatch
        currentPassword == newPassword -> PasswordChangeValidation.Unchanged
        else -> null
    }

    fun nextPasswordChangeRequest(context: PasswordChangeContext): PasswordChangeRequest? {
        if (
            passwordChanging ||
            !context.online ||
            context.anotherOperationInProgress ||
            passwordChangeValidation() != null
        ) {
            return null
        }
        return PasswordChangeRequest(
            requestId = passwordChangeRequestId + 1,
            currentPassword = currentPassword,
            newPassword = newPassword,
            appMode = context.appMode,
            clientContextGeneration = context.clientContextGeneration,
        )
    }

    fun beginPasswordChange(
        request: PasswordChangeRequest,
        context: PasswordChangeContext,
    ): AuthenticationStateHolder? {
        if (nextPasswordChangeRequest(context) != request) {
            return null
        }
        return copy(passwordChanging = true, passwordChangeRequestId = request.requestId)
    }

    fun canApplyPasswordChange(
        request: PasswordChangeRequest,
        context: PasswordChangeContext,
    ): Boolean {
        return passwordChanging &&
            passwordChangeRequestId == request.requestId &&
            context.appMode == request.appMode &&
            context.clientContextGeneration == request.clientContextGeneration
    }

    fun completePasswordChange(
        request: PasswordChangeRequest,
        context: PasswordChangeContext,
    ): AuthenticationStateHolder? {
        if (!canApplyPasswordChange(request, context)) {
            return null
        }
        return copy(
            currentPassword = "",
            newPassword = "",
            confirmPassword = "",
            passwordChanging = false,
        )
    }

    fun failPasswordChange(
        request: PasswordChangeRequest,
        context: PasswordChangeContext,
    ): AuthenticationStateHolder? {
        if (!canApplyPasswordChange(request, context)) {
            return null
        }
        return copy(passwordChanging = false)
    }

    fun clearPrimaryCredentials(clearDisplayName: Boolean): AuthenticationStateHolder = copy(
        username = "",
        displayName = if (clearDisplayName) "" else displayName,
        password = "",
    )

    fun resetPasswordChange(): AuthenticationStateHolder = copy(
        currentPassword = "",
        newPassword = "",
        confirmPassword = "",
        passwordChanging = false,
        passwordChangeRequestId = passwordChangeRequestId + 1,
    )
}
