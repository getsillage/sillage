package app.sillage.data

import android.content.Context
import app.sillage.core.domain.ask.AskConversation
import app.sillage.core.domain.ask.AskMessage
import app.sillage.core.domain.ask.AskSourceRef
import app.sillage.core.application.records.RecordDetail
import app.sillage.core.domain.records.Memo
import app.sillage.core.domain.records.MemoAI
import app.sillage.core.domain.ask.isActive
import app.sillage.core.sync.AppliedMemoSync
import app.sillage.core.sync.ConflictMemoSync
import app.sillage.core.sync.PendingMemoSync
import app.sillage.core.sync.SyncAISettingsSection
import app.sillage.core.sync.SyncSnapshot
import app.sillage.features.records.MemoListFilter
import app.sillage.features.records.matchesListFilter
import app.sillage.features.settings.AIProfileDraft
import app.sillage.features.settings.toDraft
import java.io.File
import java.time.Instant
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

class LocalDataStore internal constructor(private val stateStore: LocalStateStorage) {
    constructor(context: Context) : this(LocalStateStore(context))

    internal constructor(context: Context, cipher: ValueCipher) : this(LocalStateStore(context, cipher))

    fun exportData(): SillageExportData = loadData()

    fun listMemos(): List<Memo> = loadData().memos

    fun pendingCloudMemos(): List<PendingMemoSync> {
        val currentMutations = pendingMemoMutations()
        val resolved = resolvePendingMemoSyncs(
            memos = loadData().memos,
            cloudVersions = cloudMemoVersions(),
            pendingMutations = currentMutations,
        )
        if (resolved.pendingMutations != currentMutations) {
            savePendingMemoMutations(resolved.pendingMutations)
        }
        return resolved.pending
    }

    fun markCloudSynced(memos: List<Memo>) {
        if (memos.isEmpty()) {
            return
        }
        val versions = cloudMemoVersions().toMutableMap()
        memos.forEach { memo -> versions[memo.id] = memo.version }
        val syncedIds = memos.mapTo(mutableSetOf(), Memo::id)
        val mutations = pendingMemoMutations().filterKeys { it !in syncedIds }
        stateStore.putStrings(
            mapOf(
                KEY_CLOUD_MEMO_VERSIONS to cloudMemoVersionsJson(versions),
                KEY_PENDING_MEMO_MUTATIONS to pendingMemoMutationsJson(mutations),
            ),
        )
    }

    fun applyCloudSyncedMemos(appliedMemos: List<AppliedMemoSync>) {
        if (appliedMemos.isEmpty()) {
            return
        }
        val currentData = loadData()
        val merged = mergeAppliedCloudMemos(
            localMemos = currentData.memos,
            cloudVersions = cloudMemoVersions(),
            pendingMutations = pendingMemoMutations(),
            appliedMemos = appliedMemos,
        )
        persistMemoCloudState(currentData.copy(memos = merged.memos), merged.cloudVersions, merged.pendingMutations)
    }

    fun getMemoOrNull(id: String): Memo? = loadData().memos.find { it.id == id }

    fun listPendingLocalAttachments(): List<PendingLocalAttachment> {
        return pendingLocalAttachments().values.toList()
    }

    fun getPendingLocalAttachment(id: String): PendingLocalAttachment? {
        return pendingLocalAttachments()[id]
    }

    fun addPendingLocalAttachment(pending: PendingLocalAttachment) {
        val current = pendingLocalAttachments().toMutableMap()
        current[pending.id] = pending
        savePendingLocalAttachments(current)
    }

    fun removePendingLocalAttachment(id: String) {
        val current = pendingLocalAttachments().toMutableMap()
        if (current.remove(id) == null) {
            return
        }
        savePendingLocalAttachments(current)
    }

    /** Rewrite attachment markdown placeholders in every stored memo body. */
    fun replaceAttachmentMarkdownEverywhere(fromMarkdown: String, toMarkdown: String) {
        val currentData = loadData()
        val rewritten = rewriteAttachmentMarkdownInMemos(
            memos = currentData.memos,
            cloudVersions = cloudMemoVersions(),
            pendingMutations = pendingMemoMutations(),
            fromMarkdown = fromMarkdown,
            toMarkdown = toMarkdown,
            now = now(),
        ) ?: return
        persistMemoCloudState(
            currentData.copy(memos = rewritten.memos),
            rewritten.cloudVersions,
            rewritten.pendingMutations,
        )
    }

    /**
     * Keep the local pending edit and adopt the server version as the next baseVersion
     * so a later push resubmits against the conflicted baseline.
     */
    fun resolveConflictKeepLocal(conflict: ConflictMemoSync, newMutationId: () -> String = ::newMemoMutationId) {
        val currentData = loadData()
        val local = currentData.memos.find { it.id == conflict.resourceId }
            ?: throw ApiException("记录不存在")
        val serverVersion = conflict.serverVersion
            ?: conflict.serverMemo?.version
            ?: throw ApiException("缺少服务端版本")
        val merged = resolveConflictKeepLocalState(
            localMemos = currentData.memos,
            cloudVersions = cloudMemoVersions(),
            pendingMutations = pendingMemoMutations(),
            localMemo = local,
            serverVersion = serverVersion,
            newMutationId = newMutationId,
        )
        persistMemoCloudState(currentData.copy(memos = merged.memos), merged.cloudVersions, merged.pendingMutations)
    }

    /** Drop the local pending mutation and adopt the server resource as the local memo. */
    fun resolveConflictTakeServer(conflict: ConflictMemoSync) {
        val server = conflict.serverMemo ?: throw ApiException("缺少服务端记录")
        val currentData = loadData()
        val merged = resolveConflictTakeServerState(
            localMemos = currentData.memos,
            cloudVersions = cloudMemoVersions(),
            pendingMutations = pendingMemoMutations(),
            serverMemo = server,
        )
        persistMemoCloudState(currentData.copy(memos = merged.memos), merged.cloudVersions, merged.pendingMutations)
    }

    private fun persistMemoCloudState(
        data: SillageExportData,
        cloudVersions: Map<String, Long>,
        pendingMutations: Map<String, PendingMemoMutation>,
    ) {
        val normalized = data.normalizedForLocalStorage()
        stateStore.putStrings(
            mapOf(
                KEY_DATA to SillageExportCodec.toLocalJson(normalized),
                KEY_CLOUD_MEMO_VERSIONS to cloudMemoVersionsJson(cloudVersions),
                KEY_PENDING_MEMO_MUTATIONS to pendingMemoMutationsJson(pendingMutations),
            ),
        )
    }

    fun searchMemos(query: String, filter: MemoListFilter? = null): List<Memo> {
        val normalized = query.trim()
        if (normalized.isBlank()) {
            return emptyList()
        }
        return loadData().memos.filter {
            (filter?.let(it::matchesListFilter) ?: (it.deletedAt == null && it.purgedAt == null)) &&
                it.content.contains(normalized, ignoreCase = true)
        }
    }

    fun getMemo(id: String): RecordDetail {
        val data = loadData()
        val memo = data.memos.find { it.id == id } ?: throw ApiException("记录不存在")
        return RecordDetail(
            memo = memo,
            ai = data.memoAI.firstOrNull { it.memoId == id },
        )
    }

    fun createMemo(content: String, entryDate: String): Memo {
        val now = now()
        val memo = Memo(
            id = UUID.randomUUID().toString(),
            content = content,
            entryDate = entryDate,
            version = 1,
            createdAt = now,
            updatedAt = now,
            favoritedAt = null,
            archivedAt = null,
            deletedAt = null,
        )
        saveLocalMemoMutation(memo, append = true)
        return memo
    }

    fun updateMemo(memo: Memo, content: String, entryDate: String): Memo {
        val updated = memo.copy(
            content = content,
            entryDate = entryDate,
            version = memo.version + 1,
            updatedAt = now(),
        )
        replaceMemo(updated)
        return updated
    }

    fun deleteMemo(memo: Memo): Memo {
        val deleted = memo.copy(
            version = memo.version + 1,
            updatedAt = now(),
            deletedAt = now(),
            purgedAt = null,
        )
        replaceMemo(deleted, action = "delete")
        return deleted
    }

    fun restoreMemo(memo: Memo): Memo {
        if (memo.deletedAt == null || memo.purgedAt != null) {
            throw ApiException("记录无法恢复")
        }
        val restored = memo.copy(
            version = memo.version + 1,
            updatedAt = now(),
            deletedAt = null,
        )
        replaceMemo(
            restored,
            action = if (cloudMemoVersions()[memo.id] == null) "create" else "restore",
        )
        return restored
    }

    fun purgeMemo(memo: Memo): Memo {
        if (memo.deletedAt == null || memo.purgedAt != null) {
            throw ApiException("记录无法永久删除")
        }
        val timestamp = now()
        val purged = memo.copy(
            content = "",
            entryDate = "1970-01-01",
            version = memo.version + 1,
            updatedAt = timestamp,
            favoritedAt = null,
            archivedAt = null,
            purgedAt = timestamp,
        )
        val currentData = loadData()
        val memos = currentData.memos.map { if (it.id == memo.id) purged else it }
        val pendingAttachments = pendingLocalAttachments().toMutableMap()
        pendingAttachments.entries.removeAll { (_, pending) ->
            val path = localAttachmentPath(pending)
            val referencedHere = memo.content.contains(path)
            val referencedElsewhere = currentData.memos.any {
                it.id != memo.id && it.purgedAt == null && it.content.contains(path)
            }
            if (referencedHere && !referencedElsewhere) {
                runCatching { File(pending.absolutePath).delete() }
                true
            } else {
                false
            }
        }
        val mutations = pendingMemoMutations().toMutableMap()
        mutations[purged.id] = newPendingMemoMutation(purged, action = "purge")
        stateStore.putStrings(
            mapOf(
                KEY_DATA to SillageExportCodec.toLocalJson(
                    currentData.copy(
                        memos = memos,
                        memoAI = currentData.memoAI.filter { it.memoId != memo.id },
                    ),
                ),
                KEY_PENDING_MEMO_MUTATIONS to pendingMemoMutationsJson(mutations),
                KEY_PENDING_LOCAL_ATTACHMENTS to pendingLocalAttachmentsJson(pendingAttachments),
            ),
        )
        return purged
    }

    fun setMemoFavorited(memo: Memo, favorited: Boolean): Memo {
        val updated = memo.copy(
            version = memo.version + 1,
            updatedAt = now(),
            favoritedAt = if (favorited) now() else null,
        )
        replaceMemo(updated)
        return updated
    }

    fun setMemoArchived(memo: Memo, archived: Boolean): Memo {
        val updated = memo.copy(
            version = memo.version + 1,
            updatedAt = now(),
            archivedAt = if (archived) now() else null,
        )
        replaceMemo(updated)
        return updated
    }

    fun saveMemoAI(ai: MemoAI) {
        updateData { data ->
            data.copy(memoAI = data.memoAI.filter { it.memoId != ai.memoId } + ai)
        }
    }

    fun listAIProfiles(): List<AIProfileDraft> = loadData().aiProfiles

    fun autoSummaryEnabled(): Boolean = loadData().autoSummary

    fun saveAutoSummary(enabled: Boolean) {
        updateData { data -> data.copy(autoSummary = enabled, autoSummaryDefined = true) }
    }

    fun saveAIProfiles(profiles: List<AIProfileDraft>): List<AIProfileDraft> {
        val now = now()
        val currentById = loadData().aiProfiles.associateBy { it.id }
        var activeSeen = false
        val saved = profiles.map { profile ->
            val existing = currentById[profile.id]
            val apiKeyInput = if (profile.apiKeyInput.isBlank()) {
                existing?.apiKeyInput.orEmpty()
            } else {
                profile.apiKeyInput
            }
            val hasApiKey = profile.hasApiKey || apiKeyInput.isNotBlank() || existing?.hasApiKey == true
            val active = profile.active && !activeSeen
            if (active) {
                activeSeen = true
            }
            profile.copy(
                id = profile.id.ifBlank { UUID.randomUUID().toString() },
                active = active,
                hasApiKey = hasApiKey,
                keyUnavailable = false,
                apiKeyInput = apiKeyInput,
            )
        }
        updateData { data -> data.copy(exportedAt = now, aiProfiles = saved) }
        return saved
    }

    fun activeAIProfile(): AIProfileDraft? {
        val profiles = loadData().aiProfiles.filter { it.enabled }
        return profiles.firstOrNull { it.active } ?: profiles.firstOrNull()
    }

    fun listAskConversations(): List<AskConversation> {
        return loadData().askConversations
            .filter(AskConversation::isActive)
            .sortedByDescending { it.updatedAt }
    }

    fun listAskMessages(conversationId: String): List<AskMessage> {
        return loadData().askMessages
            .filter { it.conversationId == conversationId }
            .sortedBy { it.createdAt }
    }

    fun createAskConversation(contextScope: String): AskConversation {
        val now = now()
        val conversation = AskConversation(
            id = UUID.randomUUID().toString(),
            title = "",
            status = "active",
            contextScope = contextScope,
            headMessageId = null,
            pinnedAt = null,
            archivedAt = null,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )
        updateData { data ->
            data.copy(askConversations = listOf(conversation) + data.askConversations)
        }
        return conversation
    }

    fun appendAskTurn(
        conversationId: String,
        question: String,
        answer: String,
        sourceRefs: List<AskSourceRef>,
        model: String,
        promptVersion: String,
        parentId: String?,
        forkOfId: String?,
    ): Pair<AskConversation, List<AskMessage>> {
        val now = now()
        val userMessage = if (forkOfId == null) {
            AskMessage(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                role = "user",
                content = question,
                parentId = parentId,
                forkOfId = null,
                status = "complete",
                sourceRefs = emptyList(),
                model = "",
                promptVersion = "",
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            )
        } else {
            null
        }
        val assistant = AskMessage(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            role = "assistant",
            content = answer,
            parentId = userMessage?.id ?: parentId,
            forkOfId = forkOfId,
            status = "complete",
            sourceRefs = sourceRefs,
            model = model,
            promptVersion = promptVersion,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )
        val newMessages = listOfNotNull(userMessage, assistant)
        var updatedConversation: AskConversation? = null
        updateData { data ->
            if (data.askConversations.none { it.id == conversationId }) {
                throw ApiException("会话不存在")
            }
            val updatedConversations = data.askConversations.map { conversation ->
                if (conversation.id == conversationId) {
                    conversation.copy(
                        title = if (
                            conversation.title.isBlank() || conversation.title == LEGACY_NEW_CONVERSATION_TITLE
                        ) {
                            question.take(30)
                        } else {
                            conversation.title
                        },
                        headMessageId = assistant.id,
                        updatedAt = now,
                    ).also { updatedConversation = it }
                } else {
                    conversation
                }
            }
            data.copy(
                askConversations = updatedConversations,
                askMessages = data.askMessages + newMessages,
            )
        }
        return (updatedConversation ?: throw ApiException("会话不存在")) to newMessages
    }

    fun setAskHead(conversationId: String, messageId: String) {
        updateData { data ->
            data.copy(
                askConversations = data.askConversations.map {
                    if (it.id == conversationId) it.copy(headMessageId = messageId, updatedAt = now()) else it
                },
            )
        }
    }

    fun exportData(themeMode: String, memoViewMode: String): SillageExportData {
        val data = loadData()
        return data.copy(
            exportedAt = now(),
            themeMode = themeMode,
            memoViewMode = memoViewMode,
        )
    }

    fun mergeWith(data: SillageExportData) {
        saveData(mergeData(loadData(), data.normalizedForLocalStorage()))
    }

    fun mergeFromServer(snapshot: SyncSnapshot) {
        val currentData = loadData()
        val normalized = snapshot.toStorageData(
            themeMode = currentData.themeMode,
            memoViewMode = currentData.memoViewMode,
        ).normalizedForLocalStorage()
        val mergedMemos = mergePulledCloudMemos(
            localMemos = currentData.memos,
            pulledMemos = normalized.memos,
            cloudVersions = cloudMemoVersions(),
            pendingMutations = pendingMemoMutations(),
        )
        val purgedMemoIds = mergedMemos.memos
            .filter { it.purgedAt != null }
            .mapTo(mutableSetOf(), Memo::id)
        val mergedData = mergeData(currentData, normalized).let { merged ->
            merged.copy(
                memos = mergedMemos.memos,
                memoAI = merged.memoAI.filter { it.memoId !in purgedMemoIds },
            )
        }
        stateStore.putStrings(
            mapOf(
                KEY_DATA to SillageExportCodec.toLocalJson(mergedData),
                KEY_CLOUD_MEMO_VERSIONS to cloudMemoVersionsJson(mergedMemos.cloudVersions),
                KEY_PENDING_MEMO_MUTATIONS to pendingMemoMutationsJson(mergedMemos.pendingMutations),
            ),
        )
    }

    private fun SyncSnapshot.toStorageData(
        themeMode: String,
        memoViewMode: String,
    ): SillageExportData {
        val settings = (aiSettings as? SyncAISettingsSection.Available)?.settings
        return SillageExportData(
            formatVersion = SillageExportCodec.FORMAT_VERSION,
            exportedAt = now(),
            themeMode = themeMode,
            memoViewMode = memoViewMode,
            autoSummary = settings?.autoSummary ?: false,
            autoSummaryDefined = settings != null,
            memos = memos,
            memoAI = memoAI,
            aiProfiles = settings?.profiles?.map { it.toDraft() }.orEmpty(),
            askConversations = askConversations,
            askMessages = askMessages,
        )
    }

    private fun replaceMemo(memo: Memo, action: String = "") {
        saveLocalMemoMutation(memo, append = false, action = action)
    }

    private fun saveLocalMemoMutation(memo: Memo, append: Boolean, action: String = "") {
        val currentData = loadData()
        val memos = if (append) {
            currentData.memos + memo
        } else {
            currentData.memos.map { if (it.id == memo.id) memo else it }
        }
        val mutations = pendingMemoMutations().toMutableMap()
        val effectiveAction = action.ifBlank { mutations[memo.id]?.action.orEmpty() }
        mutations[memo.id] = newPendingMemoMutation(memo, action = effectiveAction)
        stateStore.putStrings(
            mapOf(
                KEY_DATA to SillageExportCodec.toLocalJson(currentData.copy(memos = memos)),
                KEY_PENDING_MEMO_MUTATIONS to pendingMemoMutationsJson(mutations),
            ),
        )
    }

    private fun loadData(): SillageExportData {
        return when (val stored = stateStore.readString(KEY_DATA)) {
            is SecureReadResult.Missing -> emptyData()
            is SecureReadResult.Unreadable -> throw corruptLocalState(KEY_DATA, stored.rawPayload)
            is SecureReadResult.Value -> runCatching { SillageExportCodec.fromJson(stored.value) }
                .getOrElse { throw corruptLocalState(KEY_DATA, stored.value) }
        }
    }

    /**
     * Local data exists but cannot be decrypted or parsed. Preserve the raw
     * payload once and fail loudly so covering writes cannot wipe the diary.
     */
    private fun corruptLocalState(key: String, rawPayload: String): ApiException {
        val backupKey = if (key == KEY_DATA) KEY_DATA_CORRUPT_BACKUP else "${key}_corrupt_backup"
        if (!stateStore.contains(backupKey)) {
            stateStore.putString(backupKey, rawPayload)
        }
        return ApiException(LOCAL_DATA_CORRUPT_MESSAGE)
    }

    private fun updateData(transform: (SillageExportData) -> SillageExportData) {
        saveData(transform(loadData()))
    }

    private fun saveData(data: SillageExportData) {
        stateStore.putString(KEY_DATA, SillageExportCodec.toLocalJson(data.normalizedForLocalStorage()))
    }

    private fun emptyData(): SillageExportData {
        return SillageExportData(
            formatVersion = SillageExportCodec.FORMAT_VERSION,
            exportedAt = "",
            themeMode = "",
            memoViewMode = "",
            autoSummary = false,
            memos = emptyList(),
            memoAI = emptyList(),
            aiProfiles = emptyList(),
            askConversations = emptyList(),
            askMessages = emptyList(),
        )
    }

    private fun now(): String = Instant.now().toString()

    private fun mergeData(current: SillageExportData, incoming: SillageExportData): SillageExportData {
        return incoming.copy(
            themeMode = incoming.themeMode.ifBlank { current.themeMode },
            memoViewMode = incoming.memoViewMode.ifBlank { current.memoViewMode },
            autoSummary = if (incoming.autoSummaryDefined) incoming.autoSummary else current.autoSummary,
            autoSummaryDefined = true,
            memos = mergeBy(current.memos, incoming.memos) { it.id },
            memoAI = mergeBy(current.memoAI, incoming.memoAI) { it.memoId },
            aiProfiles = mergeProfiles(current.aiProfiles, incoming.aiProfiles),
            askConversations = mergeBy(current.askConversations, incoming.askConversations) { it.id },
            askMessages = mergeBy(current.askMessages, incoming.askMessages) { it.id },
        )
    }

    private fun mergeProfiles(
        current: List<AIProfileDraft>,
        incoming: List<AIProfileDraft>,
    ): List<AIProfileDraft> {
        val currentById = current.associateBy { it.id }
        return mergeBy(current, incoming) { it.id }.map { profile ->
            val existing = currentById[profile.id]
            if (profile.apiKeyInput.isBlank() && existing?.apiKeyInput?.isNotBlank() == true) {
                profile.copy(apiKeyInput = existing.apiKeyInput, hasApiKey = true)
            } else {
                profile
            }
        }
    }

    private fun <T> mergeBy(current: List<T>, incoming: List<T>, key: (T) -> String): List<T> {
        val merged = linkedMapOf<String, T>()
        current.forEach { item -> merged[key(item)] = item }
        incoming.forEach { item -> merged[key(item)] = item }
        return merged.values.toList()
    }

    companion object {
        private const val LEGACY_NEW_CONVERSATION_TITLE = "新会话"
        private const val KEY_DATA = "data"
        private const val KEY_CLOUD_MEMO_VERSIONS = "cloud_memo_versions"
        private const val KEY_PENDING_MEMO_MUTATIONS = "pending_memo_mutations"
        private const val KEY_PENDING_LOCAL_ATTACHMENTS = "pending_local_attachments"
        internal const val KEY_DATA_CORRUPT_BACKUP = "data_corrupt_backup"
        internal const val LOCAL_DATA_CORRUPT_MESSAGE = "本地数据无法读取"
    }

    private fun cloudMemoVersions(): Map<String, Long> {
        val raw = readStateValue(KEY_CLOUD_MEMO_VERSIONS, "{}")
        val body = runCatching { JSONObject(raw) }
            .getOrElse { throw corruptLocalState(KEY_CLOUD_MEMO_VERSIONS, raw) }
        return buildMap {
            body.keys().forEach { id ->
                put(id, body.optLong(id))
            }
        }
    }

    private fun pendingLocalAttachments(): Map<String, PendingLocalAttachment> {
        val raw = when (val stored = stateStore.readString(KEY_PENDING_LOCAL_ATTACHMENTS)) {
            is SecureReadResult.Missing -> return emptyMap()
            is SecureReadResult.Unreadable -> throw corruptLocalState(
                KEY_PENDING_LOCAL_ATTACHMENTS,
                stored.rawPayload,
            )
            is SecureReadResult.Value -> stored.value
        }
        return runCatching {
            val body = JSONObject(raw)
            val out = linkedMapOf<String, PendingLocalAttachment>()
            body.keys().forEach { id ->
                val item = body.getJSONObject(id)
                out[id] = PendingLocalAttachment(
                    id = id,
                    filename = item.optString("filename"),
                    contentType = item.optString("contentType"),
                    absolutePath = item.optString("absolutePath"),
                    size = item.optLong("size"),
                )
            }
            out
        }.getOrElse { throw corruptLocalState(KEY_PENDING_LOCAL_ATTACHMENTS, raw) }
    }

    private fun savePendingLocalAttachments(items: Map<String, PendingLocalAttachment>) {
        stateStore.putString(KEY_PENDING_LOCAL_ATTACHMENTS, pendingLocalAttachmentsJson(items))
    }

    private fun pendingLocalAttachmentsJson(items: Map<String, PendingLocalAttachment>): String {
        val body = JSONObject()
        items.forEach { (id, item) ->
            body.put(
                id,
                JSONObject()
                    .put("filename", item.filename)
                    .put("contentType", item.contentType)
                    .put("absolutePath", item.absolutePath)
                    .put("size", item.size),
            )
        }
        return body.toString()
    }

    private fun pendingMemoMutations(): Map<String, PendingMemoMutation> {
        val raw = readStateValue(KEY_PENDING_MEMO_MUTATIONS, "{}")
        val body = runCatching { JSONObject(raw) }
            .getOrElse { throw corruptLocalState(KEY_PENDING_MEMO_MUTATIONS, raw) }
        return buildMap {
            body.keys().forEach { id ->
                val item = body.optJSONObject(id) ?: return@forEach
                val mutationId = item.optString("mutationId")
                val memoVersion = item.optLong("memoVersion", 0)
                val memoUpdatedAt = item.optString("memoUpdatedAt")
                val action = item.optString("action")
                if (mutationId.isNotBlank() && memoVersion > 0 && memoUpdatedAt.isNotBlank()) {
                    put(
                        id,
                        PendingMemoMutation(
                            mutationId = mutationId,
                            memoVersion = memoVersion,
                            memoUpdatedAt = memoUpdatedAt,
                            action = action,
                        ),
                    )
                }
            }
        }
    }

    private fun savePendingMemoMutations(mutations: Map<String, PendingMemoMutation>) {
        stateStore.putString(KEY_PENDING_MEMO_MUTATIONS, pendingMemoMutationsJson(mutations))
    }

    private fun readStateValue(key: String, fallback: String): String {
        return when (val stored = stateStore.readString(key)) {
            is SecureReadResult.Missing -> fallback
            is SecureReadResult.Value -> stored.value
            is SecureReadResult.Unreadable -> throw corruptLocalState(key, stored.rawPayload)
        }
    }
}

internal data class AppliedCloudMemoMerge(
    val memos: List<Memo>,
    val cloudVersions: Map<String, Long>,
    val pendingMutations: Map<String, PendingMemoMutation>,
)

internal data class PulledCloudMemoMerge(
    val memos: List<Memo>,
    val cloudVersions: Map<String, Long>,
    val pendingMutations: Map<String, PendingMemoMutation>,
)

internal fun mergePulledCloudMemos(
    localMemos: List<Memo>,
    pulledMemos: List<Memo>,
    cloudVersions: Map<String, Long>,
    pendingMutations: Map<String, PendingMemoMutation>,
): PulledCloudMemoMerge {
    val mergedMemos = linkedMapOf<String, Memo>()
    localMemos.forEach { memo -> mergedMemos[memo.id] = memo }
    val mergedVersions = cloudVersions.toMutableMap()
    val mergedMutations = pendingMutations.toMutableMap()
    pulledMemos.forEach { memo ->
        if (mergedMemos[memo.id] != null && mergedMutations[memo.id] != null) {
            return@forEach
        }
        mergedMemos[memo.id] = memo
        mergedVersions[memo.id] = memo.version
        mergedMutations.remove(memo.id)
    }
    return PulledCloudMemoMerge(
        memos = mergedMemos.values.toList(),
        cloudVersions = mergedVersions,
        pendingMutations = mergedMutations,
    )
}

/**
 * After an offline attachment is uploaded, replace localpending markdown with the
 * server URL and ensure affected memos remain pushable (version + pending mutation).
 * Returns null when nothing changes.
 */
internal fun rewriteAttachmentMarkdownInMemos(
    memos: List<Memo>,
    cloudVersions: Map<String, Long>,
    pendingMutations: Map<String, PendingMemoMutation>,
    fromMarkdown: String,
    toMarkdown: String,
    now: String,
    newMutationId: () -> String = ::newMemoMutationId,
): ConflictResolutionState? {
    if (fromMarkdown.isBlank() || fromMarkdown == toMarkdown) {
        return null
    }
    val mutations = pendingMutations.toMutableMap()
    var any = false
    val nextMemos = memos.map { memo ->
        if (!memo.content.contains(fromMarkdown)) {
            return@map memo
        }
        any = true
        val rewritten = memo.copy(
            content = memo.content.replace(fromMarkdown, toMarkdown),
            // Bump version when a cloud baseline or pending mutation exists so push
            // treats this as an update rather than dropping a no-op version.
            version = if (cloudVersions[memo.id] != null || mutations[memo.id] != null) {
                memo.version + 1
            } else {
                memo.version
            },
            updatedAt = now,
        )
        mutations[rewritten.id] = PendingMemoMutation(
            mutationId = newMutationId(),
            memoVersion = rewritten.version,
            memoUpdatedAt = rewritten.updatedAt,
            action = mutations[rewritten.id]?.action.orEmpty(),
        )
        rewritten
    }
    if (!any) {
        return null
    }
    return ConflictResolutionState(
        memos = nextMemos,
        cloudVersions = cloudVersions,
        pendingMutations = mutations,
    )
}

internal data class ConflictResolutionState(
    val memos: List<Memo>,
    val cloudVersions: Map<String, Long>,
    val pendingMutations: Map<String, PendingMemoMutation>,
)

internal fun resolveConflictKeepLocalState(
    localMemos: List<Memo>,
    cloudVersions: Map<String, Long>,
    pendingMutations: Map<String, PendingMemoMutation>,
    localMemo: Memo,
    serverVersion: Long,
    newMutationId: () -> String = ::newMemoMutationId,
    now: String = Instant.now().toString(),
): ConflictResolutionState {
    // Cloud baseline becomes the server version; local content must stay strictly
    // ahead so resolvePendingMemoSyncs still schedules a push (baseVersion=server).
    // When the conflicted local version is already <= serverVersion (common after
    // concurrent edits), bump it to serverVersion+1 and refresh mutation match fields.
    val versions = cloudVersions.toMutableMap()
    versions[localMemo.id] = serverVersion
    val resubmitVersion = maxOf(localMemo.version, serverVersion + 1)
    val keptLocal = if (resubmitVersion == localMemo.version) {
        localMemo
    } else {
        localMemo.copy(version = resubmitVersion, updatedAt = now)
    }
    val mutations = pendingMutations.toMutableMap()
    mutations[localMemo.id] = PendingMemoMutation(
        mutationId = newMutationId(),
        memoVersion = keptLocal.version,
        memoUpdatedAt = keptLocal.updatedAt,
        action = pendingMutations[localMemo.id]?.action.orEmpty(),
    )
    return ConflictResolutionState(
        memos = localMemos.map { if (it.id == localMemo.id) keptLocal else it },
        cloudVersions = versions,
        pendingMutations = mutations,
    )
}

internal fun resolveConflictTakeServerState(
    localMemos: List<Memo>,
    cloudVersions: Map<String, Long>,
    pendingMutations: Map<String, PendingMemoMutation>,
    serverMemo: Memo,
): ConflictResolutionState {
    val versions = cloudVersions.toMutableMap()
    versions[serverMemo.id] = serverMemo.version
    val mutations = pendingMutations.toMutableMap()
    mutations.remove(serverMemo.id)
    val memos = if (localMemos.any { it.id == serverMemo.id }) {
        localMemos.map { if (it.id == serverMemo.id) serverMemo else it }
    } else {
        localMemos + serverMemo
    }
    return ConflictResolutionState(
        memos = memos,
        cloudVersions = versions,
        pendingMutations = mutations,
    )
}

internal fun mergeAppliedCloudMemos(
    localMemos: List<Memo>,
    cloudVersions: Map<String, Long>,
    pendingMutations: Map<String, PendingMemoMutation>,
    appliedMemos: List<AppliedMemoSync>,
): AppliedCloudMemoMerge {
    val mergedMemos = linkedMapOf<String, Memo>()
    localMemos.forEach { memo -> mergedMemos[memo.id] = memo }
    val mergedVersions = cloudVersions.toMutableMap()
    val mergedMutations = pendingMutations.toMutableMap()
    appliedMemos.forEach { applied ->
        val memo = applied.memo
        val localMemo = mergedMemos[memo.id]
        val pendingMutation = mergedMutations[memo.id]
        val stillCurrent = pendingMutation?.mutationId == applied.mutationId &&
            localMemo != null &&
            pendingMutation.matches(localMemo)
        if (stillCurrent) {
            mergedMemos[memo.id] = memo
            mergedVersions[memo.id] = memo.version
            mergedMutations.remove(memo.id)
        } else {
            mergedVersions[memo.id] = maxOf(mergedVersions[memo.id] ?: Long.MIN_VALUE, memo.version)
        }
    }
    return AppliedCloudMemoMerge(
        memos = mergedMemos.values.toList(),
        cloudVersions = mergedVersions,
        pendingMutations = mergedMutations,
    )
}

internal data class PendingMemoMutation(
    val mutationId: String,
    val memoVersion: Long,
    val memoUpdatedAt: String,
    val action: String = "",
) {
    fun matches(memo: Memo): Boolean {
        return memoVersion == memo.version && memoUpdatedAt == memo.updatedAt
    }
}

internal data class PendingMemoSyncResolution(
    val pending: List<PendingMemoSync>,
    val pendingMutations: Map<String, PendingMemoMutation>,
)

internal fun resolvePendingMemoSyncs(
    memos: List<Memo>,
    cloudVersions: Map<String, Long>,
    pendingMutations: Map<String, PendingMemoMutation>,
    newMutationId: () -> String = ::newMemoMutationId,
): PendingMemoSyncResolution {
    val resolvedMutations = pendingMutations.toMutableMap()
    val pending = memos.mapNotNull { memo ->
        val cloudVersion = cloudVersions[memo.id]
        val baseVersion = when {
            cloudVersion == null && memo.deletedAt != null -> null
            cloudVersion == null -> null
            memo.version > cloudVersion -> cloudVersion
            else -> {
                resolvedMutations.remove(memo.id)
                return@mapNotNull null
            }
        }
        if (cloudVersion == null && memo.deletedAt != null) {
            resolvedMutations.remove(memo.id)
            return@mapNotNull null
        }
        val mutation = resolvedMutations[memo.id]
            ?.takeIf { it.matches(memo) }
            ?: newPendingMemoMutation(memo, newMutationId()).also {
                resolvedMutations[memo.id] = it
            }
        PendingMemoSync(
            memo = memo,
            baseVersion = baseVersion,
            mutationId = mutation.mutationId,
            action = mutation.action.ifBlank {
                when {
                    memo.purgedAt != null -> "purge"
                    memo.deletedAt != null -> "delete"
                    cloudVersion == null -> "create"
                    else -> "update"
                }
            },
        )
    }
    val memoIds = memos.mapTo(mutableSetOf(), Memo::id)
    resolvedMutations.keys.retainAll(memoIds)
    return PendingMemoSyncResolution(
        pending = pending,
        pendingMutations = resolvedMutations,
    )
}

private fun newPendingMemoMutation(
    memo: Memo,
    mutationId: String = newMemoMutationId(),
    action: String = "",
): PendingMemoMutation {
    return PendingMemoMutation(
        mutationId = mutationId,
        memoVersion = memo.version,
        memoUpdatedAt = memo.updatedAt,
        action = action,
    )
}

private fun newMemoMutationId(): String = "android-${UUID.randomUUID()}"

private fun cloudMemoVersionsJson(versions: Map<String, Long>): String {
    val body = JSONObject()
    versions.forEach { (id, version) -> body.put(id, version) }
    return body.toString()
}

private fun pendingMemoMutationsJson(mutations: Map<String, PendingMemoMutation>): String {
    val body = JSONObject()
    mutations.forEach { (id, mutation) ->
        body.put(
            id,
            JSONObject()
                .put("mutationId", mutation.mutationId)
                .put("memoVersion", mutation.memoVersion)
                .put("memoUpdatedAt", mutation.memoUpdatedAt)
                .put("action", mutation.action),
        )
    }
    return body.toString()
}

data class SillageExportData(
    val formatVersion: Int,
    val exportedAt: String,
    val themeMode: String,
    val memoViewMode: String,
    val autoSummary: Boolean,
    val autoSummaryDefined: Boolean = true,
    val memos: List<Memo>,
    val memoAI: List<MemoAI>,
    val aiProfiles: List<AIProfileDraft>,
    val askConversations: List<AskConversation>,
    val askMessages: List<AskMessage>,
)

object SillageExportCodec {
    const val FORMAT_VERSION = 1

    fun toJson(data: SillageExportData): String {
        return toJsonObject(data.sanitizedForFileExport()).toString(2)
    }

    internal fun toLocalJson(data: SillageExportData): String {
        return toJsonObject(data.normalizedForLocalStorage()).toString()
    }

    private fun toJsonObject(sanitized: SillageExportData): JSONObject {
        return JSONObject()
            .put("formatVersion", FORMAT_VERSION)
            .put("exportedAt", sanitized.exportedAt)
            .put("themeMode", sanitized.themeMode)
            .put("memoViewMode", sanitized.memoViewMode)
            .put("autoSummary", sanitized.autoSummary)
            .put("memos", sanitized.memos.toJsonArray(::memoToJson))
            .put("memoAI", sanitized.memoAI.toJsonArray(::memoAIToJson))
            .put("aiProfiles", sanitized.aiProfiles.toJsonArray(::aiProfileToJson))
            .put("askConversations", sanitized.askConversations.toJsonArray(::askConversationToJson))
            .put("askMessages", sanitized.askMessages.toJsonArray(::askMessageToJson))
    }

    fun fromJson(raw: String): SillageExportData {
        val root = JSONObject(raw)
        val version = root.optInt("formatVersion")
        if (version != FORMAT_VERSION) {
            throw ApiException("不支持的数据格式版本")
        }
        val profiles = root.optJSONArray("aiProfiles")
        val hasTopLevelAutoSummary = root.has("autoSummary")
        val hasLegacyProfileAutoSummary = profiles.hasLegacyAutoSummary()
        return SillageExportData(
            formatVersion = version,
            exportedAt = root.optString("exportedAt"),
            themeMode = root.optString("themeMode"),
            memoViewMode = root.optString("memoViewMode"),
            autoSummary = if (hasTopLevelAutoSummary) {
                root.optBoolean("autoSummary")
            } else {
                profiles.legacyAutoSummary()
            },
            autoSummaryDefined = hasTopLevelAutoSummary || hasLegacyProfileAutoSummary,
            memos = root.optJSONArray("memos").toListOrEmpty(::jsonToMemo),
            memoAI = root.optJSONArray("memoAI").toListOrEmpty(::jsonToMemoAI),
            aiProfiles = profiles.toListOrEmpty(::jsonToAIProfileDraft),
            askConversations = root.optJSONArray("askConversations").toListOrEmpty(::jsonToAskConversation),
            askMessages = root.optJSONArray("askMessages").toListOrEmpty(::jsonToAskMessage),
        ).normalizedForLocalStorage()
    }

    private fun JSONArray?.hasLegacyAutoSummary(): Boolean {
        if (this == null) {
            return false
        }
        for (index in 0 until length()) {
            if (getJSONObject(index).has("autoSummary")) {
                return true
            }
        }
        return false
    }

    private fun JSONArray?.legacyAutoSummary(): Boolean {
        if (this == null) {
            return false
        }
        for (index in 0 until length()) {
            val profile = getJSONObject(index)
            if (profile.has("autoSummary") && profile.optBoolean("autoSummary")) {
                return true
            }
        }
        return false
    }
}
