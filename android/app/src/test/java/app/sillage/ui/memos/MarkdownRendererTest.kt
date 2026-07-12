package app.sillage.ui.memos

import android.content.Context
import android.graphics.Color
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import app.sillage.data.resolveMarkdownLinkTarget
import io.noties.markwon.Markwon
import io.noties.markwon.core.spans.CodeBlockSpan
import io.noties.markwon.core.spans.CodeSpan
import io.noties.markwon.core.spans.EmphasisSpan
import io.noties.markwon.core.spans.HeadingSpan
import io.noties.markwon.core.spans.LinkSpan
import io.noties.markwon.core.spans.StrongEmphasisSpan
import io.noties.markwon.ext.tables.TableRowSpan
import io.noties.markwon.ext.tasklist.TaskListSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MarkdownRendererTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun rendersCommonMarkdownAndGfmStyles() {
        val rendered = renderer().toMarkdown(
            """
            # 标题

            第一行有 **粗体**、*斜体* 和 `行内代码`。
            第二行继续。

            > 引用

            - 普通列表
            - [x] 已完成

            ```kotlin
            val answer = 42
            ```

            ~~删除内容~~

            | 列一 | 列二 |
            | --- | --- |
            | A | B |
            """.trimIndent(),
        )

        assertEquals(1, rendered.spans<HeadingSpan>().size)
        assertEquals(1, rendered.spans<StrongEmphasisSpan>().size)
        assertEquals(1, rendered.spans<EmphasisSpan>().size)
        assertEquals(1, rendered.spans<CodeSpan>().size)
        assertEquals(1, rendered.spans<CodeBlockSpan>().size)
        assertEquals(1, rendered.spans<StrikethroughSpan>().size)
        assertEquals(1, rendered.spans<TaskListSpan>().size)
        assertTrue(rendered.spans<TableRowSpan>().size >= 2)
        val plainText = rendered.toString().replace('\u00A0', ' ')
        assertTrue(plainText.contains("第一行有 粗体、斜体 和  行内代码 。\n第二行继续。"))
        assertTrue(rendered.toString().contains("val answer = 42"))
    }

    @Test
    fun keepsSurroundingTextAndOnlyMakesAllowedLinksClickable() {
        val opened = mutableListOf<String>()
        val rendered = renderer(onOpenLink = opened::add).toMarkdown(
            """
            前文 [外链](https://example.com/docs) 后文
            ![附件](/file/attachments/a/photo.png)
            [危险链接](javascript:alert(1))
            """.trimIndent(),
        )

        assertTrue(rendered.toString().contains("前文 外链 后文"))
        assertTrue(rendered.toString().contains("图片：附件"))
        assertTrue(rendered.toString().contains("危险链接"))
        val links = rendered.spans<LinkSpan>()
        assertEquals(
            setOf("https://example.com/docs", "/file/attachments/a/photo.png"),
            links.map { it.link }.toSet(),
        )

        links.forEach { it.onClick(View(context)) }

        assertEquals(links.map { it.link }, opened)
    }

    @Test
    fun marksOpeningAttachmentAndPreventsAnotherClick() {
        val attachmentPath = "/file/attachments/a/photo.png"
        val rendered = renderer(
            isLinkAllowed = { rawUrl -> rawUrl != attachmentPath },
            isLinkOpening = { rawUrl -> rawUrl == attachmentPath },
        ).toMarkdown("[附件]($attachmentPath)")

        assertTrue(rendered.toString().contains("附件（正在打开）"))
        assertTrue(rendered.spans<LinkSpan>().isEmpty())
    }

    @Test
    fun onlyRequestsScrollResetWhenContentChanges() {
        val textView = TextView(context)

        assertTrue(renderMarkdownIfNeeded(textView, renderer(), "**正文**"))
        assertFalse(renderMarkdownIfNeeded(textView, renderer(), "**正文**"))
        assertTrue(renderMarkdownIfNeeded(textView, renderer(), "**修改后的正文**"))
    }

    @Test
    fun doesNotExecuteRawHtmlOrActivateDangerousSchemes() {
        val rendered = renderer().toMarkdown(
            """
            <script>alert('xss')</script>
            <a href="javascript:alert(1)">原始 HTML</a>

            [危险链接](javascript:alert(2))
            """.trimIndent(),
        )

        assertFalse(rendered.toString().contains("alert('xss')"))
        assertTrue(rendered.toString().contains("危险链接"))
        assertTrue(rendered.spans<LinkSpan>().isEmpty())
    }

    private fun renderer(
        onOpenLink: (String) -> Unit = {},
        isLinkAllowed: (String) -> Boolean = { rawUrl ->
            resolveMarkdownLinkTarget(rawUrl, "https://sillage.example") != null
        },
        isLinkOpening: (String) -> Boolean = { false },
    ): Markwon {
        return createMarkdownRenderer(
            context = context,
            style = MarkdownRenderStyle(
                textColor = Color.BLACK,
                linkColor = Color.BLUE,
                secondaryTextColor = Color.DKGRAY,
                outlineColor = Color.GRAY,
                codeBackgroundColor = Color.LTGRAY,
                surfaceColor = Color.WHITE,
            ),
            isLinkAllowed = isLinkAllowed,
            onOpenLink = onOpenLink,
            isLinkOpening = isLinkOpening,
        )
    }
}

private inline fun <reified T> Spanned.spans(): Array<T> {
    return getSpans(0, length, T::class.java)
}
