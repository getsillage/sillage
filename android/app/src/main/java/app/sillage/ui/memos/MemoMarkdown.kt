package app.sillage.ui.memos

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.sillage.data.MarkdownFormatStyle
import app.sillage.data.MarkdownLinkTarget
import app.sillage.data.resolveMarkdownLinkTarget
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.LinkResolver
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.MarkwonVisitor
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.core.CoreProps
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import org.commonmark.node.Image
import org.commonmark.node.Link
import kotlin.math.roundToInt

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
    MarkdownText(
        content = content,
        baseUrl = baseUrl,
        openingAttachmentPath = openingAttachmentPath,
        onOpenAttachment = onOpenAttachment,
        modifier = Modifier.fillMaxWidth(),
    )
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
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    ) {
        if (content.isBlank()) {
            Box(modifier = Modifier.fillMaxSize().padding(14.dp), contentAlignment = Alignment.Center) {
                Text(
                    "没有可预览的内容",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            MarkdownText(
                content = content,
                baseUrl = baseUrl,
                openingAttachmentPath = openingAttachmentPath,
                onOpenAttachment = onOpenAttachment,
                scrollable = true,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
            )
        }
    }
}

@Composable
private fun MarkdownText(
    content: String,
    baseUrl: String,
    openingAttachmentPath: String?,
    onOpenAttachment: (MarkdownLinkTarget.ProtectedAttachment) -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val colors = MaterialTheme.colorScheme
    val style = MarkdownRenderStyle(
        textColor = colors.onSurface.toArgb(),
        linkColor = colors.primary.toArgb(),
        secondaryTextColor = colors.onSurfaceVariant.toArgb(),
        outlineColor = colors.outline.toArgb(),
        codeBackgroundColor = colors.surfaceContainer.toArgb(),
        surfaceColor = colors.surface.toArgb(),
    )
    val currentOnOpenAttachment = rememberUpdatedState(onOpenAttachment)
    val currentUriHandler = rememberUpdatedState(uriHandler)
    val renderer = remember(context, style, baseUrl, openingAttachmentPath) {
        createMarkdownRenderer(
            context = context,
            style = style,
            isLinkAllowed = { rawUrl ->
                when (resolveMarkdownLinkTarget(rawUrl, baseUrl)) {
                    is MarkdownLinkTarget.ExternalHttp -> true
                    is MarkdownLinkTarget.ProtectedAttachment -> openingAttachmentPath == null
                    null -> false
                }
            },
            isLinkOpening = { rawUrl ->
                val target = resolveMarkdownLinkTarget(rawUrl, baseUrl)
                    as? MarkdownLinkTarget.ProtectedAttachment
                target?.path == openingAttachmentPath
            },
            onOpenLink = { rawUrl ->
                when (val target = resolveMarkdownLinkTarget(rawUrl, baseUrl)) {
                    is MarkdownLinkTarget.ExternalHttp -> runCatching {
                        currentUriHandler.value.openUri(target.uri)
                    }
                    is MarkdownLinkTarget.ProtectedAttachment -> {
                        if (openingAttachmentPath == null) {
                            currentOnOpenAttachment.value(target)
                        }
                    }
                    null -> Unit
                }
            },
        )
    }

    if (scrollable) {
        AndroidView(
            factory = { viewContext ->
                ScrollView(viewContext).apply {
                    isFillViewport = true
                    addView(
                        createMarkdownTextView(viewContext, style),
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ),
                    )
                }
            },
            update = { scrollView ->
                val textView = scrollView.getChildAt(0) as TextView
                applyMarkdownStyle(textView, style)
                if (renderMarkdownIfNeeded(textView, renderer, content)) {
                    scrollView.scrollTo(0, 0)
                }
            },
            modifier = modifier,
        )
    } else {
        AndroidView(
            factory = { viewContext -> createMarkdownTextView(viewContext, style) },
            update = { textView ->
                applyMarkdownStyle(textView, style)
                renderMarkdownIfNeeded(textView, renderer, content)
            },
            modifier = modifier,
        )
    }
}

internal data class MarkdownRenderStyle(
    @ColorInt val textColor: Int,
    @ColorInt val linkColor: Int,
    @ColorInt val secondaryTextColor: Int,
    @ColorInt val outlineColor: Int,
    @ColorInt val codeBackgroundColor: Int,
    @ColorInt val surfaceColor: Int,
)

internal fun createMarkdownRenderer(
    context: Context,
    style: MarkdownRenderStyle,
    isLinkAllowed: (String) -> Boolean,
    onOpenLink: (String) -> Unit,
    isLinkOpening: (String) -> Boolean = { false },
): Markwon {
    return Markwon.builder(context)
        .usePlugin(CorePlugin.create())
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(
            TablePlugin.create { tableTheme ->
                val density = context.resources.displayMetrics.density
                tableTheme
                    .tableCellPadding((8f * density).roundToInt())
                    .tableBorderWidth((1f * density).roundToInt().coerceAtLeast(1))
                    .tableBorderColor(style.outlineColor)
                    .tableOddRowBackgroundColor(style.codeBackgroundColor)
                    .tableEvenRowBackgroundColor(style.surfaceColor)
                    .tableHeaderRowBackgroundColor(style.codeBackgroundColor)
            },
        )
        .usePlugin(TaskListPlugin.create(style.linkColor, style.outlineColor, style.surfaceColor))
        .usePlugin(SoftBreakAddsNewLinePlugin.create())
        .usePlugin(MarkdownRenderingPlugin(style, isLinkAllowed, isLinkOpening, onOpenLink))
        .build()
}

private class MarkdownRenderingPlugin(
    private val style: MarkdownRenderStyle,
    private val isLinkAllowed: (String) -> Boolean,
    private val isLinkOpening: (String) -> Boolean,
    private val onOpenLink: (String) -> Unit,
) : AbstractMarkwonPlugin() {
    override fun configureTheme(builder: MarkwonTheme.Builder) {
        builder
            .linkColor(style.linkColor)
            .blockQuoteColor(style.outlineColor)
            .listItemColor(style.textColor)
            .codeTextColor(style.secondaryTextColor)
            .codeBlockTextColor(style.secondaryTextColor)
            .codeBackgroundColor(style.codeBackgroundColor)
            .codeBlockBackgroundColor(style.codeBackgroundColor)
            .headingBreakColor(style.outlineColor)
            .headingTypeface(Typeface.DEFAULT_BOLD)
            .headingTextSizeMultipliers(floatArrayOf(1.55f, 1.4f, 1.25f, 1.15f, 1.05f, 1f))
            .thematicBreakColor(style.outlineColor)
    }

    override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
        builder.linkResolver(
            LinkResolver { _, link ->
                if (isLinkAllowed(link)) {
                    onOpenLink(link)
                }
            },
        )
    }

    override fun configureVisitor(builder: MarkwonVisitor.Builder) {
        builder.on(Link::class.java) { visitor, link ->
            val start = visitor.length()
            visitor.visitChildren(link)
            if (isLinkOpening(link.destination)) {
                visitor.builder().append("（正在打开）")
            }
            if (isLinkAllowed(link.destination)) {
                CoreProps.LINK_DESTINATION.set(visitor.renderProps(), link.destination)
                visitor.setSpansForNodeOptional(link, start)
            }
        }
        builder.on(Image::class.java) { visitor, image ->
            val start = visitor.length()
            visitor.builder().append("图片：")
            val labelStart = visitor.length()
            visitor.visitChildren(image)
            if (labelStart == visitor.length()) {
                visitor.builder().append("图片")
            }
            if (isLinkOpening(image.destination)) {
                visitor.builder().append("（正在打开）")
            }
            if (isLinkAllowed(image.destination)) {
                CoreProps.LINK_DESTINATION.set(visitor.renderProps(), image.destination)
                visitor.setSpansForNode(Link::class.java, start)
            }
        }
    }
}

private data class MarkdownRenderKey(
    val renderer: Markwon,
    val content: String,
)

private fun createMarkdownTextView(context: Context, style: MarkdownRenderStyle): TextView {
    return TextView(context).also { applyMarkdownStyle(it, style) }
}

private fun applyMarkdownStyle(textView: TextView, style: MarkdownRenderStyle) {
    textView.setTextColor(style.textColor)
    textView.setLinkTextColor(style.linkColor)
    textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
    textView.includeFontPadding = false
    textView.setLineSpacing(0f, 1.25f)
}

// Re-render theme or link-state changes without forcing a scroll reset.
internal fun renderMarkdownIfNeeded(textView: TextView, renderer: Markwon, content: String): Boolean {
    val key = MarkdownRenderKey(renderer, content)
    val previous = textView.tag as? MarkdownRenderKey
    if (previous == key) {
        return false
    }
    renderer.setMarkdown(textView, content)
    textView.tag = key
    return previous?.content != content
}
