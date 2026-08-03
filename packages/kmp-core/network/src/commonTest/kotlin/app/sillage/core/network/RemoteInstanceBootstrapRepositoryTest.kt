package app.sillage.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class RemoteInstanceBootstrapRepositoryTest {
    @Test
    fun loadsAndMapsPublicBootstrapResponse() = runTest {
        val transport = CapturingTransport(
            SillageHttpResponse(
                statusCode = 200,
                body = """
                    {
                      "initialized": true,
                      "serverVersion": "0.3.1",
                      "serverRevision": "abc123",
                      "apiVersion": "v1",
                      "minimumAndroidVersionCode": 12,
                      "futureCapability": true
                    }
                """.trimIndent(),
            ),
        )

        val bootstrap = RemoteInstanceBootstrapRepository(transport).load("sillage.example/")

        assertEquals("https://sillage.example/api/v1/auth/bootstrap", transport.requestedUrl)
        assertEquals(SillageHttpMethod.Get, transport.requestedRequest?.method)
        assertEquals("application/json", transport.requestedRequest?.headers?.get("Accept"))
        assertFalse(transport.requestedRequest?.headers.orEmpty().containsKey("Authorization"))
        assertFalse(transport.requestedRequest?.headers.orEmpty().containsKey("Cookie"))
        assertTrue(bootstrap.initialized)
        assertEquals("0.3.1", bootstrap.serverVersion)
        assertEquals("abc123", bootstrap.serverRevision)
        assertEquals("v1", bootstrap.apiVersion)
        assertEquals(12, bootstrap.minimumAndroidVersionCode)
    }

    @Test
    fun permitsDevelopmentHttpAndDefaultsOptionalMetadata() = runTest {
        val transport = CapturingTransport(
            SillageHttpResponse(statusCode = 200, body = """{"initialized":false}"""),
        )

        val bootstrap = RemoteInstanceBootstrapRepository(transport)
            .load("http://127.0.0.1:5231/")

        assertEquals("http://127.0.0.1:5231/api/v1/auth/bootstrap", transport.requestedUrl)
        assertFalse(bootstrap.initialized)
        assertEquals("", bootstrap.serverVersion)
        assertEquals(0, bootstrap.minimumAndroidVersionCode)
    }

    @Test
    fun rejectsInvalidAddressBeforeTransportCall() = runTest {
        val transport = CapturingTransport(SillageHttpResponse(200, "{}"))

        assertFailsWith<InvalidServerAddressException> {
            RemoteInstanceBootstrapRepository(transport).load("ftp://example.test")
        }

        assertEquals(null, transport.requestedUrl)
    }

    @Test
    fun rejectsAddressCredentialsQueryAndFragment() = runTest {
        val repository = RemoteInstanceBootstrapRepository(
            CapturingTransport(SillageHttpResponse(200, "{}")),
        )

        listOf(
            "https://user:password@example.test",
            "https://example.test?token=value",
            "https://example.test#fragment",
        ).forEach { address ->
            assertFailsWith<InvalidServerAddressException> {
                repository.load(address)
            }
        }
    }

    @Test
    fun rejectsHttpFailureAndMalformedBootstrap() = runTest {
        assertFailsWith<SillageHttpStatusException> {
            RemoteInstanceBootstrapRepository(
                CapturingTransport(SillageHttpResponse(503, "unavailable")),
            ).load("https://example.test")
        }
        assertFailsWith<InvalidServerResponseException> {
            RemoteInstanceBootstrapRepository(
                CapturingTransport(SillageHttpResponse(200, "{}")),
            ).load("https://example.test")
        }
        assertFailsWith<InvalidServerResponseException> {
            RemoteInstanceBootstrapRepository(
                CapturingTransport(
                    SillageHttpResponse(
                        200,
                        "x".repeat(64 * 1024 + 1),
                    ),
                ),
            ).load("https://example.test")
        }
    }

    private class CapturingTransport(
        private val response: SillageHttpResponse,
    ) : SillageHttpTransport {
        var requestedRequest: SillageHttpRequest? = null
        val requestedUrl: String?
            get() = requestedRequest?.url

        override suspend fun execute(request: SillageHttpRequest): SillageHttpResponse {
            requestedRequest = request
            return response
        }
    }
}
