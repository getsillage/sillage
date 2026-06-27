package app.sillage.data

import android.content.Context
import java.time.Instant
import java.util.UUID
import org.json.JSONObject

class LocalDataStore(context: Context) {
    private val prefs = context.getSharedPreferences("sillage.local_data", Context.MODE_PRIVATE)

    fun exportData(): SillageExportData = loadData()

    fun listMemos(): List<Memo> = loadData().memos

    fun pendingCloudMemos(): List<PendingMemoSync> {
        val cloudVersions = cloudMemoVersions()
        return loadData().memos.mapNotNull { memo ->
            val cloudVersion = cloudVersions[memo.id]
            when {
                cloudVersion == null && memo.deletedAt != null -> null
                cloudVersion == null -> PendingMemoSync(memo = memo, baseVersion = null)
                memo.version > cloudVersion -> PendingMemoSync(memo = memo, baseVersion = cloudVersion)
                else -> null
            }
        }
    }

    fun markCloudSynced(memos: List<Memo>) {
        if (memos.isEmpty()) {
            return
        }
        val versions = cloudMemoVersions().toMutableMap()
        memos.forEach { memo -> versions[memo.id] = memo.version }
        saveCloudMemoVersions(versions)
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
            pinnedAt = null,
            archivedAt = null,
            deletedAt = null,
        )
        updateData { it.copy(memos = it.memos + memo) }
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

    fun setMemoPinned(memo: Memo, pinned: Boolean): Memo {
        val updated = memo.copy(
            version = memo.version + 1,
            updatedAt = now(),
            pinnedAt = if (pinned) now() else null,
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

    fun saveAIProfiles(profiles: List<AIProfileDraft>): List<AIProfileDraft> {
        val now = now()
        var activeSeen = false
        val saved = profiles.map { profile ->
            val active = profile.active && !activeSeen
            if (active) {
                activeSeen = true
            }
            profile.copy(
                id = profile.id.ifBlank { UUID.randomUUID().toString() },
                active = active,
                hasApiKey = profile.hasApiKey || profile.apiKeyInput.isNotBlank(),
                keyUnavailable = false,
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
        return loadData().askConversations.sortedByDescending { it.updatedAt }
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
            title = "新会话",
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
                        title = if (conversation.title == "新会话") question.take(30) else conversation.title,
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
        saveData(mergeData(loadData(), normalized))
        markCloudSynced(normalized.memos)
    }

    private fun replaceMemo(memo: Memo) {
        updateData { data ->
            data.copy(memos = data.memos.map { if (it.id == memo.id) memo else it })
        }
    }

    private fun loadData(): SillageExportData {
        val raw = prefs.getString(KEY_DATA, null) ?: return emptyData()
        return runCatching { SillageExportCodec.fromJson(raw) }.getOrElse { emptyData() }
    }

    private fun updateData(transform: (SillageExportData) -> SillageExportData) {
        saveData(transform(loadData()))
    }

    private fun saveData(data: SillageExportData) {
        prefs.edit().putString(KEY_DATA, SillageExportCodec.toLocalJson(data.normalizedForLocalStorage())).apply()
    }

    private fun emptyData(): SillageExportData {
        return SillageExportData(
            formatVersion = SillageExportCodec.FORMAT_VERSION,
            exportedAt = "",
            themeMode = "",
            memoViewMode = "",
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
        private const val KEY_DATA = "data"
        private const val KEY_CLOUD_MEMO_VERSIONS = "cloud_memo_versions"
    }

    private fun cloudMemoVersions(): Map<String, Long> {
        val raw = prefs.getString(KEY_CLOUD_MEMO_VERSIONS, "{}") ?: "{}"
        val body = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        return buildMap {
            body.keys().forEach { id ->
                put(id, body.optLong(id))
            }
        }
    }

    private fun saveCloudMemoVersions(versions: Map<String, Long>) {
        val body = JSONObject()
        versions.forEach { (id, version) -> body.put(id, version) }
        prefs.edit().putString(KEY_CLOUD_MEMO_VERSIONS, body.toString()).apply()
    }
}

data class PendingMemoSync(
    val memo: Memo,
    val baseVersion: Long?,
)

data class SillageExportData(
    val formatVersion: Int,
    val exportedAt: String,
    val themeMode: String,
    val memoViewMode: String,
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
        return SillageExportData(
            formatVersion = version,
            exportedAt = root.optString("exportedAt"),
            themeMode = root.optString("themeMode"),
            memoViewMode = root.optString("memoViewMode"),
            memos = root.optJSONArray("memos").toListOrEmpty(::jsonToMemo),
            memoAI = root.optJSONArray("memoAI").toListOrEmpty(::jsonToMemoAI),
            aiProfiles = root.optJSONArray("aiProfiles").toListOrEmpty(::jsonToAIProfileDraft),
            askConversations = root.optJSONArray("askConversations").toListOrEmpty(::jsonToAskConversation),
            askMessages = root.optJSONArray("askMessages").toListOrEmpty(::jsonToAskMessage),
        ).normalizedForLocalStorage()
    }
}
