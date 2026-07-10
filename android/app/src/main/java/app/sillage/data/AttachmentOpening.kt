package app.sillage.data

import java.net.URLConnection
import java.net.URLDecoder
import java.net.URI
import java.nio.charset.StandardCharsets
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull

sealed interface MarkdownLinkTarget {
    data class ExternalHttp(val uri: String) : MarkdownLinkTarget

    data class ProtectedAttachment(
        val path: String,
        val filename: String,
    ) : MarkdownLinkTarget
}

data class DownloadedAttachment(
    val contentType: String?,
    val contentDisposition: String?,
    val urlFilename: String,
)

fun resolveMarkdownLinkTarget(rawUrl: String?, baseUrl: String): MarkdownLinkTarget? {
    val candidate = rawUrl?.trim().orEmpty()
    if (candidate.isBlank()) {
        return null
    }

    val base = SessionStore.normalizeBaseUrl(baseUrl).toHttpUrlOrNull()
    candidate.toHttpUrlOrNull()?.let { absolute ->
        if (base != null && absolute.hasSameOrigin(base)) {
            absolute.toProtectedAttachment()?.let { return it }
            val rawPath = runCatching { URI(candidate).rawPath }.getOrNull()
            if (rawPath?.startsWith(ATTACHMENT_PATH_PREFIX) == true) {
                return null
            }
        }
        return MarkdownLinkTarget.ExternalHttp(absolute.toString())
    }

    if (!candidate.startsWith(ATTACHMENT_PATH_PREFIX) || base == null) {
        return null
    }
    val resolved = base.resolve(candidate) ?: return null
    if (!resolved.hasSameOrigin(base)) {
        return null
    }
    return resolved.toProtectedAttachment()
}

fun preferredAttachmentFilename(contentDisposition: String?, urlFilename: String): String {
    val candidate = contentDispositionFilename(contentDisposition)
        ?: urlFilename
    return sanitizeAttachmentFilename(candidate)
}

fun sanitizeAttachmentFilename(rawFilename: String): String {
    val leaf = rawFilename
        .replace('\\', '/')
        .substringAfterLast('/')
        .filterNot(Char::isISOControl)
        .trim()
    val safe = leaf.takeUnless { it.isBlank() || it.all { character -> character == '.' } }
        ?: DEFAULT_ATTACHMENT_FILENAME
    return truncateFilename(safe, MAX_FILENAME_UTF8_BYTES)
}

fun resolveAttachmentMimeType(responseContentType: String?, filename: String): String {
    responseContentType
        ?.toMediaTypeOrNull()
        ?.let { mediaType ->
            val normalized = "${mediaType.type}/${mediaType.subtype}".lowercase()
            if ('*' !in normalized) {
                return normalized
            }
        }

    val extension = filename.substringAfterLast('.', "").lowercase()
    return MIME_TYPES_BY_EXTENSION[extension]
        ?: URLConnection.guessContentTypeFromName(filename)
        ?: DEFAULT_MIME_TYPE
}

private fun HttpUrl.hasSameOrigin(other: HttpUrl): Boolean {
    return scheme == other.scheme && host == other.host && port == other.port
}

private fun HttpUrl.toProtectedAttachment(): MarkdownLinkTarget.ProtectedAttachment? {
    val segments = pathSegments
    if (
        segments.size != 4 ||
        segments[0] != "file" ||
        segments[1] != "attachments" ||
        segments.drop(2).any { segment ->
            segment.isBlank() ||
                segment == "." ||
                segment == ".." ||
                '/' in segment ||
                '\\' in segment ||
                segment.any(Char::isISOControl)
        }
    ) {
        return null
    }
    val requestPath = buildString {
        append(encodedPath)
        encodedQuery?.let { query -> append('?').append(query) }
    }
    return MarkdownLinkTarget.ProtectedAttachment(
        path = requestPath,
        filename = segments.last(),
    )
}

private fun contentDispositionFilename(header: String?): String? {
    if (header.isNullOrBlank()) {
        return null
    }
    val extended = EXTENDED_FILENAME_PARAMETER.find(header)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.trim('"')
        ?.let(::decodeExtendedFilename)
    if (!extended.isNullOrBlank()) {
        return extended
    }
    val match = FILENAME_PARAMETER.find(header) ?: return null
    return (match.groups[1]?.value ?: match.groups[2]?.value)
        ?.replace("\\\"", "\"")
        ?.replace("\\\\", "\\")
        ?.trim()
        ?.takeIf(String::isNotBlank)
}

private fun decodeExtendedFilename(value: String): String? {
    val firstQuote = value.indexOf('\'')
    val secondQuote = value.indexOf('\'', firstQuote + 1)
    if (firstQuote <= 0 || secondQuote < 0) {
        return null
    }
    val charsetName = value.substring(0, firstQuote)
    if (!charsetName.equals("UTF-8", ignoreCase = true) &&
        !charsetName.equals("ISO-8859-1", ignoreCase = true)
    ) {
        return null
    }
    val encoded = value.substring(secondQuote + 1).replace("+", "%2B")
    return runCatching { URLDecoder.decode(encoded, charsetName) }
        .getOrNull()
        ?.takeIf(String::isNotBlank)
}

private fun truncateFilename(filename: String, maxBytes: Int): String {
    if (filename.toByteArray(StandardCharsets.UTF_8).size <= maxBytes) {
        return filename
    }
    val extensionStart = filename.lastIndexOf('.').takeIf { it > 0 }
    val extension = extensionStart
        ?.let(filename::substring)
        ?.takeIf { it.toByteArray(StandardCharsets.UTF_8).size <= MAX_EXTENSION_UTF8_BYTES }
        .orEmpty()
    val stem = if (extension.isEmpty()) filename else filename.dropLast(extension.length)
    val stemBudget = maxBytes - extension.toByteArray(StandardCharsets.UTF_8).size
    return takeUtf8Bytes(stem, stemBudget) + extension
}

private fun takeUtf8Bytes(value: String, maxBytes: Int): String {
    var byteCount = 0
    var index = 0
    while (index < value.length) {
        val codePoint = value.codePointAt(index)
        val codePointLength = Character.charCount(codePoint)
        val encodedLength = String(Character.toChars(codePoint)).toByteArray(StandardCharsets.UTF_8).size
        if (byteCount + encodedLength > maxBytes) {
            break
        }
        byteCount += encodedLength
        index += codePointLength
    }
    return value.substring(0, index)
}

private const val ATTACHMENT_PATH_PREFIX = "/file/attachments/"
private const val DEFAULT_ATTACHMENT_FILENAME = "attachment"
private const val DEFAULT_MIME_TYPE = "application/octet-stream"
private const val MAX_FILENAME_UTF8_BYTES = 120
private const val MAX_EXTENSION_UTF8_BYTES = 24
private val EXTENDED_FILENAME_PARAMETER = Regex(
    """(?:^|;)\s*filename\*\s*=\s*([^;]+)""",
    RegexOption.IGNORE_CASE,
)
private val FILENAME_PARAMETER = Regex(
    """(?:^|;)\s*filename\s*=\s*(?:"((?:\\.|[^"])*)"|([^;]*))""",
    RegexOption.IGNORE_CASE,
)
private val MIME_TYPES_BY_EXTENSION = mapOf(
    "7z" to "application/x-7z-compressed",
    "csv" to "text/csv",
    "doc" to "application/msword",
    "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "gif" to "image/gif",
    "gz" to "application/gzip",
    "heic" to "image/heic",
    "html" to "text/html",
    "jpeg" to "image/jpeg",
    "jpg" to "image/jpeg",
    "json" to "application/json",
    "m4a" to "audio/mp4",
    "md" to "text/markdown",
    "mov" to "video/quicktime",
    "mp3" to "audio/mpeg",
    "mp4" to "video/mp4",
    "ogg" to "audio/ogg",
    "pdf" to "application/pdf",
    "png" to "image/png",
    "ppt" to "application/vnd.ms-powerpoint",
    "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "rar" to "application/vnd.rar",
    "svg" to "image/svg+xml",
    "txt" to "text/plain",
    "wav" to "audio/wav",
    "webp" to "image/webp",
    "xls" to "application/vnd.ms-excel",
    "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "xml" to "application/xml",
    "zip" to "application/zip",
)
