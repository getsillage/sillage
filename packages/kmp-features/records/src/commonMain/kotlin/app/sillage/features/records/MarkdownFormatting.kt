package app.sillage.features.records

enum class MarkdownFormatStyle {
    Heading,
    Bold,
    Italic,
    Code,
    List,
    Quote,
}

/** Returns an insertable Markdown snippet for the editor toolbar. */
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
