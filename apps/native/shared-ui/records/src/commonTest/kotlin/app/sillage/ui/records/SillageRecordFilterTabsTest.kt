package app.sillage.ui.records

import app.sillage.features.records.MemoListFilter
import app.sillage.features.records.RecordsBrowseStateHolder
import app.sillage.features.records.RecordsFeatureStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SillageRecordFilterTabsTest {
    @Test
    fun presentationReadsSelectedFilterFromFeatureAggregate() {
        val presentation = sillageRecordFilterPresentation(
            state = RecordsFeatureStateHolder(
                browse = RecordsBrowseStateHolder(
                    filter = MemoListFilter.Favorited,
                    calendarYear = 2026,
                    calendarMonth = 8,
                ),
            ),
            strings = SillageRecordFilterStrings(
                unarchived = "Current",
                archived = "Archived",
                favorited = "Favorites",
                deleted = "Recently deleted",
            ),
        )

        assertEquals(MemoListFilter.entries, presentation.options.map { it.filter })
        assertEquals(
            listOf("Current", "Archived", "Favorites", "Recently deleted"),
            presentation.options.map { it.label },
        )
        assertFalse(presentation.options[0].selected)
        assertTrue(presentation.options[2].selected)
    }
}
