package app.sillage.core.network

import app.sillage.core.application.auth.AuthenticationFailureException
import app.sillage.core.application.auth.AuthenticationFailureReason
import app.sillage.core.domain.records.Memo
import app.sillage.core.sync.AppliedMemoSync
import app.sillage.core.sync.ConflictMemoSync
import app.sillage.core.sync.MemoSyncGateway
import app.sillage.core.sync.PendingMemoSync
import app.sillage.core.sync.SyncPushSummary
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

internal class RemoteMemoSyncGateway(
    private val baseUrl: String,
    private val executeAuthenticated: suspend (SillageHttpRequest) -> SillageHttpResponse,
    private val json: Json,
) : MemoSyncGateway {
    override suspend fun pushMemos(pending: List<PendingMemoSync>): SyncPushSummary {
        if (pending.isEmpty()) {
            return SyncPushSummary(applied = 0, conflict = 0, rejected = 0)
        }

        var applied = 0
        var conflict = 0
        var rejected = 0
        val appliedMemoSyncs = mutableListOf<AppliedMemoSync>()
        val conflictMemoSyncs = mutableListOf<ConflictMemoSync>()

        for (batch in pending.chunked(MaxSyncChangesPerRequest)) {
            val response = executeAuthenticated(
                SillageHttpRequest(
                    method = SillageHttpMethod.Post,
                    url = "$baseUrl/api/v1/sync:push",
                    headers = syncJsonHeaders,
                    body = buildJsonObject {
                        put("changes", buildJsonArray {
                            batch.forEach { add(it.toSyncJson()) }
                        })
                    }.toString(),
                ),
            )
            requireSyncSuccess(response)
            val batchSummary = parseSyncPushResponse(response, batch)
            applied += batchSummary.applied
            conflict += batchSummary.conflict
            rejected += batchSummary.rejected
            appliedMemoSyncs += batchSummary.appliedMemoSyncs
            conflictMemoSyncs += batchSummary.conflictMemoSyncs
        }

        return SyncPushSummary(
            applied = applied,
            conflict = conflict,
            rejected = rejected,
            appliedMemoSyncs = appliedMemoSyncs,
            conflictMemoSyncs = conflictMemoSyncs,
        )
    }

    private fun parseSyncPushResponse(
        response: SillageHttpResponse,
        expected: List<PendingMemoSync>,
    ): SyncPushSummary {
        if (response.body.length > MaxSyncPushResponseCharacters) {
            invalidSyncResponse()
        }
        val body = try {
            json.parseToJsonElement(response.body).jsonObject
        } catch (_: SerializationException) {
            invalidSyncResponse()
        } catch (_: IllegalArgumentException) {
            invalidSyncResponse()
        }
        val results = body["results"] as? JsonArray ?: invalidSyncResponse()
        if (results.size != expected.size) {
            invalidSyncResponse()
        }

        var applied = 0
        var conflict = 0
        var rejected = 0
        val appliedMemoSyncs = mutableListOf<AppliedMemoSync>()
        val conflictMemoSyncs = mutableListOf<ConflictMemoSync>()

        results.forEachIndexed { index, element ->
            val result = element as? JsonObject ?: invalidSyncResponse()
            val pending = expected[index]
            val mutationId = result.requiredNonBlankString("mutationId")
            val resourceType = result.requiredNonBlankString("resourceType")
            val resourceId = result.requiredNonBlankString("resourceId")
            if (
                mutationId != pending.mutationId ||
                resourceType != MemoResourceType ||
                resourceId != pending.memo.id
            ) {
                invalidSyncResponse()
            }

            when (result.requiredNonBlankString("status")) {
                "applied" -> {
                    val memo = result.requiredObject("resource").toMemo()
                    if (memo.id != resourceId) {
                        invalidSyncResponse()
                    }
                    applied += 1
                    appliedMemoSyncs += AppliedMemoSync(
                        mutationId = mutationId,
                        memo = memo,
                    )
                }
                "conflict" -> {
                    val serverMemo = result.optionalObject("serverResource")?.toMemo()
                    if (serverMemo != null && serverMemo.id != resourceId) {
                        invalidSyncResponse()
                    }
                    conflict += 1
                    conflictMemoSyncs += ConflictMemoSync(
                        mutationId = mutationId,
                        resourceId = resourceId,
                        clientVersion = result.optionalLong("clientVersion"),
                        serverVersion = result.optionalLong("serverVersion")
                            ?: serverMemo?.version,
                        serverMemo = serverMemo,
                    )
                }
                "rejected" -> rejected += 1
                else -> invalidSyncResponse()
            }
        }

        return SyncPushSummary(
            applied = applied,
            conflict = conflict,
            rejected = rejected,
            appliedMemoSyncs = appliedMemoSyncs,
            conflictMemoSyncs = conflictMemoSyncs,
        )
    }
}

private fun PendingMemoSync.toSyncJson(): JsonObject {
    if (mutationId.isBlank() || memo.id.isBlank()) {
        throw IllegalArgumentException("Memo sync identifiers must not be blank.")
    }
    val resolvedAction = action.ifBlank {
        when {
            memo.purgedAt != null -> "purge"
            memo.deletedAt != null -> "delete"
            baseVersion == null -> "create"
            else -> "update"
        }
    }
    if (resolvedAction !in SupportedMemoActions) {
        throw IllegalArgumentException("The memo sync action is not supported.")
    }

    val includesMemoPayload = resolvedAction == "create" || resolvedAction == "update"
    val memoPayload = if (includesMemoPayload) {
        buildJsonObject {
            put("id", memo.id)
            put("content", memo.content)
            put("entryDate", memo.entryDate)
            put("favorited", memo.favoritedAt != null)
            put("archived", memo.archivedAt != null)
        }
    } else {
        null
    }
    return buildJsonObject {
        put("mutationId", mutationId)
        put("resourceType", MemoResourceType)
        put("resourceId", memo.id)
        put("action", resolvedAction)
        memoPayload?.let { put("memo", it) }
        if (resolvedAction != "create") {
            baseVersion?.let { put("baseVersion", it) }
        }
    }
}

private fun requireSyncSuccess(response: SillageHttpResponse) {
    if (response.statusCode in 200..299) return
    if (response.statusCode == 401) {
        throw AuthenticationFailureException(AuthenticationFailureReason.SessionExpired)
    }
    throw SillageHttpStatusException(response.statusCode)
}

private fun JsonObject.toMemo(): Memo {
    return Memo(
        id = requiredNonBlankString("id"),
        content = requiredString("content"),
        entryDate = requiredNonBlankString("entryDate"),
        version = requiredLong("version"),
        createdAt = requiredNonBlankString("createdAt"),
        updatedAt = requiredNonBlankString("updatedAt"),
        favoritedAt = optionalString("favoritedAt") ?: optionalString("pinnedAt"),
        archivedAt = optionalString("archivedAt"),
        deletedAt = optionalString("deletedAt"),
        purgedAt = optionalString("purgedAt"),
    )
}

private fun JsonObject.requiredObject(key: String): JsonObject {
    return this[key] as? JsonObject ?: invalidSyncResponse()
}

private fun JsonObject.optionalObject(key: String): JsonObject? {
    return when (val value = this[key]) {
        null, JsonNull -> null
        is JsonObject -> value
        else -> invalidSyncResponse()
    }
}

private fun JsonObject.requiredString(key: String): String {
    val value = this[key] as? JsonPrimitive ?: invalidSyncResponse()
    if (!value.isString) invalidSyncResponse()
    return value.content
}

private fun JsonObject.requiredNonBlankString(key: String): String {
    return requiredString(key).takeIf(String::isNotBlank) ?: invalidSyncResponse()
}

private fun JsonObject.optionalString(key: String): String? {
    return when (val value = this[key]) {
        null, JsonNull -> null
        is JsonPrimitive -> {
            if (!value.isString) invalidSyncResponse()
            value.content
        }
        else -> invalidSyncResponse()
    }
}

private fun JsonObject.requiredLong(key: String): Long {
    val value = this[key] as? JsonPrimitive ?: invalidSyncResponse()
    if (value.isString) invalidSyncResponse()
    return value.longOrNull ?: invalidSyncResponse()
}

private fun JsonObject.optionalLong(key: String): Long? {
    return when (val value = this[key]) {
        null, JsonNull -> null
        is JsonPrimitive -> {
            if (value.isString) invalidSyncResponse()
            value.longOrNull ?: invalidSyncResponse()
        }
        else -> invalidSyncResponse()
    }
}

private fun invalidSyncResponse(): Nothing {
    throw InvalidServerResponseException("The Sillage sync response is invalid.")
}

private val syncJsonHeaders = mapOf(
    "Accept" to "application/json",
    "Content-Type" to "application/json; charset=utf-8",
    "Cache-Control" to "no-cache",
)
private val SupportedMemoActions = setOf("create", "update", "delete", "restore", "purge")
private const val MemoResourceType = "memo"
private const val MaxSyncChangesPerRequest = 200
private const val MaxSyncPushResponseCharacters = 8 * 1024 * 1024
