package app.sillage.features.records

import app.sillage.core.domain.records.Memo
import app.sillage.core.domain.records.MemoAI

/**
 * Aggregated immutable ownership for the records feature.
 *
 * Individual holders remain the unit of request identity and late-response
 * validation. This type owns cross-holder transitions that must stay consistent
 * (visible list loads, canonical cache mutations, and related invalidation).
 */
data class RecordsFeatureStateHolder(
    val collection: RecordsCollectionStateHolder = RecordsCollectionStateHolder(),
    val pagination: RecordsPaginationStateHolder = RecordsPaginationStateHolder(),
    val refresh: RecordsRefreshStateHolder = RecordsRefreshStateHolder(),
    val selection: RecordsSelectionStateHolder = RecordsSelectionStateHolder(),
    val mutation: RecordsMutationStateHolder = RecordsMutationStateHolder(),
    val summary: RecordsSummaryStateHolder = RecordsSummaryStateHolder(),
    val attachmentOpen: RecordsAttachmentOpenStateHolder = RecordsAttachmentOpenStateHolder(),
    val editor: RecordsEditorStateHolder = RecordsEditorStateHolder(),
    val search: RecordsSearchStateHolder = RecordsSearchStateHolder(),
    val browse: RecordsBrowseStateHolder = RecordsBrowseStateHolder(
        calendarYear = 1970,
        calendarMonth = 1,
    ),
) {
    val records: List<Memo> get() = collection.records
    val cacheGeneration: Long get() = collection.cacheGeneration
    val nextCursor: String get() = pagination.nextCursor
    val loadingMore: Boolean get() = pagination.loadingMore
    val pageRequestId: Long get() = pagination.requestId
    val refreshStatus: RecordsRefreshStatus get() = refresh.status
    val filter: MemoListFilter get() = browse.filter
    val viewMode: MemoViewMode get() = browse.viewMode

    /** Allocates the next attachment-open identity without starting platform work. */
    fun nextAttachmentOpenRequest(path: String): RecordsAttachmentOpenRequest? {
        return attachmentOpen.nextRequest(path)
    }

    /** Begins a prepared attachment-open request only while its identity is current. */
    fun beginAttachmentOpen(
        request: RecordsAttachmentOpenRequest,
    ): RecordsFeatureStateHolder? {
        val nextAttachmentOpen = attachmentOpen.begin(request) ?: return null
        return copy(attachmentOpen = nextAttachmentOpen)
    }

    /** Completes an attachment-open request only while it still owns the surface. */
    fun completeAttachmentOpen(requestId: Long): RecordsFeatureStateHolder {
        val nextAttachmentOpen = attachmentOpen.complete(requestId)
        if (nextAttachmentOpen === attachmentOpen) {
            return this
        }
        return copy(attachmentOpen = nextAttachmentOpen)
    }

    /** Invalidates any queued attachment-open result when navigation context changes. */
    fun invalidateAttachmentOpen(): RecordsFeatureStateHolder {
        val nextAttachmentOpen = attachmentOpen.invalidate()
        if (nextAttachmentOpen === attachmentOpen) {
            return this
        }
        return copy(attachmentOpen = nextAttachmentOpen)
    }

    /** Marks one record mutation active through the feature aggregate. */
    fun beginMemoMutation(memoId: String?): RecordsFeatureStateHolder {
        val nextMutation = mutation.begin(memoId)
        return if (nextMutation === mutation) this else copy(mutation = nextMutation)
    }

    /** Clears one record mutation presentation through the feature aggregate. */
    fun finishMemoMutation(memoId: String?): RecordsFeatureStateHolder {
        val nextMutation = mutation.finish(memoId)
        return if (nextMutation === mutation) this else copy(mutation = nextMutation)
    }

    /** Updates search input and invalidates stale search ownership. */
    fun updateSearchQuery(value: String): RecordsFeatureStateHolder {
        return copy(search = search.updateQuery(value))
    }

    /** Clears search input, results, and in-flight request ownership. */
    fun clearSearch(): RecordsFeatureStateHolder {
        return copy(search = search.clear())
    }

    /**
     * Clears the visible cache and stops in-flight list loads without inventing
     * a mutation generation. Used when the active source or client context ends.
     */
    fun clearVisibleList(): RecordsFeatureStateHolder {
        return copy(
            collection = collection.clear(),
            pagination = pagination.copy(nextCursor = "", loadingMore = false),
            refresh = refresh.cancel(),
        )
    }

    /**
     * Clears the interactive records surface for a workspace or client-context
     * change: visible list ownership, mutations, selection, summary presentation,
     * in-flight attachment upload, and search. Browse mode/filter and editor draft
     * text remain for the host when navigation context differs.
     */
    fun clearInteractiveSurface(): RecordsFeatureStateHolder {
        return clearVisibleList().copy(
            mutation = mutation.clear(),
            selection = selection.clear(),
            summary = summary.replacePresentation(null, loading = false),
            editor = editor.stopAttachmentUpload(),
            search = search.clear(),
        )
    }

    /**
     * Clears selected-memo presentation when leaving detail/editor destinations.
     * Optionally stops an in-flight attachment upload, resets the editor draft,
     * and/or clears search ownership.
     */
    fun clearPresentedMemo(
        stopAttachmentUpload: Boolean = false,
        resetEditorEntryDate: String? = null,
        clearSearch: Boolean = false,
    ): RecordsFeatureStateHolder {
        val nextEditor = when {
            resetEditorEntryDate != null -> editor.reset(resetEditorEntryDate)
            stopAttachmentUpload -> editor.stopAttachmentUpload()
            else -> editor
        }
        return copy(
            selection = selection.clear(),
            summary = summary.replacePresentation(null, loading = false),
            editor = nextEditor,
            search = if (clearSearch) search.clear() else search,
        )
    }

    /**
     * Selects [memo] and replaces summary presentation for detail/editor hosts.
     */
    fun presentMemo(
        memo: Memo,
        summary: MemoAI? = null,
        summaryLoading: Boolean = false,
    ): RecordsFeatureStateHolder {
        return copy(
            selection = selection.select(memo),
            summary = this.summary.replacePresentation(summary, summaryLoading),
        )
    }

    /**
     * Presents [memo] for the detail viewer: selection/summary plus a clean
     * non-preview editor surface with no in-flight attachment upload.
     */
    fun presentMemoDetail(
        memo: Memo,
        summary: MemoAI? = null,
        summaryLoading: Boolean = false,
    ): RecordsFeatureStateHolder {
        val presented = presentMemo(memo, summary, summaryLoading)
        return presented.copy(
            editor = presented.editor
                .stopAttachmentUpload()
                .setMarkdownPreview(false),
        )
    }

    /**
     * Presents a just-saved memo, resets the editor draft, and clears search so
     * the host can navigate to detail without stale composer/search ownership.
     */
    fun presentSavedMemo(
        memo: Memo,
        summary: MemoAI? = null,
        summaryLoading: Boolean = false,
        resetEditorEntryDate: String,
    ): RecordsFeatureStateHolder {
        val presented = presentMemo(memo, summary, summaryLoading)
        return presented.copy(
            editor = presented.editor.reset(resetEditorEntryDate),
            search = presented.search.clear(),
        )
    }

    /**
     * Leaves the editor while keeping the current selection for detail, clearing
     * summary presentation and resetting the draft session.
     */
    fun returnToPresentedMemo(
        resetEditorEntryDate: String,
        summaryLoading: Boolean = false,
    ): RecordsFeatureStateHolder {
        return copy(
            summary = summary.replacePresentation(null, loading = summaryLoading),
            editor = editor.reset(resetEditorEntryDate),
        )
    }

    /**
     * Drops selection/summary presentation when [memoId] is the selected memo.
     * Used after list-surface lifecycle deletes that should not force navigation.
     */
    fun forgetMemoIfSelected(memoId: String): RecordsFeatureStateHolder {
        val wasSelected = selection.selectedMemo?.id == memoId
        return copy(
            selection = selection.clearIfSelected(memoId),
            summary = if (wasSelected) {
                summary.replaceSummary(null)
            } else {
                summary
            },
        )
    }

    /**
     * Inserts [memo] into the visible cache/search results and opens it as the
     * detail presentation target. Used by source-record navigation.
     */
    fun absorbVisibleMemo(
        memo: Memo,
        summary: MemoAI? = null,
        filter: MemoListFilter = browse.filter,
    ): RecordsFeatureStateHolder {
        val cached = memosForFilter(
            collection.records.filter { it.id != memo.id } + memo,
            filter,
        )
        return presentMemoDetail(
            memo = memo,
            summary = summary,
            summaryLoading = false,
        ).copy(
            collection = collection.replace(cached),
            search = search.mergeResultMemo(memo, filter),
        )
    }

    /**
     * Opens a new or duplicated editor draft without a selected memo.
     */
    fun beginNewEditorDraft(
        draftContent: String,
        draftEntryDate: String,
        initialDraftContent: String = "",
        initialDraftEntryDate: String = draftEntryDate,
    ): RecordsFeatureStateHolder {
        val cleared = clearPresentedMemo()
        return cleared.copy(
            editor = cleared.editor.open(
                draftContent = draftContent,
                draftEntryDate = draftEntryDate,
                initialDraftContent = initialDraftContent,
                initialDraftEntryDate = initialDraftEntryDate,
            ),
        )
    }

    /**
     * Opens the editor for an existing memo while keeping that memo selected.
     */
    fun beginMemoEditor(
        memo: Memo,
        draftContent: String,
        draftEntryDate: String,
        initialDraftContent: String,
        initialDraftEntryDate: String,
        summaryLoading: Boolean = false,
    ): RecordsFeatureStateHolder {
        val presented = presentMemo(memo, summaryLoading = summaryLoading)
        return presented.copy(
            editor = presented.editor.open(
                draftContent = draftContent,
                draftEntryDate = draftEntryDate,
                initialDraftContent = initialDraftContent,
                initialDraftEntryDate = initialDraftEntryDate,
            ),
        )
    }

    /** Updates editor content without exposing the nested holder to hosts. */
    fun updateEditorContent(value: String): RecordsFeatureStateHolder {
        return copy(editor = editor.updateContent(value))
    }

    /** Updates editor entry date without exposing the nested holder to hosts. */
    fun updateEditorEntryDate(value: String): RecordsFeatureStateHolder {
        return copy(editor = editor.updateEntryDate(value))
    }

    /** Updates Markdown preview presentation through the feature aggregate. */
    fun setEditorMarkdownPreview(value: Boolean): RecordsFeatureStateHolder {
        return copy(editor = editor.setMarkdownPreview(value))
    }

    /** Appends a shared Markdown toolbar snippet through the feature aggregate. */
    fun appendEditorFormattedSnippet(snippet: String): RecordsFeatureStateHolder {
        return copy(editor = editor.appendFormattedSnippet(snippet))
    }

    /** Begins one attachment upload owned by the active editor session. */
    fun beginEditorAttachmentUpload(
        expectedSessionId: Long,
    ): RecordsFeatureStateHolder? {
        val nextEditor = editor.beginAttachmentUpload(expectedSessionId) ?: return null
        return copy(editor = nextEditor)
    }

    /** Appends an uploaded attachment only while the captured editor session owns it. */
    fun appendEditorAttachmentSnippet(
        expectedSessionId: Long,
        snippet: String,
    ): RecordsFeatureStateHolder {
        if (!editor.canApplyAttachmentUpload(expectedSessionId)) {
            return this
        }
        return copy(editor = editor.appendAttachmentSnippet(snippet))
    }

    /** Finishes attachment-upload presentation for the captured editor session. */
    fun finishEditorAttachmentUpload(
        expectedSessionId: Long,
    ): RecordsFeatureStateHolder {
        return copy(editor = editor.finishAttachmentUpload(expectedSessionId))
    }

    /**
     * Prepares a fresh list query: empty cache, reset cursor, and optional
     * refresh-loading presentation before the host begins a real refresh request.
     */
    fun resetVisibleList(
        markLoading: Boolean = true,
    ): RecordsFeatureStateHolder {
        return copy(
            collection = collection.clear(),
            pagination = pagination.copy(nextCursor = "", loadingMore = false),
            refresh = if (markLoading) {
                refresh.copy(status = RecordsRefreshStatus.Loading)
            } else {
                refresh.cancel()
            },
        )
    }

    /**
     * Replaces the visible snapshot and pagination cursor without advancing the
     * mutation generation. Used by refresh and offline snapshot hydration.
     */
    fun replaceVisibleRecords(
        records: List<Memo>,
        nextCursor: String = "",
    ): RecordsFeatureStateHolder {
        return copy(
            collection = collection.replace(records),
            pagination = pagination.copy(nextCursor = nextCursor, loadingMore = false),
        )
    }

    /**
     * Appends a validated page onto the visible cache. Callers must already have
     * completed pagination ownership for [nextCursor].
     */
    fun appendVisiblePage(
        pageRecords: List<Memo>,
        nextCursor: String,
        filter: MemoListFilter = browse.filter,
    ): RecordsFeatureStateHolder {
        val merged = memosForFilter(collection.records + pageRecords, filter)
        return copy(
            collection = collection.replace(merged),
            pagination = pagination.copy(nextCursor = nextCursor, loadingMore = false),
        )
    }

    /**
     * Applies a canonical create/update/lifecycle memo to the visible cache and
     * invalidates list loads plus search ownership bound to the previous cache.
     */
    fun applyCanonicalMemo(memo: Memo): RecordsFeatureStateHolder {
        return copy(
            collection = collection.applyMemo(memo, browse.filter),
            search = search.invalidateForMemoChange(memo, browse.filter),
            pagination = pagination.cancel(),
            refresh = refresh.cancel(),
            selection = selection.mergeMemo(memo),
        )
    }

    /**
     * Applies a canonical memo and replaces detail summary presentation in one
     * transition after a validated detail response.
     */
    fun completePresentedDetail(
        memo: Memo,
        summary: MemoAI?,
    ): RecordsFeatureStateHolder {
        val applied = applyCanonicalMemo(memo)
        return applied.copy(
            summary = applied.summary.completeDetail(summary),
        )
    }

    /**
     * Marks the list surface as loading before the host starts a real refresh
     * request identity. Does not clear the visible cache.
     */
    fun markListLoading(): RecordsFeatureStateHolder {
        return copy(refresh = refresh.copy(status = RecordsRefreshStatus.Loading))
    }

    /**
     * Clears load-more busy presentation after a failed or cancelled page request.
     */
    fun stopLoadingMore(): RecordsFeatureStateHolder {
        return copy(pagination = pagination.copy(loadingMore = false))
    }

    /**
     * Cancels in-flight pagination ownership without changing the visible cache.
     */
    fun cancelPagination(): RecordsFeatureStateHolder {
        return copy(pagination = pagination.cancel())
    }

    /**
     * Updates calendar month selection and clears the selected day.
     */
    fun selectCalendarMonth(
        year: Int,
        month: Int,
    ): RecordsFeatureStateHolder {
        return copy(browse = browse.selectMonth(year, month))
    }

    /**
     * Updates the selected calendar day key.
     */
    fun selectCalendarDay(date: String?): RecordsFeatureStateHolder {
        return copy(browse = browse.selectCalendarDate(date))
    }

    /**
     * Accepts a validated detail-request selection and starts optional summary
     * loading for the detail/editor destination.
     */
    fun acceptDetailRequest(
        selection: RecordsSelectionStateHolder,
        loadSummary: Boolean,
    ): RecordsFeatureStateHolder {
        return copy(
            selection = selection,
            summary = summary.beginDetailLoad(loadSummary = loadSummary),
        )
    }

    /**
     * Stops detail-summary loading without replacing the selected memo.
     */
    fun finishDetailSummary(): RecordsFeatureStateHolder {
        return copy(summary = summary.finishDetail())
    }

    /**
     * Replaces the selected memo when it matches [memoId], used after explicit
     * conflict resolution that keeps the user on the current destination.
     */
    fun replaceSelectedMemo(
        memoId: String,
        memo: Memo?,
    ): RecordsFeatureStateHolder {
        return copy(selection = selection.replaceIfSelected(memoId, memo))
    }

    /**
     * Resets list surface and search after the semantic filter changes. Selection
     * and summary are left to the host when navigation context also changes.
     */
    fun resetForFilterChange(): RecordsFeatureStateHolder {
        return resetVisibleList(markLoading = true).copy(
            search = search.clear(),
        )
    }

    /**
     * Applies a semantic list filter and resets the visible list, search, and
     * selected-memo presentation for a fresh query.
     */
    fun applyListFilter(filter: MemoListFilter): RecordsFeatureStateHolder {
        return copy(browse = browse.selectFilter(filter))
            .resetForFilterChange()
            .clearPresentedMemo()
    }

    /**
     * Applies list/calendar view mode. When [resetFilter] is true, the visible
     * list is prepared for a fresh unarchived calendar query. Calendar mode
     * always clears search ownership and selected-memo presentation.
     */
    fun applyViewMode(
        mode: MemoViewMode,
        resetFilter: Boolean,
    ): RecordsFeatureStateHolder {
        val browsed = copy(browse = browse.selectViewMode(mode))
        val withList = if (resetFilter) {
            browsed.resetVisibleList(markLoading = true)
        } else {
            browsed
        }
        return withList.clearPresentedMemo().copy(
            search = if (mode == MemoViewMode.Calendar) {
                withList.search.clear()
            } else {
                withList.search
            },
        )
    }
}
