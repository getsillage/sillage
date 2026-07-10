package app.sillage.data

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

data class MemoPage(
    val memos: List<Memo>,
    val nextCursor: String,
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

data class AISettings(
    val profiles: List<AIProfile>,
    val autoSummary: Boolean,
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

data class AskPathEntry(
    val message: AskMessage,
    val variants: List<AskMessage>,
    val index: Int,
)

data class AskStreamEvent(
    val event: String,
    val data: String,
)

data class MarkdownBlock(
    val kind: MarkdownBlockKind,
    val text: String,
    val url: String? = null,
)

enum class MarkdownBlockKind {
    Paragraph,
    Heading,
    Quote,
    ListItem,
    Code,
    Link,
    Image,
}

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

fun excerpt(body: String, max: Int = 120): String {
    val text = body.replace(Regex("\\s+"), " ").trim()
    return if (text.length > max) "${text.take(max)}…" else text
}

fun onThisDay(memos: List<Memo>, todayISO: String): List<Memo> {
    val monthDay = todayISO.drop(5)
    val year = todayISO.take(4)
    return memos
        .filter {
            it.isActive() &&
                it.entryDate.drop(5) == monthDay &&
                it.entryDate.take(4) < year
        }
        .sortedByDescending { it.entryDate }
}

fun yearsBetween(fromISO: String, toISO: String): Int {
    return toISO.take(4).toInt() - fromISO.take(4).toInt()
}

fun entryDateCounts(memos: List<Memo>): Map<String, Int> {
    return memos.filter { it.isActive() }
        .groupingBy { it.entryDate }
        .eachCount()
}

fun entriesByDate(memos: List<Memo>, date: String): List<Memo> {
    return activeMemos(memos.filter { it.entryDate == date })
}

fun monthGrid(year: Int, month: Int): List<List<String?>> {
    val ym = YearMonth.of(year, month)
    val lead = firstWeekday(ym.atDay(1).dayOfWeek)
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

data class CalendarMemoCoverage(
    val hasMoreOlderRecords: Boolean,
    val currentMonthMayBeIncomplete: Boolean,
)

fun calendarMemoCoverage(
    memos: List<Memo>,
    nextCursor: String,
    year: Int,
    month: Int,
): CalendarMemoCoverage {
    val hasMore = nextCursor.isNotBlank()
    if (!hasMore) {
        return CalendarMemoCoverage(
            hasMoreOlderRecords = false,
            currentMonthMayBeIncomplete = false,
        )
    }
    val viewedMonth = YearMonth.of(year, month)
    val oldestLoadedMonth = memos
        .asSequence()
        .filter { it.isActive() }
        .mapNotNull { memo ->
            runCatching { YearMonth.parse(memo.entryDate.take(7)) }.getOrNull()
        }
        .minOrNull()
    return CalendarMemoCoverage(
        hasMoreOlderRecords = true,
        currentMonthMayBeIncomplete = oldestLoadedMonth == null || !viewedMonth.isAfter(oldestLoadedMonth),
    )
}

private fun firstWeekday(day: DayOfWeek): Int {
    return day.value % 7
}

fun attachmentMarkdown(attachment: Attachment): String {
    return if (attachment.contentType.startsWith("image/")) {
        "\n![${attachment.filename}](${attachment.url})\n"
    } else {
        "\n[${attachment.filename}](${attachment.url})\n"
    }
}

fun askAnswerMemoContent(message: AskMessage): String {
    return if (message.role == "assistant") message.content.trim() else ""
}

fun askSourceLabel(source: AskSourceRef): String {
    return "${source.entryDate} · ${source.excerpt}"
}

fun memoMetadataLines(memo: Memo?): List<String> {
    if (memo == null) {
        return emptyList()
    }
    return buildList {
        add("创建于 ${memo.createdAt}")
        if (memo.version > 1) {
            add("最近修改 ${memo.updatedAt}，共修改 ${memo.version - 1} 次")
        }
    }
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

fun parseMarkdownPreview(content: String): List<MarkdownBlock> {
    return content
        .lines()
        .mapNotNull { raw ->
            val line = raw.trim()
            when {
                line.isBlank() -> null
                line.startsWith("```") -> MarkdownBlock(MarkdownBlockKind.Code, line.removePrefix("```").ifBlank { "代码块" })
                line.startsWith("### ") -> MarkdownBlock(MarkdownBlockKind.Heading, line.removePrefix("### ").trim())
                line.startsWith("## ") -> MarkdownBlock(MarkdownBlockKind.Heading, line.removePrefix("## ").trim())
                line.startsWith("# ") -> MarkdownBlock(MarkdownBlockKind.Heading, line.removePrefix("# ").trim())
                line.startsWith("> ") -> MarkdownBlock(MarkdownBlockKind.Quote, line.removePrefix("> ").trim())
                line.startsWith("- ") -> MarkdownBlock(MarkdownBlockKind.ListItem, line.removePrefix("- ").trim())
                line.startsWith("* ") -> MarkdownBlock(MarkdownBlockKind.ListItem, line.removePrefix("* ").trim())
                line.matches(Regex("""\d+\.\s+.+""")) -> MarkdownBlock(
                    MarkdownBlockKind.ListItem,
                    line.replaceFirst(Regex("""\d+\.\s+"""), ""),
                )
                else -> parseMarkdownMedia(line) ?: MarkdownBlock(MarkdownBlockKind.Paragraph, stripInlineMarkdown(line))
            }
        }
}

fun markdownFormatSnippet(style: MarkdownFormatStyle): String {
    return when (style) {
        MarkdownFormatStyle.Heading -> "\n# 标题\n"
        MarkdownFormatStyle.Bold -> "**加粗**"
        MarkdownFormatStyle.Italic -> "*斜体*"
        MarkdownFormatStyle.Code -> "`代码`"
        MarkdownFormatStyle.List -> "\n- 列表项\n"
        MarkdownFormatStyle.Quote -> "\n> 引用\n"
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

private fun parseMarkdownMedia(line: String): MarkdownBlock? {
    val image = Regex("""!\[([^]]*)]\(([^)]+)\)""").find(line)
    if (image != null) {
        return MarkdownBlock(
            kind = MarkdownBlockKind.Image,
            text = image.groupValues[1].ifBlank { "图片" },
            url = image.groupValues[2],
        )
    }
    val link = Regex("""\[([^]]+)]\(([^)]+)\)""").find(line)
    if (link != null) {
        return MarkdownBlock(
            kind = MarkdownBlockKind.Link,
            text = link.groupValues[1],
            url = link.groupValues[2],
        )
    }
    return null
}

private fun stripInlineMarkdown(line: String): String {
    return line
        .replace(Regex("""\*\*([^*]+)\*\*"""), "$1")
        .replace(Regex("""\*([^*]+)\*"""), "$1")
        .replace(Regex("""`([^`]+)`"""), "$1")
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
