package app.sillage.core.domain.records

/**
 * A user-authored record and its synchronization version.
 *
 * Timestamps remain wire-compatible ISO-8601 strings during the transport and
 * persistence extraction. Domain code must not parse them with platform APIs.
 */
data class Memo(
    val id: String,
    val content: String,
    val entryDate: String,
    val version: Long,
    val createdAt: String,
    val updatedAt: String,
    val favoritedAt: String?,
    val archivedAt: String?,
    val deletedAt: String?,
    val purgedAt: String? = null,
)

/** Returns true only when the record is eligible for active-domain behavior. */
fun Memo.isActive(): Boolean =
    archivedAt == null && deletedAt == null && purgedAt == null
