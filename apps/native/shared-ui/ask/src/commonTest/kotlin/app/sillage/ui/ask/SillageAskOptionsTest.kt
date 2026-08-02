package app.sillage.ui.ask

import app.sillage.features.ask.AskComposerStateHolder
import app.sillage.features.ask.AskFeatureStateHolder
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SillageAskOptionsTest {
    @Test
    fun defaultComposerSelectsThirtyDaysAndRecords() {
        val presentation = sillageAskOptionsPresentation(AskFeatureStateHolder())

        assertFalse(presentation.recentSevenDaysSelected)
        assertTrue(presentation.recentThirtyDaysSelected)
        assertFalse(presentation.allTimeSelected)
        assertTrue(presentation.recordsSelected)
        assertFalse(presentation.summariesSelected)
    }

    @Test
    fun customComposerSelectsAllTimeAndSummaries() {
        val presentation = sillageAskOptionsPresentation(
            AskFeatureStateHolder(
                composer = AskComposerStateHolder(
                    contextScope = "all",
                    sourceKind = "summaries",
                ),
            ),
        )

        assertFalse(presentation.recentSevenDaysSelected)
        assertFalse(presentation.recentThirtyDaysSelected)
        assertTrue(presentation.allTimeSelected)
        assertFalse(presentation.recordsSelected)
        assertTrue(presentation.summariesSelected)
    }

    @Test
    fun unknownValuesDoNotInventASelection() {
        val presentation = sillageAskOptionsPresentation(
            AskFeatureStateHolder(
                composer = AskComposerStateHolder(
                    contextScope = "custom",
                    sourceKind = "favorites",
                ),
            ),
        )

        assertFalse(presentation.recentSevenDaysSelected)
        assertFalse(presentation.recentThirtyDaysSelected)
        assertFalse(presentation.allTimeSelected)
        assertFalse(presentation.recordsSelected)
        assertFalse(presentation.summariesSelected)
    }
}
