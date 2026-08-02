package app.sillage.ui.ask

import app.sillage.core.domain.ask.AskConversation
import app.sillage.features.ask.AskFeatureStateHolder
import app.sillage.features.ask.AskMemoSaveStateHolder
import app.sillage.features.ask.AskSourceNavigationStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SillageAskConversationSheetTest {
    @Test
    fun idleStateEnablesRefreshAndSelection() {
        val presentation = sillageAskConversationSheetPresentation(AskFeatureStateHolder())

        assertTrue(presentation.refreshEnabled)
        assertTrue(presentation.selectionEnabled)
    }

    @Test
    fun sourceNavigationOnlyDisablesSelection() {
        val presentation = sillageAskConversationSheetPresentation(
            AskFeatureStateHolder(
                sourceNavigation = AskSourceNavigationStateHolder(loading = true),
            ),
        )

        assertTrue(presentation.refreshEnabled)
        assertFalse(presentation.selectionEnabled)
    }

    @Test
    fun memoSaveOnlyDisablesRefresh() {
        val presentation = sillageAskConversationSheetPresentation(
            AskFeatureStateHolder(
                memoSave = AskMemoSaveStateHolder(savingMessageId = "message"),
            ),
        )

        assertFalse(presentation.refreshEnabled)
        assertTrue(presentation.selectionEnabled)
    }

    @Test
    fun blankConversationTitleUsesLocalizedFallback() {
        assertEquals(
            "Untitled",
            sillageAskConversationTitle(
                conversation = conversation(title = "  "),
                untitledConversation = "Untitled",
            ),
        )
    }

    @Test
    fun nonBlankConversationTitleIsPreserved() {
        assertEquals(
            "August notes",
            sillageAskConversationTitle(
                conversation = conversation(title = "August notes"),
                untitledConversation = "Untitled",
            ),
        )
    }

    private fun conversation(title: String): AskConversation = AskConversation(
        id = "conversation",
        title = title,
        status = "active",
        contextScope = "recent_30_days",
        headMessageId = null,
        pinnedAt = null,
        archivedAt = null,
        createdAt = "2026-08-02T00:00:00Z",
        updatedAt = "2026-08-02T00:00:00Z",
        deletedAt = null,
    )
}
