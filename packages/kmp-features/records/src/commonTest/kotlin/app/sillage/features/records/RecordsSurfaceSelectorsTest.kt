package app.sillage.features.records

import app.sillage.core.domain.records.Memo
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecordsSurfaceSelectorsTest {
    @Test
    fun listLoadFailureRequiresFailedEmptyUnsearchedList() {
        val failed = state(
            refresh = RecordsRefreshStateHolder(status = RecordsRefreshStatus.Failed),
        )

        assertTrue(failed.shouldShowRecordListLoadFailure())
        assertFalse(
            failed.copy(
                browse = failed.browse.copy(viewMode = MemoViewMode.Calendar),
            ).shouldShowRecordListLoadFailure(),
        )
        assertFalse(
            failed.copy(
                search = failed.search.copy(query = "query"),
            ).shouldShowRecordListLoadFailure(),
        )
        assertFalse(
            failed.copy(
                refresh = failed.refresh.copy(status = RecordsRefreshStatus.Loading),
            ).shouldShowRecordListLoadFailure(),
        )
        assertFalse(
            failed.copy(
                collection = RecordsCollectionStateHolder(records = listOf(memo())),
            ).shouldShowRecordListLoadFailure(),
        )
        assertFalse(
            failed.copy(
                search = failed.search.copy(results = emptyList()),
            ).shouldShowRecordListLoadFailure(),
        )
    }

    @Test
    fun searchFailureIsBoundToCurrentFailedQuery() {
        val failed = state(
            search = RecordsSearchStateHolder(
                query = "new query",
                results = listOf(memo()),
                resultQuery = "old query",
                failureQuery = "new query",
                searching = false,
            ),
        )

        assertTrue(failed.shouldShowRecordSearchFailure())
        assertFalse(
            failed.copy(
                search = failed.search.copy(searching = true),
            ).shouldShowRecordSearchFailure(),
        )
        assertFalse(
            failed.copy(
                search = failed.search.copy(failureQuery = "old query"),
            ).shouldShowRecordSearchFailure(),
        )
        assertFalse(
            failed.copy(
                search = failed.search.copy(resultQuery = "new query"),
            ).shouldShowRecordSearchFailure(),
        )
        assertFalse(
            failed.copy(
                search = failed.search.copy(query = ""),
            ).shouldShowRecordSearchFailure(),
        )
        assertFalse(
            failed.copy(
                browse = failed.browse.copy(viewMode = MemoViewMode.Calendar),
            ).shouldShowRecordSearchFailure(),
        )
    }

    private fun state(
        refresh: RecordsRefreshStateHolder = RecordsRefreshStateHolder(),
        search: RecordsSearchStateHolder = RecordsSearchStateHolder(),
    ): RecordsFeatureStateHolder = RecordsFeatureStateHolder(
        refresh = refresh,
        search = search,
        browse = RecordsBrowseStateHolder(
            calendarYear = 2026,
            calendarMonth = 8,
        ),
    )

    private fun memo(): Memo = Memo(
        id = "record",
        content = "Record",
        entryDate = "2026-08-02",
        version = 1,
        createdAt = "2026-08-02T00:00:00Z",
        updatedAt = "2026-08-02T00:00:00Z",
        favoritedAt = null,
        archivedAt = null,
        deletedAt = null,
    )
}
