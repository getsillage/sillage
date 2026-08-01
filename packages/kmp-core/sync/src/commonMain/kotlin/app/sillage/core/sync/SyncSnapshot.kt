package app.sillage.core.sync

import app.sillage.core.domain.ask.AskConversation
import app.sillage.core.domain.ask.AskMessage
import app.sillage.core.domain.records.Memo
import app.sillage.core.domain.records.MemoAI
import app.sillage.core.domain.settings.AISettings

/**
 * Platform-neutral full synchronization snapshot.
 *
 * Backup format metadata and client presentation preferences intentionally do
 * not belong here. They are file- or host-specific adapter concerns.
 */
data class SyncSnapshot(
    val memos: List<Memo>,
    val memoAI: List<MemoAI>,
    val aiSettings: SyncAISettingsSection,
    val askConversations: List<AskConversation>,
    val askMessages: List<AskMessage>,
)

sealed interface SyncAISettingsSection {
    data class Available(val settings: AISettings) : SyncAISettingsSection

    /**
     * The independent AI-settings request failed. Merge adapters must preserve
     * existing local settings instead of interpreting this as an empty value.
     */
    data object Unavailable : SyncAISettingsSection
}

data class PullSyncResult(
    val aiSettingsAvailable: Boolean,
)

interface SyncSnapshotGateway {
    suspend fun pull(): SyncSnapshot
}

interface SyncSnapshotRepository {
    /** Atomically merges the snapshot with local data and sync metadata. */
    suspend fun merge(snapshot: SyncSnapshot)
}

class PullSyncUseCase(
    private val gateway: SyncSnapshotGateway,
    private val repository: SyncSnapshotRepository,
) {
    suspend operator fun invoke(): PullSyncResult {
        val snapshot = gateway.pull()
        repository.merge(snapshot)
        return PullSyncResult(
            aiSettingsAvailable = snapshot.aiSettings is SyncAISettingsSection.Available,
        )
    }
}
