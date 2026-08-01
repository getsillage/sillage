package app.sillage.core.domain.ask

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AskTest {
    @Test
    fun activeConversationExcludesArchivedAndDeletedStates() {
        val active = conversation()

        assertTrue(active.isActive())
        assertFalse(active.copy(archivedAt = "2026-08-01T00:00:00Z").isActive())
        assertFalse(active.copy(deletedAt = "2026-08-01T00:00:00Z").isActive())
    }

    private fun conversation() = AskConversation(
        id = "ask-1",
        title = "Question",
        status = "active",
        contextScope = "recent_30_days",
        headMessageId = null,
        pinnedAt = null,
        archivedAt = null,
        createdAt = "2026-08-01T00:00:00Z",
        updatedAt = "2026-08-01T00:00:00Z",
        deletedAt = null,
    )
}
