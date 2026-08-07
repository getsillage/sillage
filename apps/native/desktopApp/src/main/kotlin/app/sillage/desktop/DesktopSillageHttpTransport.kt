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
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine

internal class DesktopSillageHttpTransport(
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build(),
) : SillageHttpTransport {
    override suspend fun execute(request: SillageHttpRequest): SillageHttpResponse {
        val response = client.sendAsync(
            request.toNativeRequest(Duration.ofSeconds(30)),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8),
        ).awaitCancellable()
        return SillageHttpResponse(
            statusCode = response.statusCode(),
            headers = response.headers().map(),
            body = response.body(),
        )
    }

    override suspend fun executeStreaming(
        request: SillageHttpRequest,
        onChunk: suspend (String) -> Unit,
    ): SillageHttpResponse {
        val response = client.sendAsync(
            request.toNativeRequest(Duration.ofMinutes(5)),
            HttpResponse.BodyHandlers.ofInputStream(),
        ).awaitCancellable()
        val body = StringBuilder()
        val reader = response.body().bufferedReader(StandardCharsets.UTF_8)
        try {
            val buffer = CharArray(STREAM_BUFFER_CHARACTERS)
            while (true) {
                val count = runInterruptible(Dispatchers.IO) { reader.read(buffer) }
                if (count < 0) break
                if (count == 0) continue
                val chunk = buffer.concatToString(0, count)
                body.append(chunk)
                if (response.statusCode() in 200..299) onChunk(chunk)
            }
        } finally {
            reader.close()
        }
        return SillageHttpResponse(
            statusCode = response.statusCode(),
            headers = response.headers().map(),
            body = body.toString(),
        )
    }
}

private fun SillageHttpRequest.toNativeRequest(timeout: Duration): HttpRequest {
    val builder = HttpRequest.newBuilder(URI.create(url))
        .timeout(timeout)
        .header("User-Agent", "Sillage-Desktop/$DesktopVersion")
    headers.forEach { (name, value) -> builder.header(name, value) }
    return when (method) {
        SillageHttpMethod.Get -> builder.GET()
        SillageHttpMethod.Post -> builder.POST(
            body?.let { HttpRequest.BodyPublishers.ofString(it, StandardCharsets.UTF_8) }
                ?: HttpRequest.BodyPublishers.noBody(),
        )
    }.build()
}

private suspend fun <T> CompletableFuture<T>.awaitCancellable(): T =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel(true) }
        whenComplete { value, error ->
            if (!continuation.isActive) return@whenComplete
            if (error != null) {
                continuation.resumeWithException(error)
            } else {
                continuation.resume(value)
            }
        }
    }

private const val STREAM_BUFFER_CHARACTERS = 4_096
