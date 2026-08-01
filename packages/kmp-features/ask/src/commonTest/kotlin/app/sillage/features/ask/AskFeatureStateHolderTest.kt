package app.sillage.features.ask

import app.sillage.core.domain.ask.AskConversation
import app.sillage.core.domain.ask.AskMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AskFeatureStateHolderTest {
    @Test
    fun clearWorkspaceResetsSelectionComposerLoadAndSession() {
        val state = AskFeatureStateHolder(
            conversation = AskConversationStateHolder(
                conversations = listOf(conversation("c1")),
                activeConversationId = "c1",
                headMessageId = "m1",
                messages = listOf(message("m1", "c1")),
            ),
            composer = AskComposerStateHolder(question = "问题"),
            load = AskLoadStateHolder(loading = true, errorMessage = "旧错误"),
            stream = AskStreamStateHolder(sending = true, requestId = 3),
            variant = AskVariantStateHolder(loading = true, requestId = 4),
            session = AskSessionStateHolder(generation = 2),
            sourceNavigation = AskSourceNavigationStateHolder(loading = true, requestId = 5),
            memoSave = AskMemoSaveStateHolder(requestId = 6, savingMessageId = "m1"),
        )

        val cleared = state.clearWorkspace()

        assertEquals(emptyList(), cleared.conversations)
        assertEquals("", cleared.activeConversationId)
        assertEquals("", cleared.question)
        assertFalse(cleared.loading)
        assertNull(cleared.loadErrorMessage)
        assertTrue(cleared.stream.sending)
        assertEquals(3, cleared.stream.requestId)
        assertTrue(cleared.variant.loading)
        assertEquals(3, cleared.screenSessionId)
        assertFalse(cleared.sourceLoading)
        assertEquals("", cleared.savingMessageId)
        assertEquals(7, cleared.memoSave.requestId)
    }

    @Test
    fun clearWorkspaceCanInvalidateStreamAndVariantIdentity() {
        val state = AskFeatureStateHolder(
            stream = AskStreamStateHolder(sending = true, requestId = 2),
            variant = AskVariantStateHolder(loading = true, requestId = 3),
        )

        val cleared = state.clearWorkspace(
            invalidateStream = true,
            invalidateVariant = true,
        )

        assertFalse(cleared.stream.sending)
        assertEquals(3, cleared.stream.requestId)
        assertFalse(cleared.variant.loading)
        assertEquals(4, cleared.variant.requestId)
    }

    @Test
    fun enterScreenAdvancesSessionOnlyWhenIdle() {
        val idle = AskFeatureStateHolder(session = AskSessionStateHolder(generation = 4))
        val busy = idle.copy(load = AskLoadStateHolder(loading = true))

        assertEquals(5, idle.enterScreen(requestInFlight = false).screenSessionId)
        assertEquals(4, busy.enterScreen(requestInFlight = true).screenSessionId)
        assertFalse(idle.enterScreen(requestInFlight = false).sourceLoading)
    }

    @Test
    fun startNewConversationDeselectsAndClearsTransientPresentation() {
        val state = AskFeatureStateHolder(
            conversation = AskConversationStateHolder(
                activeConversationId = "c1",
                headMessageId = "m1",
                messages = listOf(message("m1", "c1")),
            ),
            composer = AskComposerStateHolder(question = "继续问"),
            stream = AskStreamStateHolder(liveAnswer = "半截回答", streaming = true),
            session = AskSessionStateHolder(generation = 1),
            variant = AskVariantStateHolder(loading = true, requestId = 2),
        )

        val started = state.startNewConversation()

        assertEquals("", started.activeConversationId)
        assertEquals(emptyList(), started.messages)
        assertEquals("", started.question)
        assertEquals("", started.stream.liveAnswer)
        assertFalse(started.streaming)
        assertEquals(2, started.screenSessionId)
        assertFalse(started.variantLoading)
        assertEquals(3, started.variant.requestId)
    }

    @Test
    fun beginAndCompleteConversationLoadKeepSessionAndMessagesConsistent() {
        val state = AskFeatureStateHolder(
            composer = AskComposerStateHolder(contextScope = "recent_30_days"),
            session = AskSessionStateHolder(generation = 8),
        )
        val messages = listOf(message("m2", "c2"), message("m3", "c2"))

        val loading = state.beginConversationLoad(
            conversationId = "c2",
            headMessageId = "m3",
            contextScope = "all",
        )
        val completed = loading.completeConversationLoad(
            conversationId = "c2",
            headMessageId = "m3",
            messages = messages,
        )

        assertEquals("c2", loading.activeConversationId)
        assertEquals(emptyList(), loading.messages)
        assertTrue(loading.loading)
        assertEquals("all", loading.contextScope)
        assertEquals(9, loading.screenSessionId)
        assertEquals(messages, completed.messages)
        assertFalse(completed.loading)
        assertEquals("m3", completed.headMessageId)
    }

    @Test
    fun completeConversationCatalogReplacesListAndFinishesLoad() {
        val conversations = listOf(conversation("c1"), conversation("c2"))
        val state = AskFeatureStateHolder(load = AskLoadStateHolder(loading = true))

        val completed = state.completeConversationCatalog(conversations)

        assertEquals(conversations, completed.conversations)
        assertFalse(completed.loading)
    }

    @Test
    fun activateConversationMakesCreatedConversationCurrent() {
        val created = conversation("c-new").copy(headMessageId = "h1")
        val state = AskFeatureStateHolder(
            conversation = AskConversationStateHolder(
                conversations = listOf(conversation("c-old")),
                activeConversationId = "c-old",
            ),
        )

        val activated = state.activateConversation(created)

        assertEquals("c-new", activated.activeConversationId)
        assertEquals("h1", activated.headMessageId)
        assertEquals(listOf("c-new", "c-old"), activated.conversations.map { it.id })
    }

    @Test
    fun applyVariantHeadMovesBranchAndStoresFinishedVariant() {
        val state = AskFeatureStateHolder(
            conversation = AskConversationStateHolder(
                conversations = listOf(conversation("c1").copy(headMessageId = "old")),
                activeConversationId = "c1",
                headMessageId = "old",
            ),
            variant = AskVariantStateHolder(loading = true, requestId = 3),
        )
        val finished = AskVariantStateHolder(loading = false, requestId = 3)

        val applied = state.applyVariantHead(
            conversationId = "c1",
            headMessageId = "new-head",
            variant = finished,
        )

        assertEquals("new-head", applied.headMessageId)
        assertEquals(finished, applied.variant)
        assertEquals("new-head", applied.conversations.single().headMessageId)
    }

    @Test
    fun replaceActiveSnapshotUpdatesOnlyMatchingConversation() {
        val messages = listOf(message("m1", "c1"), message("m2", "c1"))
        val state = AskFeatureStateHolder(
            conversation = AskConversationStateHolder(
                conversations = listOf(conversation("c1"), conversation("c2")),
                activeConversationId = "c1",
            ),
        )

        val replaced = state.replaceActiveSnapshot(
            conversationId = "c1",
            conversations = listOf(conversation("c1").copy(title = "更新"), conversation("c2")),
            headMessageId = "m2",
            messages = messages,
        )
        val ignored = state.replaceActiveSnapshot(
            conversationId = "other",
            conversations = listOf(conversation("x")),
            headMessageId = "x",
            messages = listOf(message("x", "other")),
        )

        assertEquals("更新", replaced.conversations.first().title)
        assertEquals(messages, replaced.messages)
        assertEquals("m2", replaced.headMessageId)
        assertEquals(state.conversation, ignored.conversation)
    }

    @Test
    fun clearConversationCatalogDropsRowsWithoutAdvancingSession() {
        val state = AskFeatureStateHolder(
            conversation = AskConversationStateHolder(
                conversations = listOf(conversation("c1")),
                activeConversationId = "c1",
                messages = listOf(message("m1", "c1")),
            ),
            session = AskSessionStateHolder(generation = 4),
        )

        val cleared = state.clearConversationCatalog()

        assertEquals(emptyList(), cleared.conversations)
        assertEquals(emptyList(), cleared.messages)
        assertEquals("c1", cleared.activeConversationId)
        assertEquals(4, cleared.screenSessionId)
    }

    @Test
    fun finishStreamClearsLiveAnswerAndOptionallyComposer() {
        val state = AskFeatureStateHolder(
            composer = AskComposerStateHolder(question = "问题"),
            stream = AskStreamStateHolder(
                sending = true,
                streaming = true,
                liveAnswer = "半截",
                completionEventId = 2,
            ),
        )

        val kept = state.finishStream(answerCompleted = true, clearQuestion = false)
        val cleared = state.finishStream(answerCompleted = true, clearQuestion = true)

        assertFalse(kept.sending)
        assertFalse(kept.streaming)
        assertEquals("", kept.stream.liveAnswer)
        assertEquals(3, kept.stream.completionEventId)
        assertEquals("问题", kept.question)
        assertEquals("", cleared.question)
    }

    @Test
    fun replaceActiveSnapshotCanClearComposerDraft() {
        val messages = listOf(message("m1", "c1"))
        val state = AskFeatureStateHolder(
            conversation = AskConversationStateHolder(activeConversationId = "c1"),
            composer = AskComposerStateHolder(question = "草稿"),
        )

        val replaced = state.replaceActiveSnapshot(
            conversationId = "c1",
            conversations = listOf(conversation("c1")),
            headMessageId = "m1",
            messages = messages,
            clearQuestion = true,
        )

        assertEquals(messages, replaced.messages)
        assertEquals("", replaced.question)
    }

    private fun conversation(id: String): AskConversation {
        return AskConversation(
            id = id,
            title = id,
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

    private fun message(id: String, conversationId: String): AskMessage {
        return AskMessage(
            id = id,
            conversationId = conversationId,
            role = "assistant",
            content = id,
            parentId = null,
            forkOfId = null,
            status = "complete",
            sourceRefs = emptyList(),
            model = "test-model",
            promptVersion = "test-prompt",
            createdAt = "2026-08-01T00:00:00Z",
            updatedAt = "2026-08-01T00:00:00Z",
            deletedAt = null,
        )
    }
}
