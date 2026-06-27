package com.miofelix.sillage.data

data class Account(
    val id: String,
    val username: String,
    val displayName: String,
)

data class AuthSession(
    val account: Account,
    val accessToken: String,
    val expiresAt: String,
)

data class Memo(
    val id: String,
    val content: String,
    val entryDate: String,
    val version: Long,
    val createdAt: String,
    val updatedAt: String,
    val pinnedAt: String?,
    val archivedAt: String?,
    val deletedAt: String?,
)

data class MemoDetail(
    val memo: Memo,
    val ai: MemoAI?,
)

data class MemoAI(
    val memoId: String,
    val summary: String?,
    val sentiment: String?,
    val provider: String,
    val model: String,
    val profileId: String,
    val promptVersion: String,
    val sourceMemoIds: String,
    val status: String,
    val errorCode: String?,
    val startedAt: String?,
    val finishedAt: String?,
    val inputTokens: Long,
    val outputTokens: Long,
    val totalTokens: Long,
    val createdAt: String,
    val updatedAt: String,
)

data class Attachment(
    val uid: String,
    val url: String,
    val filename: String,
    val contentType: String,
    val size: Long,
    val sha256: String?,
)

data class AttachmentUpload(
    val filename: String,
    val contentType: String,
    val bytes: ByteArray,
)

data class AIProfile(
    val id: String,
    val name: String,
    val provider: String,
    val baseUrl: String,
    val model: String,
    val temperature: Double,
    val maxTokens: Long,
    val enabled: Boolean,
    val active: Boolean,
    val hasApiKey: Boolean,
    val keyUnavailable: Boolean,
    val autoSummary: Boolean,
    val createdAt: String,
    val updatedAt: String,
)

data class AIProfileDraft(
    val id: String = "",
    val name: String = "新档案",
    val provider: String = "anthropic",
    val baseUrl: String = "",
    val model: String = "",
    val temperature: Double = 0.3,
    val maxTokens: Long = 1000,
    val enabled: Boolean = true,
    val active: Boolean = false,
    val hasApiKey: Boolean = false,
    val keyUnavailable: Boolean = false,
    val autoSummary: Boolean = false,
    val apiKeyInput: String = "",
)

data class AIProfileInput(
    val id: String?,
    val name: String,
    val provider: String,
    val baseUrl: String,
    val model: String,
    val temperature: Double,
    val maxTokens: Long,
    val enabled: Boolean,
    val active: Boolean,
    val autoSummary: Boolean,
    val apiKey: String?,
)

data class AskConversation(
    val id: String,
    val title: String,
    val status: String,
    val contextScope: String,
    val headMessageId: String?,
    val pinnedAt: String?,
    val archivedAt: String?,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String?,
)

data class AskSourceRef(
    val memoId: String,
    val entryDate: String,
    val excerpt: String,
    val rank: Int,
)

data class AskMessage(
    val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val parentId: String?,
    val forkOfId: String?,
    val status: String,
    val sourceRefs: List<AskSourceRef>,
    val model: String,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String?,
)

class ApiException(message: String) : Exception(message)

fun Memo.isActive(): Boolean = archivedAt == null && deletedAt == null

fun sortMemos(memos: List<Memo>): List<Memo> {
    return memos.sortedWith(
        compareByDescending<Memo> { if (it.pinnedAt != null) 1 else 0 }
            .thenByDescending { it.entryDate }
            .thenByDescending { it.createdAt },
    )
}

fun activeMemos(memos: List<Memo>): List<Memo> {
    return sortMemos(memos.filter { it.isActive() })
}

fun attachmentMarkdown(attachment: Attachment): String {
    return if (attachment.contentType.startsWith("image/")) {
        "\n![${attachment.filename}](${attachment.url})\n"
    } else {
        "\n[${attachment.filename}](${attachment.url})\n"
    }
}

fun AIProfile.toDraft(): AIProfileDraft {
    return AIProfileDraft(
        id = id,
        name = name,
        provider = provider,
        baseUrl = baseUrl,
        model = model,
        temperature = temperature,
        maxTokens = maxTokens,
        enabled = enabled,
        active = active,
        hasApiKey = hasApiKey,
        keyUnavailable = keyUnavailable,
        autoSummary = autoSummary,
    )
}

fun AIProfileDraft.toInput(): AIProfileInput {
    val trimmedKey = apiKeyInput.trim()
    return AIProfileInput(
        id = id.takeIf { it.isNotBlank() },
        name = name,
        provider = provider,
        baseUrl = baseUrl,
        model = model,
        temperature = temperature,
        maxTokens = maxTokens,
        enabled = enabled,
        active = active,
        autoSummary = autoSummary,
        apiKey = trimmedKey.takeIf { it.isNotBlank() },
    )
}
