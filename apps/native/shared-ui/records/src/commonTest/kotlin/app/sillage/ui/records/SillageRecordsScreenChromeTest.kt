package app.sillage.ui.records

import app.sillage.features.records.MemoListFilter
import app.sillage.features.records.MemoViewMode
import app.sillage.features.records.RecordsBrowseStateHolder
import app.sillage.features.records.RecordsFeatureStateHolder
import app.sillage.features.records.RecordsRefreshStateHolder
import app.sillage.features.records.RecordsRefreshStatus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SillageRecordsScreenChromeTest {
    @Test
    fun defaultRecordsViewEnablesRefreshAndNewRecord() {
        val presentation = sillageRecordsScreenChromePresentation(
            state = RecordsFeatureStateHolder(),
            hostActionsEnabled = true,
        )

        assertFalse(presentation.calendarMode)
        assertTrue(presentation.refreshEnabled)
        assertTrue(presentation.showNewRecord)
    }

    @Test
    fun calendarDeletedViewSelectsCalendarAndHidesNewRecord() {
        val presentation = sillageRecordsScreenChromePresentation(
            state = RecordsFeatureStateHolder(
                browse = RecordsBrowseStateHolder(
                    filter = MemoListFilter.Deleted,
                    viewMode = MemoViewMode.Calendar,
                    calendarYear = 2026,
                    calendarMonth = 8,
                ),
            ),
            hostActionsEnabled = true,
        )

        assertTrue(presentation.calendarMode)
        assertFalse(presentation.showNewRecord)
    }

    @Test
    fun hostOrRefreshLoadingDisablesRefresh() {
        assertFalse(
            sillageRecordsScreenChromePresentation(
                state = RecordsFeatureStateHolder(),
                hostActionsEnabled = false,
            ).refreshEnabled,
        )
        assertFalse(
            sillageRecordsScreenChromePresentation(
                state = RecordsFeatureStateHolder(
                    refresh = RecordsRefreshStateHolder(status = RecordsRefreshStatus.Loading),
                ),
                hostActionsEnabled = true,
            ).refreshEnabled,
        )
    }
}
