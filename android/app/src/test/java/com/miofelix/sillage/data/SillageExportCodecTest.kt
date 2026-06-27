package com.miofelix.sillage.data

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
            memos = listOf(memo(content = "离线记录")),
            memoAI = listOf(memoAI(summary = "总结")),
            aiProfiles = listOf(aiProfile()),
            askConversations = listOf(askConversation()),
            askMessages = listOf(askMessage()),
        )

        val decoded = SillageExportCodec.fromJson(SillageExportCodec.toJson(data))

        assertEquals(SessionStore.THEME_DARK, decoded.themeMode)
        assertEquals("Calendar", decoded.memoViewMode)
        assertEquals("离线记录", decoded.memos.single().content)
        assertEquals("总结", decoded.memoAI.single().summary)
        assertEquals("默认", decoded.aiProfiles.single().name)
        assertEquals("会话", decoded.askConversations.single().title)
        assertEquals("回答", decoded.askMessages.single().content)
    }

    @Test
    fun localJsonKeepsApiKeyForOfflineAiUse() {
        val data = SillageExportData(
            formatVersion = SillageExportCodec.FORMAT_VERSION,
            exportedAt = "2026-06-27T00:00:00Z",
            themeMode = SessionStore.THEME_DARK,
            memoViewMode = "List",
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

    private fun memo(content: String = "content"): Memo {
        return Memo(
            id = "m1",
            content = content,
            entryDate = "2026-06-27",
            version = 1,
            createdAt = "2026-06-27T00:00:00Z",
            updatedAt = "2026-06-27T00:00:00Z",
            pinnedAt = null,
            archivedAt = null,
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
        hasApiKey: Boolean = false,
        keyUnavailable: Boolean = false,
        apiKey: String = "",
    ): AIProfileDraft {
        return AIProfileDraft(
            id = "p1",
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
            autoSummary = true,
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
            createdAt = "2026-06-27T00:00:00Z",
            updatedAt = "2026-06-27T00:00:00Z",
            deletedAt = null,
        )
    }
}
