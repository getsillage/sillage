package com.miofelix.sillage.data

import org.json.JSONArray
import org.json.JSONObject

internal fun SillageExportData.normalizedForLocalStorage(): SillageExportData {
    return copy(
        formatVersion = SillageExportCodec.FORMAT_VERSION,
        themeMode = SessionStore.normalizeThemeMode(themeMode),
        aiProfiles = aiProfiles.map {
            it.copy(
                hasApiKey = it.apiKeyInput.isNotBlank() || it.hasApiKey,
                keyUnavailable = false,
            )
        },
    )
}

internal fun SillageExportData.sanitizedForFileExport(): SillageExportData {
    return normalizedForLocalStorage().copy(
        aiProfiles = aiProfiles.map {
            it.copy(
                hasApiKey = false,
                keyUnavailable = false,
                apiKeyInput = "",
            )
        },
    )
}

internal fun memoToJson(memo: Memo): JSONObject {
    return JSONObject()
        .put("id", memo.id)
        .put("content", memo.content)
        .put("entryDate", memo.entryDate)
        .put("version", memo.version)
        .put("createdAt", memo.createdAt)
        .put("updatedAt", memo.updatedAt)
        .putNullable("pinnedAt", memo.pinnedAt)
        .putNullable("archivedAt", memo.archivedAt)
        .putNullable("deletedAt", memo.deletedAt)
}

internal fun jsonToMemo(body: JSONObject): Memo {
    return Memo(
        id = body.getString("id"),
        content = body.optString("content"),
        entryDate = body.optString("entryDate"),
        version = body.optLong("version", 1),
        createdAt = body.optString("createdAt"),
        updatedAt = body.optString("updatedAt"),
        pinnedAt = body.nullableString("pinnedAt"),
        archivedAt = body.nullableString("archivedAt"),
        deletedAt = body.nullableString("deletedAt"),
    )
}

internal fun memoAIToJson(ai: MemoAI): JSONObject {
    return JSONObject()
        .put("memoId", ai.memoId)
        .putNullable("summary", ai.summary)
        .putNullable("sentiment", ai.sentiment)
        .put("provider", ai.provider)
        .put("model", ai.model)
        .put("profileId", ai.profileId)
        .put("promptVersion", ai.promptVersion)
        .put("sourceMemoIds", ai.sourceMemoIds)
        .put("status", ai.status)
        .putNullable("errorCode", ai.errorCode)
        .putNullable("startedAt", ai.startedAt)
        .putNullable("finishedAt", ai.finishedAt)
        .put("inputTokens", ai.inputTokens)
        .put("outputTokens", ai.outputTokens)
        .put("totalTokens", ai.totalTokens)
        .put("createdAt", ai.createdAt)
        .put("updatedAt", ai.updatedAt)
}

internal fun jsonToMemoAI(body: JSONObject): MemoAI {
    return MemoAI(
        memoId = body.getString("memoId"),
        summary = body.nullableString("summary"),
        sentiment = body.nullableString("sentiment"),
        provider = body.optString("provider"),
        model = body.optString("model"),
        profileId = body.optString("profileId"),
        promptVersion = body.optString("promptVersion"),
        sourceMemoIds = body.optString("sourceMemoIds"),
        status = body.optString("status"),
        errorCode = body.nullableString("errorCode"),
        startedAt = body.nullableString("startedAt"),
        finishedAt = body.nullableString("finishedAt"),
        inputTokens = body.optLong("inputTokens"),
        outputTokens = body.optLong("outputTokens"),
        totalTokens = body.optLong("totalTokens"),
        createdAt = body.optString("createdAt"),
        updatedAt = body.optString("updatedAt"),
    )
}

internal fun aiProfileToJson(profile: AIProfileDraft): JSONObject {
    val body = JSONObject()
        .put("id", profile.id)
        .put("name", profile.name)
        .put("provider", profile.provider)
        .put("baseUrl", profile.baseUrl)
        .put("model", profile.model)
        .put("temperature", profile.temperature)
        .put("maxTokens", profile.maxTokens)
        .put("enabled", profile.enabled)
        .put("active", profile.active)
        .put("hasApiKey", false)
        .put("keyUnavailable", false)
        .put("autoSummary", profile.autoSummary)
    if (profile.apiKeyInput.isNotBlank()) {
        body.put("apiKey", profile.apiKeyInput)
    }
    return body
}

internal fun jsonToAIProfileDraft(body: JSONObject): AIProfileDraft {
    return AIProfileDraft(
        id = body.getString("id"),
        name = body.optString("name"),
        provider = body.optString("provider"),
        baseUrl = body.optString("baseUrl"),
        model = body.optString("model"),
        temperature = body.optDouble("temperature"),
        maxTokens = body.optLong("maxTokens"),
        enabled = body.optBoolean("enabled"),
        active = body.optBoolean("active"),
        hasApiKey = body.optString("apiKey").isNotBlank(),
        keyUnavailable = false,
        autoSummary = body.optBoolean("autoSummary"),
        apiKeyInput = body.optString("apiKey"),
    )
}

internal fun askConversationToJson(conversation: AskConversation): JSONObject {
    return JSONObject()
        .put("id", conversation.id)
        .put("title", conversation.title)
        .put("status", conversation.status)
        .put("contextScope", conversation.contextScope)
        .putNullable("headMessageId", conversation.headMessageId)
        .putNullable("pinnedAt", conversation.pinnedAt)
        .putNullable("archivedAt", conversation.archivedAt)
        .put("createdAt", conversation.createdAt)
        .put("updatedAt", conversation.updatedAt)
        .putNullable("deletedAt", conversation.deletedAt)
}

internal fun jsonToAskConversation(body: JSONObject): AskConversation {
    return AskConversation(
        id = body.getString("id"),
        title = body.optString("title"),
        status = body.optString("status"),
        contextScope = body.optString("contextScope"),
        headMessageId = body.nullableString("headMessageId"),
        pinnedAt = body.nullableString("pinnedAt"),
        archivedAt = body.nullableString("archivedAt"),
        createdAt = body.optString("createdAt"),
        updatedAt = body.optString("updatedAt"),
        deletedAt = body.nullableString("deletedAt"),
    )
}

internal fun askMessageToJson(message: AskMessage): JSONObject {
    return JSONObject()
        .put("id", message.id)
        .put("conversationId", message.conversationId)
        .put("role", message.role)
        .put("content", message.content)
        .putNullable("parentId", message.parentId)
        .putNullable("forkOfId", message.forkOfId)
        .put("status", message.status)
        .put("sourceRefs", message.sourceRefs.toJsonArray(::askSourceRefToJson))
        .put("model", message.model)
        .put("createdAt", message.createdAt)
        .put("updatedAt", message.updatedAt)
        .putNullable("deletedAt", message.deletedAt)
}

internal fun jsonToAskMessage(body: JSONObject): AskMessage {
    return AskMessage(
        id = body.getString("id"),
        conversationId = body.optString("conversationId"),
        role = body.optString("role"),
        content = body.optString("content"),
        parentId = body.nullableString("parentId"),
        forkOfId = body.nullableString("forkOfId"),
        status = body.optString("status"),
        sourceRefs = body.optJSONArray("sourceRefs").toListOrEmpty(::jsonToAskSourceRef),
        model = body.optString("model"),
        createdAt = body.optString("createdAt"),
        updatedAt = body.optString("updatedAt"),
        deletedAt = body.nullableString("deletedAt"),
    )
}

private fun askSourceRefToJson(source: AskSourceRef): JSONObject {
    return JSONObject()
        .put("memoId", source.memoId)
        .put("entryDate", source.entryDate)
        .put("excerpt", source.excerpt)
        .put("rank", source.rank)
}

private fun jsonToAskSourceRef(body: JSONObject): AskSourceRef {
    return AskSourceRef(
        memoId = body.optString("memoId"),
        entryDate = body.optString("entryDate"),
        excerpt = body.optString("excerpt"),
        rank = body.optInt("rank"),
    )
}

internal fun <T> List<T>.toJsonArray(toJson: (T) -> JSONObject): JSONArray {
    val array = JSONArray()
    forEach { array.put(toJson(it)) }
    return array
}

internal fun <T> JSONArray?.toListOrEmpty(fromJson: (JSONObject) -> T): List<T> {
    if (this == null) {
        return emptyList()
    }
    return buildList {
        for (index in 0 until length()) {
            add(fromJson(getJSONObject(index)))
        }
    }
}

private fun JSONObject.putNullable(name: String, value: String?): JSONObject {
    return if (value == null) put(name, JSONObject.NULL) else put(name, value)
}

private fun JSONObject.nullableString(name: String): String? {
    return if (isNull(name)) null else optString(name)
}
