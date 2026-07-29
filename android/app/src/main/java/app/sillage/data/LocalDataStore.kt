package app.sillage.data

import android.content.Context
import java.time.Instant
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

class LocalDataStore(context: Context) {
    private val prefs = context.getSharedPreferences("sillage.local_data", Context.MODE_PRIVATE)
    private val securePrefs = SecurePreferences(prefs)

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
        val editor = prefs.edit()
        securePrefs.putString(
            editor,
            KEY_CLOUD_MEMO_VERSIONS,
            cloudMemoVersionsJson(versions),
        )
        securePrefs.putString(
            editor,
            KEY_PENDING_MEMO_MUTATIONS,
            pendingMemoMutationsJson(mutations),
        ).apply()
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
        if (fromMarkdown.isBlank() || fromMarkdown == toMarkdown) {
            return
        }
        val currentData = loadData()
        val cloudVersions = cloudMemoVersions()
        val mutations = pendingMemoMutations().toMutableMap()
        val now = now()
        var any = false
        val memos = currentData.memos.map { memo ->
            if (!memo.content.contains(fromMarkdown)) {
                return@map memo
            }
            any = true
            val rewritten = memo.copy(
                content = memo.content.replace(fromMarkdown, toMarkdown),
                // Bump version when a cloud baseline exists so push treats this as an update.
                version = if (cloudVersions[memo.id] != null || mutations[memo.id] != null) {
                    memo.version + 1
                } else {
                    memo.version
                },
                updatedAt = now,
            )
            mutations[rewritten.id] = newPendingMemoMutation(rewritten)
            rewritten
        }
        if (!any) {
            return
        }
        persistMemoCloudState(
            currentData.copy(memos = memos),
            cloudVersions,
            mutations,
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
        val editor = prefs.edit()
        securePrefs.putString(
            editor,
            KEY_DATA,
            SillageExportCodec.toLocalJson(normalized),
        )
        securePrefs.putString(
            editor,
            KEY_CLOUD_MEMO_VERSIONS,
            cloudMemoVersionsJson(cloudVersions),
        )
        securePrefs.putString(
            editor,
            KEY_PENDING_MEMO_MUTATIONS,
            pendingMemoMutationsJson(pendingMutations),
        ).apply()
    }

    fun searchMemos(query: String): List<Memo> {
        val normalized = query.trim()
        if (normalized.isBlank()) {
            return emptyList()
        }
        return loadData().memos.filter {
            it.deletedAt == null && it.content.contains(normalized, ignoreCase = true)
        }
    }

    fun getMemo(id: String): MemoDetail {
        val data = loadData()
        val memo = data.memos.find { it.id == id } ?: throw ApiException("记录不存在")
        return MemoDetail(
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
        )
        replaceMemo(deleted)
        return deleted
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

    fun mergeFromServer(data: SillageExportData) {
        val normalized = data.normalizedForLocalStorage()
        val currentData = loadData()
        val mergedMemos = mergePulledCloudMemos(
            localMemos = currentData.memos,
            pulledMemos = normalized.memos,
            cloudVersions = cloudMemoVersions(),
            pendingMutations = pendingMemoMutations(),
        )
        val mergedData = mergeData(currentData, normalized).copy(memos = mergedMemos.memos)
        val editor = prefs.edit()
        securePrefs.putString(
            editor,
            KEY_DATA,
            SillageExportCodec.toLocalJson(mergedData),
        )
        securePrefs.putString(
            editor,
            KEY_CLOUD_MEMO_VERSIONS,
            cloudMemoVersionsJson(mergedMemos.cloudVersions),
        )
        securePrefs.putString(
            editor,
            KEY_PENDING_MEMO_MUTATIONS,
            pendingMemoMutationsJson(mergedMemos.pendingMutations),
        ).apply()
    }

    private fun replaceMemo(memo: Memo) {
        saveLocalMemoMutation(memo, append = false)
    }

    private fun saveLocalMemoMutation(memo: Memo, append: Boolean) {
        val currentData = loadData()
        val memos = if (append) {
            currentData.memos + memo
        } else {
            currentData.memos.map { if (it.id == memo.id) memo else it }
        }
        val mutations = pendingMemoMutations().toMutableMap()
        mutations[memo.id] = newPendingMemoMutation(memo)
        val editor = prefs.edit()
        securePrefs.putString(
            editor,
            KEY_DATA,
            SillageExportCodec.toLocalJson(currentData.copy(memos = memos)),
        )
        securePrefs.putString(
            editor,
            KEY_PENDING_MEMO_MUTATIONS,
            pendingMemoMutationsJson(mutations),
        ).apply()
    }

    private fun loadData(): SillageExportData {
        return when (val stored = securePrefs.readString(KEY_DATA)) {
            is SecureReadResult.Missing -> emptyData()
            is SecureReadResult.Unreadable -> throw corruptLocalData(stored.rawPayload)
            is SecureReadResult.Value -> runCatching { SillageExportCodec.fromJson(stored.value) }
                .getOrElse { throw corruptLocalData(stored.value) }
        }
    }

    /**
     * Local data exists but cannot be decrypted or parsed. Preserve the raw
     * payload once and fail loudly so covering writes cannot wipe the diary.
     */
    private fun corruptLocalData(rawPayload: String): ApiException {
        if (!prefs.contains(KEY_DATA_CORRUPT_BACKUP)) {
            prefs.edit().putString(KEY_DATA_CORRUPT_BACKUP, rawPayload).apply()
        }
        return ApiException(LOCAL_DATA_CORRUPT_MESSAGE)
    }

    private fun updateData(transform: (SillageExportData) -> SillageExportData) {
        saveData(transform(loadData()))
    }

    private fun saveData(data: SillageExportData) {
        securePrefs.putString(
            prefs.edit(),
            KEY_DATA,
            SillageExportCodec.toLocalJson(data.normalizedForLocalStorage()),
        ).apply()
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
        val raw = securePrefs.getString(KEY_CLOUD_MEMO_VERSIONS, "{}") ?: "{}"
        val body = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        return buildMap {
            body.keys().forEach { id ->
                put(id, body.optLong(id))
            }
        }
    }

    private fun pendingLocalAttachments(): Map<String, PendingLocalAttachment> {
        val raw = when (val stored = securePrefs.readString(KEY_PENDING_LOCAL_ATTACHMENTS)) {
            is SecureReadResult.Missing -> return emptyMap()
            is SecureReadResult.Unreadable -> return emptyMap()
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
        }.getOrDefault(emptyMap())
    }

    private fun savePendingLocalAttachments(items: Map<String, PendingLocalAttachment>) {
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
        securePrefs.putString(prefs.edit(), KEY_PENDING_LOCAL_ATTACHMENTS, body.toString()).apply()
    }

    private fun pendingMemoMutations(): Map<String, PendingMemoMutation> {
        val raw = securePrefs.getString(KEY_PENDING_MEMO_MUTATIONS, "{}") ?: "{}"
        val body = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        return buildMap {
            body.keys().forEach { id ->
                val item = body.optJSONObject(id) ?: return@forEach
                val mutationId = item.optString("mutationId")
                val memoVersion = item.optLong("memoVersion", 0)
                val memoUpdatedAt = item.optString("memoUpdatedAt")
                if (mutationId.isNotBlank() && memoVersion > 0 && memoUpdatedAt.isNotBlank()) {
                    put(
                        id,
                        PendingMemoMutation(
                            mutationId = mutationId,
                            memoVersion = memoVersion,
                            memoUpdatedAt = memoUpdatedAt,
                        ),
                    )
                }
            }
        }
    }

    private fun savePendingMemoMutations(mutations: Map<String, PendingMemoMutation>) {
        securePrefs.putString(
            prefs.edit(),
            KEY_PENDING_MEMO_MUTATIONS,
            pendingMemoMutationsJson(mutations),
        ).apply()
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
): ConflictResolutionState {
    val versions = cloudVersions.toMutableMap()
    versions[localMemo.id] = serverVersion
    val mutations = pendingMutations.toMutableMap()
    mutations[localMemo.id] = PendingMemoMutation(
        mutationId = newMutationId(),
        memoVersion = localMemo.version,
        memoUpdatedAt = localMemo.updatedAt,
    )
    return ConflictResolutionState(
        memos = localMemos.map { if (it.id == localMemo.id) localMemo else it },
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
): PendingMemoMutation {
    return PendingMemoMutation(
        mutationId = mutationId,
        memoVersion = memo.version,
        memoUpdatedAt = memo.updatedAt,
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
                .put("memoUpdatedAt", mutation.memoUpdatedAt),
        )
    }
    return body.toString()
}

data class PendingMemoSync(
    val memo: Memo,
    val baseVersion: Long?,
    val mutationId: String,
)

data class AppliedMemoSync(
    val mutationId: String,
    val memo: Memo,
)

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
