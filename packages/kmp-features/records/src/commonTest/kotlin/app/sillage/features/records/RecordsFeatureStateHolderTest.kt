package app.sillage.features.records

import app.sillage.core.domain.records.Memo
import app.sillage.core.domain.records.MemoAI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordsFeatureStateHolderTest {
    @Test
    fun clearPresentedMemoClearsSelectionAndSummaryWithoutTouchingCache() {
        val selected = memo("memo-1")
        val state = RecordsFeatureStateHolder(
            collection = RecordsCollectionStateHolder(records = listOf(selected), cacheGeneration = 2),
            selection = RecordsSelectionStateHolder(selectedMemo = selected),
            summary = RecordsSummaryStateHolder(loading = true),
            editor = RecordsEditorStateHolder(uploadingAttachment = true, draftContent = "draft"),
            search = RecordsSearchStateHolder(query = "keep"),
        )

        val cleared = state.clearPresentedMemo(stopAttachmentUpload = true)

        assertEquals(listOf(selected), cleared.records)
        assertEquals(2, cleared.cacheGeneration)
        assertNull(cleared.selection.selectedMemo)
        assertFalse(cleared.summary.loading)
        assertFalse(cleared.editor.uploadingAttachment)
        assertEquals("draft", cleared.editor.draftContent)
        assertEquals("keep", cleared.search.query)
    }

    @Test
    fun clearPresentedMemoCanAlsoClearSearchOwnership() {
        val selected = memo("memo-1b")
        val state = RecordsFeatureStateHolder(
            selection = RecordsSelectionStateHolder(selectedMemo = selected),
            search = RecordsSearchStateHolder(query = "drop", searching = true),
        )

        val cleared = state.clearPresentedMemo(clearSearch = true)

        assertNull(cleared.selection.selectedMemo)
        assertEquals("", cleared.search.query)
        assertFalse(cleared.search.searching)
    }

    @Test
    fun presentMemoSelectsMemoAndSummaryPresentation() {
        val selected = memo("memo-2")
        val summary = MemoAI(
            memoId = selected.id,
            summary = "摘要",
            sentiment = null,
            provider = "openai",
            model = "model",
            profileId = "p1",
            promptVersion = "v1",
            sourceMemoIds = selected.id,
            status = "complete",
            errorCode = null,
            startedAt = null,
            finishedAt = null,
            inputTokens = 1,
            outputTokens = 1,
            totalTokens = 2,
            createdAt = "2026-08-01T00:00:00Z",
            updatedAt = "2026-08-01T00:00:00Z",
        )

        val presented = RecordsFeatureStateHolder().presentMemo(
            memo = selected,
            summary = summary,
            summaryLoading = false,
        )

        assertEquals(selected, presented.selection.selectedMemo)
        assertEquals(summary, presented.summary.summary)
        assertFalse(presented.summary.loading)
    }

    @Test
    fun beginNewEditorDraftClearsSelectionAndOpensEditor() {
        val selected = memo("memo-3")
        val state = RecordsFeatureStateHolder(
            selection = RecordsSelectionStateHolder(selectedMemo = selected),
            summary = RecordsSummaryStateHolder(loading = true),
        )

        val opened = state.beginNewEditorDraft(
            draftContent = "新内容",
            draftEntryDate = "2026-08-02",
        )

        assertNull(opened.selection.selectedMemo)
        assertFalse(opened.summary.loading)
        assertEquals("新内容", opened.editor.draftContent)
        assertEquals("2026-08-02", opened.editor.draftEntryDate)
        assertEquals(1, opened.editor.sessionId)
    }

    @Test
    fun beginMemoEditorKeepsSelectionAndOpensDraft() {
        val selected = memo("memo-4")
        val opened = RecordsFeatureStateHolder().beginMemoEditor(
            memo = selected,
            draftContent = "编辑中",
            draftEntryDate = selected.entryDate,
            initialDraftContent = selected.content,
            initialDraftEntryDate = selected.entryDate,
            summaryLoading = true,
        )

        assertEquals(selected, opened.selection.selectedMemo)
        assertTrue(opened.summary.loading)
        assertEquals("编辑中", opened.editor.draftContent)
        assertEquals(selected.content, opened.editor.initialDraftContent)
    }

    @Test
    fun presentMemoDetailStopsUploadAndDisablesMarkdownPreview() {
        val selected = memo("memo-5")
        val state = RecordsFeatureStateHolder(
            editor = RecordsEditorStateHolder(
                uploadingAttachment = true,
                markdownPreview = true,
            ),
        )

        val presented = state.presentMemoDetail(
            memo = selected,
            summaryLoading = true,
        )

        assertEquals(selected, presented.selection.selectedMemo)
        assertTrue(presented.summary.loading)
        assertFalse(presented.editor.uploadingAttachment)
        assertFalse(presented.editor.markdownPreview)
    }

    @Test
    fun presentSavedMemoResetsEditorAndClearsSearch() {
        val selected = memo("memo-6")
        val state = RecordsFeatureStateHolder(
            search = RecordsSearchStateHolder(query = "旧查询", results = listOf(selected)),
            editor = RecordsEditorStateHolder(draftContent = "草稿", sessionId = 3),
        )

        val presented = state.presentSavedMemo(
            memo = selected,
            summaryLoading = true,
            resetEditorEntryDate = "2026-08-03",
        )

        assertEquals(selected, presented.selection.selectedMemo)
        assertTrue(presented.summary.loading)
        assertEquals("", presented.editor.draftContent)
        assertEquals("2026-08-03", presented.editor.draftEntryDate)
        assertEquals("", presented.search.query)
        assertNull(presented.search.results)
    }

    @Test
    fun returnToPresentedMemoKeepsSelectionAndResetsEditor() {
        val selected = memo("memo-7")
        val state = RecordsFeatureStateHolder(
            selection = RecordsSelectionStateHolder(selectedMemo = selected),
            summary = RecordsSummaryStateHolder(
                summary = MemoAI(
                    memoId = selected.id,
                    summary = "旧摘要",
                    sentiment = null,
                    provider = "openai",
                    model = "model",
                    profileId = "p1",
                    promptVersion = "v1",
                    sourceMemoIds = selected.id,
                    status = "complete",
                    errorCode = null,
                    startedAt = null,
                    finishedAt = null,
                    inputTokens = 1,
                    outputTokens = 1,
                    totalTokens = 2,
                    createdAt = "2026-08-01T00:00:00Z",
                    updatedAt = "2026-08-01T00:00:00Z",
                ),
            ),
            editor = RecordsEditorStateHolder(draftContent = "编辑中"),
        )

        val returned = state.returnToPresentedMemo(
            resetEditorEntryDate = "2026-08-04",
            summaryLoading = true,
        )

        assertEquals(selected, returned.selection.selectedMemo)
        assertNull(returned.summary.summary)
        assertTrue(returned.summary.loading)
        assertEquals("", returned.editor.draftContent)
        assertEquals("2026-08-04", returned.editor.draftEntryDate)
    }

    @Test
    fun forgetMemoIfSelectedOnlyClearsMatchingSelectionAndSummary() {
        val selected = memo("memo-8")
        val other = memo("memo-9")
        val selectedState = RecordsFeatureStateHolder(
            selection = RecordsSelectionStateHolder(selectedMemo = selected),
            summary = RecordsSummaryStateHolder(
                summary = MemoAI(
                    memoId = selected.id,
                    summary = "摘要",
                    sentiment = null,
                    provider = "openai",
                    model = "model",
                    profileId = "p1",
                    promptVersion = "v1",
                    sourceMemoIds = selected.id,
                    status = "complete",
                    errorCode = null,
                    startedAt = null,
                    finishedAt = null,
                    inputTokens = 1,
                    outputTokens = 1,
                    totalTokens = 2,
                    createdAt = "2026-08-01T00:00:00Z",
                    updatedAt = "2026-08-01T00:00:00Z",
                ),
            ),
        )

        val forgotten = selectedState.forgetMemoIfSelected(selected.id)
        val untouched = selectedState.forgetMemoIfSelected(other.id)

        assertNull(forgotten.selection.selectedMemo)
        assertNull(forgotten.summary.summary)
        assertEquals(selected, untouched.selection.selectedMemo)
        assertEquals("摘要", untouched.summary.summary?.summary)
    }

    @Test
    fun absorbVisibleMemoUpdatesCacheSearchAndDetailPresentation() {
        val existing = memo("memo-10")
        val incoming = memo("memo-11")
        val state = RecordsFeatureStateHolder(
            collection = RecordsCollectionStateHolder(records = listOf(existing)),
            search = RecordsSearchStateHolder(
                query = "memo",
                results = listOf(existing),
                resultQuery = "memo",
            ),
            browse = RecordsBrowseStateHolder(
                filter = MemoListFilter.Unarchived,
                calendarYear = 2026,
                calendarMonth = 8,
            ),
            editor = RecordsEditorStateHolder(uploadingAttachment = true, markdownPreview = true),
        )

        val absorbed = state.absorbVisibleMemo(incoming)

        assertEquals(listOf(existing, incoming), absorbed.records)
        assertEquals(listOf(existing, incoming), absorbed.search.results)
        assertEquals(incoming, absorbed.selection.selectedMemo)
        assertFalse(absorbed.editor.uploadingAttachment)
        assertFalse(absorbed.editor.markdownPreview)
    }

    @Test
    fun completePresentedDetailAppliesCanonicalMemoAndSummaryTogether() {
        val original = memo("memo-12")
        val updated = original.copy(content = "更新", version = 2, updatedAt = "2026-08-01T03:00:00Z")
        val summary = MemoAI(
            memoId = updated.id,
            summary = "新摘要",
            sentiment = null,
            provider = "openai",
            model = "model",
            profileId = "p1",
            promptVersion = "v1",
            sourceMemoIds = updated.id,
            status = "complete",
            errorCode = null,
            startedAt = null,
            finishedAt = null,
            inputTokens = 1,
            outputTokens = 1,
            totalTokens = 2,
            createdAt = "2026-08-01T00:00:00Z",
            updatedAt = "2026-08-01T03:00:00Z",
        )
        val state = RecordsFeatureStateHolder(
            collection = RecordsCollectionStateHolder(records = listOf(original), cacheGeneration = 1),
            selection = RecordsSelectionStateHolder(selectedMemo = original),
            summary = RecordsSummaryStateHolder(loading = true),
            pagination = RecordsPaginationStateHolder(loadingMore = true, requestId = 2),
            refresh = RecordsRefreshStateHolder(status = RecordsRefreshStatus.Loading, requestId = 3),
        )

        val completed = state.completePresentedDetail(updated, summary)

        assertEquals(listOf(updated), completed.records)
        assertEquals(2, completed.cacheGeneration)
        assertEquals(updated, completed.selection.selectedMemo)
        assertEquals(summary, completed.summary.summary)
        assertFalse(completed.summary.loading)
        assertFalse(completed.loadingMore)
        assertEquals(RecordsRefreshStatus.Idle, completed.refreshStatus)
    }

    @Test
    fun markListLoadingPreservesVisibleCache() {
        val existing = memo("memo-13")
        val state = RecordsFeatureStateHolder(
            collection = RecordsCollectionStateHolder(records = listOf(existing), cacheGeneration = 4),
            refresh = RecordsRefreshStateHolder(status = RecordsRefreshStatus.Idle),
        )

        val loading = state.markListLoading()

        assertEquals(listOf(existing), loading.records)
        assertEquals(4, loading.cacheGeneration)
        assertEquals(RecordsRefreshStatus.Loading, loading.refreshStatus)
    }

    @Test
    fun stopLoadingMoreAndCancelPaginationPreserveCache() {
        val existing = memo("memo-13b")
        val state = RecordsFeatureStateHolder(
            collection = RecordsCollectionStateHolder(records = listOf(existing)),
            pagination = RecordsPaginationStateHolder(
                nextCursor = "c",
                loadingMore = true,
                requestId = 4,
            ),
        )

        val stopped = state.stopLoadingMore()
        val cancelled = state.cancelPagination()

        assertEquals(listOf(existing), stopped.records)
        assertFalse(stopped.loadingMore)
        assertEquals("c", stopped.nextCursor)
        assertFalse(cancelled.loadingMore)
        assertEquals(5, cancelled.pageRequestId)
    }

    @Test
    fun selectCalendarMonthAndDayUpdateBrowseState() {
        val state = RecordsFeatureStateHolder(
            browse = RecordsBrowseStateHolder(
                calendarYear = 2026,
                calendarMonth = 7,
                selectedCalendarDate = "2026-07-10",
            ),
        )

        val month = state.selectCalendarMonth(2026, 8)
        val day = month.selectCalendarDay("2026-08-02")

        assertEquals(2026, month.browse.calendarYear)
        assertEquals(8, month.browse.calendarMonth)
        assertNull(month.browse.selectedCalendarDate)
        assertEquals("2026-08-02", day.browse.selectedCalendarDate)
    }

    @Test
    fun acceptDetailRequestPairsSelectionWithSummaryLoad() {
        val selected = memo("memo-14")
        val selection = RecordsSelectionStateHolder(
            selectedMemo = selected,
            detailRequestId = 9,
        )
        val state = RecordsFeatureStateHolder(
            summary = RecordsSummaryStateHolder(loading = false),
        )

        val started = state.acceptDetailRequest(selection, loadSummary = true)

        assertEquals(selected, started.selection.selectedMemo)
        assertEquals(9, started.selection.detailRequestId)
        assertTrue(started.summary.loading)
    }

    @Test
    fun finishDetailSummaryStopsLoadingWithoutClearingSelection() {
        val selected = memo("memo-15")
        val state = RecordsFeatureStateHolder(
            selection = RecordsSelectionStateHolder(selectedMemo = selected),
            summary = RecordsSummaryStateHolder(loading = true),
        )

        val finished = state.finishDetailSummary()

        assertEquals(selected, finished.selection.selectedMemo)
        assertFalse(finished.summary.loading)
    }

    @Test
    fun replaceSelectedMemoOnlyUpdatesMatchingSelection() {
        val selected = memo("memo-16")
        val replacement = selected.copy(content = "冲突后", version = 3)
        val state = RecordsFeatureStateHolder(
            selection = RecordsSelectionStateHolder(selectedMemo = selected),
        )

        val replaced = state.replaceSelectedMemo(selected.id, replacement)
        val ignored = state.replaceSelectedMemo("other", replacement)

        assertEquals(replacement, replaced.selection.selectedMemo)
        assertEquals(selected, ignored.selection.selectedMemo)
    }

    @Test
    fun clearInteractiveSurfaceResetsListMutationSelectionSummaryUploadAndSearch() {
        val selected = memo("memo-1")
        val state = RecordsFeatureStateHolder(
            collection = RecordsCollectionStateHolder(
                records = listOf(selected),
                cacheGeneration = 4,
            ),
            pagination = RecordsPaginationStateHolder(
                nextCursor = "cursor",
                loadingMore = true,
                requestId = 2,
            ),
            refresh = RecordsRefreshStateHolder(
                status = RecordsRefreshStatus.Loading,
                requestId = 3,
            ),
            mutation = RecordsMutationStateHolder(setOf(selected.id)),
            selection = RecordsSelectionStateHolder(selectedMemo = selected),
            summary = RecordsSummaryStateHolder(loading = true, requestId = 5),
            editor = RecordsEditorStateHolder(uploadingAttachment = true),
            search = RecordsSearchStateHolder(query = "memo", searching = true, requestId = 6),
            browse = RecordsBrowseStateHolder(
                filter = MemoListFilter.Archived,
                calendarYear = 2026,
                calendarMonth = 8,
            ),
        )

        val cleared = state.clearInteractiveSurface()

        assertEquals(emptyList(), cleared.records)
        assertEquals(4, cleared.cacheGeneration)
        assertEquals("", cleared.nextCursor)
        assertFalse(cleared.loadingMore)
        assertEquals(RecordsRefreshStatus.Idle, cleared.refreshStatus)
        assertFalse(cleared.mutation.isActive(selected.id))
        assertNull(cleared.selection.selectedMemo)
        assertNull(cleared.summary.summary)
        assertFalse(cleared.summary.loading)
        assertFalse(cleared.editor.uploadingAttachment)
        assertEquals("", cleared.search.query)
        assertFalse(cleared.search.searching)
        assertEquals(MemoListFilter.Archived, cleared.filter)
    }

    @Test
    fun clearVisibleListResetsCacheAndLoadOwnershipWithoutMutationGeneration() {
        val state = RecordsFeatureStateHolder(
            collection = RecordsCollectionStateHolder(
                records = listOf(memo("memo-1")),
                cacheGeneration = 3,
            ),
            pagination = RecordsPaginationStateHolder(
                nextCursor = "cursor-1",
                loadingMore = true,
                requestId = 4,
            ),
            refresh = RecordsRefreshStateHolder(
                status = RecordsRefreshStatus.Loading,
                requestId = 2,
            ),
        )

        val cleared = state.clearVisibleList()

        assertEquals(emptyList(), cleared.records)
        assertEquals(3, cleared.cacheGeneration)
        assertEquals("", cleared.nextCursor)
        assertFalse(cleared.loadingMore)
        assertEquals(RecordsRefreshStatus.Idle, cleared.refreshStatus)
        assertEquals(3, cleared.refresh.requestId)
        assertEquals(4, cleared.pageRequestId)
    }

    @Test
    fun resetVisibleListMarksLoadingAndPreservesGeneration() {
        val state = RecordsFeatureStateHolder(
            collection = RecordsCollectionStateHolder(
                records = listOf(memo("memo-1")),
                cacheGeneration = 5,
            ),
            pagination = RecordsPaginationStateHolder(nextCursor = "c", loadingMore = true),
            refresh = RecordsRefreshStateHolder(requestId = 1),
        )

        val reset = state.resetVisibleList(markLoading = true)

        assertEquals(emptyList(), reset.records)
        assertEquals(5, reset.cacheGeneration)
        assertEquals("", reset.nextCursor)
        assertFalse(reset.loadingMore)
        assertEquals(RecordsRefreshStatus.Loading, reset.refreshStatus)
        assertEquals(1, reset.refresh.requestId)
    }

    @Test
    fun replaceVisibleRecordsUpdatesCacheAndCursorWithoutAdvancingGeneration() {
        val state = RecordsFeatureStateHolder(
            collection = RecordsCollectionStateHolder(cacheGeneration = 8),
            pagination = RecordsPaginationStateHolder(loadingMore = true, requestId = 2),
        )
        val records = listOf(memo("memo-a"), memo("memo-b"))

        val replaced = state.replaceVisibleRecords(records, nextCursor = "next")

        assertEquals(records, replaced.records)
        assertEquals(8, replaced.cacheGeneration)
        assertEquals("next", replaced.nextCursor)
        assertFalse(replaced.loadingMore)
        assertEquals(2, replaced.pageRequestId)
    }

    @Test
    fun appendVisiblePageMergesThroughActiveFilter() {
        val active = memo("memo-1")
        val archived = memo("memo-2").copy(archivedAt = "2026-08-01T01:00:00Z")
        val state = RecordsFeatureStateHolder(
            collection = RecordsCollectionStateHolder(records = listOf(active)),
            browse = RecordsBrowseStateHolder(
                filter = MemoListFilter.Unarchived,
                calendarYear = 2026,
                calendarMonth = 8,
            ),
        )

        val appended = state.appendVisiblePage(
            pageRecords = listOf(archived, memo("memo-3")),
            nextCursor = "c2",
        )

        assertEquals(listOf("memo-1", "memo-3"), appended.records.map(Memo::id))
        assertEquals("c2", appended.nextCursor)
    }

    @Test
    fun applyCanonicalMemoAdvancesGenerationAndInvalidatesLoadsAndSearch() {
        val original = memo("memo-1")
        val updated = original.copy(
            content = "updated",
            version = 2,
            updatedAt = "2026-08-01T02:00:00Z",
        )
        val state = RecordsFeatureStateHolder(
            collection = RecordsCollectionStateHolder(
                records = listOf(original),
                cacheGeneration = 4,
            ),
            pagination = RecordsPaginationStateHolder(
                nextCursor = "cursor",
                loadingMore = true,
                requestId = 3,
            ),
            refresh = RecordsRefreshStateHolder(
                status = RecordsRefreshStatus.Loading,
                requestId = 5,
            ),
            selection = RecordsSelectionStateHolder(selectedMemo = original),
            search = RecordsSearchStateHolder(
                query = "memo",
                results = listOf(original),
                resultQuery = "memo",
                requestId = 7,
                searching = true,
            ),
            browse = RecordsBrowseStateHolder(
                filter = MemoListFilter.Unarchived,
                calendarYear = 2026,
                calendarMonth = 8,
            ),
        )

        val applied = state.applyCanonicalMemo(updated)

        assertEquals(listOf(updated), applied.records)
        assertEquals(5, applied.cacheGeneration)
        assertFalse(applied.loadingMore)
        assertEquals(4, applied.pageRequestId)
        assertEquals(RecordsRefreshStatus.Idle, applied.refreshStatus)
        assertEquals(6, applied.refresh.requestId)
        assertEquals(updated, applied.selection.selectedMemo)
        assertFalse(applied.search.searching)
        assertEquals(8, applied.search.requestId)
        assertEquals(listOf(updated), applied.search.results)
    }

    @Test
    fun applyCanonicalMemoDropsItemsOutsideActiveFilter() {
        val original = memo("memo-1")
        val archived = original.copy(
            archivedAt = "2026-08-01T03:00:00Z",
            version = 2,
        )
        val state = RecordsFeatureStateHolder(
            collection = RecordsCollectionStateHolder(records = listOf(original)),
            selection = RecordsSelectionStateHolder(selectedMemo = original),
            browse = RecordsBrowseStateHolder(
                filter = MemoListFilter.Unarchived,
                calendarYear = 2026,
                calendarMonth = 8,
            ),
        )

        val applied = state.applyCanonicalMemo(archived)

        assertEquals(emptyList(), applied.records)
        assertEquals(archived, applied.selection.selectedMemo)
    }

    @Test
    fun resetForFilterChangeClearsSearchAndMarksListLoading() {
        val state = RecordsFeatureStateHolder(
            collection = RecordsCollectionStateHolder(records = listOf(memo("memo-1"))),
            pagination = RecordsPaginationStateHolder(nextCursor = "c"),
            search = RecordsSearchStateHolder(query = "hello", searching = true),
            selection = RecordsSelectionStateHolder(selectedMemo = memo("memo-1")),
        )

        val reset = state.resetForFilterChange()

        assertEquals(emptyList(), reset.records)
        assertEquals("", reset.nextCursor)
        assertEquals(RecordsRefreshStatus.Loading, reset.refreshStatus)
        assertEquals("", reset.search.query)
        assertFalse(reset.search.searching)
        assertEquals(memo("memo-1"), reset.selection.selectedMemo)
    }

    @Test
    fun applyListFilterResetsListSearchAndPresentedMemo() {
        val selected = memo("memo-20")
        val state = RecordsFeatureStateHolder(
            collection = RecordsCollectionStateHolder(records = listOf(selected)),
            pagination = RecordsPaginationStateHolder(nextCursor = "c"),
            search = RecordsSearchStateHolder(query = "q", searching = true),
            selection = RecordsSelectionStateHolder(selectedMemo = selected),
            summary = RecordsSummaryStateHolder(loading = true),
            browse = RecordsBrowseStateHolder(
                filter = MemoListFilter.Unarchived,
                calendarYear = 2026,
                calendarMonth = 8,
            ),
        )

        val applied = state.applyListFilter(MemoListFilter.Archived)

        assertEquals(MemoListFilter.Archived, applied.filter)
        assertEquals(emptyList(), applied.records)
        assertEquals(RecordsRefreshStatus.Loading, applied.refreshStatus)
        assertEquals("", applied.search.query)
        assertNull(applied.selection.selectedMemo)
        assertFalse(applied.summary.loading)
    }

    @Test
    fun applyViewModeCalendarClearsSearchAndCanResetFilterSurface() {
        val selected = memo("memo-21")
        val state = RecordsFeatureStateHolder(
            collection = RecordsCollectionStateHolder(records = listOf(selected)),
            search = RecordsSearchStateHolder(query = "keep-or-clear"),
            selection = RecordsSelectionStateHolder(selectedMemo = selected),
            browse = RecordsBrowseStateHolder(
                filter = MemoListFilter.Favorited,
                calendarYear = 2026,
                calendarMonth = 8,
            ),
        )

        val calendar = state.applyViewMode(MemoViewMode.Calendar, resetFilter = true)

        assertEquals(MemoViewMode.Calendar, calendar.viewMode)
        assertEquals(MemoListFilter.Unarchived, calendar.filter)
        assertEquals(emptyList(), calendar.records)
        assertEquals(RecordsRefreshStatus.Loading, calendar.refreshStatus)
        assertEquals("", calendar.search.query)
        assertNull(calendar.selection.selectedMemo)
    }

    @Test
    fun editorDraftTransitionsStayInsideAggregate() {
        val selected = memo("memo-editor")
        val state = RecordsFeatureStateHolder(
            collection = RecordsCollectionStateHolder(
                records = listOf(selected),
                cacheGeneration = 2,
            ),
            editor = RecordsEditorStateHolder(
                sessionId = 4,
                draftContent = "old",
                draftEntryDate = "2026-08-01",
                initialDraftContent = "old",
                initialDraftEntryDate = "2026-08-01",
                markdownPreview = true,
            ),
            search = RecordsSearchStateHolder(query = "keep"),
        )

        val updated = state
            .updateEditorContent("new")
            .updateEditorEntryDate("2026-08-02")
            .setEditorMarkdownPreview(true)
            .appendEditorFormattedSnippet("**bold**")

        assertEquals("new **bold**", updated.editor.draftContent)
        assertEquals("2026-08-02", updated.editor.draftEntryDate)
        assertFalse(updated.editor.markdownPreview)
        assertEquals(listOf(selected), updated.records)
        assertEquals(2, updated.cacheGeneration)
        assertEquals("keep", updated.search.query)
    }

    @Test
    fun editorAttachmentTransitionsRejectStaleSessions() {
        val state = RecordsFeatureStateHolder(
            editor = RecordsEditorStateHolder(
                sessionId = 7,
                draftContent = "memo",
            ),
        )

        assertNull(state.beginEditorAttachmentUpload(expectedSessionId = 6))
        val started = assertNotNull(
            state.beginEditorAttachmentUpload(expectedSessionId = 7),
        )
        val appended = started.appendEditorAttachmentSnippet(
            expectedSessionId = 7,
            snippet = "\n[file](/attachment)",
        )
        val stale = appended.appendEditorAttachmentSnippet(
            expectedSessionId = 6,
            snippet = "\n[stale](/attachment)",
        )
        val finished = stale.finishEditorAttachmentUpload(expectedSessionId = 7)

        assertEquals("memo\n[file](/attachment)", finished.editor.draftContent)
        assertFalse(finished.editor.uploadingAttachment)
    }

    @Test
    fun individualHoldersRemainIndependentlyReplaceable() {
        val state = RecordsFeatureStateHolder()
        val selected = memo("memo-9")

        val updated = state.copy(
            selection = state.selection.select(selected),
            mutation = state.mutation.begin(selected.id),
        )

        assertEquals(selected, updated.selection.selectedMemo)
        assertTrue(updated.mutation.isActive(selected.id))
        assertNull(state.selection.selectedMemo)
    }

    private fun memo(id: String): Memo {
        return Memo(
            id = id,
            content = id,
            entryDate = "2026-08-01",
            version = 1,
            createdAt = "2026-08-01T00:00:00Z",
            updatedAt = "2026-08-01T00:00:00Z",
            favoritedAt = null,
            archivedAt = null,
            deletedAt = null,
        )
    }
}
