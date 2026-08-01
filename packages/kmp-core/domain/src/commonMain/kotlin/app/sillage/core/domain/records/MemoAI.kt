package app.sillage.core.domain.records

/** AI-derived metadata associated with one memo. */
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
