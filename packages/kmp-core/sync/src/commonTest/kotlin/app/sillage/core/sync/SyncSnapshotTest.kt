package app.sillage.core.sync

import app.sillage.core.domain.ask.AskConversation
import app.sillage.core.domain.ask.AskMessage
import app.sillage.core.domain.records.Memo
import app.sillage.core.domain.settings.AIProfile
import app.sillage.core.domain.settings.AISettings
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SyncSnapshotTest {
    @Test
    fun pullMergesTheExactTransportSnapshot() = runSuspend {
        val snapshot = snapshot(
            SyncAISettingsSection.Available(
                AISettings(listOf(profile()), autoSummary = true),
            ),
        )
        val repository = CapturingRepository()

        val result = PullSyncUseCase(FixedGateway(snapshot), repository)()

        assertTrue(result.aiSettingsAvailable)
        assertSame(snapshot, repository.merged)
    }

    @Test
    fun unavailableAISettingsRemainAnExplicitPartialResult() = runSuspend {
        val snapshot = snapshot(SyncAISettingsSection.Unavailable)
        val repository = CapturingRepository()

        val result = PullSyncUseCase(FixedGateway(snapshot), repository)()

        assertFalse(result.aiSettingsAvailable)
        assertSame(SyncAISettingsSection.Unavailable, repository.merged?.aiSettings)
    }

    private class FixedGateway(
        private val snapshot: SyncSnapshot,
    ) : SyncSnapshotGateway {
        override suspend fun pull(): SyncSnapshot = snapshot
    }

    private class CapturingRepository : SyncSnapshotRepository {
        var merged: SyncSnapshot? = null

        override suspend fun merge(snapshot: SyncSnapshot) {
            check(merged == null)
            merged = snapshot
        }
    }

    private fun snapshot(aiSettings: SyncAISettingsSection) = SyncSnapshot(
        memos = listOf(memo()),
        memoAI = emptyList(),
        aiSettings = aiSettings,
        askConversations = listOf(conversation()),
        askMessages = listOf(message()),
    )

    private fun memo() = Memo(
        id = "memo-1",
        content = "content",
        entryDate = "2026-08-01",
        version = 1,
        createdAt = "2026-08-01T00:00:00Z",
        updatedAt = "2026-08-01T00:00:00Z",
        favoritedAt = null,
        archivedAt = null,
        deletedAt = null,
        purgedAt = null,
    )

    private fun conversation() = AskConversation(
        id = "ask-1",
        title = "Question",
        status = "active",
        contextScope = "recent_30_days",
        headMessageId = "message-1",
        pinnedAt = null,
        archivedAt = null,
        createdAt = "2026-08-01T00:00:00Z",
        updatedAt = "2026-08-01T00:00:00Z",
        deletedAt = null,
    )

    private fun message() = AskMessage(
        id = "message-1",
        conversationId = "ask-1",
        role = "assistant",
        content = "Answer",
        parentId = null,
        forkOfId = null,
        status = "complete",
        sourceRefs = emptyList(),
        model = "model",
        promptVersion = "v1",
        createdAt = "2026-08-01T00:00:00Z",
        updatedAt = "2026-08-01T00:00:00Z",
        deletedAt = null,
    )

    private fun profile() = AIProfile(
        id = "profile-1",
        name = "Primary",
        provider = "anthropic",
        baseUrl = "https://example.com",
        model = "model",
        temperature = 0.3,
        maxTokens = 1_000,
        enabled = true,
        active = true,
        hasApiKey = true,
        keyUnavailable = false,
        autoSummary = true,
        createdAt = "2026-08-01T00:00:00Z",
        updatedAt = "2026-08-01T00:00:00Z",
    )

    private fun <T> runSuspend(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(
            object : Continuation<T> {
                override val context = EmptyCoroutineContext

                override fun resumeWith(result: Result<T>) {
                    outcome = result
                }
            },
        )
        return checkNotNull(outcome) { "Test coroutine did not complete synchronously" }.getOrThrow()
    }
}
