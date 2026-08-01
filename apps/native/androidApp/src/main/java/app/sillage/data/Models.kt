package app.sillage.data

import app.sillage.core.domain.records.Memo
import app.sillage.core.domain.ask.AskMessage
import app.sillage.core.domain.ask.AskSourceRef
import app.sillage.core.domain.settings.AIProfile
import org.json.JSONArray
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

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

data class BootstrapInfo(
    val initialized: Boolean,
    val serverVersion: String,
    val serverRevision: String,
    val apiVersion: String,
    val minimumAndroidVersionCode: Int,
)

data class MemoPage(
    val memos: List<Memo>,
    val nextCursor: String,
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

data class AIProfileDraft(
    val id: String = "",
    // Stable editor identity for unsaved profiles. Manual JSON/API mappings
    // intentionally omit it so it never becomes part of a persistence contract.
    val draftKey: String = "",
    val name: String = "",
    val provider: String = "anthropic",
    val baseUrl: String = "",
    val model: String = "",
    val temperature: Double = 0.3,
    val maxTokens: Long = 1000,
    val enabled: Boolean = true,
    val active: Boolean = false,
    val hasApiKey: Boolean = false,
    val keyUnavailable: Boolean = false,
    val apiKeyInput: String = "",
    // Raw input drafts avoid coercing transient values such as "" or "0." while
    // the user types. Parse only when saving/testing.
    val temperatureInput: String = temperature.toString(),
    val maxTokensInput: String = maxTokens.toString(),
)

data class AIProfileInput(
    val id: String?,
    val name: String,
    val provider: String,
    val baseUrl: String,
    val model: String,
    val temperature: Double?,
    val maxTokens: Long?,
    val enabled: Boolean,
    val active: Boolean,
    val apiKey: String?,
)

data class AskPathEntry(
    val message: AskMessage,
    val variants: List<AskMessage>,
    val index: Int,
)

data class AskStreamEvent(
    val event: String,
    val data: String,
)

class ApiException(message: String, val statusCode: Int? = null) : Exception(message)

fun monthGrid(
    year: Int,
    month: Int,
    firstDayOfWeek: DayOfWeek = DayOfWeek.SUNDAY,
): List<List<String?>> {
    val ym = YearMonth.of(year, month)
    val lead = firstWeekday(ym.atDay(1).dayOfWeek, firstDayOfWeek)
    val cells = mutableListOf<String?>()
    repeat(lead) { cells += null }
    for (day in 1..ym.lengthOfMonth()) {
        cells += LocalDate.of(year, month, day).toString()
    }
    while (cells.size % 7 != 0) {
        cells += null
    }
    return cells.chunked(7)
}

fun adjacentMonth(year: Int, month: Int, delta: Int): Pair<Int, Int> {
    val ym = YearMonth.of(year, month).plusMonths(delta.toLong())
    return ym.year to ym.monthValue
}

private fun firstWeekday(day: DayOfWeek, firstDayOfWeek: DayOfWeek): Int {
    return (day.value - firstDayOfWeek.value + 7) % 7
}

fun attachmentMarkdown(attachment: Attachment): String {
    return if (attachment.contentType.startsWith("image/")) {
        "\n![${attachment.filename}](${attachment.url})\n"
    } else {
        "\n[${attachment.filename}](${attachment.url})\n"
    }
}

/** Local offline-queued attachment waiting for an online upload. */
data class PendingLocalAttachment(
    val id: String,
    val filename: String,
    val contentType: String,
    val absolutePath: String,
    val size: Long,
)

const val LOCAL_PENDING_ATTACHMENT_UID_PREFIX = "localpending-"

fun localAttachmentPath(pending: PendingLocalAttachment): String {
    val safeName = pending.filename
        .replace('\\', '/')
        .substringAfterLast('/')
        .ifBlank { "attachment" }
    return "/file/attachments/$LOCAL_PENDING_ATTACHMENT_UID_PREFIX${pending.id}/$safeName"
}

fun localAttachmentMarkdown(pending: PendingLocalAttachment): String {
    val url = localAttachmentPath(pending)
    return if (pending.contentType.startsWith("image/")) {
        "\n![${pending.filename}]($url)\n"
    } else {
        "\n[${pending.filename}]($url)\n"
    }
}

fun pendingLocalAttachmentId(target: MarkdownLinkTarget.ProtectedAttachment): String? {
    val segments = target.path.substringBefore('?').trim('/').split('/')
    if (segments.size != 4 || segments[0] != "file" || segments[1] != "attachments") {
        return null
    }
    val uid = segments[2]
    if (!uid.startsWith(LOCAL_PENDING_ATTACHMENT_UID_PREFIX)) {
        return null
    }
    return uid.removePrefix(LOCAL_PENDING_ATTACHMENT_UID_PREFIX).ifBlank { null }
}

fun askAnswerMemoContent(message: AskMessage): String {
    return if (message.role == "assistant") message.content.trim() else ""
}

fun askSourceLabel(source: AskSourceRef): String {
    return "${source.entryDate} · ${source.excerpt}"
}

fun memoSummarySourceCount(sourceMemoIds: String): Int? {
    val ids = runCatching { JSONArray(sourceMemoIds) }.getOrNull() ?: return null
    val uniqueIds = buildSet {
        for (index in 0 until ids.length()) {
            val id = (ids.opt(index) as? String)?.trim().orEmpty()
            if (id.isNotEmpty()) {
                add(id)
            }
        }
    }
    return uniqueIds.size.takeIf { it > 0 }
}

fun markdownFormatSnippet(style: MarkdownFormatStyle, sample: String): String {
    return when (style) {
        MarkdownFormatStyle.Heading -> "\n# $sample\n"
        MarkdownFormatStyle.Bold -> "**$sample**"
        MarkdownFormatStyle.Italic -> "*$sample*"
        MarkdownFormatStyle.Code -> "`$sample`"
        MarkdownFormatStyle.List -> "\n- $sample\n"
        MarkdownFormatStyle.Quote -> "\n> $sample\n"
    }
}

enum class MarkdownFormatStyle {
    Heading,
    Bold,
    Italic,
    Code,
    List,
    Quote,
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
        temperatureInput = temperature.toString(),
        maxTokensInput = maxTokens.toString(),
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
        temperature = temperatureInput.trim().toDoubleOrNull(),
        maxTokens = maxTokensInput.trim().toLongOrNull()?.takeIf { it > 0 },
        enabled = enabled,
        active = active,
        apiKey = trimmedKey.takeIf { it.isNotBlank() },
    )
}

fun firstBlankAIProfileNameIndex(profiles: List<AIProfileDraft>): Int? {
    return profiles.indexOfFirst { it.name.isBlank() }.takeIf { it >= 0 }
}

fun mergeSavedAIProfilesForLocalStorage(
    currentProfiles: List<AIProfileDraft>,
    remoteProfiles: List<AIProfileDraft>,
    submittedProfiles: List<AIProfileDraft>,
): List<AIProfileDraft> {
    val currentById = currentProfiles.associateBy { it.id }
    return remoteProfiles.mapIndexed { index, profile ->
        val submitted = submittedProfiles.getOrNull(index)
        val existing = currentById[profile.id]
        val apiKeyInput = when {
            submitted?.apiKeyInput.orEmpty().isNotBlank() -> submitted?.apiKeyInput?.trim().orEmpty()
            existing?.apiKeyInput.orEmpty().isNotBlank() -> existing?.apiKeyInput?.trim().orEmpty()
            else -> ""
        }
        profile.copy(
            hasApiKey = profile.hasApiKey || apiKeyInput.isNotBlank(),
            apiKeyInput = apiKeyInput,
            keyUnavailable = false,
        )
    }
}

fun activeAskMessages(messages: List<AskMessage>): List<AskMessage> {
    return messages.filter { it.deletedAt == null }
}

fun buildAskActivePath(messages: List<AskMessage>, headId: String?): List<AskPathEntry> {
    val active = activeAskMessages(messages)
    if (active.isEmpty()) {
        return emptyList()
    }
    val byId = active.associateBy { it.id }
    val children = askChildrenByParent(active)
    var leaf = headId?.let(byId::get) ?: active.maxByOrNull { it.createdAt }
    if (leaf == null) {
        return emptyList()
    }

    val pathIds = mutableListOf<String>()
    val seen = mutableSetOf<String>()
    while (leaf != null && seen.add(leaf.id)) {
        pathIds += leaf.id
        leaf = leaf.parentId?.let(byId::get)
    }

    return pathIds.asReversed().mapNotNull { id ->
        val message = byId[id] ?: return@mapNotNull null
        val variants = children[message.parentId.orEmpty()].orEmpty()
            .filter { it.role == message.role }
        AskPathEntry(
            message = message,
            variants = variants,
            index = variants.indexOfFirst { it.id == message.id },
        )
    }
}

fun askBranchLeafId(messages: List<AskMessage>, fromId: String): String {
    val children = askChildrenByParent(activeAskMessages(messages))
    var current = fromId
    while (true) {
        val kids = children[current].orEmpty()
        if (kids.isEmpty()) {
            return current
        }
        current = kids.last().id
    }
}

fun lastAssistantMessageId(entries: List<AskPathEntry>): String? {
    return entries.lastOrNull { it.message.role == "assistant" }?.message?.id
}

fun parseAskStreamEvent(block: String): AskStreamEvent? {
    var event = "message"
    val data = StringBuilder()
    for (line in block.lineSequence()) {
        when {
            line.startsWith("event:") -> event = line.removePrefix("event:").trim()
            line.startsWith("data:") -> data.append(line.removePrefix("data:").trim())
        }
    }
    if (data.isBlank()) {
        return null
    }
    return AskStreamEvent(event = event, data = data.toString())
}

private fun askChildrenByParent(messages: List<AskMessage>): Map<String, List<AskMessage>> {
    return messages.groupBy { it.parentId.orEmpty() }
        .mapValues { (_, children) -> children.sortedBy { it.createdAt } }
}
