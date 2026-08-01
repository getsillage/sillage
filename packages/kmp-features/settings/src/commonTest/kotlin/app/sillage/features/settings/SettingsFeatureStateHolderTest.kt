package app.sillage.features.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsFeatureStateHolderTest {
    @Test
    fun clearWorkspaceResetsProfilesLoadDiagnosticsAndAutoSummary() {
        val original = listOf(draft("p1", name = "One"))
        val state = SettingsFeatureStateHolder(
            profilesMutation = AIProfilesMutationStateHolder(
                profiles = original,
                saving = true,
                requestId = 3,
            ),
            autoSummary = AIAutoSummaryStateHolder(enabled = true, saving = true, requestId = 2),
            load = AISettingsLoadStateHolder(loading = true, errorMessage = "旧错误", requestId = 4),
            diagnostics = AIProfileDiagnosticsStateHolder(
                testingProfileKey = "p1",
                testResults = mapOf("p1" to "ok"),
                testRequestId = 5,
                modelsRequestId = 8,
            ),
        )

        val cleared = state.clearWorkspace()

        assertEquals(emptyList(), cleared.profiles)
        assertFalse(cleared.profilesSaving)
        assertEquals(4, cleared.profilesRequestId)
        assertFalse(cleared.autoSummaryEnabled)
        assertFalse(cleared.autoSummarySaving)
        assertEquals(3, cleared.autoSummaryRequestId)
        assertFalse(cleared.loading)
        assertEquals(null, cleared.loadErrorMessage)
        assertEquals(5, cleared.load.requestId)
        assertEquals("", cleared.testingProfileKey)
        assertTrue(cleared.testResults.isEmpty())
        assertEquals(6, cleared.diagnostics.testRequestId)
        assertEquals(9, cleared.diagnostics.modelsRequestId)
    }

    @Test
    fun clearWorkspaceCanSeedOfflineSnapshot() {
        val local = listOf(draft("local", name = "Local"))
        val state = SettingsFeatureStateHolder(
            profilesMutation = AIProfilesMutationStateHolder(profiles = listOf(draft("old"))),
            autoSummary = AIAutoSummaryStateHolder(enabled = false),
        )

        val cleared = state.clearWorkspace(
            profiles = local,
            autoSummaryEnabled = true,
        )

        assertEquals(local, cleared.profiles)
        assertTrue(cleared.autoSummaryEnabled)
        assertFalse(cleared.loading)
    }

    @Test
    fun diagnosticsTransitionsPreserveOtherSettingsOwnership() {
        val state = SettingsFeatureStateHolder(
            profilesMutation = AIProfilesMutationStateHolder(
                profiles = listOf(draft("p1")),
            ),
            autoSummary = AIAutoSummaryStateHolder(enabled = true),
            load = AISettingsLoadStateHolder(loading = true),
            diagnostics = AIProfileDiagnosticsStateHolder(
                testResults = mapOf("p1" to "old"),
                modelResults = mapOf("p1" to listOf("model-a")),
            ),
        )

        val recorded = state.recordDiagnosticsFeedback("p2", "offline")
        val cleared = recorded.clearDiagnosticsResults()

        assertEquals("offline", recorded.testResults["p2"])
        assertEquals(listOf("model-a"), recorded.modelResults["p1"])
        assertTrue(cleared.testResults.isEmpty())
        assertTrue(cleared.modelResults.isEmpty())
        assertEquals(state.profiles, cleared.profiles)
        assertTrue(cleared.autoSummaryEnabled)
        assertTrue(cleared.loading)
    }

    @Test
    fun replaceProfilesPreservesOtherSettingsOwnership() {
        val replacement = listOf(draft("p2", name = "Two"))
        val state = SettingsFeatureStateHolder(
            profilesMutation = AIProfilesMutationStateHolder(
                profiles = listOf(draft("p1", name = "One")),
                requestId = 3,
            ),
            autoSummary = AIAutoSummaryStateHolder(enabled = true),
            load = AISettingsLoadStateHolder(loading = true, requestId = 4),
            diagnostics = AIProfileDiagnosticsStateHolder(
                testResults = mapOf("p1" to "ok"),
            ),
        )

        val replaced = state.replaceProfiles(replacement)

        assertEquals(replacement, replaced.profiles)
        assertEquals(3L, replaced.profilesRequestId)
        assertTrue(replaced.autoSummaryEnabled)
        assertTrue(replaced.loading)
        assertEquals(mapOf("p1" to "ok"), replaced.testResults)
    }

    @Test
    fun applyLoadedSnapshotReplacesEditableSettingsAndClearsDiagnostics() {
        val loaded = listOf(draft("p2", name = "Two"))
        val state = SettingsFeatureStateHolder(
            profilesMutation = AIProfilesMutationStateHolder(profiles = listOf(draft("old"))),
            autoSummary = AIAutoSummaryStateHolder(enabled = false),
            diagnostics = AIProfileDiagnosticsStateHolder(
                testResults = mapOf("old" to "stale"),
                modelResults = mapOf("old" to listOf("m1")),
            ),
        )

        val applied = state.applyLoadedSnapshot(
            profiles = loaded,
            autoSummaryEnabled = true,
        )

        assertEquals(loaded, applied.profiles)
        assertTrue(applied.autoSummaryEnabled)
        assertTrue(applied.testResults.isEmpty())
        assertTrue(applied.modelResults.isEmpty())
    }

    @Test
    fun applyImportedPreferencesInvalidatesProfilesWithoutResettingLoad() {
        val imported = listOf(draft("imp", name = "Imported"))
        val state = SettingsFeatureStateHolder(
            profilesMutation = AIProfilesMutationStateHolder(profiles = listOf(draft("old")), requestId = 2),
            autoSummary = AIAutoSummaryStateHolder(enabled = false),
            load = AISettingsLoadStateHolder(loading = true, requestId = 7),
        )

        val applied = state.applyImportedPreferences(
            profiles = imported,
            autoSummaryEnabled = true,
        )

        assertEquals(imported, applied.profiles)
        assertEquals(3, applied.profilesRequestId)
        assertTrue(applied.autoSummaryEnabled)
        assertTrue(applied.loading)
        assertEquals(7, applied.load.requestId)
    }

    private fun draft(
        key: String,
        name: String = key,
    ): AIProfileDraft {
        return AIProfileDraft(
            id = key,
            draftKey = key,
            name = name,
            provider = "openai",
            baseUrl = "https://example.com",
            model = "model",
            temperature = 0.2,
            maxTokens = 1024,
            enabled = true,
            active = true,
            apiKeyInput = "",
            temperatureInput = "0.2",
            maxTokensInput = "1024",
        )
    }
}
