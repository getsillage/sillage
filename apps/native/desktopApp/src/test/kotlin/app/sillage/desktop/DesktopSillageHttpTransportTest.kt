package app.sillage.desktop

import com.sun.net.httpserver.HttpServer
import app.sillage.core.network.SillageHttpMethod
import app.sillage.core.network.SillageHttpRequest
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class DesktopSillageHttpTransportTest {
    @Test
    fun performsJsonGetAgainstLoopbackServer() = runTest {
        val server = HttpServer.create(
            InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
            0,
        )
        server.createContext("/api/v1/auth/bootstrap") { exchange ->
            val body = """{"initialized":true}""".toByteArray(StandardCharsets.UTF_8)
            assertEquals("GET", exchange.requestMethod)
            assertEquals("application/json", exchange.requestHeaders.getFirst("Accept"))
            assertEquals(null, exchange.requestHeaders.getFirst("Authorization"))
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()

        try {
            val response = DesktopSillageHttpTransport().execute(
                SillageHttpRequest(
                    method = SillageHttpMethod.Get,
                    url = "http://${server.address.hostString}:${server.address.port}/api/v1/auth/bootstrap",
                    headers = mapOf("Accept" to "application/json"),
                ),
            )

            assertEquals(200, response.statusCode)
            assertEquals("""{"initialized":true}""", response.body)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun performsJsonPostAndPreservesAuthenticationCookies() = runTest {
        val server = HttpServer.create(
            InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
            0,
        )
        server.createContext("/api/v1/auth/signin") { exchange ->
            val requestBody = exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).use {
                it.readText()
            }
            val body = """{"accessToken":"token"}""".toByteArray(StandardCharsets.UTF_8)
            assertEquals("POST", exchange.requestMethod)
            assertEquals("application/json; charset=utf-8", exchange.requestHeaders.getFirst("Content-Type"))
            assertEquals("sillage_refresh=old-refresh", exchange.requestHeaders.getFirst("Cookie"))
            assertEquals("""{"username":"felix"}""", requestBody)
            exchange.responseHeaders.add("Set-Cookie", "sillage_access=token; Path=/; HttpOnly")
            exchange.responseHeaders.add(
                "Set-Cookie",
                "sillage_refresh=new-refresh; Path=/api/v1/auth; HttpOnly",
            )
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val response = DesktopSillageHttpTransport().execute(
                SillageHttpRequest(
                    method = SillageHttpMethod.Post,
                    url = "http://${server.address.hostString}:${server.address.port}/api/v1/auth/signin",
                    headers = mapOf(
                        "Content-Type" to "application/json; charset=utf-8",
                        "Cookie" to "sillage_refresh=old-refresh",
                    ),
                    body = """{"username":"felix"}""",
                ),
            )

            assertEquals(200, response.statusCode)
            assertEquals(2, response.headerValues("Set-Cookie").size)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun doesNotFollowRedirectsForCredentialBearingRequests() = runTest {
        val server = HttpServer.create(
            InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
            0,
        )
        var redirectedRequestCount = 0
        server.createContext("/redirect") { exchange ->
            exchange.responseHeaders.add("Location", "/target")
            exchange.sendResponseHeaders(307, -1)
            exchange.close()
        }
        server.createContext("/target") { exchange ->
            redirectedRequestCount += 1
            exchange.sendResponseHeaders(204, -1)
            exchange.close()
        }
        server.start()
        try {
            val response = DesktopSillageHttpTransport().execute(
                SillageHttpRequest(
                    method = SillageHttpMethod.Post,
                    url = "http://${server.address.hostString}:${server.address.port}/redirect",
                    headers = mapOf("Content-Type" to "application/json"),
                    body = """{"password":"must-not-be-forwarded"}""",
                ),
            )

            assertEquals(307, response.statusCode)
            assertEquals(0, redirectedRequestCount)
        } finally {
            server.stop(0)
        }
    }
}
