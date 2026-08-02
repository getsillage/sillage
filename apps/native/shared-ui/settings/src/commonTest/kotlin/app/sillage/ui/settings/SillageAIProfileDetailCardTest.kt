package app.sillage.ui.settings

import app.sillage.features.settings.AIProfileDiagnosticsStateHolder
import app.sillage.features.settings.AIProfileDraft
import app.sillage.features.settings.AIProfilesMutationStateHolder
import app.sillage.features.settings.SettingsFeatureStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SillageAIProfileDetailCardTest {
    private val strings = SillageAIProfileDetailStrings(
        title = "Profile details",
        supporting = "Save to apply changes",
        collapse = "Collapse",
        nameLabel = "Name",
        providerLabel = "Provider",
        anthropicCompatible = "Anthropic-compatible",
        openAICompatible = "OpenAI-compatible",
        baseUrlLabel = "Base URL",
        modelLabel = "Model",
        modelsLoading = "Loading",
        getModels = "Get models",
        temperatureLabel = "Temperature",
        maxTokensLabel = "Max tokens",
        apiKeyLabel = "API key",
        keepApiKey = "Leave blank to keep the current key",
        apiKeyNotConfigured = "Not configured",
        keyDecryptError = "Key unavailable",
        testing = "Testing",
        testConnection = "Test connection",
        confirmDelete = "Confirm delete",
        delete = "Delete",
    )

    @Test
    fun presentationReadsDraftAndDiagnosticsFromFeatureAggregate() {
        val profile = AIProfileDraft(
            id = "profile-1",
            name = "Primary",
            provider = "openai",
            baseUrl = "https://example.test",
            model = "model-1",
            hasApiKey = true,
            temperatureInput = "0.7",
            maxTokensInput = "2048",
        )
        val state = SettingsFeatureStateHolder(
            profilesMutation = AIProfilesMutationStateHolder(profiles = listOf(profile)),
            diagnostics = AIProfileDiagnosticsStateHolder(
                modelResults = mapOf("profile-1" to listOf("model-1", "model-2")),
                testResults = mapOf("profile-1" to "Connection succeeded"),
            ),
        )

        val presentation = sillageAIProfileDetailPresentation(
            state = state,
            profileIndex = 0,
            strings = strings,
            editingBlocked = false,
            mutationBlocked = false,
        )

        assertEquals("profile-1", presentation.profileKey)
        assertEquals("Primary", presentation.name)
        assertEquals("OpenAI-compatible", presentation.providerLabel)
        assertEquals("https://example.test", presentation.baseUrl)
        assertEquals(listOf("model-1", "model-2"), presentation.modelOptions)
        assertEquals("0.7", presentation.temperatureInput)
        assertEquals("2048", presentation.maxTokensInput)
        assertEquals("Leave blank to keep the current key", presentation.apiKeyPlaceholder)
        assertEquals("Connection succeeded", presentation.testResult)
        assertTrue(presentation.controlsEnabled)
        assertTrue(presentation.deleteEnabled)
    }

    @Test
    fun activeDiagnosticAndMutationStateLockControls() {
        val profile = AIProfileDraft(draftKey = "draft-1")
        val testing = sillageAIProfileDetailPresentation(
            state = SettingsFeatureStateHolder(
                profilesMutation = AIProfilesMutationStateHolder(profiles = listOf(profile)),
                diagnostics = AIProfileDiagnosticsStateHolder(testingProfileKey = "draft-1"),
            ),
            profileIndex = 0,
            strings = strings,
            editingBlocked = false,
            mutationBlocked = false,
        )
        val loadingModels = sillageAIProfileDetailPresentation(
            state = SettingsFeatureStateHolder(
                profilesMutation = AIProfilesMutationStateHolder(profiles = listOf(profile)),
                diagnostics = AIProfileDiagnosticsStateHolder(loadingModelsProfileKey = "draft-1"),
            ),
            profileIndex = 0,
            strings = strings,
            editingBlocked = false,
            mutationBlocked = false,
        )
        val mutationBlocked = sillageAIProfileDetailPresentation(
            state = SettingsFeatureStateHolder(
                profilesMutation = AIProfilesMutationStateHolder(profiles = listOf(profile)),
            ),
            profileIndex = 0,
            strings = strings,
            editingBlocked = false,
            mutationBlocked = true,
        )

        assertFalse(testing.controlsEnabled)
        assertEquals("Testing", testing.testAction)
        assertFalse(loadingModels.controlsEnabled)
        assertEquals("Loading", loadingModels.modelsAction)
        assertTrue(mutationBlocked.controlsEnabled)
        assertFalse(mutationBlocked.deleteEnabled)
        assertEquals("Not configured", mutationBlocked.apiKeyPlaceholder)
    }
}
