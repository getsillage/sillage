package app.sillage.features.records

import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownFormattingTest {
    @Test
    fun markdownFormatSnippetReturnsExpectedMarkup() {
        assertEquals("**加粗**", markdownFormatSnippet(MarkdownFormatStyle.Bold, "加粗"))
        assertEquals("\n# Heading\n", markdownFormatSnippet(MarkdownFormatStyle.Heading, "Heading"))
        assertEquals("\n- List item\n", markdownFormatSnippet(MarkdownFormatStyle.List, "List item"))
        assertEquals("*斜体*", markdownFormatSnippet(MarkdownFormatStyle.Italic, "斜体"))
        assertEquals("`code`", markdownFormatSnippet(MarkdownFormatStyle.Code, "code"))
        assertEquals("\n> quote\n", markdownFormatSnippet(MarkdownFormatStyle.Quote, "quote"))
    }
}
