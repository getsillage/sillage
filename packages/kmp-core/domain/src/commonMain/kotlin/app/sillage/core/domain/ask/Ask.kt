package app.sillage.core.domain.ask

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

fun AskConversation.isActive(): Boolean = archivedAt == null && deletedAt == null

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
    val promptVersion: String,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String?,
)
