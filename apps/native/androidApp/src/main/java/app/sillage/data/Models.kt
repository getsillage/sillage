package app.sillage.data

import app.sillage.core.domain.records.Memo
import app.sillage.core.domain.ask.AskMessage
import app.sillage.core.domain.ask.AskSourceRef
import app.sillage.core.application.records.UploadedAttachment
import app.sillage.features.settings.AIProfileDraft
import org.json.JSONArray
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

data class MemoPage(
    val memos: List<Memo>,
    val nextCursor: String,
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

fun attachmentMarkdown(attachment: UploadedAttachment): String {
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
