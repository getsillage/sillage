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
