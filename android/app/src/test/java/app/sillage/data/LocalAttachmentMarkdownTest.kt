package app.sillage.data

import org.junit.Assert.assertEquals
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
}
