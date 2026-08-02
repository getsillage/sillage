package app.sillage.ui.records

import app.sillage.features.records.RecordsFeatureStateHolder
import app.sillage.features.records.RecordsSearchStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SillageRecordSearchBarTest {
    @Test
    fun presentationReadsQueryAndSearchingFromAggregate() {
        val presentation = sillageRecordSearchPresentation(
            RecordsFeatureStateHolder(
                search = RecordsSearchStateHolder(
                    query = "garden",
                    searching = true,
                ),
            ),
        )

        assertEquals("garden", presentation.query)
        assertTrue(presentation.searching)
        assertTrue(presentation.showClear)
        assertFalse(presentation.searchEnabled)
    }

    @Test
    fun presentationKeepsClearActionForPublishedEmptyResults() {
        val presentation = sillageRecordSearchPresentation(
            RecordsFeatureStateHolder(
                search = RecordsSearchStateHolder(results = emptyList()),
            ),
        )

        assertTrue(presentation.showClear)
        assertFalse(presentation.searchEnabled)
    }
}
