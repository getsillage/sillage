package app.sillage.desktop

import com.sun.net.httpserver.HttpServer
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
            val response = DesktopSillageHttpTransport().get(
                "http://${server.address.hostString}:${server.address.port}/api/v1/auth/bootstrap",
            )

            assertEquals(200, response.statusCode)
            assertEquals("""{"initialized":true}""", response.body)
        } finally {
            server.stop(0)
        }
    }
}
