package app.sillage.ui.settings

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import app.sillage.features.settings.AIProfileDiagnosticsStateHolder
import app.sillage.features.settings.AIProfileDraft
import app.sillage.features.settings.AIProfilesMutationStateHolder
import app.sillage.features.settings.SettingsFeatureStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SillageAIProfileSummaryCardTest {
    private val strings = SillageAIProfileSummaryStrings(
        unnamedProfile = "Unnamed profile",
        anthropicCompatible = "Anthropic-compatible",
        openAICompatible = "OpenAI-compatible",
        defaultProfile = "Default",
        modelUnset = "Model not set",
        keyPresent = "API key configured",
        keyMissing = "API key missing",
        keyError = "Key unavailable",
        configure = "Configure",
        currentDefault = "Current default",
        setDefault = "Set default",
    )

    @Test
    fun presentationReadsProfileAndDiagnosticsFromFeatureAggregate() {
        val profile = AIProfileDraft(
            id = "profile-1",
            name = "Primary",
            provider = "anthropic",
            model = "claude-test",
            active = true,
            hasApiKey = true,
        )
        val state = SettingsFeatureStateHolder(
            profilesMutation = AIProfilesMutationStateHolder(profiles = listOf(profile)),
            diagnostics = AIProfileDiagnosticsStateHolder(
                testResults = mapOf("profile-1" to "Connection succeeded"),
            ),
        )

        val presentation = sillageAIProfileSummaryPresentation(
            state = state,
            profileIndex = 0,
            strings = strings,
            editingBlocked = false,
            mutationBlocked = false,
        )

        assertEquals("Primary", presentation.name)
        assertEquals("Anthropic-compatible", presentation.providerLabel)
        assertEquals("claude-test", presentation.model)
        assertEquals("API key configured", presentation.keyStatus)
        assertEquals("Connection succeeded", presentation.testResult)
        assertTrue(presentation.active)
        assertTrue(presentation.configureEnabled)
        assertFalse(presentation.setDefaultEnabled)
        assertEquals("Current default", presentation.setDefaultLabel)
    }

    @Test
    fun presentationAppliesFallbacksAndMutationLocks() {
        val state = SettingsFeatureStateHolder(
            profilesMutation = AIProfilesMutationStateHolder(
                profiles = listOf(
                    AIProfileDraft(
                        draftKey = "draft-1",
                        provider = "openai",
                        keyUnavailable = true,
                    ),
                ),
            ),
        )

        val presentation = sillageAIProfileSummaryPresentation(
            state = state,
            profileIndex = 0,
            strings = strings,
            editingBlocked = true,
            mutationBlocked = true,
        )

        assertEquals("Unnamed profile", presentation.name)
        assertEquals("OpenAI-compatible", presentation.providerLabel)
        assertEquals("Model not set", presentation.model)
        assertEquals("API key missing", presentation.keyStatus)
        assertTrue(presentation.keyUnavailable)
        assertFalse(presentation.configureEnabled)
        assertFalse(presentation.setDefaultEnabled)
        assertEquals("Set default", presentation.setDefaultLabel)
    }

    @Test
    fun colorsUseSemanticSelectedAndIdleTokens() {
        val colorScheme = lightColorScheme(
            primary = Color(0xFF6750A4),
            surfaceContainerHigh = Color(0xFFECE6F0),
            surfaceContainerLow = Color(0xFFF7F2FA),
            outlineVariant = Color(0xFFCAC4D0),
        )

        val selected = sillageAIProfileSummaryColors(true, colorScheme)
        val idle = sillageAIProfileSummaryColors(false, colorScheme)

        assertEquals(colorScheme.surfaceContainerHigh, selected.container)
        assertEquals(colorScheme.primary, selected.border)
        assertEquals(colorScheme.surfaceContainerLow, idle.container)
        assertEquals(colorScheme.outlineVariant, idle.border)
    }
}
