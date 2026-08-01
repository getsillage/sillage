package app.sillage.data

import app.sillage.core.domain.records.Memo
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDataStoreLifecycleTest {
    @Test
    fun localDeleteRestoreAndPurgePreserveSyncIntentAndScrubPrivateData() {
        val store = LocalDataStore(InMemoryLocalStateStorage())
        val created = store.createMemo("仅保存在设备的正文", "2026-07-30")
        store.saveMemoAI(memoAI(created.id))

        val deleted = store.deleteMemo(created)
        assertTrue(deleted.deletedAt != null)
        assertEquals(listOf(deleted.id), store.searchMemos("仅保存在", MemoListFilter.Deleted).map(Memo::id))

        val restored = store.restoreMemo(deleted)
        val pendingCreate = store.pendingCloudMemos().single()
        assertEquals(restored.id, pendingCreate.memo.id)
        assertEquals("create", pendingCreate.action)
        assertNull(pendingCreate.baseVersion)

        val purged = store.purgeMemo(store.deleteMemo(restored))
        assertEquals("", purged.content)
        assertEquals("1970-01-01", purged.entryDate)
        assertNotNull(purged.deletedAt)
        assertNotNull(purged.purgedAt)
        assertTrue(store.exportData().memoAI.none { it.memoId == purged.id })
        assertTrue(store.pendingCloudMemos().isEmpty())
        MemoListFilter.entries.forEach { filter ->
            assertFalse(purged.matchesListFilter(filter))
        }
    }

    @Test
    fun purgeOfCloudMemoKeepsBaseVersionAndLifecycleAction() {
        val store = LocalDataStore(InMemoryLocalStateStorage())
        val created = store.createMemo("已经存在于服务端", "2026-07-30")
        store.markCloudSynced(listOf(created))

        val purged = store.purgeMemo(store.deleteMemo(created))
        val pending = store.pendingCloudMemos().single()

        assertEquals(purged.id, pending.memo.id)
        assertEquals(created.version, pending.baseVersion)
        assertEquals("purge", pending.action)
    }

    @Test
    fun sharedPendingAttachmentIsRemovedOnlyAfterLastLiveReferenceIsPurged() {
        val store = LocalDataStore(InMemoryLocalStateStorage())
        val path = Files.createTempFile("sillage-lifecycle-", ".txt")
        try {
            val attachment = PendingLocalAttachment(
                id = "shared",
                filename = "shared.txt",
                contentType = "text/plain",
                absolutePath = path.toString(),
                size = 4,
            )
            store.addPendingLocalAttachment(attachment)
            val markdown = localAttachmentMarkdown(attachment)
            val first = store.createMemo("第一条$markdown", "2026-07-30")
            val second = store.createMemo("第二条$markdown", "2026-07-30")

            store.purgeMemo(store.deleteMemo(first))
            assertTrue(Files.exists(path))
            assertNotNull(store.getPendingLocalAttachment(attachment.id))

            store.purgeMemo(store.deleteMemo(second))
            assertFalse(Files.exists(path))
            assertNull(store.getPendingLocalAttachment(attachment.id))
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun pulledPurgedTombstoneRemovesPreviouslyStoredMemoAi() {
        val store = LocalDataStore(InMemoryLocalStateStorage())
        val memo = memo(id = "remote", version = 1)
        store.mergeWith(exportData(memos = listOf(memo), memoAI = listOf(memoAI(memo.id))))

        store.mergeFromServer(
            exportData(
                memos = listOf(
                    memo.copy(
                        content = "",
                        entryDate = "1970-01-01",
                        version = 3,
                        updatedAt = "2026-07-30T02:00:00Z",
                        deletedAt = "2026-07-30T01:00:00Z",
                        purgedAt = "2026-07-30T02:00:00Z",
                    ),
                ),
                memoAI = emptyList(),
            ),
        )

        assertTrue(store.exportData().memoAI.none { it.memoId == memo.id })
        assertNotNull(store.getMemoOrNull(memo.id)?.purgedAt)
    }

    private fun exportData(memos: List<Memo>, memoAI: List<MemoAI>): SillageExportData {
        return SillageExportData(
            formatVersion = SillageExportCodec.FORMAT_VERSION,
            exportedAt = "2026-07-30T00:00:00Z",
            themeMode = "",
            memoViewMode = "",
            autoSummary = false,
            memos = memos,
            memoAI = memoAI,
            aiProfiles = emptyList(),
            askConversations = emptyList(),
            askMessages = emptyList(),
        )
    }

    private fun memo(id: String, version: Long): Memo {
        return Memo(
            id = id,
            content = "服务端正文",
            entryDate = "2026-07-30",
            version = version,
            createdAt = "2026-07-30T00:00:00Z",
            updatedAt = "2026-07-30T00:00:00Z",
            favoritedAt = null,
            archivedAt = null,
            deletedAt = null,
        )
    }

    private fun memoAI(memoId: String): MemoAI {
        return MemoAI(
            memoId = memoId,
            summary = "不应保留的摘要",
            sentiment = null,
            provider = "openai",
            model = "model",
            profileId = "profile",
            promptVersion = "v1",
            sourceMemoIds = memoId,
            status = "complete",
            errorCode = null,
            startedAt = null,
            finishedAt = null,
            inputTokens = 1,
            outputTokens = 1,
            totalTokens = 2,
            createdAt = "2026-07-30T00:00:00Z",
            updatedAt = "2026-07-30T00:00:00Z",
        )
    }

    private class InMemoryLocalStateStorage : LocalStateStorage {
        private val values = mutableMapOf<String, String>()

        override fun readString(key: String): SecureReadResult =
            values[key]?.let(SecureReadResult::Value) ?: SecureReadResult.Missing

        override fun contains(key: String): Boolean = values.containsKey(key)

        override fun putString(key: String, value: String) {
            values[key] = value
        }

        override fun putStrings(values: Map<String, String>) {
            this.values.putAll(values)
        }
    }
}
