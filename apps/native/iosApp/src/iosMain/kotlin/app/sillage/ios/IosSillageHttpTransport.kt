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
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.HTTPBody
import platform.Foundation.HTTPMethod
import platform.Foundation.HTTPShouldHandleCookies
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSError
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionResponseAllow
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSURLSessionTaskDelegateProtocol
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setValue
import platform.darwin.NSObject
import platform.posix.memcpy

internal class IosSillageHttpTransport(
    private val session: NSURLSession = createIosSillageHttpSession(),
) : SillageHttpTransport {
    override suspend fun execute(request: SillageHttpRequest): SillageHttpResponse {
        val nativeRequest = request.toNativeRequest(timeoutSeconds = 30.0)

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

    override suspend fun executeStreaming(
        request: SillageHttpRequest,
        onChunk: suspend (ByteArray) -> Unit,
    ): SillageHttpResponse = coroutineScope {
        val chunks = Channel<ByteArray>(Channel.UNLIMITED)
        val response = async {
            try {
                executeNativeStreaming(
                    request = request.toNativeRequest(timeoutSeconds = 300.0),
                    onChunk = { chunk -> chunks.trySend(chunk).getOrThrow() },
                )
            } finally {
                chunks.close()
            }
        }
        try {
            for (chunk in chunks) onChunk(chunk)
            response.await()
        } finally {
            response.cancel()
            chunks.cancel()
        }
    }
}

private fun SillageHttpRequest.toNativeRequest(timeoutSeconds: Double): NSMutableURLRequest {
    val nativeUrl = NSURL.URLWithString(url)
        ?: throw IllegalArgumentException("The Sillage server URL is invalid.")
    return NSMutableURLRequest(
        uRL = nativeUrl,
        cachePolicy = NSURLRequestReloadIgnoringLocalCacheData,
        timeoutInterval = timeoutSeconds,
    ).apply {
        HTTPMethod = when (method) {
            SillageHttpMethod.Get -> "GET"
            SillageHttpMethod.Post -> "POST"
        }
        HTTPShouldHandleCookies = false
        setValue("Sillage-iOS/$FallbackIosVersion", forHTTPHeaderField = "User-Agent")
        headers.forEach { (name, value) ->
            setValue(value, forHTTPHeaderField = name)
        }
        body?.let { requestBody ->
            val bytes = requestBody.encodeToByteArray()
            bytes.usePinned { pinned ->
                HTTPBody = NSData.create(
                    bytes = pinned.addressOf(0),
                    length = bytes.size.toULong(),
                )
            }
        }
    }
}

private suspend fun executeNativeStreaming(
    request: NSURLRequest,
    onChunk: (ByteArray) -> Unit,
): SillageHttpResponse = suspendCancellableCoroutine { continuation ->
    val delegate = IosStreamingSessionDelegate(
        onChunk = onChunk,
        onComplete = { result ->
            if (!continuation.isActive) return@IosStreamingSessionDelegate
            result.fold(continuation::resume, continuation::resumeWithException)
        },
    )
    val streamingSession = NSURLSession.sessionWithConfiguration(
        configuration = NSURLSessionConfiguration.ephemeralSessionConfiguration,
        delegate = delegate,
        delegateQueue = null,
    )
    val task = streamingSession.dataTaskWithRequest(request)
    continuation.invokeOnCancellation {
        task.cancel()
        streamingSession.invalidateAndCancel()
    }
    task.resume()
}

private class IosStreamingSessionDelegate(
    private val onChunk: (ByteArray) -> Unit,
    private val onComplete: (Result<SillageHttpResponse>) -> Unit,
) : NSObject(), NSURLSessionDataDelegateProtocol, NSURLSessionTaskDelegateProtocol {
    private val bodyChunks = mutableListOf<ByteArray>()
    private var response: NSHTTPURLResponse? = null
    private var callbackFailure: Throwable? = null
    private var completed = false

    override fun URLSession(
        session: NSURLSession,
        dataTask: NSURLSessionDataTask,
        didReceiveResponse: NSURLResponse,
        completionHandler: (Long) -> Unit,
    ) {
        response = didReceiveResponse as? NSHTTPURLResponse
        completionHandler(NSURLSessionResponseAllow)
    }

    override fun URLSession(
        session: NSURLSession,
        dataTask: NSURLSessionDataTask,
        didReceiveData: NSData,
    ) {
        val chunk = didReceiveData.copyBytes()
        bodyChunks += chunk
        val currentResponse = response ?: return
        if (currentResponse.statusCode.toInt() !in 200..299 || callbackFailure != null) return
        runCatching { onChunk(chunk) }
            .onFailure {
                callbackFailure = it
                dataTask.cancel()
            }
    }

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didCompleteWithError: NSError?,
    ) {
        val result = when {
            callbackFailure != null -> Result.failure(callbackFailure!!)
            didCompleteWithError != null -> Result.failure(
                IllegalStateException("The Sillage server streaming request failed."),
            )
            response == null -> Result.failure(
                IllegalStateException("The Sillage server returned no HTTP response."),
            )
            else -> Result.success(
                SillageHttpResponse(
                    statusCode = response!!.statusCode.toInt(),
                    headers = response!!.allHeaderFields.entries.groupBy(
                        keySelector = { (name, _) -> name.toString() },
                        valueTransform = { (_, value) -> value.toString() },
                    ),
                    body = bodyChunks.decodeUtf8Body(),
                ),
            )
        }
        if (!completed) {
            completed = true
            onComplete(result)
        }
        session.finishTasksAndInvalidate()
    }

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

private fun NSData.copyBytes(): ByteArray {
    check(length <= Int.MAX_VALUE.toULong()) {
        "The Sillage server response chunk is too large."
    }
    if (length == 0UL) return ByteArray(0)

    return ByteArray(length.toInt()).also { copy ->
        copy.usePinned { pinned ->
            memcpy(pinned.addressOf(0), bytes, length)
        }
    }
}

private fun List<ByteArray>.decodeUtf8Body(): String {
    var size = 0
    for (chunk in this) {
        check(chunk.size <= Int.MAX_VALUE - size) {
            "The Sillage server response body is too large."
        }
        size += chunk.size
    }
    val body = ByteArray(size)
    var offset = 0
    for (chunk in this) {
        chunk.copyInto(body, destinationOffset = offset)
        offset += chunk.size
    }
    return body.decodeToString()
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
