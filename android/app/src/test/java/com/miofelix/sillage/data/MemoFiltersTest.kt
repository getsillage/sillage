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

    private fun memo(
        id: String,
        entryDate: String = "2024-01-01",
        pinnedAt: String? = null,
        archivedAt: String? = null,
        deletedAt: String? = null,
    ): Memo {
        return Memo(
            id = id,
            content = "content",
            entryDate = entryDate,
            version = 1,
            createdAt = "${entryDate}T00:00:00Z",
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
}
