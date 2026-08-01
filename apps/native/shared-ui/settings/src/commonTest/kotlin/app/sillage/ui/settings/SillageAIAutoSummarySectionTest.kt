package app.sillage.ui.settings

import app.sillage.features.settings.AIAutoSummaryStateHolder
import app.sillage.features.settings.AIProfilesMutationStateHolder
import app.sillage.features.settings.SettingsFeatureStateHolder
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SillageAIAutoSummarySectionTest {
    private val strings = SillageAIAutoSummaryStrings(
        sectionTitle = "AI",
        title = "Automatic summary",
        supporting = "Summarize new records automatically.",
    )

    @Test
    fun presentationReadsPreferenceAndSaveLifecycleFromAggregate() {
        val enabled = sillageAIAutoSummaryPresentation(
            state = SettingsFeatureStateHolder(
                autoSummary = AIAutoSummaryStateHolder(enabled = true),
            ),
            strings = strings,
            operationBlocked = false,
        )
        val saving = sillageAIAutoSummaryPresentation(
            state = SettingsFeatureStateHolder(
                autoSummary = AIAutoSummaryStateHolder(enabled = true, saving = true),
            ),
            strings = strings,
            operationBlocked = false,
        )

        assertTrue(enabled.checked)
        assertTrue(enabled.enabled)
        assertTrue(saving.checked)
        assertFalse(saving.enabled)
    }

    @Test
    fun presentationCombinesProfileAndHostOperationGates() {
        val profilesSaving = sillageAIAutoSummaryPresentation(
            state = SettingsFeatureStateHolder(
                profilesMutation = AIProfilesMutationStateHolder(saving = true),
            ),
            strings = strings,
            operationBlocked = false,
        )
        val hostBlocked = sillageAIAutoSummaryPresentation(
            state = SettingsFeatureStateHolder(),
            strings = strings,
            operationBlocked = true,
        )

        assertFalse(profilesSaving.enabled)
        assertFalse(hostBlocked.enabled)
    }
}
