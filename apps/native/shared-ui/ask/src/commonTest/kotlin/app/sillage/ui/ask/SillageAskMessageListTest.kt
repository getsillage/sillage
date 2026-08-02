package app.sillage.ui.ask

import app.sillage.core.domain.ask.AskMessage
import app.sillage.features.ask.AskConversationStateHolder
import app.sillage.features.ask.AskFeatureStateHolder
import app.sillage.features.ask.AskLoadStateHolder
import app.sillage.features.ask.AskMemoSaveStateHolder
import app.sillage.features.ask.AskPathEntry
import app.sillage.features.ask.AskSourceNavigationStateHolder
import app.sillage.features.ask.AskStreamStateHolder
import app.sillage.features.ask.AskVariantStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SillageAskMessageListTest {
    @Test
    fun initialLoadReplacesTheList() {
        val presentation = presentation(
            state = AskFeatureStateHolder(load = AskLoadStateHolder(loading = true)),
            entries = emptyList(),
        )

        assertTrue(presentation.initialLoading)
        assertFalse(presentation.showEmpty)
    }

    @Test
    fun loadErrorSuppressesEmptyPrompt() {
        val presentation = presentation(
            state = AskFeatureStateHolder(
                load = AskLoadStateHolder(errorMessage = "Load failed"),
            ),
            entries = emptyList(),
        )

        assertEquals("Load failed", presentation.loadErrorMessage)
        assertFalse(presentation.showEmpty)
    }

    @Test
    fun latestAssistantGetsRegenerateAndRequestGates() {
        val first = entry(message("a1"))
        val latest = entry(message("a2"))
        val presentation = presentation(
            entries = listOf(first, latest),
            latestAssistantId = "a2",
        )

        assertFalse(presentation.messages[0].canRegenerate)
        assertTrue(presentation.messages[1].canRegenerate)
        assertTrue(presentation.messages[1].sourceActionsEnabled)
        assertFalse(presentation.messages[1].savingDisabled)
    }

    @Test
    fun regenerationRoutesLiveTextOnlyToOwnedMessage() {
        val entry = entry(message("a1"))
        val presentation = presentation(
            state = AskFeatureStateHolder(
                stream = AskStreamStateHolder(
                    sending = true,
                    regeneratingMessageId = "a1",
                    liveAnswer = "New answer",
                ),
            ),
            entries = listOf(entry),
            latestAssistantId = "a1",
        )

        val item = presentation.messages.single()
        assertTrue(item.regenerating)
        assertEquals("New answer", item.streamingText)
        assertFalse(item.canRegenerate)
        assertFalse(presentation.showLiveAnswer)
    }

    @Test
    fun newTurnExposesLiveUserAndAnswer() {
        val liveUser = message("u1", role = "user")
        val presentation = presentation(
            state = AskFeatureStateHolder(
                stream = AskStreamStateHolder(
                    sending = true,
                    liveUser = liveUser,
                    liveAnswer = "Partial",
                ),
            ),
            entries = emptyList(),
        )

        assertEquals(liveUser, presentation.liveUser)
        assertTrue(presentation.showLiveAnswer)
        assertEquals("Partial", presentation.liveAnswer)
    }

    @Test
    fun hostAndFeatureRequestsDisableMessageActions() {
        val entry = entry(message("a1"))
        val presentation = presentation(
            state = AskFeatureStateHolder(
                conversation = AskConversationStateHolder(messages = listOf(entry.message)),
                variant = AskVariantStateHolder(loading = true),
                sourceNavigation = AskSourceNavigationStateHolder(loading = true),
                memoSave = AskMemoSaveStateHolder(savingMessageId = "other"),
            ),
            entries = listOf(entry),
            latestAssistantId = "a1",
            hostActionsEnabled = false,
        )

        val item = presentation.messages.single()
        assertFalse(item.canRegenerate)
        assertTrue(item.variantChanging)
        assertTrue(item.savingDisabled)
        assertFalse(item.sourceActionsEnabled)
        assertNull(item.streamingText)
    }

    private fun presentation(
        state: AskFeatureStateHolder = AskFeatureStateHolder(),
        entries: List<AskPathEntry>,
        latestAssistantId: String? = null,
        hostActionsEnabled: Boolean = true,
    ): SillageAskMessageListPresentation = sillageAskMessageListPresentation(
        state = state,
        entries = entries,
        latestAssistantId = latestAssistantId,
        hostActionsEnabled = hostActionsEnabled,
    )

    private fun entry(message: AskMessage): AskPathEntry = AskPathEntry(
        message = message,
        variants = listOf(message),
        index = 0,
    )

    private fun message(
        id: String,
        role: String = "assistant",
    ): AskMessage = AskMessage(
        id = id,
        conversationId = "conversation-id",
        role = role,
        content = id,
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
