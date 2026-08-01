package app.sillage.features.auth

/**
 * Aggregated immutable ownership for authentication presentation.
 *
 * Form and password-change request identity remain on
 * [AuthenticationStateHolder]. This type is the feature-level surface hosts
 * compose so root UI state stays consistent with other feature aggregates.
 */
data class AuthFeatureStateHolder(
    val authentication: AuthenticationStateHolder = AuthenticationStateHolder(),
) {
    val username: String get() = authentication.username
    val displayName: String get() = authentication.displayName
    val password: String get() = authentication.password
    val currentPassword: String get() = authentication.currentPassword
    val newPassword: String get() = authentication.newPassword
    val confirmPassword: String get() = authentication.confirmPassword
    val passwordChanging: Boolean get() = authentication.passwordChanging
    val passwordChangeRequestId: Long get() = authentication.passwordChangeRequestId

    fun updateUsername(value: String): AuthFeatureStateHolder =
        copy(authentication = authentication.updateUsername(value))

    fun updateDisplayName(value: String): AuthFeatureStateHolder =
        copy(authentication = authentication.updateDisplayName(value))

    fun updatePassword(value: String): AuthFeatureStateHolder =
        copy(authentication = authentication.updatePassword(value))

    fun updateCurrentPassword(value: String): AuthFeatureStateHolder =
        copy(authentication = authentication.updateCurrentPassword(value))

    fun updateNewPassword(value: String): AuthFeatureStateHolder =
        copy(authentication = authentication.updateNewPassword(value))

    fun updateConfirmPassword(value: String): AuthFeatureStateHolder =
        copy(authentication = authentication.updateConfirmPassword(value))

    fun clearPrimaryCredentials(clearDisplayName: Boolean): AuthFeatureStateHolder =
        copy(authentication = authentication.clearPrimaryCredentials(clearDisplayName))

    fun withAuthentication(
        transform: (AuthenticationStateHolder) -> AuthenticationStateHolder,
    ): AuthFeatureStateHolder = copy(authentication = transform(authentication))
}
