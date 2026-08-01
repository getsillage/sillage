package app.sillage.data

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoFiltersTest {
    @Test
    fun sortMemosUsesNewestEntryDateWithoutPrioritizingFavorites() {
        val oldFavorite = memo(
            id = "old-favorite",
            entryDate = "2024-01-01",
            favoritedAt = "2024-01-04T00:00:00Z",
        )
        val newest = memo(id = "newest", entryDate = "2024-01-03")
        val older = memo(id = "older", entryDate = "2024-01-02")

        val sorted = sortMemos(listOf(older, newest, oldFavorite))

        assertEquals(listOf("newest", "older", "old-favorite"), sorted.map { it.id })
    }

    @Test
    fun activeMemosExcludesArchivedFavoritedAndDeletedEntries() {
        val active = memo(id = "active")
        val archived = memo(id = "archived", archivedAt = "2024-01-02T00:00:00Z")
        val favorited = memo(id = "favorited", favoritedAt = "2024-01-02T00:00:00Z")
        val deleted = memo(id = "deleted", deletedAt = "2024-01-03T00:00:00Z")

        val filtered = activeMemos(listOf(archived, favorited, deleted, active))

        assertEquals(listOf("active"), filtered.map { it.id })
    }

    @Test
    fun memoListFiltersAreMutuallyExclusiveAndFavoritesIncludeArchivedRecords() {
        val unarchived = memo(id = "unarchived", entryDate = "2024-01-01")
        val archived = memo(
            id = "archived",
            entryDate = "2024-01-02",
            archivedAt = "2024-01-03T00:00:00Z",
        )
        val favorited = memo(
            id = "favorited",
            entryDate = "2024-01-03",
            favoritedAt = "2024-01-04T00:00:00Z",
        )
        val archivedFavorite = memo(
            id = "archived-favorite",
            entryDate = "2024-01-04",
            favoritedAt = "2024-01-05T00:00:00Z",
            archivedAt = "2024-01-05T00:00:00Z",
        )
        val deletedFavorite = memo(
            id = "deleted-favorite",
            entryDate = "2024-01-05",
            favoritedAt = "2024-01-06T00:00:00Z",
            deletedAt = "2024-01-06T00:00:00Z",
        )
        val memos = listOf(unarchived, archived, favorited, archivedFavorite, deletedFavorite)

        assertEquals(
            listOf("unarchived"),
            memosForFilter(memos, MemoListFilter.Unarchived).map { it.id },
        )
        assertEquals(
            listOf("archived"),
            memosForFilter(memos, MemoListFilter.Archived).map { it.id },
        )
        assertEquals(
            listOf("archived-favorite", "favorited"),
            memosForFilter(memos, MemoListFilter.Favorited).map { it.id },
        )
    }

    @Test
    fun activeAskConversationExcludesArchivedAndDeletedEntries() {
        val active = askConversation(id = "active")
        val archived = askConversation(id = "archived", archivedAt = "2024-01-02T00:00:00Z")
        val deleted = askConversation(id = "deleted", deletedAt = "2024-01-03T00:00:00Z")

        assertEquals(true, active.isActive())
        assertEquals(false, archived.isActive())
        assertEquals(false, deleted.isActive())
    }

    @Test
    fun onThisDayReturnsEarlierYearsNewestFirst() {
        val newest = memo(id = "newest", entryDate = "2025-06-27")
        val older = memo(id = "older", entryDate = "2024-06-27")
        val today = memo(id = "today", entryDate = "2026-06-27")
        val otherDay = memo(id = "other-day", entryDate = "2025-06-26")
        val archived = memo(id = "archived", entryDate = "2023-06-27", archivedAt = "x")
        val favorited = memo(id = "favorited", entryDate = "2022-06-27", favoritedAt = "x")

        val memories = onThisDay(
            listOf(older, today, newest, archived, favorited, otherDay),
            "2026-06-27",
        )

        assertEquals(listOf("newest", "older"), memories.map { it.id })
    }

    @Test
    fun entryDateCountsAndEntriesByDateUseActiveMemosOnly() {
        val first = memo(id = "first", entryDate = "2026-06-27", createdAt = "2026-06-27T00:00:00Z")
        val second = memo(id = "second", entryDate = "2026-06-27", createdAt = "2026-06-27T00:01:00Z")
        val archived = memo(id = "archived", entryDate = "2026-06-27", archivedAt = "x")
        val favorited = memo(id = "favorited", entryDate = "2026-06-27", favoritedAt = "x")
        val other = memo(id = "other", entryDate = "2026-06-28")

        val memos = listOf(first, second, archived, favorited, other)

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
    fun monthGridRespectsLocaleFirstDayOfWeek() {
        val grid = monthGrid(2026, 6, DayOfWeek.MONDAY)

        assertEquals("2026-06-01", grid.first().first())
        assertEquals("2026-06-07", grid.first().last())
    }

    @Test
    fun adjacentMonthCrossesYears() {
        assertEquals(2025 to 12, adjacentMonth(2026, 1, -1))
        assertEquals(2027 to 1, adjacentMonth(2026, 12, 1))
    }

    @Test
    fun calendarCoverageMarksCursorBoundaryAndOlderMonthsIncomplete() {
        val memos = listOf(
            memo(id = "new", entryDate = "2026-07-08"),
            memo(id = "oldest", entryDate = "2026-06-30"),
        )

        assertEquals(false, calendarMemoCoverage(memos, "cursor", 2026, 7).currentMonthMayBeIncomplete)
        assertEquals(true, calendarMemoCoverage(memos, "cursor", 2026, 6).currentMonthMayBeIncomplete)
        assertEquals(true, calendarMemoCoverage(memos, "cursor", 2026, 5).currentMonthMayBeIncomplete)
    }

    @Test
    fun calendarCoverageIsCompleteOnlyWhenPaginationIsExhausted() {
        val exhausted = calendarMemoCoverage(emptyList(), "", 2026, 7)
        val unknown = calendarMemoCoverage(emptyList(), "cursor", 2026, 7)

        assertEquals(false, exhausted.hasMoreOlderRecords)
        assertEquals(false, exhausted.currentMonthMayBeIncomplete)
        assertEquals(true, unknown.hasMoreOlderRecords)
        assertEquals(true, unknown.currentMonthMayBeIncomplete)
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
    fun markdownFormatSnippetReturnsExpectedMarkup() {
        assertEquals("**加粗**", markdownFormatSnippet(MarkdownFormatStyle.Bold, "加粗"))
        assertEquals("\n# Heading\n", markdownFormatSnippet(MarkdownFormatStyle.Heading, "Heading"))
        assertEquals("\n- List item\n", markdownFormatSnippet(MarkdownFormatStyle.List, "List item"))
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
    fun aiProfileDraftInputPreservesZeroTemperature() {
        val input = AIProfileDraft(temperatureInput = "0", maxTokensInput = "").toInput()

        assertEquals(0.0, input.temperature)
        assertEquals(null, input.maxTokens)
    }

    @Test
    fun firstBlankAIProfileNameIndexRejectsEmptyAndWhitespaceNames() {
        assertEquals(
            1,
            firstBlankAIProfileNameIndex(
                listOf(
                    AIProfileDraft(name = "Primary"),
                    AIProfileDraft(name = "   "),
                    AIProfileDraft(name = "Secondary"),
                ),
            ),
        )
        assertEquals(0, firstBlankAIProfileNameIndex(listOf(AIProfileDraft(name = ""))))
    }

    @Test
    fun firstBlankAIProfileNameIndexAllowsNamedOrEmptyProfileLists() {
        assertEquals(
            null,
            firstBlankAIProfileNameIndex(
                listOf(AIProfileDraft(name = "Primary"), AIProfileDraft(name = " Secondary ")),
            ),
        )
        assertEquals(null, firstBlankAIProfileNameIndex(emptyList()))
    }

    @Test
    fun localAskQueryTermsAddsChineseBigrams() {
        val terms = localAskQueryTerms("最近睡眠怎样")

        assertEquals(true, terms.contains("睡眠"))
        assertEquals(true, terms.contains("怎样"))
    }

    @Test
    fun normalizeThemeModeAcceptsOnlyDarkOtherwiseLight() {
        assertEquals(SessionStore.THEME_DARK, SessionStore.normalizeThemeMode("dark"))
        assertEquals(SessionStore.THEME_LIGHT, SessionStore.normalizeThemeMode("light"))
        assertEquals(SessionStore.THEME_LIGHT, SessionStore.normalizeThemeMode("system"))
    }

    @Test
    fun normalizeLanguageModeDefaultsToSimplifiedChinese() {
        assertEquals(SessionStore.LANGUAGE_EN, SessionStore.normalizeLanguageMode("en"))
        assertEquals(SessionStore.LANGUAGE_EN, SessionStore.normalizeLanguageMode("en-US"))
        assertEquals(SessionStore.LANGUAGE_ZH_CN, SessionStore.normalizeLanguageMode("zh-CN"))
        assertEquals(SessionStore.LANGUAGE_ZH_CN, SessionStore.normalizeLanguageMode("fr"))
    }

    @Test
    fun newAIProfileDraftKeepsThePersistedNameLanguageNeutral() {
        assertEquals("", AIProfileDraft().name)
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

    @Test
    fun memoSummarySourceCountCountsUniqueNonBlankIds() {
        assertEquals(2, memoSummarySourceCount("""["memo-1", "memo-2"]"""))
        assertEquals(1, memoSummarySourceCount("""["memo-1", "memo-1", "", null, 3]"""))
    }

    @Test
    fun memoSummarySourceCountSafelyIgnoresMissingOrMalformedData() {
        assertEquals(null, memoSummarySourceCount(""))
        assertEquals(null, memoSummarySourceCount("not-json"))
        assertEquals(null, memoSummarySourceCount("{}"))
        assertEquals(null, memoSummarySourceCount("[]"))
    }

    private fun memo(
        id: String,
        entryDate: String = "2024-01-01",
        createdAt: String = "${entryDate}T00:00:00Z",
        updatedAt: String = "${entryDate}T00:00:00Z",
        version: Long = 1,
        favoritedAt: String? = null,
        archivedAt: String? = null,
        deletedAt: String? = null,
    ): Memo {
        return Memo(
            id = id,
            content = "content",
            entryDate = entryDate,
            version = version,
            createdAt = createdAt,
            updatedAt = updatedAt,
            favoritedAt = favoritedAt,
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

    private fun askConversation(
        id: String,
        archivedAt: String? = null,
        deletedAt: String? = null,
    ): AskConversation {
        return AskConversation(
            id = id,
            title = id,
            status = "active",
            contextScope = "recent_30_days",
            headMessageId = null,
            pinnedAt = null,
            archivedAt = archivedAt,
            createdAt = "2024-01-01T00:00:00Z",
            updatedAt = "2024-01-01T00:00:00Z",
            deletedAt = deletedAt,
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
            promptVersion = "",
            createdAt = createdAt,
            updatedAt = createdAt,
            deletedAt = null,
        )
    }
}
