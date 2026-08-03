package app.sillage.desktop

import app.sillage.core.network.SillageHttpResponse
import app.sillage.core.network.SillageHttpTransport
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal class DesktopSillageHttpTransport(
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build(),
) : SillageHttpTransport {
    override suspend fun get(url: String): SillageHttpResponse {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json")
            .header("User-Agent", "Sillage-Desktop/$DesktopVersion")
            .GET()
            .build()
        val future = client.sendAsync(
            request,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8),
        )
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { future.cancel(true) }
            future.whenComplete { response, error ->
                if (!continuation.isActive) return@whenComplete
                if (error != null) {
                    continuation.resumeWithException(error)
                } else {
                    continuation.resume(
                        SillageHttpResponse(
                            statusCode = response.statusCode(),
                            body = response.body(),
                        ),
                    )
                }
            }
        }
    }
}
