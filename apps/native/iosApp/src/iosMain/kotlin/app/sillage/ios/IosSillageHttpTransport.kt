@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package app.sillage.ios

import app.sillage.core.network.SillageHttpResponse
import app.sillage.core.network.SillageHttpTransport
import app.sillage.core.network.SillageHttpMethod
import app.sillage.core.network.SillageHttpRequest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.HTTPBody
import platform.Foundation.HTTPMethod
import platform.Foundation.HTTPShouldHandleCookies
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSURLSessionTaskDelegateProtocol
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setValue
import platform.darwin.NSObject

internal class IosSillageHttpTransport(
    private val session: NSURLSession = createIosSillageHttpSession(),
) : SillageHttpTransport {
    override suspend fun execute(request: SillageHttpRequest): SillageHttpResponse {
        val nativeUrl = NSURL.URLWithString(request.url)
            ?: throw IllegalArgumentException("The Sillage server URL is invalid.")
        val nativeRequest = NSMutableURLRequest(
            uRL = nativeUrl,
            cachePolicy = NSURLRequestReloadIgnoringLocalCacheData,
            timeoutInterval = 30.0,
        ).apply {
            HTTPMethod = when (request.method) {
                SillageHttpMethod.Get -> "GET"
                SillageHttpMethod.Post -> "POST"
            }
            HTTPShouldHandleCookies = false
            setValue("Sillage-iOS/$FallbackIosVersion", forHTTPHeaderField = "User-Agent")
            request.headers.forEach { (name, value) ->
                setValue(value, forHTTPHeaderField = name)
            }
            request.body?.let { body ->
                val bytes = body.encodeToByteArray()
                bytes.usePinned { pinned ->
                    HTTPBody = NSData.create(
                        bytes = pinned.addressOf(0),
                        length = bytes.size.toULong(),
                    )
                }
            }
        }

        return suspendCancellableCoroutine { continuation ->
            val task = session.dataTaskWithRequest(nativeRequest) { data, response, error ->
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
                                headers = response.allHeaderFields.entries.groupBy(
                                    keySelector = { (name, _) -> name.toString() },
                                    valueTransform = { (_, value) -> value.toString() },
                                ),
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

private fun createIosSillageHttpSession(): NSURLSession {
    return NSURLSession.sessionWithConfiguration(
        configuration = NSURLSessionConfiguration.ephemeralSessionConfiguration,
        delegate = IosNoRedirectSessionDelegate(),
        delegateQueue = null,
    )
}

private class IosNoRedirectSessionDelegate : NSObject(), NSURLSessionTaskDelegateProtocol {
    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        willPerformHTTPRedirection: NSHTTPURLResponse,
        newRequest: NSURLRequest,
        completionHandler: (NSURLRequest?) -> Unit,
    ) {
        completionHandler(null)
    }
}
