package app.sillage.data

import app.sillage.core.domain.records.Memo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAttachmentMarkdownTest {
    @Test
    fun localMarkdownUsesReservedPendingUidPath() {
        val pending = PendingLocalAttachment(
            id = "abc-123",
            filename = "photo.jpg",
            contentType = "image/jpeg",
            absolutePath = "/data/pending/abc-123",
            size = 12,
        )
        val markdown = localAttachmentMarkdown(pending)
        assertTrue(markdown.contains("/file/attachments/localpending-abc-123/photo.jpg"))
        assertTrue(markdown.contains("![photo.jpg]"))
    }

    @Test
    fun resolveMarkdownLinkTargetFindsLocalPendingWithoutBaseUrl() {
        val target = resolveMarkdownLinkTarget(
            "/file/attachments/localpending-xyz/notes.txt",
            baseUrl = "",
        )
        assertTrue(target is MarkdownLinkTarget.ProtectedAttachment)
        val protected = target as MarkdownLinkTarget.ProtectedAttachment
        assertEquals("notes.txt", protected.filename)
        assertEquals("xyz", pendingLocalAttachmentId(protected))
    }

    @Test
    fun pendingLocalAttachmentIdIgnoresNormalServerUids() {
        val target = MarkdownLinkTarget.ProtectedAttachment(
            path = "/file/attachments/server-uid/file.pdf",
            filename = "file.pdf",
        )
        assertNull(pendingLocalAttachmentId(target))
    }

    @Test
    fun offlineUploadRewriteMarksCloudMemoForPushWithServerUrl() {
        val pending = PendingLocalAttachment(
            id = "att-1",
            filename = "shot.png",
            contentType = "image/png",
            absolutePath = "/data/pending/att-1",
            size = 4,
        )
        val localMd = localAttachmentMarkdown(pending).trim()
        val remoteMd = "![shot.png](/file/attachments/server-uid/shot.png)"
        val memo = Memo(
            id = "memo-1",
            content = "before\n$localMd\nafter",
            entryDate = "2026-07-30",
            version = 2,
            createdAt = "2026-07-30T00:00:00Z",
            updatedAt = "2026-07-30T00:00:00Z",
            favoritedAt = null,
            archivedAt = null,
            deletedAt = null,
        )
        val rewritten = rewriteAttachmentMarkdownInMemos(
            memos = listOf(memo),
            cloudVersions = mapOf(memo.id to 2L),
            pendingMutations = emptyMap(),
            fromMarkdown = localMd,
            toMarkdown = remoteMd,
            now = "2026-07-30T01:00:00Z",
            newMutationId = { "mut-upload" },
        )
        assertNotNull(rewritten)
        val next = rewritten!!
        assertEquals(remoteMd, next.memos.single().content.lines().first { it.contains("shot.png") })
        assertTrue(next.memos.single().content.contains("/file/attachments/server-uid/shot.png"))
        assertTrue(!next.memos.single().content.contains("localpending-"))
        assertEquals(3L, next.memos.single().version)
        assertEquals("mut-upload", next.pendingMutations[memo.id]?.mutationId)

        val pendingPush = resolvePendingMemoSyncs(
            memos = next.memos,
            cloudVersions = next.cloudVersions,
            pendingMutations = next.pendingMutations,
            newMutationId = { error("should reuse mutation") },
        )
        assertEquals(1, pendingPush.pending.size)
        assertEquals(2L, pendingPush.pending.single().baseVersion)
        assertTrue(pendingPush.pending.single().memo.content.contains("server-uid"))
    }
}
