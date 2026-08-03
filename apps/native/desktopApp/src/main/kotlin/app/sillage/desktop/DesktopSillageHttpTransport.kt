package app.sillage.desktop

import app.sillage.core.network.SillageHttpResponse
import app.sillage.core.network.SillageHttpTransport
import app.sillage.core.network.SillageHttpMethod
import app.sillage.core.network.SillageHttpRequest
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
        .followRedirects(HttpClient.Redirect.NEVER)
        .build(),
) : SillageHttpTransport {
    override suspend fun execute(request: SillageHttpRequest): SillageHttpResponse {
        val builder = HttpRequest.newBuilder(URI.create(request.url))
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", "Sillage-Desktop/$DesktopVersion")
        request.headers.forEach { (name, value) -> builder.header(name, value) }
        val nativeRequest = when (request.method) {
            SillageHttpMethod.Get -> builder.GET()
            SillageHttpMethod.Post -> builder.POST(
                request.body?.let(HttpRequest.BodyPublishers::ofString)
                    ?: HttpRequest.BodyPublishers.noBody(),
            )
        }.build()
        val future = client.sendAsync(
            nativeRequest,
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
                            headers = response.headers().map(),
                            body = response.body(),
                        ),
                    )
                }
            }
        }
    }
}
