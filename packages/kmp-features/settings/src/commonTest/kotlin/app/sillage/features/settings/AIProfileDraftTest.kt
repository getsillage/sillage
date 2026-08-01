package app.sillage.features.settings

import app.sillage.core.domain.settings.AIProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AIProfileDraftTest {
    @Test
    fun domainProfileBecomesSecretFreeEditableState() {
        val draft = profile(
            id = "profile-1",
            temperature = 0.7,
            maxTokens = 2048,
            hasApiKey = true,
        ).toDraft()

        assertEquals("profile-1", draft.id)
        assertEquals("0.7", draft.temperatureInput)
        assertEquals("2048", draft.maxTokensInput)
        assertEquals("", draft.apiKeyInput)
        assertTrue(draft.hasApiKey)
    }

    @Test
    fun blankNameValidationAcceptsEmptyListAndFindsWhitespace() {
        assertNull(firstBlankAIProfileNameIndex(emptyList()))
        assertEquals(
            1,
            firstBlankAIProfileNameIndex(
                listOf(
                    AIProfileDraft(name = "Primary"),
                    AIProfileDraft(name = "  "),
                ),
            ),
        )
    }

    @Test
    fun saveNormalizationSelectsOneActiveEnabledProfile() {
        val normalized = normalizeAIProfilesForSave(
            listOf(
                AIProfileDraft(id = "a", enabled = false),
                AIProfileDraft(id = "b", enabled = false, active = true),
                AIProfileDraft(id = "c", enabled = false, active = true),
            ),
        )

        assertTrue(normalized.all { it.enabled })
        assertEquals(listOf(false, true, false), normalized.map { it.active })
        assertEquals(emptyList(), normalizeAIProfilesForSave(emptyList()))
    }

    @Test
    fun savedProfilesKeepSubmittedAndExistingSecretsByIdentity() {
        val merged = mergeSavedAIProfilesForLocalStorage(
            currentProfiles = listOf(
                AIProfileDraft(id = "a", apiKeyInput = " old-a "),
                AIProfileDraft(id = "b", apiKeyInput = " old-b "),
            ),
            remoteProfiles = listOf(
                AIProfileDraft(id = "b"),
                AIProfileDraft(id = "a"),
            ),
            submittedProfiles = listOf(
                AIProfileDraft(id = "a", apiKeyInput = " new-a "),
                AIProfileDraft(id = "b"),
            ),
        )

        assertEquals("old-b", merged[0].apiKeyInput)
        assertEquals("new-a", merged[1].apiKeyInput)
        assertTrue(merged.all { it.hasApiKey })
        assertTrue(merged.none { it.keyUnavailable })
    }

    @Test
    fun positionalSecretFallbackOnlyAppliesToUnsavedDraft() {
        val merged = mergeSavedAIProfilesForLocalStorage(
            currentProfiles = emptyList(),
            remoteProfiles = listOf(
                AIProfileDraft(id = "server-created"),
                AIProfileDraft(id = "stable"),
            ),
            submittedProfiles = listOf(
                AIProfileDraft(apiKeyInput = " new-key "),
                AIProfileDraft(id = "different", apiKeyInput = "wrong-key"),
            ),
        )

        assertEquals("new-key", merged[0].apiKeyInput)
        assertTrue(merged[0].hasApiKey)
        assertEquals("", merged[1].apiKeyInput)
        assertFalse(merged[1].hasApiKey)
    }

    private fun profile(
        id: String,
        temperature: Double,
        maxTokens: Long,
        hasApiKey: Boolean,
    ): AIProfile {
        return AIProfile(
            id = id,
            name = "Primary",
            provider = "anthropic",
            baseUrl = "https://example.test",
            model = "model",
            temperature = temperature,
            maxTokens = maxTokens,
            enabled = true,
            active = true,
            hasApiKey = hasApiKey,
            keyUnavailable = false,
            autoSummary = true,
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z",
        )
    }
}
