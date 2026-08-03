package app.sillage.core.network

import app.sillage.core.application.auth.AuthSession
import app.sillage.core.application.auth.AuthenticationFailureException
import app.sillage.core.application.auth.AuthenticationFailureReason
import app.sillage.core.application.auth.CapturedSignOutSession
import app.sillage.core.application.auth.ChangePasswordCommand
import app.sillage.core.application.auth.InitializeAccountCommand
import app.sillage.core.application.auth.InstanceAuthenticationRepository
import app.sillage.core.application.auth.InstanceAuthenticationRepositoryFactory
import app.sillage.core.application.auth.SignInCommand
import app.sillage.core.domain.auth.Account
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

class RemoteInstanceAuthenticationRepositoryFactory(
    private val transport: SillageHttpTransport,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : InstanceAuthenticationRepositoryFactory {
    private val sessions = InMemoryAuthenticationSessionStore()

    override fun create(baseUrl: String): InstanceAuthenticationRepository {
        return RemoteInstanceAuthenticationRepository(
            baseUrl = normalizeAndValidateServerBaseUrl(baseUrl),
            transport = transport,
            sessions = sessions,
            json = json,
        )
    }
}

private class RemoteInstanceAuthenticationRepository(
    private val baseUrl: String,
    private val transport: SillageHttpTransport,
    private val sessions: InMemoryAuthenticationSessionStore,
    private val json: Json,
) : InstanceAuthenticationRepository {
    override suspend fun initialize(command: InitializeAccountCommand): AuthSession {
        return authenticate(
            path = "/api/v1/auth/initialize",
            operation = AuthenticationOperation.Initialize,
            payload = buildJsonObject {
                put("username", command.username)
                put("displayName", command.displayName)
                put("password", command.password)
            },
        )
    }

    override suspend fun signIn(command: SignInCommand): AuthSession {
        return authenticate(
            path = "/api/v1/auth/signin",
            operation = AuthenticationOperation.SignIn,
            payload = buildJsonObject {
                put("username", command.username)
                put("password", command.password)
            },
        )
    }

    override suspend fun currentAccount(): Account {
        val (response, _) = executeAuthenticated(
            SillageHttpRequest(
                method = SillageHttpMethod.Get,
                url = "$baseUrl/api/v1/auth/me",
                headers = jsonHeaders,
            ),
        )
        requireSuccess(response, AuthenticationOperation.CurrentAccount)
        return parseAccountEnvelope(response)
    }

    override suspend fun changePassword(command: ChangePasswordCommand): AuthSession {
        val request = SillageHttpRequest(
            method = SillageHttpMethod.Post,
            url = "$baseUrl/api/v1/auth/change-password",
            headers = jsonHeaders,
            body = buildJsonObject {
                put("currentPassword", command.currentPassword)
                put("newPassword", command.newPassword)
            }.toString(),
        )
        val (response, expected) = executeAuthenticated(request)
        requireSuccess(response, AuthenticationOperation.ChangePassword)
        val authenticated = parseAuthenticatedResponse(response)
        if (sessions.replace(expected, authenticated.session, authenticated.refreshCookie) == null) {
            throw AuthenticationFailureException(AuthenticationFailureReason.SessionExpired)
        }
        return authenticated.session
    }

    override fun captureSession(): CapturedSignOutSession {
        val captured = sessions.current(baseUrl)
        return object : CapturedSignOutSession {
            override suspend fun signOutRemote() {
                val expected = captured
                    ?: throw AuthenticationFailureException(AuthenticationFailureReason.SessionExpired)
                if (!sessions.owns(expected)) {
                    throw AuthenticationFailureException(AuthenticationFailureReason.SessionExpired)
                }
                val response = transport.execute(
                    SillageHttpRequest(
                        method = SillageHttpMethod.Post,
                        url = "$baseUrl/api/v1/auth/signout",
                        headers = jsonHeaders + ("Cookie" to refreshCookieHeader(expected.refreshCookie)),
                    ),
                )
                requireSuccess(response, AuthenticationOperation.SignOut)
                if (!sessions.clear(expected)) {
                    throw AuthenticationFailureException(AuthenticationFailureReason.SessionExpired)
                }
            }

            override fun clearLocalSession(): Boolean {
                return captured?.let(sessions::clear) ?: false
            }
        }
    }

    private suspend fun authenticate(
        path: String,
        operation: AuthenticationOperation,
        payload: JsonObject,
    ): AuthSession {
        val response = transport.execute(
            SillageHttpRequest(
                method = SillageHttpMethod.Post,
                url = baseUrl + path,
                headers = jsonHeaders,
                body = payload.toString(),
            ),
        )
        requireSuccess(response, operation)
        val authenticated = parseAuthenticatedResponse(response)
        sessions.replace(baseUrl, authenticated.session, authenticated.refreshCookie)
        return authenticated.session
    }

    private suspend fun executeAuthenticated(
        request: SillageHttpRequest,
    ): Pair<SillageHttpResponse, AuthenticationSessionSnapshot> {
        var session = sessions.current(baseUrl)
            ?: throw AuthenticationFailureException(AuthenticationFailureReason.SessionExpired)
        var response = transport.execute(request.withAccessToken(session.authSession.accessToken))
        if (response.statusCode == 401) {
            session = refresh(session)
            response = transport.execute(request.withAccessToken(session.authSession.accessToken))
        }
        return response to session
    }

    private suspend fun refresh(
        expected: AuthenticationSessionSnapshot,
    ): AuthenticationSessionSnapshot {
        if (!sessions.owns(expected)) {
            throw AuthenticationFailureException(AuthenticationFailureReason.SessionExpired)
        }
        val response = transport.execute(
            SillageHttpRequest(
                method = SillageHttpMethod.Post,
                url = "$baseUrl/api/v1/auth/refresh",
                headers = jsonHeaders + ("Cookie" to refreshCookieHeader(expected.refreshCookie)),
            ),
        )
        if (response.statusCode == 401) {
            sessions.clear(expected)
            throw AuthenticationFailureException(AuthenticationFailureReason.SessionExpired)
        }
        requireSuccess(response, AuthenticationOperation.Refresh)
        val authenticated = parseAuthenticatedResponse(response)
        return sessions.replace(expected, authenticated.session, authenticated.refreshCookie)
            ?: throw AuthenticationFailureException(AuthenticationFailureReason.SessionExpired)
    }

    private fun parseAuthenticatedResponse(response: SillageHttpResponse): AuthenticatedResponse {
        val body = parseJsonObject(response)
        val account = body["account"]?.asObject()
            ?: throw AuthenticationFailureException(AuthenticationFailureReason.InvalidResponse)
        val session = AuthSession(
            account = parseAccount(account),
            accessToken = body.requiredHeaderValue("accessToken", MaxAccessTokenCharacters),
            expiresAt = body.requiredString("expiresAt"),
        )
        val refreshCookie = response.headerValues("Set-Cookie")
            .firstNotNullOfOrNull(::extractRefreshCookie)
            ?: throw AuthenticationFailureException(AuthenticationFailureReason.InvalidResponse)
        return AuthenticatedResponse(session, refreshCookie)
    }

    private fun parseAccountEnvelope(response: SillageHttpResponse): Account {
        val body = parseJsonObject(response)
        val account = body["account"]?.asObject()
            ?: throw AuthenticationFailureException(AuthenticationFailureReason.InvalidResponse)
        return parseAccount(account)
    }

    private fun parseJsonObject(response: SillageHttpResponse): JsonObject {
        if (response.body.length > MaxAuthenticationResponseCharacters) {
            throw AuthenticationFailureException(AuthenticationFailureReason.InvalidResponse)
        }
        return try {
            json.parseToJsonElement(response.body).jsonObject
        } catch (_: SerializationException) {
            throw AuthenticationFailureException(AuthenticationFailureReason.InvalidResponse)
        } catch (_: IllegalArgumentException) {
            throw AuthenticationFailureException(AuthenticationFailureReason.InvalidResponse)
        }
    }

    private fun parseAccount(body: JsonObject): Account {
        val username = body.requiredString("username")
        return Account(
            id = body.requiredString("id"),
            username = username,
            displayName = body.stringValue("displayName").ifBlank { username },
        )
    }
}

private data class AuthenticatedResponse(
    val session: AuthSession,
    val refreshCookie: String,
)

private data class AuthenticationSessionSnapshot(
    val generation: Long,
    val baseUrl: String,
    val authSession: AuthSession,
    val refreshCookie: String,
)

/** Single native controller owns this store; generation checks reject stale async work. */
private class InMemoryAuthenticationSessionStore {
    private var generation: Long = 0
    private var session: AuthenticationSessionSnapshot? = null

    fun current(baseUrl: String): AuthenticationSessionSnapshot? {
        return session?.takeIf { it.baseUrl == baseUrl }
    }

    fun replace(
        baseUrl: String,
        authSession: AuthSession,
        refreshCookie: String,
    ): AuthenticationSessionSnapshot {
        generation += 1
        return AuthenticationSessionSnapshot(
            generation = generation,
            baseUrl = baseUrl,
            authSession = authSession,
            refreshCookie = refreshCookie,
        ).also { session = it }
    }

    fun replace(
        expected: AuthenticationSessionSnapshot,
        authSession: AuthSession,
        refreshCookie: String,
    ): AuthenticationSessionSnapshot? {
        if (!owns(expected)) return null
        return replace(expected.baseUrl, authSession, refreshCookie)
    }

    fun owns(expected: AuthenticationSessionSnapshot): Boolean {
        val current = session ?: return false
        return current.generation == expected.generation && current.baseUrl == expected.baseUrl
    }

    fun clear(expected: AuthenticationSessionSnapshot): Boolean {
        if (!owns(expected)) return false
        generation += 1
        session = null
        return true
    }
}

private enum class AuthenticationOperation {
    Initialize,
    SignIn,
    Refresh,
    SignOut,
    CurrentAccount,
    ChangePassword,
}

private fun requireSuccess(
    response: SillageHttpResponse,
    operation: AuthenticationOperation,
) {
    if (response.statusCode in 200..299) return
    val reason = when {
        response.statusCode == 429 -> AuthenticationFailureReason.RateLimited
        response.statusCode == 400 -> AuthenticationFailureReason.InvalidRequest
        response.statusCode == 403 && operation == AuthenticationOperation.Initialize -> {
            AuthenticationFailureReason.AlreadyInitialized
        }
        response.statusCode == 401 && operation == AuthenticationOperation.SignIn -> {
            AuthenticationFailureReason.InvalidCredentials
        }
        response.statusCode == 401 && operation == AuthenticationOperation.ChangePassword -> {
            AuthenticationFailureReason.InvalidCredentials
        }
        response.statusCode == 401 -> AuthenticationFailureReason.SessionExpired
        else -> AuthenticationFailureReason.ServerRejected
    }
    throw AuthenticationFailureException(reason)
}

private fun SillageHttpRequest.withAccessToken(accessToken: String): SillageHttpRequest {
    return copy(headers = headers + ("Authorization" to "Bearer $accessToken"))
}

private fun JsonObject.requiredString(key: String): String {
    val value = (this[key] as? JsonPrimitive)
        ?.takeIf { it.isString }
        ?.content
        .orEmpty()
    if (value.isBlank()) {
        throw AuthenticationFailureException(AuthenticationFailureReason.InvalidResponse)
    }
    return value
}

private fun JsonObject.stringValue(key: String): String {
    return (this[key] as? JsonPrimitive)
        ?.takeIf { it.isString }
        ?.content
        .orEmpty()
}

private fun JsonObject.requiredHeaderValue(key: String, maxCharacters: Int): String {
    val value = requiredString(key)
    if (value.length > maxCharacters || value.any(Char::isWhitespace)) {
        throw AuthenticationFailureException(AuthenticationFailureReason.InvalidResponse)
    }
    return value
}

private fun kotlinx.serialization.json.JsonElement.asObject(): JsonObject? {
    return this as? JsonObject
}

private fun extractRefreshCookie(header: String): String? {
    return RefreshCookiePattern.find(header)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf { it.isNotBlank() && it.length <= MaxRefreshCookieCharacters }
}

private fun refreshCookieHeader(value: String): String = "$RefreshCookieName=$value"

private val jsonHeaders = mapOf(
    "Accept" to "application/json",
    "Content-Type" to "application/json; charset=utf-8",
    "Cache-Control" to "no-cache",
)

private const val MaxAuthenticationResponseCharacters = 64 * 1024
private const val MaxAccessTokenCharacters = 16 * 1024
private const val MaxRefreshCookieCharacters = 4096
private const val RefreshCookieName = "sillage_refresh"
private val RefreshCookiePattern = Regex(
    "(?:^|[\\s,\\\"(])$RefreshCookieName=([^;,\\s\\\")]+)",
)
