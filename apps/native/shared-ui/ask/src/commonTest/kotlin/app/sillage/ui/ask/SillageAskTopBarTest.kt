package app.sillage.ui.ask

import app.sillage.features.ask.AskFeatureStateHolder
import app.sillage.features.ask.AskLoadStateHolder
import app.sillage.features.ask.AskMemoSaveStateHolder
import app.sillage.features.ask.AskSourceNavigationStateHolder
import app.sillage.features.ask.AskStreamStateHolder
import app.sillage.features.ask.AskVariantStateHolder
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SillageAskTopBarTest {
    @Test
    fun idleStateEnablesControlsWithoutSavingIndicator() {
        val presentation = sillageAskTopBarPresentation(AskFeatureStateHolder())

        assertTrue(presentation.controlsEnabled)
        assertFalse(presentation.saving)
    }

    @Test
    fun everyConflictingRequestDisablesControls() {
        listOf(
            AskFeatureStateHolder(load = AskLoadStateHolder(loading = true)),
            AskFeatureStateHolder(stream = AskStreamStateHolder(sending = true)),
            AskFeatureStateHolder(variant = AskVariantStateHolder(loading = true)),
            AskFeatureStateHolder(
                sourceNavigation = AskSourceNavigationStateHolder(loading = true),
            ),
        ).forEach { state ->
            assertFalse(sillageAskTopBarPresentation(state).controlsEnabled)
        }
    }

    @Test
    fun memoSaveShowsProgressWithoutBlockingContextControls() {
        val presentation = sillageAskTopBarPresentation(
            AskFeatureStateHolder(
                memoSave = AskMemoSaveStateHolder(savingMessageId = "answer-id"),
            ),
        )

        assertTrue(presentation.controlsEnabled)
        assertTrue(presentation.saving)
    }
}
