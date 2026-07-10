package app.sillage.data

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentOpeningTest {
    @Test
    fun ordinaryHttpLinksRemainExternal() {
        assertEquals(
            MarkdownLinkTarget.ExternalHttp("https://example.com/docs"),
            resolveMarkdownLinkTarget("https://example.com/docs", "https://sillage.example"),
        )
        assertEquals(
            MarkdownLinkTarget.ExternalHttp("http://example.com/help"),
            resolveMarkdownLinkTarget("http://example.com/help", ""),
        )
    }

    @Test
    fun relativeAndSameOriginAbsoluteAttachmentsAreProtected() {
        assertEquals(
            MarkdownLinkTarget.ProtectedAttachment(
                path = "/file/attachments/a/note.pdf",
                filename = "note.pdf",
            ),
            resolveMarkdownLinkTarget(
                "/file/attachments/a/note.pdf",
                "https://example.com",
            ),
        )
        assertEquals(
            MarkdownLinkTarget.ProtectedAttachment(
                path = "/file/attachments/a/%E6%97%A5%E8%AE%B0.pdf?download=1",
                filename = "日记.pdf",
            ),
            resolveMarkdownLinkTarget(
                "https://EXAMPLE.com:443/file/attachments/a/%E6%97%A5%E8%AE%B0.pdf?download=1#page",
                "https://example.com",
            ),
        )
    }

    @Test
    fun crossOriginAttachmentUrlsRemainExternal() {
        assertEquals(
            MarkdownLinkTarget.ExternalHttp("https://cdn.example/file/attachments/a/note.pdf"),
            resolveMarkdownLinkTarget(
                "https://cdn.example/file/attachments/a/note.pdf",
                "https://example.com",
            ),
        )
        assertEquals(
            MarkdownLinkTarget.ExternalHttp("http://example.com/file/attachments/a/note.pdf"),
            resolveMarkdownLinkTarget(
                "http://example.com/file/attachments/a/note.pdf",
                "https://example.com",
            ),
        )
    }

    @Test
    fun unsafeOrMalformedTargetsAreRejected() {
        assertNull(resolveMarkdownLinkTarget("javascript:alert(1)", "https://example.com"))
        assertNull(resolveMarkdownLinkTarget("file:///tmp/note.pdf", "https://example.com"))
        assertNull(resolveMarkdownLinkTarget("mailto:user@example.com", "https://example.com"))
        assertNull(resolveMarkdownLinkTarget("/other/path", "https://example.com"))
        assertNull(resolveMarkdownLinkTarget("/file/attachments/%2e%2e/private", "https://example.com"))
        assertNull(
            resolveMarkdownLinkTarget(
                "https://example.com/file/attachments/%2e%2e/private",
                "https://example.com",
            ),
        )
        assertNull(resolveMarkdownLinkTarget("/file/attachments/a/note.pdf", ""))
    }

    @Test
    fun responseFilenameIsPreferredAndSanitized() {
        assertEquals(
            "report.pdf",
            preferredAttachmentFilename(
                contentDisposition = "attachment; filename=\"../../report.pdf\"",
                urlFilename = "fallback.bin",
            ),
        )
        assertEquals(
            "记录.pdf",
            preferredAttachmentFilename(
                contentDisposition = "attachment; filename*=UTF-8''%E8%AE%B0%E5%BD%95.pdf",
                urlFilename = "fallback.bin",
            ),
        )
        assertEquals("note.txt", sanitizeAttachmentFilename("C:\\temp\\note.txt"))
        assertEquals("attachment", sanitizeAttachmentFilename("\u0000..\u0001"))
    }

    @Test
    fun sanitizedFilenameIsLengthBoundAndKeepsExtension() {
        val filename = sanitizeAttachmentFilename("记录".repeat(100) + ".pdf")

        assertTrue(filename.toByteArray(StandardCharsets.UTF_8).size <= 120)
        assertTrue(filename.endsWith(".pdf"))
    }

    @Test
    fun mimeUsesResponseThenExtensionThenBinaryFallback() {
        assertEquals("text/plain", resolveAttachmentMimeType("Text/Plain; charset=utf-8", "note.bin"))
        assertEquals("application/pdf", resolveAttachmentMimeType(null, "report.PDF"))
        assertEquals("image/png", resolveAttachmentMimeType("*/*", "image.png"))
        assertEquals("application/octet-stream", resolveAttachmentMimeType(null, "unknown.sillage"))
    }
}
