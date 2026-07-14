package app.sillage.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SillageExportCodecTest {
    @Test
    fun exportJsonExcludesConnectionAccountAndSecretFields() {
        val json = SillageExportCodec.toJson(
            SillageExportData(
                formatVersion = SillageExportCodec.FORMAT_VERSION,
                exportedAt = "2026-06-27T00:00:00Z",
                themeMode = SessionStore.THEME_DARK,
                memoViewMode = "Calendar",
                autoSummary = true,
                memos = listOf(memo()),
                memoAI = emptyList(),
                aiProfiles = listOf(aiProfile(hasApiKey = true, keyUnavailable = true)),
                askConversations = emptyList(),
                askMessages = emptyList(),
            ),
        )

        assertFalse(json.contains("base_url"))
        assertFalse(json.contains("access_token"))
        assertFalse(json.contains("cookies"))
        assertFalse(json.contains("username"))
        assertFalse(json.contains("password"))
        assertFalse(json.contains("apiKey"))
        assertFalse(json.contains("10.0.2.2"))
        assertFalse(json.contains("sk-test"))
        assertEquals(false, SillageExportCodec.fromJson(json).aiProfiles.single().hasApiKey)
        assertEquals(false, SillageExportCodec.fromJson(json).aiProfiles.single().keyUnavailable)
    }

    @Test
    fun exportJsonRoundTripsPortableData() {
        val data = SillageExportData(
            formatVersion = SillageExportCodec.FORMAT_VERSION,
            exportedAt = "2026-06-27T00:00:00Z",
            themeMode = SessionStore.THEME_DARK,
            memoViewMode = "Calendar",
            autoSummary = true,
            memos = listOf(memo(content = "离线记录")),
            memoAI = listOf(memoAI(summary = "总结")),
            aiProfiles = listOf(aiProfile()),
            askConversations = listOf(askConversation()),
            askMessages = listOf(askMessage()),
        )

        val decoded = SillageExportCodec.fromJson(SillageExportCodec.toJson(data))

        assertEquals(SessionStore.THEME_DARK, decoded.themeMode)
        assertEquals("Calendar", decoded.memoViewMode)
        assertEquals(true, decoded.autoSummary)
        assertEquals("离线记录", decoded.memos.single().content)
        assertEquals("总结", decoded.memoAI.single().summary)
        assertEquals("默认", decoded.aiProfiles.single().name)
        assertEquals("会话", decoded.askConversations.single().title)
        assertEquals("回答", decoded.askMessages.single().content)
        assertEquals("ask-answer-v2", decoded.askMessages.single().promptVersion)
    }

    @Test
    fun legacyAskMessageWithoutPromptVersionImportsAsEmptyString() {
        val legacy = askMessageToJson(askMessage()).apply { remove("promptVersion") }

        val decoded = jsonToAskMessage(legacy)

        assertEquals("", decoded.promptVersion)
    }

    @Test
    fun legacyPinnedMemoImportsAsFavoriteAndNewJsonUsesFavoritedAt() {
        val legacy = JSONObject()
            .put("id", "legacy")
            .put("content", "旧记录")
            .put("entryDate", "2026-06-27")
            .put("version", 2)
            .put("createdAt", "2026-06-27T00:00:00Z")
            .put("updatedAt", "2026-06-28T00:00:00Z")
            .put("pinnedAt", "2026-06-28T00:00:00Z")

        val decoded = jsonToMemo(legacy)
        val encoded = memoToJson(decoded)

        assertEquals("2026-06-28T00:00:00Z", decoded.favoritedAt)
        assertEquals("2026-06-28T00:00:00Z", encoded.getString("favoritedAt"))
        assertFalse(encoded.has("pinnedAt"))
    }

    @Test
    fun syncCreateAndUpdatePayloadsAlwaysIncludeFavoriteState() {
        val create = pendingMemoSyncToJson(
            PendingMemoSync(
                memo = memo(
                    favoritedAt = "2026-06-28T00:00:00Z",
                    archivedAt = "2026-06-28T00:00:00Z",
                ),
                baseVersion = null,
                mutationId = "mutation-create",
            ),
        )
        val update = pendingMemoSyncToJson(
            PendingMemoSync(
                memo = memo(),
                baseVersion = 3,
                mutationId = "mutation-update",
            ),
        )

        assertEquals("mutation-create", create.getString("mutationId"))
        assertEquals("create", create.getString("action"))
        assertEquals(true, create.getJSONObject("memo").getBoolean("favorited"))
        assertEquals(true, create.getJSONObject("memo").getBoolean("pinned"))
        assertEquals(true, create.getJSONObject("memo").getBoolean("archived"))
        assertEquals("mutation-update", update.getString("mutationId"))
        assertEquals("update", update.getString("action"))
        assertEquals(false, update.getJSONObject("memo").getBoolean("favorited"))
        assertEquals(false, update.getJSONObject("memo").getBoolean("pinned"))
        assertEquals(false, update.getJSONObject("memo").getBoolean("archived"))
        assertEquals(3L, update.getLong("baseVersion"))
    }

    @Test
    fun localJsonKeepsApiKeyForOfflineAiUse() {
        val data = SillageExportData(
            formatVersion = SillageExportCodec.FORMAT_VERSION,
            exportedAt = "2026-06-27T00:00:00Z",
            themeMode = SessionStore.THEME_DARK,
            memoViewMode = "List",
            autoSummary = false,
            memos = emptyList(),
            memoAI = emptyList(),
            aiProfiles = listOf(aiProfile(hasApiKey = true, apiKey = "sk-test")),
            askConversations = emptyList(),
            askMessages = emptyList(),
        )

        val decoded = SillageExportCodec.fromJson(SillageExportCodec.toLocalJson(data))

        assertEquals("sk-test", decoded.aiProfiles.single().apiKeyInput)
        assertEquals(true, decoded.aiProfiles.single().hasApiKey)
    }

    @Test
    fun localJsonDoesNotPersistAIProfileDraftKeys() {
        val data = SillageExportData(
            formatVersion = SillageExportCodec.FORMAT_VERSION,
            exportedAt = "2026-07-14T00:00:00Z",
            themeMode = SessionStore.THEME_DARK,
            memoViewMode = "List",
            autoSummary = false,
            memos = emptyList(),
            memoAI = emptyList(),
            aiProfiles = listOf(aiProfile(id = "").copy(draftKey = "draft-only-key")),
            askConversations = emptyList(),
            askMessages = emptyList(),
        )

        val encoded = SillageExportCodec.toLocalJson(data)
        val decoded = SillageExportCodec.fromJson(encoded)

        assertFalse(encoded.contains("draft-only-key"))
        assertEquals("", decoded.aiProfiles.single().draftKey)
    }

    @Test
    fun mergeSavedAIProfilesForLocalStorageKeepsExistingApiKeyWhenServerOmitsIt() {
        val current = listOf(aiProfile(id = "p1", hasApiKey = true, apiKey = "sk-local"))
        val remote = listOf(aiProfile(id = "p1", hasApiKey = true, apiKey = ""))
        val submitted = listOf(aiProfile(id = "p1", hasApiKey = true, apiKey = ""))

        val merged = mergeSavedAIProfilesForLocalStorage(
            currentProfiles = current,
            remoteProfiles = remote,
            submittedProfiles = submitted,
        )

        assertEquals("sk-local", merged.single().apiKeyInput)
        assertEquals(true, merged.single().hasApiKey)
    }

    @Test
    fun mergeSavedAIProfilesForLocalStoragePrefersSubmittedApiKey() {
        val current = listOf(aiProfile(id = "p1", hasApiKey = true, apiKey = "sk-old"))
        val remote = listOf(aiProfile(id = "p1", hasApiKey = true, apiKey = ""))
        val submitted = listOf(aiProfile(id = "p1", hasApiKey = true, apiKey = "sk-new"))

        val merged = mergeSavedAIProfilesForLocalStorage(
            currentProfiles = current,
            remoteProfiles = remote,
            submittedProfiles = submitted,
        )

        assertEquals("sk-new", merged.single().apiKeyInput)
        assertEquals(true, merged.single().hasApiKey)
    }

    @Test
    fun legacyProfileAutoSummaryMigratesToGlobalSetting() {
        val json = """
            {
              "formatVersion": 1,
              "exportedAt": "2026-06-27T00:00:00Z",
              "themeMode": "dark",
              "memoViewMode": "List",
              "memos": [],
              "memoAI": [],
              "aiProfiles": [
                {
                  "id": "p1",
                  "name": "默认",
                  "provider": "openai",
                  "baseUrl": "https://api.example.com",
                  "model": "model",
                  "temperature": 0.3,
                  "maxTokens": 1000,
                  "enabled": true,
                  "active": true,
                  "autoSummary": true
                }
              ],
              "askConversations": [],
              "askMessages": []
            }
        """.trimIndent()

        val decoded = SillageExportCodec.fromJson(json)

        assertEquals(true, decoded.autoSummary)
        assertEquals(true, decoded.autoSummaryDefined)
        assertEquals("默认", decoded.aiProfiles.single().name)
    }

    private fun memo(
        content: String = "content",
        favoritedAt: String? = null,
        archivedAt: String? = null,
    ): Memo {
        return Memo(
            id = "m1",
            content = content,
            entryDate = "2026-06-27",
            version = 1,
            createdAt = "2026-06-27T00:00:00Z",
            updatedAt = "2026-06-27T00:00:00Z",
            favoritedAt = favoritedAt,
            archivedAt = archivedAt,
            deletedAt = null,
        )
    }

    private fun memoAI(summary: String): MemoAI {
        return MemoAI(
            memoId = "m1",
            summary = summary,
            sentiment = null,
            provider = "openai",
            model = "model",
            profileId = "p1",
            promptVersion = "v1",
            sourceMemoIds = "m1",
            status = "complete",
            errorCode = null,
            startedAt = null,
            finishedAt = null,
            inputTokens = 1,
            outputTokens = 2,
            totalTokens = 3,
            createdAt = "2026-06-27T00:00:00Z",
            updatedAt = "2026-06-27T00:00:00Z",
        )
    }

    private fun aiProfile(
        id: String = "p1",
        hasApiKey: Boolean = false,
        keyUnavailable: Boolean = false,
        apiKey: String = "",
    ): AIProfileDraft {
        return AIProfileDraft(
            id = id,
            name = "默认",
            provider = "openai",
            baseUrl = "https://api.example.com",
            model = "model",
            temperature = 0.3,
            maxTokens = 1000,
            enabled = true,
            active = true,
            hasApiKey = hasApiKey,
            keyUnavailable = keyUnavailable,
            apiKeyInput = apiKey,
        )
    }

    private fun askConversation(): AskConversation {
        return AskConversation(
            id = "c1",
            title = "会话",
            status = "active",
            contextScope = "all",
            headMessageId = "a1",
            pinnedAt = null,
            archivedAt = null,
            createdAt = "2026-06-27T00:00:00Z",
            updatedAt = "2026-06-27T00:00:00Z",
            deletedAt = null,
        )
    }

    private fun askMessage(): AskMessage {
        return AskMessage(
            id = "a1",
            conversationId = "c1",
            role = "assistant",
            content = "回答",
            parentId = null,
            forkOfId = null,
            status = "complete",
            sourceRefs = listOf(AskSourceRef("m1", "2026-06-27", "来源", 1)),
            model = "model",
            promptVersion = "ask-answer-v2",
            createdAt = "2026-06-27T00:00:00Z",
            updatedAt = "2026-06-27T00:00:00Z",
            deletedAt = null,
        )
    }
}
