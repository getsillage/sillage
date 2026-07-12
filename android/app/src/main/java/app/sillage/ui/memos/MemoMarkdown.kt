package app.sillage.ui.memos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
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
private fun MarkdownModeSelector(preview: Boolean, onPreviewChange: (Boolean) -> Unit) {
    Surface(
        modifier = Modifier.width(160.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    ) {
        Row(modifier = Modifier.selectableGroup()) {
            MarkdownModeButton(
                label = "编辑",
                selected = !preview,
                onClick = { onPreviewChange(false) },
                modifier = Modifier.weight(1f),
            )
            MarkdownModeButton(
                label = "预览",
                selected = preview,
                onClick = { onPreviewChange(true) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MarkdownModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 3.dp)
                .height(36.dp),
            shape = RoundedCornerShape(6.dp),
            color = if (selected) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    label,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
internal fun MarkdownContent(
    content: String,
    baseUrl: String,
    openingAttachmentPath: String?,
    onOpenAttachment: (MarkdownLinkTarget.ProtectedAttachment) -> Unit,
) {
    val blocks = remember(content) { parseMarkdownPreview(content) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MarkdownModeSelector(preview = preview, onPreviewChange = onPreviewChange)
            Text(
                markdownDraftStats(content),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    ) {
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            MarkdownToolButton(Icons.Rounded.Title, "标题") { onFormat(MarkdownFormatStyle.Heading) }
            MarkdownToolButton(Icons.Rounded.FormatBold, "加粗") { onFormat(MarkdownFormatStyle.Bold) }
            MarkdownToolButton(Icons.Rounded.FormatItalic, "斜体") { onFormat(MarkdownFormatStyle.Italic) }
            MarkdownToolButton(Icons.Rounded.Code, "代码") { onFormat(MarkdownFormatStyle.Code) }
            MarkdownToolButton(Icons.AutoMirrored.Rounded.FormatListBulleted, "列表") {
                onFormat(MarkdownFormatStyle.List)
            }
            MarkdownToolButton(Icons.Rounded.FormatQuote, "引用") { onFormat(MarkdownFormatStyle.Quote) }
        }
    }
}

@Composable
private fun MarkdownToolButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(19.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
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
        MarkdownBlockKind.Quote -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(21.dp)
                    .background(
                        MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(1.dp),
                    ),
            )
            Text(
                block.text,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        MarkdownBlockKind.ListItem -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("•", style = MaterialTheme.typography.bodyLarge)
            Text(
                block.text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        MarkdownBlockKind.Code -> Text(
            block.text,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceContainer,
                    RoundedCornerShape(6.dp),
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
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
            style = MaterialTheme.typography.bodyLarge,
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
        style = MaterialTheme.typography.bodyLarge,
        textDecoration = if (target == null) null else TextDecoration.Underline,
    )
}
