package com.miofelix.sillage.data

import android.content.Context
import java.time.Instant
import java.util.UUID
import org.json.JSONObject

class LocalDataStore(context: Context) {
    private val prefs = context.getSharedPreferences("sillage.local_data", Context.MODE_PRIVATE)

    fun listMemos(): List<Memo> = loadData().memos

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

    fun exportData(themeMode: String, memoViewMode: String): SillageExportData {
        val data = loadData()
        return data.copy(
            exportedAt = now(),
            themeMode = themeMode,
            memoViewMode = memoViewMode,
        )
    }

    fun replaceWith(data: SillageExportData) {
        saveData(data.sanitizedForLocalImport())
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
        prefs.edit().putString(KEY_DATA, SillageExportCodec.toJson(data.sanitizedForLocalImport())).apply()
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

    companion object {
        private const val KEY_DATA = "data"
    }
}

data class SillageExportData(
    val formatVersion: Int,
    val exportedAt: String,
    val themeMode: String,
    val memoViewMode: String,
    val memos: List<Memo>,
    val memoAI: List<MemoAI>,
    val aiProfiles: List<AIProfile>,
    val askConversations: List<AskConversation>,
    val askMessages: List<AskMessage>,
)

object SillageExportCodec {
    const val FORMAT_VERSION = 1

    fun toJson(data: SillageExportData): String {
        val sanitized = data.sanitizedForLocalImport()
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
            .toString(2)
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
            aiProfiles = root.optJSONArray("aiProfiles").toListOrEmpty(::jsonToAIProfile),
            askConversations = root.optJSONArray("askConversations").toListOrEmpty(::jsonToAskConversation),
            askMessages = root.optJSONArray("askMessages").toListOrEmpty(::jsonToAskMessage),
        ).sanitizedForLocalImport()
    }
}
