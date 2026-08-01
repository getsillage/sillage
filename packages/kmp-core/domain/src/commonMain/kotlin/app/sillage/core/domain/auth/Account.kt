package app.sillage.core.domain.auth

/** Authenticated single-user account metadata shared by every native host. */
data class Account(
    val id: String,
    val username: String,
    val displayName: String,
)
