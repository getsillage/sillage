package app.sillage.ui.records

import app.sillage.core.domain.records.Memo
import app.sillage.features.records.MemoViewMode
import app.sillage.features.records.RecordsBrowseStateHolder
import app.sillage.features.records.RecordsCollectionStateHolder
import app.sillage.features.records.RecordsFeatureStateHolder
import app.sillage.features.records.RecordsRefreshStateHolder
import app.sillage.features.records.RecordsRefreshStatus
import app.sillage.features.records.RecordsSearchStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals

class SillageRecordsContentTest {
    @Test
    fun emptySurfaceShowsInitialOrRefreshLoading() {
        val idle = state()

        assertEquals(SillageRecordsBody.Loading, sillageRecordsBody(idle, initialLoading = true))
        assertEquals(
            SillageRecordsBody.Loading,
            sillageRecordsBody(
                idle.copy(
                    refresh = RecordsRefreshStateHolder(status = RecordsRefreshStatus.Loading),
                ),
                initialLoading = false,
            ),
        )
    }

    @Test
    fun refreshKeepsExistingListContentVisible() {
        val refreshing = state(records = listOf(memo())).copy(
            refresh = RecordsRefreshStateHolder(status = RecordsRefreshStatus.Loading),
        )

        assertEquals(
            SillageRecordsBody.List,
            sillageRecordsBody(refreshing, initialLoading = false),
        )
    }

    @Test
    fun calendarModeSelectsCalendarContent() {
        val calendar = state(viewMode = MemoViewMode.Calendar)

        assertEquals(
            SillageRecordsBody.Calendar,
            sillageRecordsBody(calendar, initialLoading = false),
        )
    }

    @Test
    fun failedEmptyListSelectsLoadFailureContent() {
        val failed = state().copy(
            refresh = RecordsRefreshStateHolder(status = RecordsRefreshStatus.Failed),
        )

        assertEquals(
            SillageRecordsBody.ListLoadFailure,
            sillageRecordsBody(failed, initialLoading = false),
        )
    }

    @Test
    fun currentFailedSearchSelectsSearchFailureContent() {
        val failed = state().copy(
            search = RecordsSearchStateHolder(
                query = "query",
                failureQuery = "query",
            ),
        )

        assertEquals(
            SillageRecordsBody.SearchFailure,
            sillageRecordsBody(failed, initialLoading = false),
        )
    }

    private fun state(
        records: List<Memo> = emptyList(),
        viewMode: MemoViewMode = MemoViewMode.List,
    ): RecordsFeatureStateHolder = RecordsFeatureStateHolder(
        collection = RecordsCollectionStateHolder(records = records),
        browse = RecordsBrowseStateHolder(
            viewMode = viewMode,
            calendarYear = 2026,
            calendarMonth = 8,
        ),
    )

    private fun memo(): Memo = Memo(
        id = "memo",
        content = "Memo",
        entryDate = "2026-08-02",
        version = 1,
        createdAt = "2026-08-02T00:00:00Z",
        updatedAt = "2026-08-02T00:00:00Z",
        favoritedAt = null,
        archivedAt = null,
        deletedAt = null,
    )
}
