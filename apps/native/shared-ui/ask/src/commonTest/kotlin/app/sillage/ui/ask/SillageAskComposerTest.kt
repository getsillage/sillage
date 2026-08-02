package app.sillage.ui.ask

import app.sillage.features.ask.AskComposerStateHolder
import app.sillage.features.ask.AskFeatureStateHolder
import app.sillage.features.ask.AskLoadStateHolder
import app.sillage.features.ask.AskStreamStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SillageAskComposerTest {
    @Test
    fun defaultContextUsesThirtyDaysAndRecords() {
        val presentation = sillageAskContextPresentation(AskFeatureStateHolder(), strings)

        assertEquals("30 days", presentation.scopeLabel)
        assertEquals("Records", presentation.sourceLabel)
    }

    @Test
    fun explicitContextUsesSevenDaysAndSummaries() {
        val presentation = sillageAskContextPresentation(
            AskFeatureStateHolder(
                composer = AskComposerStateHolder(
                    contextScope = "recent_7_days",
                    sourceKind = "summaries",
                ),
            ),
            strings,
        )

        assertEquals("7 days", presentation.scopeLabel)
        assertEquals("Summaries", presentation.sourceLabel)
    }

    @Test
    fun unknownContextFallsBackToThirtyDaysAndRecords() {
        val presentation = sillageAskContextPresentation(
            AskFeatureStateHolder(
                composer = AskComposerStateHolder(
                    contextScope = "custom",
                    sourceKind = "favorites",
                ),
            ),
            strings,
        )

        assertEquals("30 days", presentation.scopeLabel)
        assertEquals("Records", presentation.sourceLabel)
    }

    @Test
    fun nonBlankQuestionEnablesSendAndCountsTrimmedCharacters() {
        val presentation = sillageAskComposerPresentation(
            AskFeatureStateHolder(
                composer = AskComposerStateHolder(question = "  hello  "),
            ),
            strings,
        )

        assertEquals(5, presentation.trimmedCharacterCount)
        assertTrue(presentation.sendEnabled)
        assertFalse(presentation.showStop)
    }

    @Test
    fun loadingDisablesSendWithoutReplacingItWithStop() {
        val presentation = sillageAskComposerPresentation(
            AskFeatureStateHolder(
                composer = AskComposerStateHolder(question = "hello"),
                load = AskLoadStateHolder(loading = true),
            ),
            strings,
        )

        assertFalse(presentation.sendEnabled)
        assertFalse(presentation.showStop)
    }

    @Test
    fun sendingShowsStopAndDisablesSend() {
        val presentation = sillageAskComposerPresentation(
            AskFeatureStateHolder(
                composer = AskComposerStateHolder(question = "hello"),
                stream = AskStreamStateHolder(sending = true),
            ),
            strings,
        )

        assertFalse(presentation.sendEnabled)
        assertTrue(presentation.showStop)
    }

    private val strings = SillageAskContextStrings(
        recentSevenDays = "7 days",
        recentThirtyDays = "30 days",
        allTime = "All time",
        recordsSource = "Records",
        summariesSource = "Summaries",
    )
}
