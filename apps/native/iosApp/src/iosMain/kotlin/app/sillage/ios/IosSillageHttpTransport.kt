@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package app.sillage.ios

import app.sillage.core.network.SillageHttpResponse
import app.sillage.core.network.SillageHttpTransport
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.HTTPMethod
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLSession
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setValue

internal class IosSillageHttpTransport(
    private val session: NSURLSession = NSURLSession.sharedSession,
) : SillageHttpTransport {
    override suspend fun get(url: String): SillageHttpResponse {
        val nativeUrl = NSURL.URLWithString(url)
            ?: throw IllegalArgumentException("The Sillage server URL is invalid.")
        val request = NSMutableURLRequest(
            uRL = nativeUrl,
            cachePolicy = NSURLRequestReloadIgnoringLocalCacheData,
            timeoutInterval = 30.0,
        ).apply {
            HTTPMethod = "GET"
            setValue("application/json", forHTTPHeaderField = "Accept")
            setValue("Sillage-iOS/$FallbackIosVersion", forHTTPHeaderField = "User-Agent")
            setValue("no-cache", forHTTPHeaderField = "Cache-Control")
        }

        return suspendCancellableCoroutine { continuation ->
            val task = session.dataTaskWithRequest(request) { data, response, error ->
                when {
                    !continuation.isActive -> Unit
                    error != null -> continuation.resumeWithException(
                        IllegalStateException("The Sillage server request failed."),
                    )
                    response !is NSHTTPURLResponse -> continuation.resumeWithException(
                        IllegalStateException("The Sillage server returned no HTTP response."),
                    )
                    else -> {
                        val body = data?.let {
                            NSString.create(data = it, encoding = NSUTF8StringEncoding)?.toString()
                        }.orEmpty()
                        continuation.resume(
                            SillageHttpResponse(
                                statusCode = response.statusCode.toInt(),
                                body = body,
                            ),
                        )
                    }
                }
            }
            continuation.invokeOnCancellation { task.cancel() }
            task.resume()
        }
    }
}
