package app.sillage.core.network

import app.sillage.core.application.auth.AuthenticationFailureException
import app.sillage.core.application.auth.AuthenticationFailureReason
import app.sillage.core.application.auth.ChangePasswordCommand
import app.sillage.core.application.auth.InitializeAccountCommand
import app.sillage.core.application.auth.SignInCommand
import app.sillage.core.application.auth.SignOutMode
import app.sillage.core.application.auth.SignOutResult
import app.sillage.core.application.auth.SignOutUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class RemoteInstanceAuthenticationRepositoryTest {
    @Test
    fun signsInWithoutSendingExistingCredentialsAndKeepsSessionInMemory() = runTest {
        val transport = QueueTransport(authenticatedResponse("access-1", "refresh-1"))
        val repository = RemoteInstanceAuthenticationRepositoryFactory(transport)
            .create("https://example.test/")

        val session = repository.signIn(SignInCommand("felix", "correct horse battery staple"))

        assertEquals("account-1", session.account.id)
        assertEquals("Felix", session.account.displayName)
        assertEquals("access-1", session.accessToken)
        val request = transport.requests.single()
        assertEquals(SillageHttpMethod.Post, request.method)
        assertEquals("https://example.test/api/v1/auth/signin", request.url)
        assertFalse(request.headers.containsKey("Authorization"))
        assertFalse(request.headers.containsKey("Cookie"))
        assertTrue(request.body.orEmpty().contains("\"username\":\"felix\""))
    }

    @Test
    fun initializesAccountAndMapsAuthenticationFailuresWithoutServerBody() = runTest {
        val transport = QueueTransport(
            authenticatedResponse("access-1", "refresh-1"),
            SillageHttpResponse(
                statusCode = 403,
                body = """{"error":{"message":"private server detail"}}""",
            ),
        )
        val factory = RemoteInstanceAuthenticationRepositoryFactory(transport)
        val initialized = factory.create("example.test").initialize(
            InitializeAccountCommand(
                username = "felix",
                displayName = "Felix",
                password = "correct horse battery staple",
            ),
        )

        assertEquals("access-1", initialized.accessToken)
        val error = assertFailsWith<AuthenticationFailureException> {
            factory.create("https://example.test").initialize(
                InitializeAccountCommand("felix", "Felix", "another password"),
            )
        }
        assertEquals(AuthenticationFailureReason.AlreadyInitialized, error.reason)
        assertFalse(error.message.orEmpty().contains("private server detail"))
    }

    @Test
    fun refreshesOnceAfterUnauthorizedAndRetriesWithRotatedAccessToken() = runTest {
        val transport = QueueTransport(
            authenticatedResponse("access-old", "refresh-old"),
            SillageHttpResponse(401, """{"error":{"message":"expired"}}"""),
            authenticatedResponse("access-new", "refresh-new"),
            SillageHttpResponse(
                statusCode = 200,
                body = """{"account":{"id":"account-1","username":"felix","displayName":"Felix"}}""",
            ),
        )
        val repository = RemoteInstanceAuthenticationRepositoryFactory(transport)
            .create("https://example.test")
        repository.signIn(SignInCommand("felix", "password"))

        val account = repository.currentAccount()

        assertEquals("account-1", account.id)
        assertEquals("Bearer access-old", transport.requests[1].headers["Authorization"])
        assertEquals("sillage_refresh=refresh-old", transport.requests[2].headers["Cookie"])
        assertFalse(transport.requests[2].headers.containsKey("Authorization"))
        assertEquals("Bearer access-new", transport.requests[3].headers["Authorization"])
    }

    @Test
    fun passwordChangeRotatesSessionBeforeLaterAuthenticatedRequest() = runTest {
        val transport = QueueTransport(
            authenticatedResponse("access-old", "refresh-old"),
            authenticatedResponse("access-new", "refresh-new"),
            SillageHttpResponse(
                statusCode = 200,
                body = """{"account":{"id":"account-1","username":"felix","displayName":"Felix"}}""",
            ),
        )
        val repository = RemoteInstanceAuthenticationRepositoryFactory(transport)
            .create("https://example.test")
        repository.signIn(SignInCommand("felix", "old password"))

        repository.changePassword(ChangePasswordCommand("old password", "new password"))
        repository.currentAccount()

        assertEquals("Bearer access-old", transport.requests[1].headers["Authorization"])
        assertEquals("Bearer access-new", transport.requests[2].headers["Authorization"])
    }

    @Test
    fun passwordChangeMapsUnauthorizedAfterRefreshToInvalidCredentials() = runTest {
        val transport = QueueTransport(
            authenticatedResponse("access-old", "refresh-old"),
            SillageHttpResponse(statusCode = 401, body = ""),
            authenticatedResponse("access-new", "refresh-new"),
            SillageHttpResponse(statusCode = 401, body = ""),
        )
        val repository = RemoteInstanceAuthenticationRepositoryFactory(transport)
            .create("https://example.test")
        repository.signIn(SignInCommand("felix", "old password"))

        val error = assertFailsWith<AuthenticationFailureException> {
            repository.changePassword(ChangePasswordCommand("wrong password", "new password"))
        }

        assertEquals(AuthenticationFailureReason.InvalidCredentials, error.reason)
        assertEquals("Bearer access-old", transport.requests[1].headers["Authorization"])
        assertEquals("sillage_refresh=refresh-old", transport.requests[2].headers["Cookie"])
        assertEquals("Bearer access-new", transport.requests[3].headers["Authorization"])
    }

    @Test
    fun signOutUsesCapturedRefreshCookieAndClearsOnlyOwnedSession() = runTest {
        val transport = QueueTransport(
            authenticatedResponse("access-1", "refresh-1"),
            SillageHttpResponse(statusCode = 204, body = ""),
        )
        val repository = RemoteInstanceAuthenticationRepositoryFactory(transport)
            .create("https://example.test")
        repository.signIn(SignInCommand("felix", "password"))

        val result = SignOutUseCase(repository).prepare(SignOutMode.Online)()

        assertEquals(SignOutResult.SignedOut, result)
        assertEquals("sillage_refresh=refresh-1", transport.requests[1].headers["Cookie"])
        val error = assertFailsWith<AuthenticationFailureException> {
            repository.currentAccount()
        }
        assertEquals(AuthenticationFailureReason.SessionExpired, error.reason)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun rejectsMalformedSessionAndMissingRefreshCookie() = runTest {
        val transport = QueueTransport(
            SillageHttpResponse(statusCode = 200, body = "{}"),
            SillageHttpResponse(
                statusCode = 200,
                body = authBody("access-1"),
            ),
        )
        val factory = RemoteInstanceAuthenticationRepositoryFactory(transport)

        repeat(2) {
            val error = assertFailsWith<AuthenticationFailureException> {
                factory.create("https://example.test").signIn(SignInCommand("felix", "password"))
            }
            assertEquals(AuthenticationFailureReason.InvalidResponse, error.reason)
        }
    }

    @Test
    fun httpDiagnosticsRedactBodiesAndHeaderValues() {
        val request = SillageHttpRequest(
            method = SillageHttpMethod.Post,
            url = "https://example.test/api/v1/auth/signin",
            headers = mapOf("Authorization" to "Bearer private-token"),
            body = """{"password":"private-password"}""",
        )
        val response = SillageHttpResponse(
            statusCode = 401,
            body = "private response body",
            headers = mapOf("Set-Cookie" to listOf("sillage_refresh=private-refresh")),
        )

        assertFalse(request.toString().contains("private-token"))
        assertFalse(request.toString().contains("private-password"))
        assertFalse(response.toString().contains("private response body"))
        assertFalse(response.toString().contains("private-refresh"))
    }

    @Test
    fun parsesFoundationStyleCombinedCookieHeader() = runTest {
        val transport = QueueTransport(
            SillageHttpResponse(
                statusCode = 200,
                body = authBody("access-1"),
                headers = mapOf(
                    "Set-Cookie" to listOf(
                        "(\n  \"sillage_access=ignored; Path=/\",\n" +
                            "  \"sillage_refresh=refresh-1; Path=/api/v1/auth\"\n)",
                    ),
                ),
            ),
            SillageHttpResponse(statusCode = 204, body = ""),
        )
        val repository = RemoteInstanceAuthenticationRepositoryFactory(transport)
            .create("https://example.test")
        repository.signIn(SignInCommand("felix", "password"))

        SignOutUseCase(repository).prepare(SignOutMode.Online)()

        assertEquals("sillage_refresh=refresh-1", transport.requests[1].headers["Cookie"])
    }

    @Test
    fun rejectsAccessTokenThatCannotBeUsedAsAHeaderValue() = runTest {
        val transport = QueueTransport(
            authenticatedResponse("access-token\nInjected: value", "refresh-1"),
        )
        val repository = RemoteInstanceAuthenticationRepositoryFactory(transport)
            .create("https://example.test")

        val error = assertFailsWith<AuthenticationFailureException> {
            repository.signIn(SignInCommand("felix", "password"))
        }

        assertEquals(AuthenticationFailureReason.InvalidResponse, error.reason)
    }

    private class QueueTransport(
        vararg responses: SillageHttpResponse,
    ) : SillageHttpTransport {
        private val responses = responses.toMutableList()
        val requests = mutableListOf<SillageHttpRequest>()

        override suspend fun execute(request: SillageHttpRequest): SillageHttpResponse {
            requests += request
            return responses.removeAt(0)
        }
    }

    private companion object {
        fun authenticatedResponse(accessToken: String, refreshToken: String): SillageHttpResponse {
            return SillageHttpResponse(
                statusCode = 200,
                body = authBody(accessToken),
                headers = mapOf(
                    "Set-Cookie" to listOf(
                        "sillage_access=ignored; Path=/; HttpOnly",
                        "sillage_refresh=$refreshToken; Path=/api/v1/auth; HttpOnly; SameSite=Lax",
                    ),
                ),
            )
        }

        fun authBody(accessToken: String): String {
            return """
                {
                  "account": {
                    "id": "account-1",
                    "username": "felix",
                    "displayName": "Felix"
                  },
                  "accessToken": "$accessToken",
                  "expiresAt": "2026-08-03T12:00:00Z"
                }
            """.trimIndent()
        }
    }
}
