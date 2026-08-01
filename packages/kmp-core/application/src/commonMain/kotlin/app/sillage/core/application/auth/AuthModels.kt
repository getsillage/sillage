package app.sillage.core.application.auth

import app.sillage.core.domain.auth.Account

/** Session material returned by an authentication adapter; never a domain entity. */
data class AuthSession(
    val account: Account,
    val accessToken: String,
    val expiresAt: String,
)

/** Public instance capabilities required before native authentication. */
data class BootstrapInfo(
    val initialized: Boolean,
    val serverVersion: String,
    val serverRevision: String,
    val apiVersion: String,
    val minimumAndroidVersionCode: Int,
)
