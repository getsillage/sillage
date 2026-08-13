package app.sillage.core.network

import app.sillage.core.application.ask.AskAnswerStreamEvent
import app.sillage.core.application.ask.AskAnswerStreamer
import app.sillage.core.application.ask.AskRepository
import app.sillage.core.application.ask.StreamAskAnswerCommand
import app.sillage.core.domain.ask.AskConversation
import app.sillage.core.domain.ask.AskMessage
import app.sillage.core.domain.ask.AskSourceRef
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

enum class RemoteAskFailureReason {
    RequestRejected,
    ServerUnavailable,
    InvalidResponse,
}

class RemoteAskFailureException(
    val reason: RemoteAskFailureReason,
    val statusCode: Int? = null,
) : Exception(
    when (reason) {
        RemoteAskFailureReason.RequestRejected -> "The Ask request was rejected."
        RemoteAskFailureReason.ServerUnavailable -> "The Ask service is unavailable."
        RemoteAskFailureReason.InvalidResponse -> "The Ask service returned an invalid response."
    },
)

class RemoteAskRepository(
    baseUrl: String,
    private val executeAuthenticated: suspend (SillageHttpRequest) -> SillageHttpResponse,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : AskRepository {
    private val baseUrl = normalizeAndValidateServerBaseUrl(baseUrl)

    override suspend fun listConversations(): List<AskConversation> {
        val response = executeAuthenticated(
            SillageHttpRequest(
                method = SillageHttpMethod.Get,
                url = "$baseUrl/api/v1/ask/conversations?limit=50",
            ),
        ).requireAskSuccess()
        return response.parseObject(json)
            .requiredArray("conversations")
            .map(JsonObject::parseAskConversation)
    }

    override suspend fun listMessages(conversationId: String): List<AskMessage> {
        val response = executeAuthenticated(
            SillageHttpRequest(
                method = SillageHttpMethod.Get,
                url = "$baseUrl/api/v1/ask/conversations/${conversationId.pathComponent()}/messages",
            ),
        ).requireAskSuccess()
        return response.parseObject(json)
            .requiredArray("messages")
            .map(JsonObject::parseAskMessage)
    }

    override suspend fun createConversation(contextScope: String): AskConversation {
        val response = executeAuthenticated(
            SillageHttpRequest(
                method = SillageHttpMethod.Post,
                url = "$baseUrl/api/v1/ask/conversations",
                headers = jsonHeaders,
                body = buildJsonObject { put("contextScope", contextScope) }.toString(),
            ),
        ).requireAskSuccess()
        return response.parseObject(json)
            .requiredObject("conversation")
            .parseAskConversation()
    }

    override suspend fun setHead(conversationId: String, messageId: String) {
        executeAuthenticated(
            SillageHttpRequest(
                method = SillageHttpMethod.Post,
                url = "$baseUrl/api/v1/ask/conversations/${conversationId.pathComponent()}/head",
                headers = jsonHeaders,
                body = buildJsonObject { put("messageId", messageId) }.toString(),
            ),
        ).requireAskSuccess()
    }
}

class RemoteAskAnswerStreamer(
    baseUrl: String,
    private val executeAuthenticatedStreaming:
        suspend (SillageHttpRequest, suspend (ByteArray) -> Unit) -> SillageHttpResponse,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : AskAnswerStreamer {
    private val baseUrl = normalizeAndValidateServerBaseUrl(baseUrl)

    override suspend fun stream(
        command: StreamAskAnswerCommand,
        onEvent: (AskAnswerStreamEvent) -> Unit,
    ) {
        val payload = buildJsonObject {
            put("content", command.content)
            put("contextScope", command.contextScope)
            put("sourceKind", command.sourceKind)
            command.forkOfMessageId
                ?.takeIf(String::isNotBlank)
                ?.let { put("forkOfId", it) }
        }
        val events = AskServerSentEventBuffer(json, onEvent)
        val decoder = IncrementalUtf8Decoder()
        val response = executeAuthenticatedStreaming(
            SillageHttpRequest(
                method = SillageHttpMethod.Post,
                url = "$baseUrl/api/v1/ask/conversations/" +
                    "${command.conversationId.pathComponent()}/messages:stream",
                headers = jsonHeaders + ("Accept" to "text/event-stream"),
                body = payload.toString(),
            ),
            { chunk -> events.append(decoder.appendAskChunk(chunk)) },
        ).requireAskSuccess()
        events.append(decoder.finishAskStream())
        events.finish()
    }
}

private fun IncrementalUtf8Decoder.appendAskChunk(chunk: ByteArray): String =
    try {
        append(chunk)
    } catch (_: InvalidUtf8Exception) {
        throw RemoteAskFailureException(RemoteAskFailureReason.InvalidResponse)
    }

private fun IncrementalUtf8Decoder.finishAskStream(): String =
    try {
        finish()
    } catch (_: InvalidUtf8Exception) {
        throw RemoteAskFailureException(RemoteAskFailureReason.InvalidResponse)
    }

private class AskServerSentEventBuffer(
    private val json: Json,
    private val onEvent: (AskAnswerStreamEvent) -> Unit,
) {
    private var pending = ""

    fun append(chunk: String) {
        if (chunk.isEmpty()) return
        pending += chunk
        if (pending.length > MAX_PENDING_EVENT_CHARACTERS) {
            throw RemoteAskFailureException(RemoteAskFailureReason.InvalidResponse)
        }
        drain(completeOnly = true)
    }

    fun finish() {
        drain(completeOnly = false)
        pending = ""
    }

    private fun drain(completeOnly: Boolean) {
        while (pending.isNotEmpty()) {
            val boundary = pending.nextEventBoundary()
            if (boundary == null) {
                if (!completeOnly && pending.isNotBlank()) {
                    dispatch(pending)
                }
                return
            }
            val block = pending.substring(0, boundary.index)
            pending = pending.substring(boundary.index + boundary.length)
            if (block.isNotBlank()) dispatch(block)
        }
    }

    private fun dispatch(block: String) {
        var eventName = "message"
        val data = StringBuilder()
        block.lineSequence().forEach { rawLine ->
            val line = rawLine.removeSuffix("\r")
            when {
                line.startsWith("event:") -> eventName = line.removePrefix("event:").trim()
                line.startsWith("data:") -> data.append(line.removePrefix("data:").trim())
            }
        }
        if (data.isBlank()) return
        val payload = runCatching { json.parseToJsonElement(data.toString()).jsonObject }
            .getOrElse {
                throw RemoteAskFailureException(RemoteAskFailureReason.InvalidResponse)
            }
        when (eventName) {
            "start" -> onEvent(
                AskAnswerStreamEvent.Started(
                    userMessage = payload.requiredObject("userMessage").parseAskMessage(),
                    regenerating = payload.optionalBoolean("regenerate"),
                ),
            )
            "delta" -> onEvent(AskAnswerStreamEvent.Delta(payload.optionalString("text")))
            "error" -> onEvent(
                AskAnswerStreamEvent.Failed(
                    payload.optionalString("message").ifBlank { "Ask answer generation failed." },
                ),
            )
        }
    }
}

private data class EventBoundary(val index: Int, val length: Int)

private fun String.nextEventBoundary(): EventBoundary? {
    val unix = indexOf("\n\n").takeIf { it >= 0 }?.let { EventBoundary(it, 2) }
    val windows = indexOf("\r\n\r\n").takeIf { it >= 0 }?.let { EventBoundary(it, 4) }
    return listOfNotNull(unix, windows).minByOrNull(EventBoundary::index)
}

private fun SillageHttpResponse.requireAskSuccess(): SillageHttpResponse {
    if (statusCode in 200..299) return this
    val reason = if (statusCode >= 500) {
        RemoteAskFailureReason.ServerUnavailable
    } else {
        RemoteAskFailureReason.RequestRejected
    }
    throw RemoteAskFailureException(reason, statusCode)
}

private fun SillageHttpResponse.parseObject(json: Json): JsonObject =
    runCatching { json.parseToJsonElement(body).jsonObject }
        .getOrElse { throw RemoteAskFailureException(RemoteAskFailureReason.InvalidResponse) }

private fun JsonObject.parseAskConversation(): AskConversation = AskConversation(
    id = requiredString("id"),
    title = optionalString("title"),
    status = optionalString("status"),
    contextScope = optionalString("contextScope"),
    headMessageId = nullableString("headMessageId"),
    pinnedAt = nullableString("pinnedAt"),
    archivedAt = nullableString("archivedAt"),
    createdAt = optionalString("createdAt"),
    updatedAt = optionalString("updatedAt"),
    deletedAt = nullableString("deletedAt"),
)

private fun JsonObject.parseAskMessage(): AskMessage = AskMessage(
    id = requiredString("id"),
    conversationId = requiredString("conversationId"),
    role = optionalString("role"),
    content = optionalString("content"),
    parentId = nullableString("parentId"),
    forkOfId = nullableString("forkOfId"),
    status = optionalString("status"),
    sourceRefs = optionalArray("sourceRefs").map(JsonObject::parseAskSourceRef),
    model = optionalString("model"),
    promptVersion = optionalString("promptVersion"),
    createdAt = optionalString("createdAt"),
    updatedAt = optionalString("updatedAt"),
    deletedAt = nullableString("deletedAt"),
)

private fun JsonObject.parseAskSourceRef(): AskSourceRef = AskSourceRef(
    memoId = requiredString("memoId"),
    entryDate = optionalString("entryDate"),
    excerpt = optionalString("excerpt"),
    rank = this["rank"]?.jsonPrimitive?.intOrNull ?: 0,
)

private fun JsonObject.requiredObject(name: String): JsonObject =
    runCatching { getValue(name).jsonObject }
        .getOrElse { throw RemoteAskFailureException(RemoteAskFailureReason.InvalidResponse) }

private fun JsonObject.requiredArray(name: String): List<JsonObject> =
    runCatching { getValue(name).jsonArray.map { it.jsonObject } }
        .getOrElse { throw RemoteAskFailureException(RemoteAskFailureReason.InvalidResponse) }

private fun JsonObject.optionalArray(name: String): List<JsonObject> =
    when (val value = this[name]) {
        null, JsonNull -> emptyList()
        is JsonArray -> runCatching { value.map { it.jsonObject } }
            .getOrElse { throw RemoteAskFailureException(RemoteAskFailureReason.InvalidResponse) }
        else -> throw RemoteAskFailureException(RemoteAskFailureReason.InvalidResponse)
    }

private fun JsonObject.requiredString(name: String): String =
    (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
        ?: throw RemoteAskFailureException(RemoteAskFailureReason.InvalidResponse)

private fun JsonObject.optionalString(name: String): String =
    (this[name] as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun JsonObject.nullableString(name: String): String? =
    when (val value = this[name]) {
        null, JsonNull -> null
        is JsonPrimitive -> value.contentOrNull?.takeIf(String::isNotBlank)
        else -> throw RemoteAskFailureException(RemoteAskFailureReason.InvalidResponse)
    }

private fun JsonObject.optionalBoolean(name: String): Boolean =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: false

private fun String.pathComponent(): String = buildString {
    this@pathComponent.encodeToByteArray().forEach { byte ->
        val value = byte.toInt() and 0xff
        val character = value.toChar()
        if (
            character in 'a'..'z' ||
            character in 'A'..'Z' ||
            character in '0'..'9' ||
            character == '-' || character == '.' || character == '_' || character == '~'
        ) {
            append(character)
        } else {
            append('%')
            append(HEX[value ushr 4])
            append(HEX[value and 0x0f])
        }
    }
}

private val jsonHeaders = mapOf(
    "Accept" to "application/json",
    "Content-Type" to "application/json",
)

private const val MAX_PENDING_EVENT_CHARACTERS = 1_048_576
private const val HEX = "0123456789ABCDEF"
