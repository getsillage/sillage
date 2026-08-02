package app.sillage.ui.ask

import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import app.sillage.core.domain.ask.AskMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SillageAskMessageCardTest {
    @Test
    fun finalAssistantAnswerUsesHostContentSlot() {
        val presentation = presentation(message = message(content = "Final answer"))

        assertTrue(presentation.isAssistant)
        assertEquals("Final answer", presentation.displayedContent)
        assertTrue(presentation.useFinalAssistantContent)
    }

    @Test
    fun streamedTextOverridesStoredAnswerAndStaysPlain() {
        val presentation = presentation(
            message = message(content = "Stored answer"),
            streamingText = "Live answer",
        )

        assertEquals("Live answer", presentation.displayedContent)
        assertFalse(presentation.useFinalAssistantContent)
    }

    @Test
    fun regeneratingCopyIsUsedUntilStreamHasContent() {
        val waiting = presentation(
            message = message(content = "Stored answer"),
            streamingText = "",
            regenerating = true,
        )
        val streaming = presentation(
            message = message(content = "Stored answer"),
            streamingText = "New answer",
            regenerating = true,
        )

        assertEquals("Regenerating…", waiting.displayedContent)
        assertFalse(waiting.useFinalAssistantContent)
        assertEquals("New answer", streaming.displayedContent)
        assertFalse(streaming.useFinalAssistantContent)
    }

    @Test
    fun userMessageNeverUsesAssistantContentSlot() {
        val presentation = presentation(
            message = message(role = "user", content = "Question"),
        )

        assertFalse(presentation.isAssistant)
        assertEquals("Question", presentation.displayedContent)
        assertFalse(presentation.useFinalAssistantContent)
    }

    @Test
    fun messageExposesSpeakerAndDisplayedContentTogether() {
        val semantics = SemanticsConfiguration()

        semantics.applySillageAskMessageSemantics("Sillage: answer")

        assertEquals(
            listOf("Sillage: answer"),
            semantics[SemanticsProperties.ContentDescription],
        )
    }

    private fun presentation(
        message: AskMessage,
        streamingText: String? = null,
        regenerating: Boolean = false,
    ): SillageAskMessageCardPresentation = sillageAskMessageCardPresentation(
        message = message,
        streamingText = streamingText,
        regenerating = regenerating,
        regeneratingText = "Regenerating…",
    )

    private fun message(
        role: String = "assistant",
        content: String,
    ): AskMessage = AskMessage(
        id = "message-id",
        conversationId = "conversation-id",
        role = role,
        content = content,
        parentId = null,
        forkOfId = null,
        status = "complete",
        sourceRefs = emptyList(),
        model = "model",
        promptVersion = "v1",
        createdAt = "2026-08-02T00:00:00Z",
        updatedAt = "2026-08-02T00:00:00Z",
        deletedAt = null,
    )
}
