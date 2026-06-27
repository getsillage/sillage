package com.miofelix.sillage.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoFiltersTest {
    @Test
    fun sortMemosPinsFirstThenNewestEntryDate() {
        val oldPinned = memo(id = "old-pinned", entryDate = "2024-01-01", pinnedAt = "2024-01-04T00:00:00Z")
        val newest = memo(id = "newest", entryDate = "2024-01-03")
        val older = memo(id = "older", entryDate = "2024-01-02")

        val sorted = sortMemos(listOf(older, newest, oldPinned))

        assertEquals(listOf("old-pinned", "newest", "older"), sorted.map { it.id })
    }

    @Test
    fun activeMemosExcludesArchivedAndDeletedEntries() {
        val active = memo(id = "active")
        val archived = memo(id = "archived", archivedAt = "2024-01-02T00:00:00Z")
        val deleted = memo(id = "deleted", deletedAt = "2024-01-03T00:00:00Z")

        val filtered = activeMemos(listOf(archived, deleted, active))

        assertEquals(listOf("active"), filtered.map { it.id })
    }

    @Test
    fun onThisDayReturnsEarlierYearsNewestFirst() {
        val newest = memo(id = "newest", entryDate = "2025-06-27")
        val older = memo(id = "older", entryDate = "2024-06-27")
        val today = memo(id = "today", entryDate = "2026-06-27")
        val otherDay = memo(id = "other-day", entryDate = "2025-06-26")
        val archived = memo(id = "archived", entryDate = "2023-06-27", archivedAt = "x")

        val memories = onThisDay(listOf(older, today, newest, archived, otherDay), "2026-06-27")

        assertEquals(listOf("newest", "older"), memories.map { it.id })
    }

    @Test
    fun entryDateCountsAndEntriesByDateUseActiveMemosOnly() {
        val first = memo(id = "first", entryDate = "2026-06-27", createdAt = "2026-06-27T00:00:00Z")
        val second = memo(id = "second", entryDate = "2026-06-27", createdAt = "2026-06-27T00:01:00Z")
        val archived = memo(id = "archived", entryDate = "2026-06-27", archivedAt = "x")
        val other = memo(id = "other", entryDate = "2026-06-28")

        val memos = listOf(first, second, archived, other)

        assertEquals(2, entryDateCounts(memos)["2026-06-27"])
        assertEquals(listOf("second", "first"), entriesByDate(memos, "2026-06-27").map { it.id })
    }

    @Test
    fun monthGridStartsOnSundayAndPadsTrailingCells() {
        val grid = monthGrid(2026, 6)

        assertEquals("2026-06-01", grid.first()[1])
        assertEquals(null, grid.first()[0])
        assertEquals("2026-06-30", grid.last()[2])
        assertEquals(null, grid.last()[6])
    }

    @Test
    fun adjacentMonthCrossesYears() {
        assertEquals(2025 to 12, adjacentMonth(2026, 1, -1))
        assertEquals(2027 to 1, adjacentMonth(2026, 12, 1))
    }

    @Test
    fun excerptCollapsesWhitespaceAndTruncates() {
        assertEquals("a b c", excerpt(" a\n b\t c "))
        assertEquals("abc…", excerpt("abcdef", max = 3))
    }

    @Test
    fun attachmentMarkdownUsesImageSyntaxForImages() {
        val attachment = attachment(filename = "photo.jpg", contentType = "image/jpeg")

        assertEquals("\n![photo.jpg](/file/attachments/a/photo.jpg)\n", attachmentMarkdown(attachment))
    }

    @Test
    fun attachmentMarkdownUsesLinkSyntaxForOtherFiles() {
        val attachment = attachment(filename = "note.pdf", contentType = "application/pdf")

        assertEquals("\n[note.pdf](/file/attachments/a/note.pdf)\n", attachmentMarkdown(attachment))
    }

    @Test
    fun parseMarkdownPreviewRecognizesCommonBlocks() {
        val blocks = parseMarkdownPreview(
            """
            # 标题
            > 引用
            - 列表项
            ![图](/a.png)
            [链接](https://example.com)
            **普通** `文字`
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                MarkdownBlockKind.Heading,
                MarkdownBlockKind.Quote,
                MarkdownBlockKind.ListItem,
                MarkdownBlockKind.Image,
                MarkdownBlockKind.Link,
                MarkdownBlockKind.Paragraph,
            ),
            blocks.map { it.kind },
        )
        assertEquals("普通 文字", blocks.last().text)
        assertEquals("/a.png", blocks[3].url)
    }

    @Test
    fun markdownFormatSnippetReturnsExpectedMarkup() {
        assertEquals("**加粗**", markdownFormatSnippet(MarkdownFormatStyle.Bold))
        assertEquals("\n# 标题\n", markdownFormatSnippet(MarkdownFormatStyle.Heading))
        assertEquals("\n- 列表项\n", markdownFormatSnippet(MarkdownFormatStyle.List))
    }

    @Test
    fun aiProfileDraftInputKeepsStoredKeyWhenApiKeyIsBlank() {
        val input = AIProfileDraft(id = "p1", name = "默认", provider = "anthropic", apiKeyInput = " ").toInput()

        assertEquals("p1", input.id)
        assertEquals(null, input.apiKey)
    }

    @Test
    fun aiProfileDraftInputIncludesNewApiKeyWhenProvided() {
        val input = AIProfileDraft(apiKeyInput = " sk-test ").toInput()

        assertEquals("sk-test", input.apiKey)
    }

    @Test
    fun buildAskActivePathFollowsSelectedHeadAndExposesAnswerVariants() {
        val user = askMessage(id = "u1", role = "user", createdAt = "2026-01-01T00:00:00Z")
        val first = askMessage(
            id = "a1",
            role = "assistant",
            parentId = "u1",
            createdAt = "2026-01-01T00:00:01Z",
        )
        val second = askMessage(
            id = "a2",
            role = "assistant",
            parentId = "u1",
            forkOfId = "a1",
            createdAt = "2026-01-01T00:00:02Z",
        )

        val path = buildAskActivePath(listOf(user, first, second), "a1")

        assertEquals(listOf("u1", "a1"), path.map { it.message.id })
        assertEquals(listOf("a1", "a2"), path.last().variants.map { it.id })
        assertEquals(0, path.last().index)
    }

    @Test
    fun askBranchLeafIdDescendsThroughNewestChildren() {
        val user = askMessage(id = "u1", role = "user", createdAt = "2026-01-01T00:00:00Z")
        val answer = askMessage(
            id = "a1",
            role = "assistant",
            parentId = "u1",
            createdAt = "2026-01-01T00:00:01Z",
        )
        val followUp = askMessage(
            id = "u2",
            role = "user",
            parentId = "a1",
            createdAt = "2026-01-01T00:00:02Z",
        )

        assertEquals("u2", askBranchLeafId(listOf(user, answer, followUp), "a1"))
    }

    @Test
    fun parseAskStreamEventReadsEventNameAndJsonData() {
        val event = parseAskStreamEvent(
            """
            event: delta
            data: {"text":"你好"}
            """.trimIndent(),
        )

        assertEquals("delta", event?.event)
        assertEquals("""{"text":"你好"}""", event?.data)
    }

    @Test
    fun parseAskStreamEventIgnoresBlankOrInvalidData() {
        assertEquals(null, parseAskStreamEvent("event: delta"))
        assertEquals("nope", parseAskStreamEvent("event: delta\ndata: nope")?.data)
    }

    @Test
    fun askAnswerMemoContentOnlyUsesTrimmedAssistantAnswers() {
        val answer = askMessage(id = "a1", role = "assistant", content = "  可保存的回答  ")
        val question = askMessage(id = "u1", role = "user", content = "  不应保存  ")

        assertEquals("可保存的回答", askAnswerMemoContent(answer))
        assertEquals("", askAnswerMemoContent(question))
    }

    @Test
    fun askSourceLabelIncludesDateAndExcerpt() {
        val source = AskSourceRef(
            memoId = "m1",
            entryDate = "2026-06-27",
            excerpt = "来源摘要",
            rank = 1,
        )

        assertEquals("2026-06-27 · 来源摘要", askSourceLabel(source))
    }

    private fun memo(
        id: String,
        entryDate: String = "2024-01-01",
        createdAt: String = "${entryDate}T00:00:00Z",
        pinnedAt: String? = null,
        archivedAt: String? = null,
        deletedAt: String? = null,
    ): Memo {
        return Memo(
            id = id,
            content = "content",
            entryDate = entryDate,
            version = 1,
            createdAt = createdAt,
            updatedAt = "${entryDate}T00:00:00Z",
            pinnedAt = pinnedAt,
            archivedAt = archivedAt,
            deletedAt = deletedAt,
        )
    }

    private fun attachment(filename: String, contentType: String): Attachment {
        return Attachment(
            uid = "a",
            url = "/file/attachments/a/$filename",
            filename = filename,
            contentType = contentType,
            size = 10,
            sha256 = null,
        )
    }

    private fun askMessage(
        id: String,
        role: String,
        content: String = id,
        parentId: String? = null,
        forkOfId: String? = null,
        createdAt: String = "2026-01-01T00:00:00Z",
    ): AskMessage {
        return AskMessage(
            id = id,
            conversationId = "c1",
            role = role,
            content = content,
            parentId = parentId,
            forkOfId = forkOfId,
            status = "complete",
            sourceRefs = emptyList(),
            model = "",
            createdAt = createdAt,
            updatedAt = createdAt,
            deletedAt = null,
        )
    }
}
