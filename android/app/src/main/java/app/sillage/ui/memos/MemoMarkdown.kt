package app.sillage.ui.memos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.sillage.data.MarkdownBlock
import app.sillage.data.MarkdownBlockKind
import app.sillage.data.MarkdownFormatStyle
import app.sillage.data.MarkdownLinkTarget
import app.sillage.data.parseMarkdownPreview
import app.sillage.data.resolveMarkdownLinkTarget

@Composable
private fun MarkdownModeButton(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
internal fun MarkdownContent(
    content: String,
    baseUrl: String,
    openingAttachmentPath: String?,
    onOpenAttachment: (MarkdownLinkTarget.ProtectedAttachment) -> Unit,
) {
    val blocks = remember(content) { parseMarkdownPreview(content) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (blocks.isEmpty()) {
            Text(
                content,
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            blocks.forEach { block ->
                MarkdownPreviewBlock(
                    block = block,
                    baseUrl = baseUrl,
                    openingAttachmentPath = openingAttachmentPath,
                    onOpenAttachment = onOpenAttachment,
                )
            }
        }
    }
}

@Composable
internal fun MarkdownEditorSection(
    content: String,
    baseUrl: String,
    openingAttachmentPath: String?,
    preview: Boolean,
    onContentChange: (String) -> Unit,
    onPreviewChange: (Boolean) -> Unit,
    onFormat: (MarkdownFormatStyle) -> Unit,
    onOpenAttachment: (MarkdownLinkTarget.ProtectedAttachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MarkdownModeButton("编辑", !preview) { onPreviewChange(false) }
                MarkdownModeButton("预览", preview) { onPreviewChange(true) }
            }
            Text(
                markdownDraftStats(content),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
        MarkdownToolbar(onFormat)
        if (preview) {
            MarkdownPreview(
                content = content,
                baseUrl = baseUrl,
                openingAttachmentPath = openingAttachmentPath,
                onOpenAttachment = onOpenAttachment,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        } else {
            OutlinedTextField(
                value = content,
                onValueChange = onContentChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = { Text("内容") },
                placeholder = { Text("写下想记录的内容…") },
            )
        }
    }
}

private fun markdownDraftStats(content: String): String {
    val characters = content.trim().length
    val lines = if (content.isBlank()) 0 else content.lines().size
    return "$characters 字 · $lines 行"
}

@Composable
private fun MarkdownToolbar(onFormat: (MarkdownFormatStyle) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MarkdownToolButton(Icons.Rounded.Title, "标题") { onFormat(MarkdownFormatStyle.Heading) }
        MarkdownToolButton(Icons.Rounded.FormatBold, "加粗") { onFormat(MarkdownFormatStyle.Bold) }
        MarkdownToolButton(Icons.Rounded.FormatItalic, "斜体") { onFormat(MarkdownFormatStyle.Italic) }
        MarkdownToolButton(Icons.Rounded.Code, "代码") { onFormat(MarkdownFormatStyle.Code) }
        MarkdownToolButton(Icons.AutoMirrored.Rounded.FormatListBulleted, "列表") { onFormat(MarkdownFormatStyle.List) }
        MarkdownToolButton(Icons.Rounded.FormatQuote, "引用") { onFormat(MarkdownFormatStyle.Quote) }
    }
}

@Composable
private fun MarkdownToolButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        },
    )
}

@Composable
private fun MarkdownPreview(
    content: String,
    baseUrl: String,
    openingAttachmentPath: String?,
    onOpenAttachment: (MarkdownLinkTarget.ProtectedAttachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(content) { parseMarkdownPreview(content) }
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        if (blocks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(14.dp), contentAlignment = Alignment.Center) {
                Text(
                    "没有可预览的内容",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(blocks) { block ->
                    MarkdownPreviewBlock(
                        block = block,
                        baseUrl = baseUrl,
                        openingAttachmentPath = openingAttachmentPath,
                        onOpenAttachment = onOpenAttachment,
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownPreviewBlock(
    block: MarkdownBlock,
    baseUrl: String,
    openingAttachmentPath: String?,
    onOpenAttachment: (MarkdownLinkTarget.ProtectedAttachment) -> Unit,
) {
    when (block.kind) {
        MarkdownBlockKind.Heading -> Text(
            block.text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        MarkdownBlockKind.Quote -> Text(
            "｜ ${block.text}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        MarkdownBlockKind.ListItem -> Text(
            "• ${block.text}",
            style = MaterialTheme.typography.bodyMedium,
        )
        MarkdownBlockKind.Code -> Text(
            block.text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        MarkdownBlockKind.Link -> MarkdownLinkText(
            label = block.text.ifBlank { "打开链接" },
            rawUrl = block.url,
            baseUrl = baseUrl,
            openingAttachmentPath = openingAttachmentPath,
            onOpenAttachment = onOpenAttachment,
        )
        MarkdownBlockKind.Image -> MarkdownLinkText(
            label = "图片：${block.text.ifBlank { "图片" }}",
            rawUrl = block.url,
            baseUrl = baseUrl,
            openingAttachmentPath = openingAttachmentPath,
            onOpenAttachment = onOpenAttachment,
        )
        MarkdownBlockKind.Paragraph -> Text(
            block.text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun MarkdownLinkText(
    label: String,
    rawUrl: String?,
    baseUrl: String,
    openingAttachmentPath: String?,
    onOpenAttachment: (MarkdownLinkTarget.ProtectedAttachment) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val target = remember(rawUrl, baseUrl) { resolveMarkdownLinkTarget(rawUrl, baseUrl) }
    val protectedTarget = target as? MarkdownLinkTarget.ProtectedAttachment
    val opening = protectedTarget?.path == openingAttachmentPath
    val enabled = when (target) {
        is MarkdownLinkTarget.ExternalHttp -> true
        is MarkdownLinkTarget.ProtectedAttachment -> openingAttachmentPath == null
        null -> false
    }
    Text(
        if (opening) "$label（正在打开）" else label,
        modifier = if (target == null) {
            Modifier
        } else {
            Modifier.clickable(enabled = enabled) {
                when (target) {
                    is MarkdownLinkTarget.ExternalHttp -> runCatching { uriHandler.openUri(target.uri) }
                    is MarkdownLinkTarget.ProtectedAttachment -> onOpenAttachment(target)
                }
            }
        },
        color = if (target == null || (!enabled && !opening)) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.primary
        },
        style = MaterialTheme.typography.bodyMedium,
        textDecoration = if (target == null) null else TextDecoration.Underline,
    )
}
