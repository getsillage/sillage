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
     * Optionally stops an in-flight attachment upload or resets the editor draft.
     */
    fun clearPresentedMemo(
        stopAttachmentUpload: Boolean = false,
        resetEditorEntryDate: String? = null,
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
     * Resets list surface and search after the semantic filter changes. Selection
     * and summary are left to the host when navigation context also changes.
     */
    fun resetForFilterChange(): RecordsFeatureStateHolder {
        return resetVisibleList(markLoading = true).copy(
            search = search.clear(),
        )
    }
}
