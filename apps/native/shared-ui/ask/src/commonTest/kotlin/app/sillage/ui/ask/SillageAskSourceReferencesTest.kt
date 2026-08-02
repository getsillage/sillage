package app.sillage.ui.ask

import app.sillage.core.domain.ask.AskSourceRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SillageAskSourceReferencesTest {
    @Test
    fun collapsedPresentationKeepsCountWithoutRows() {
        val presentation = sillageAskSourceReferencesPresentation(
            sources = listOf(source("first"), source("second")),
            enabled = true,
            expanded = false,
        )

        assertEquals(2, presentation.totalSources)
        assertTrue(presentation.visibleSources.isEmpty())
    }

    @Test
    fun expandedPresentationShowsAtMostFiveSources() {
        val presentation = sillageAskSourceReferencesPresentation(
            sources = (1..7).map { source("memo-$it") },
            enabled = true,
            expanded = true,
        )

        assertEquals(7, presentation.totalSources)
        assertEquals(
            listOf("memo-1", "memo-2", "memo-3", "memo-4", "memo-5"),
            presentation.visibleSources.map { it.source.memoId },
        )
        assertTrue(presentation.visibleSources.all { it.enabled })
    }

    @Test
    fun blankMemoIdDisablesOnlyThatSource() {
        val presentation = sillageAskSourceReferencesPresentation(
            sources = listOf(source("first"), source("   "), source("third")),
            enabled = true,
            expanded = true,
        )

        assertTrue(presentation.visibleSources[0].enabled)
        assertFalse(presentation.visibleSources[1].enabled)
        assertTrue(presentation.visibleSources[2].enabled)
    }

    @Test
    fun hostGateDisablesEverySource() {
        val presentation = sillageAskSourceReferencesPresentation(
            sources = listOf(source("first"), source("second")),
            enabled = false,
            expanded = true,
        )

        assertTrue(presentation.visibleSources.isNotEmpty())
        assertTrue(presentation.visibleSources.none { it.enabled })
    }

    private fun source(memoId: String): AskSourceRef = AskSourceRef(
        memoId = memoId,
        entryDate = "2026-08-02",
        excerpt = "Excerpt for $memoId",
        rank = 0,
    )
}
