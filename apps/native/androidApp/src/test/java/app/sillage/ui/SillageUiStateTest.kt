package app.sillage.ui

import app.sillage.features.settings.AIProfileDraft
import app.sillage.features.settings.AIProfilesMutationStateHolder
import app.sillage.features.settings.AISettingsLoadStateHolder
import app.sillage.features.settings.SettingsFeatureStateHolder
import app.sillage.core.domain.ask.AskMessage
import app.sillage.features.ask.AskConversationStateHolder
import app.sillage.features.ask.AskComposerStateHolder
import app.sillage.features.ask.AskFeatureStateHolder
import app.sillage.features.ask.AskLoadStateHolder
import app.sillage.features.ask.AskMemoSaveStateHolder
import app.sillage.features.ask.AskSourceNavigationStateHolder
import app.sillage.features.ask.AskStreamStateHolder
import app.sillage.features.ask.AskSessionStateHolder
import app.sillage.features.ask.AskVariantStateHolder
import app.sillage.features.settings.AIAutoSummaryStateHolder
import app.sillage.core.application.records.RecordsPageQuery
import app.sillage.core.application.records.RecordsQueryScope
import app.sillage.core.application.records.RecordsSearchQuery
import app.sillage.core.application.records.RecordDetail
import app.sillage.core.domain.records.Memo
import app.sillage.core.domain.records.MemoAI
import app.sillage.features.records.MemoListFilter
import app.sillage.features.records.MemoViewMode
import app.sillage.features.records.RecordsAttachmentOpenStateHolder
import app.sillage.features.records.RecordsCollectionStateHolder
import app.sillage.features.records.RecordsEditorStateHolder
import app.sillage.features.records.RecordsFeatureStateHolder
import app.sillage.features.records.RecordsMutationStateHolder
import app.sillage.features.records.RecordsPaginationStateHolder
import app.sillage.features.records.RecordsRefreshStateHolder
import app.sillage.features.records.RecordsSearchStateHolder
import app.sillage.features.records.RecordsSelectionStateHolder
import app.sillage.features.records.RecordsSummaryStateHolder
import app.sillage.data.SessionStore
import app.sillage.features.sync.MemoSyncConflictItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SillageUiStateTest {
    @Test
    fun unchangedEditorDraftIsNotDirty() {
        val state = editorState(
            draftContent = "原始内容",
            initialDraftContent = "原始内容",
        )

        assertFalse(state.hasUnsavedMemoDraft())
    }

    @Test
    fun contentOrDateChangeMarksEditorDraftDirty() {
        val contentChanged = editorState(
            draftContent = "修改后",
            initialDraftContent = "修改前",
        )
        val dateChanged = editorState(
            draftEntryDate = "2026-07-11",
            initialDraftEntryDate = "2026-07-10",
        )

        assertTrue(contentChanged.hasUnsavedMemoDraft())
        assertTrue(dateChanged.hasUnsavedMemoDraft())
    }

    @Test
    fun draftOutsideEditorDoesNotRequestDiscardConfirmation() {
        val state = editorState(
            draftContent = "修改后",
            initialDraftContent = "修改前",
        ).copy(screen = Screen.Memos)

        assertFalse(state.hasUnsavedMemoDraft())
    }

    @Test
    fun memoEditorActionsAreDisabledWhileBusyOrUploading() {
        assertTrue(editorState().canRunMemoEditorAction())
        assertFalse(editorState().copy(loading = true).canRunMemoEditorAction())
        val uploading = editorState().let {
            it.copy(
                records = it.records.copy(editor = it.records.editor.copy(uploadingAttachment = true)),
            )
        }
        assertFalse(uploading.canRunMemoEditorAction())
        assertFalse(editorState().copy(screen = Screen.Memos).canRunMemoEditorAction())
        assertFalse(
            editorState().let { base -> base.copy(
                records = base.records.copy(
                    selection = RecordsSelectionStateHolder(selectedMemo = memo()),
                    mutation = RecordsMutationStateHolder(setOf("memo-1")),
                ),
            ) }.canRunMemoEditorAction(),
        )
    }

    @Test
    fun memoEditorBusyReasonOnlyCoversBlockingOperations() {
        val selected = editorState().let { base -> base.copy(
            records = base.records.copy(selection = RecordsSelectionStateHolder(selectedMemo = memo())),
        ) }

        assertEquals(null, selected.memoEditorBusyReason())
        assertEquals(
            MemoEditorBusyReason.AttachmentUpload,
            selected.copy(
                records = selected.records.copy(editor = selected.records.editor.copy(uploadingAttachment = true)),
            ).memoEditorBusyReason(),
        )
        assertEquals(
            MemoEditorBusyReason.Operation,
            editorState().copy(loading = true).memoEditorBusyReason(),
        )
        assertEquals(
            MemoEditorBusyReason.Operation,
            selected.copy(
                records = selected.records.copy(mutation = RecordsMutationStateHolder(setOf("memo-1"))),
            )
                .memoEditorBusyReason(),
        )
        assertEquals(
            null,
            selected.copy(
                records = selected.records.copy(mutation = RecordsMutationStateHolder(setOf("memo-2"))),
            )
                .memoEditorBusyReason(),
        )
        assertEquals(
            null,
            selected.copy(
                records = selected.records.copy(summary = selected.records.summary.copy(loading = true)),
            )
                .memoEditorBusyReason(),
        )
        assertEquals(
            null,
            selected.copy(
                records = selected.records.copy(
                    attachmentOpen = RecordsAttachmentOpenStateHolder(
                    path = "/attachments/file-1",
                    requestId = 1,
                ),
                ),
            ).memoEditorBusyReason(),
        )
        assertEquals(null, selected.copy(screen = Screen.Memos, loading = true).memoEditorBusyReason())
    }

    @Test
    fun memoEditorBackBlockedNoticeClearsOldErrorAndKeepsIdleStateUnchanged() {
        val uploading = editorState().let { base -> base.copy(
            records = base.records.copy(editor = RecordsEditorStateHolder(uploadingAttachment = true)),
            error = "旧错误",
            notice = "旧提示",
        ) }
        val operation = editorState().copy(loading = true)
        val idle = editorState().copy(error = "保留错误")

        val uploadFeedback = uploading.withMemoEditorBackBlockedNotice(
            attachmentUploadNotice = "附件仍在上传",
            operationNotice = "操作仍在进行",
        )
        val operationFeedback = operation.withMemoEditorBackBlockedNotice(
            attachmentUploadNotice = "附件仍在上传",
            operationNotice = "操作仍在进行",
        )

        assertEquals(null, uploadFeedback.error)
        assertEquals("附件仍在上传", uploadFeedback.notice)
        assertEquals("操作仍在进行", operationFeedback.notice)
        assertEquals(
            idle,
            idle.withMemoEditorBackBlockedNotice(
                attachmentUploadNotice = "附件仍在上传",
                operationNotice = "操作仍在进行",
            ),
        )
    }

    @Test
    fun clientContextChangesAreBlockedByActiveOperations() {
        val idle = editorState().copy(screen = Screen.AISettings)

        assertFalse(idle.hasClientContextOperationInProgress())
        assertTrue(
            idle.copy(
                records = idle.records.copy(summary = idle.records.summary.copy(loading = true)),
            )
                .hasClientContextOperationInProgress(),
        )
        assertTrue(
            idle.copy(
                records = idle.records.copy(mutation = RecordsMutationStateHolder(setOf("memo-1"))),
            )
                .hasClientContextOperationInProgress(),
        )
        assertTrue(
            idle.copy(
                ask = idle.ask.copy(memoSave = AskMemoSaveStateHolder(savingMessageId = "answer-1")),
            )
                .hasClientContextOperationInProgress(),
        )
        assertTrue(
            idle.copy(
                settings = idle.settings.copy(profilesMutation = idle.settings.profilesMutation.copy(saving = true)),
            )
                .hasClientContextOperationInProgress(),
        )
        assertTrue(idle.withAIAutoSummary(saving = true).hasClientContextOperationInProgress())
    }

    @Test
    fun attachmentResultOnlyAppliesToActiveUploadingEditorSession() {
        val uploading = editorState().let { base -> base.copy(
            records = base.records.copy(
                editor = RecordsEditorStateHolder(
                sessionId = 7,
                uploadingAttachment = true,
            ),
            ),
        ) }

        assertTrue(uploading.canApplyAttachmentUpload(7))
        assertFalse(uploading.canApplyAttachmentUpload(6))
        assertFalse(
            uploading.copy(
                records = uploading.records.copy(editor = uploading.records.editor.copy(sessionId = 8)),
            )
                .canApplyAttachmentUpload(7),
        )
        assertFalse(uploading.copy(screen = Screen.Memos).canApplyAttachmentUpload(7))
        assertFalse(
            uploading.copy(
                records = uploading.records.copy(editor = uploading.records.editor.copy(uploadingAttachment = false)),
            ).canApplyAttachmentUpload(7),
        )
    }

    @Test
    fun rootAttachmentOpenRequestUsesAggregateIdentityChecks() {
        val idle = editorState()
        val request = checkNotNull(
            idle.nextAttachmentOpenRequest("/api/v1/attachments/file-1"),
        )

        val opening = checkNotNull(idle.beginAttachmentOpenRequest(request))
        val completed = opening.completeAttachmentOpenRequest(request.requestId)

        assertTrue(opening.canHandleAttachmentOpen(request.requestId))
        assertEquals("/api/v1/attachments/file-1", opening.openingAttachmentPath)
        assertEquals(null, completed.openingAttachmentPath)
        assertFalse(completed.canHandleAttachmentOpen(request.requestId))
        assertEquals(null, completed.beginAttachmentOpenRequest(request))
    }

    @Test
    fun leavingAttachmentContextInvalidatesAQueuedOpenEvent() {
        val opening = editorState().let { base -> base.copy(
            records = base.records.copy(
                attachmentOpen = RecordsAttachmentOpenStateHolder(
                path = "/api/v1/attachments/file-1",
                requestId = 8,
            ),
            ),
        ) }

        assertTrue(opening.canHandleAttachmentOpen(8))
        val invalidated = opening.invalidateAttachmentOpenRequest()

        assertEquals(null, invalidated.openingAttachmentPath)
        assertEquals(9L, invalidated.attachmentOpenRequestId)
        assertFalse(invalidated.canHandleAttachmentOpen(8))
        assertEquals(invalidated, invalidated.invalidateAttachmentOpenRequest())
    }

    @Test
    fun stoppingAskKeepsGeneratedContentAndAddsFeedback() {
        val streaming = editorState().let { base -> base.copy(
            screen = Screen.Ask,
            ask = base.ask.copy(
                stream = AskStreamStateHolder(
                sending = true,
                streaming = true,
                liveAnswer = "已生成的部分",
            ),
            ),
            error = "旧错误",
        ) }

        val stopped = streaming.withAskStreamingStoppedNotice("已停止生成")

        assertEquals("已生成的部分", stopped.askLiveAnswer)
        assertTrue(stopped.askSending)
        assertTrue(stopped.askStreaming)
        assertEquals(null, stopped.error)
        assertEquals("已停止生成", stopped.notice)
        val idle = streaming.withAskStream(sending = false)
        assertEquals(idle, idle.withAskStreamingStoppedNotice("已停止生成"))
    }

    @Test
    fun memoPageRequestIsSingleFlightAndBoundToItsCursor() {
        val state = editorState().let { base -> base.copy(
            screen = Screen.Memos,
            appMode = SessionStore.MODE_ONLINE,
            records = base.records.copy(pagination = RecordsPaginationStateHolder(nextCursor = "cursor-1", requestId = 4)),
        ) }
        val request = requireNotNull(state.nextMemoPageRequest())
        val pending = requireNotNull(state.beginMemoPage(request))

        assertEquals("cursor-1", request.cursor)
        assertEquals(MemoListFilter.Unarchived, request.filter)
        assertEquals(null, pending.nextMemoPageRequest())
        assertTrue(pending.canApplyMemoPage(request))
        assertFalse(
            pending.copy(
                records = pending.records.copy(pagination = pending.records.pagination.copy(nextCursor = "cursor-2")),
            )
                .canApplyMemoPage(request),
        )
        assertFalse(
            pending.copy(
                records = pending.records.copy(pagination = pending.records.pagination.copy(requestId = request.requestId + 1)),
            ).canApplyMemoPage(request),
        )
        assertFalse(pending.copy(appMode = SessionStore.MODE_OFFLINE).canApplyMemoPage(request))
        assertFalse(
            pending.copy(clientContextGeneration = pending.clientContextGeneration + 1)
                .canApplyMemoPage(request),
        )
        assertFalse(
            pending.copy(
                records = pending.records.copy(browse = pending.records.browse.selectFilter(MemoListFilter.Archived)),
            ).canApplyMemoPage(request),
        )
        assertFalse(
            pending.copy(
                records = pending.records.copy(collection = pending.records.collection.copy(cacheGeneration = 1)),
            ).canApplyMemoPage(request),
        )
        assertFalse(
            pending.copy(
                records = pending.records.copy(pagination = pending.records.pagination.copy(loadingMore = false)),
            )
                .canApplyMemoPage(request),
        )
    }

    @Test
    fun rootStateOwnsSingleRecordsAggregateWithTransitionalSliceGetters() {
        val original = memo()
        val state = editorState().withRecords {
            it.copy(
                collection = RecordsCollectionStateHolder(records = listOf(original), cacheGeneration = 2),
                pagination = RecordsPaginationStateHolder(nextCursor = "cursor-9", loadingMore = true),
                selection = RecordsSelectionStateHolder(selectedMemo = original),
            )
        }

        assertEquals(listOf(original), state.records.collection.records)
        assertEquals(state.records.collection, state.recordsCollection)
        assertEquals(state.records.pagination, state.recordsPagination)
        assertEquals(state.records.selection, state.recordsSelection)
        assertEquals("cursor-9", state.memoNextCursor)
        assertEquals(2L, state.memoCacheGeneration)
        assertEquals(original, state.selectedMemo)

        val applied = state.applyMemoToCache(
            original.copy(content = "更新", version = 2, updatedAt = "2026-07-10T02:00:00Z"),
        )
        assertEquals(3L, applied.memoCacheGeneration)
        assertEquals(applied.records.collection, applied.recordsCollection)
        assertFalse(applied.loadingMoreMemos)
    }

    @Test
    fun rootRecordsTransitionKeepsHostStateWhileUpdatingEditorAggregate() {
        val state = editorState().copy(error = "keep")

        val updated = state.withRecords { records ->
            records
                .updateEditorContent("draft")
                .updateEditorEntryDate("2026-08-02")
                .setEditorMarkdownPreview(true)
                .appendEditorFormattedSnippet("**bold**")
        }

        assertEquals("draft **bold**", updated.draftContent)
        assertEquals("2026-08-02", updated.draftEntryDate)
        assertFalse(updated.markdownPreview)
        assertEquals("keep", updated.error)
        assertEquals(state.screen, updated.screen)
    }

    @Test
    fun syncAggregateOwnsConflictPresentationWithTransitionalGetter() {
        val state = editorState()
        assertEquals(emptyList<MemoSyncConflictItem>(), state.syncConflicts)
        assertEquals(state.sync.conflicts, state.syncConflictState)
    }

    @Test
    fun clearClientWorkspaceResetsRecordsSettingsAndAskTogether() {
        val memo = memo()
        val profile = AIProfileDraft(
            id = "p1",
            draftKey = "p1",
            name = "Profile",
            provider = "openai",
            baseUrl = "https://example.com",
            model = "model",
            enabled = true,
            active = true,
        )
        val state = editorState().copy(
            records = editorState().records.copy(
                collection = RecordsCollectionStateHolder(records = listOf(memo), cacheGeneration = 2),
                selection = RecordsSelectionStateHolder(selectedMemo = memo),
            ),
            settings = SettingsFeatureStateHolder(
                profilesMutation = AIProfilesMutationStateHolder(profiles = listOf(profile)),
                autoSummary = AIAutoSummaryStateHolder(enabled = true),
                load = AISettingsLoadStateHolder(loading = true),
            ),
            ask = AskFeatureStateHolder(
                conversation = AskConversationStateHolder(activeConversationId = "c1"),
                composer = AskComposerStateHolder(question = "问"),
                load = AskLoadStateHolder(loading = true),
                session = AskSessionStateHolder(generation = 3),
            ),
        )

        val cleared = state.clearClientWorkspace()

        assertEquals(emptyList<Memo>(), cleared.memos)
        assertEquals(2L, cleared.memoCacheGeneration)
        assertEquals(null, cleared.selectedMemo)
        assertEquals(emptyList<AIProfileDraft>(), cleared.aiProfiles)
        assertFalse(cleared.aiAutoSummary)
        assertFalse(cleared.aiSettingsLoading)
        assertEquals("", cleared.activeAskId)
        assertEquals("", cleared.askQuestion)
        assertFalse(cleared.askLoading)
        assertEquals(4L, cleared.askScreenSessionId)
        // host-only fields stay put
        assertEquals(state.screen, cleared.screen)
        assertEquals(state.loading, cleared.loading)
    }

    @Test
    fun canonicalMemoInvalidatesEarlierRefreshAndSearchRequests() {
        val original = memo()
        val initial = editorState().let { base -> base.copy(
            screen = Screen.Memos,
            appMode = SessionStore.MODE_ONLINE,
            records = base.records.copy(
                collection = RecordsCollectionStateHolder(records = listOf(original)),
                search = RecordsSearchStateHolder(
                query = "记录",
                results = listOf(original),
                resultQuery = "记录",
                completionEventId = 4,
            ),
            ),
        ) }
        val refresh = initial.nextMemoRefreshRequest()
        val search = requireNotNull(initial.nextMemoSearchRequest())
        val refreshing = requireNotNull(initial.beginMemoRefresh(refresh))
        val pending = refreshing.startMemoSearch(search)

        assertTrue(pending.canApplyMemoRefresh(refresh))
        assertTrue(pending.canApplyMemoSearch(search))
        assertFalse(
            pending.copy(clientContextGeneration = pending.clientContextGeneration + 1)
                .canApplyMemoRefresh(refresh),
        )
        assertFalse(
            pending.copy(clientContextGeneration = pending.clientContextGeneration + 1)
                .canApplyMemoSearch(search),
        )

        val canonical = original.copy(
            version = 2,
            updatedAt = "2026-07-10T02:00:00Z",
            favoritedAt = "2026-07-10T02:00:00Z",
        )
        val updated = pending.applyMemoToCache(canonical)

        assertEquals(1L, updated.memoCacheGeneration)
        assertFalse(updated.canApplyMemoRefresh(refresh))
        assertFalse(updated.canApplyMemoSearch(search))
        assertTrue(updated.memos.isEmpty())
        assertEquals(emptyList<Memo>(), updated.searchResults)
        assertEquals("", updated.searchResultQuery)
        assertEquals(4L, updated.searchCompletionEventId)
        assertEquals(null, updated.completedMemoSearch())
        assertFalse(updated.searching)
    }

    @Test
    fun lateMemoDetailDoesNotOverwriteCanonicalMutationAndStopsLoading() {
        val original = memo()
        val initial = editorState().let { base -> base.copy(
            screen = Screen.MemoDetail,
            appMode = SessionStore.MODE_ONLINE,
            records = base.records.copy(
                collection = RecordsCollectionStateHolder(records = listOf(original)),
                selection = RecordsSelectionStateHolder(selectedMemo = original),
                summary = RecordsSummaryStateHolder(loading = true),
            ),
        ) }
        val request = requireNotNull(initial.nextMemoDetailRequest(original.id))
        val pending = initial.startMemoDetailRequest(request)
        val canonical = original.copy(
            version = 2,
            updatedAt = "2026-07-10T02:00:00Z",
            favoritedAt = "2026-07-10T02:00:00Z",
        )
        val mutated = pending.applyMemoToCache(canonical)

        val completed = mutated.completeMemoDetailRequest(
            request,
            RecordDetail(memo = original, ai = null),
        )

        assertEquals(canonical, completed.selectedMemo)
        assertTrue(completed.memos.isEmpty())
        assertEquals(mutated.memoCacheGeneration, completed.memoCacheGeneration)
        assertFalse(completed.summaryLoading)
    }

    @Test
    fun currentMemoDetailAppliesMemoAndSummaryInOneStateTransition() {
        val original = memo()
        val initial = editorState().let { base -> base.copy(
            screen = Screen.MemoDetail,
            appMode = SessionStore.MODE_ONLINE,
            records = base.records.copy(
                collection = RecordsCollectionStateHolder(records = listOf(original)),
                selection = RecordsSelectionStateHolder(selectedMemo = original),
            ),
        ) }
        val request = requireNotNull(initial.nextMemoDetailRequest(original.id))
        val pending = initial.startMemoDetailRequest(request)
        val canonical = original.copy(
            version = 2,
            updatedAt = "2026-07-10T02:00:00Z",
            archivedAt = "2026-07-10T02:00:00Z",
        )

        val completed = pending.completeMemoDetailRequest(
            request,
            RecordDetail(memo = canonical, ai = null),
        )

        assertEquals(canonical, completed.selectedMemo)
        assertTrue(completed.memos.isEmpty())
        assertEquals(pending.memoCacheGeneration + 1, completed.memoCacheGeneration)
        assertFalse(completed.summaryLoading)
    }

    @Test
    fun supersededMemoDetailFailureOnlyStopsItsLoadingState() {
        val original = memo()
        val initial = editorState().let { base -> base.copy(
            screen = Screen.MemoDetail,
            appMode = SessionStore.MODE_ONLINE,
            records = base.records.copy(selection = RecordsSelectionStateHolder(selectedMemo = original)),
        ) }
        val request = requireNotNull(initial.nextMemoDetailRequest(original.id))
        val pending = initial.startMemoDetailRequest(request)
        val canonical = original.copy(
            version = 2,
            updatedAt = "2026-07-10T02:00:00Z",
            favoritedAt = "2026-07-10T02:00:00Z",
        )
        val mutated = pending.applyMemoToCache(canonical)

        val failed = mutated.failMemoDetailRequest(request, "旧请求失败")

        assertEquals(canonical, failed.selectedMemo)
        assertEquals(null, failed.error)
        assertFalse(failed.summaryLoading)
    }

    @Test
    fun memoSummaryRequestIsSingleFlightAndBoundToItsRecordContext() {
        val original = memo()
        val initial = editorState().let { base -> base.copy(
            screen = Screen.MemoDetail,
            appMode = SessionStore.MODE_OFFLINE,
            clientContextGeneration = 3,
            records = base.records.copy(
                selection = RecordsSelectionStateHolder(
                selectedMemo = original,
                detailRequestId = 11,
            ),
                editor = RecordsEditorStateHolder(sessionId = 7),
            ),
        ) }
        val request = requireNotNull(initial.nextMemoSummaryRequest())
        val pending = initial.startMemoSummaryRequest(request)

        assertTrue(pending.canApplyMemoSummaryRequest(request))
        assertEquals(null, pending.nextMemoSummaryRequest())
        assertFalse(
            pending.copy(
                records = pending.records.copy(selection = pending.records.selection.select(original.copy(id = "memo-2"))),
            )
                .canApplyMemoSummaryRequest(request),
        )
        assertFalse(
            pending.copy(
                records = pending.records.copy(selection = pending.records.selection.select(original.copy(version = 2))),
            )
                .canApplyMemoSummaryRequest(request),
        )
        assertFalse(pending.copy(screen = Screen.Memos).canApplyMemoSummaryRequest(request))
        assertFalse(
            pending.copy(clientContextGeneration = 4)
                .canApplyMemoSummaryRequest(request),
        )
        assertFalse(
            pending.copy(
                records = pending.records.copy(selection = pending.records.selection.copy(detailRequestId = 12)),
            )
                .canApplyMemoSummaryRequest(request),
        )

        val summary = memoAI("新总结")
        val completed = pending.completeMemoSummaryRequest(request, summary, "总结已生成")
        assertEquals(summary, completed.selectedSummary)
        assertFalse(completed.summaryLoading)
        assertEquals("总结已生成", completed.notice)

        val stale = pending.copy(screen = Screen.Memos)
        assertEquals(
            stale,
            stale.completeMemoSummaryRequest(request, summary, "不应出现"),
        )
        assertEquals(stale, stale.failMemoSummaryRequest(request, "旧请求失败"))

        val versionChanged = pending.copy(
            records = pending.records.copy(selection = pending.records.selection.select(original.copy(version = 2))),
        )
        val finished = versionChanged.finishMemoSummaryRequest(request)
        assertFalse(finished.summaryLoading)
        assertEquals(null, finished.selectedSummary)

        val invalidated = pending.invalidateMemoSummaryRequest()
        assertFalse(invalidated.summaryLoading)
        assertEquals(request.requestId + 1, invalidated.memoSummaryRequestId)
        assertEquals(invalidated, invalidated.finishMemoSummaryRequest(request))
    }

    @Test
    fun searchFailureKeepsLoadedResultsAndCanBeRetried() {
        val loaded = listOf(memo())
        val initial = editorState().let { base -> base.copy(
            screen = Screen.Memos,
            records = base.records.copy(
                search = RecordsSearchStateHolder(
                query = "记录",
                results = loaded,
                resultQuery = "记录",
                completionEventId = 4,
            ),
            ),
        ) }
        val request = requireNotNull(initial.nextMemoSearchRequest())
        val pending = initial.startMemoSearch(request)

        val failed = pending.failMemoSearch(request, "网络错误")

        assertEquals(loaded, failed.searchResults)
        assertEquals("", failed.searchResultQuery)
        assertEquals("记录", failed.searchFailureQuery)
        assertEquals(4L, failed.searchCompletionEventId)
        assertFalse(failed.searching)
        assertEquals("网络错误", failed.error)
        assertFalse(failed.canApplyMemoSearch(request))
        val retry = requireNotNull(failed.nextMemoSearchRequest())
        assertEquals(request.requestId + 1, retry.requestId)
        assertTrue(failed.startMemoSearch(retry).canApplyMemoSearch(retry))
    }

    @Test
    fun newerSearchAttemptSupersedesTheSameQuery() {
        val initial = editorState().let { base -> base.copy(screen = Screen.Memos, records = base.records.copy(search = RecordsSearchStateHolder(query = "记录"))) }
        val firstRequest = requireNotNull(initial.nextMemoSearchRequest())
        val first = initial.startMemoSearch(firstRequest)
        val secondRequest = requireNotNull(first.nextMemoSearchRequest())
        val second = first.startMemoSearch(secondRequest)

        assertTrue(first.canApplyMemoSearch(firstRequest))
        assertFalse(second.canApplyMemoSearch(firstRequest))
        assertTrue(second.canApplyMemoSearch(secondRequest))
        assertEquals(second, second.failMemoSearch(firstRequest, "旧请求失败"))
        assertEquals(second, second.completeMemoSearch(firstRequest, listOf(memo())))

        val completed = second.completeMemoSearch(secondRequest, listOf(memo()))
        assertEquals(1L, completed.searchCompletionEventId)
        assertFalse(completed.searching)
    }

    @Test
    fun completedSearchSummaryIsBoundToTheAppliedQuery() {
        val oldResults = listOf(memo(id = "memo-old"))
        val initial = editorState().let { base -> base.copy(
            screen = Screen.Memos,
            records = base.records.copy(
                search = RecordsSearchStateHolder(
                query = "新查询",
                results = oldResults,
                resultQuery = "旧查询",
                completionEventId = 4,
            ),
            ),
        ) }
        val request = requireNotNull(initial.nextMemoSearchRequest())
        val pending = initial.startMemoSearch(request)

        assertEquals(null, pending.completedMemoSearch())
        assertEquals(null, pending.currentMemoSearchResults())

        val results = listOf(memo(id = "memo-new"))
        val completed = pending.completeMemoSearch(request, results)

        assertEquals(results, completed.searchResults)
        assertEquals("新查询", completed.searchResultQuery)
        assertEquals(5L, completed.searchCompletionEventId)
        assertFalse(completed.searching)
        assertEquals(results, completed.currentMemoSearchResults())
        assertEquals(CompletedMemoSearch(query = "新查询", resultCount = 1), completed.completedMemoSearch())
        assertEquals(
            null,
            completed.copy(
                records = completed.records.copy(search = completed.records.search.copy(query = "又一查询")),
            ).currentMemoSearchResults(),
        )
        assertEquals(
            null,
            completed.copy(
                records = completed.records.copy(search = completed.records.search.copy(query = "又一查询")),
            ).completedMemoSearch(),
        )
        assertEquals(
            null,
            completed.copy(
                records = completed.records.copy(search = completed.records.search.copy(searching = true)),
            ).completedMemoSearch(),
        )
        assertEquals(completed.completedMemoSearch(), completed.copy(error = "无关错误").completedMemoSearch())

        val empty = pending.completeMemoSearch(request, emptyList())
        assertEquals(CompletedMemoSearch(query = "新查询", resultCount = 0), empty.completedMemoSearch())
        assertEquals(5L, empty.searchCompletionEventId)

        val stale = pending.copy(
            records = pending.records.copy(search = pending.records.search.copy(query = "其他查询")),
        )
        assertEquals(stale, stale.completeMemoSearch(request, results))
    }

    @Test
    fun failedSearchStateIsBoundToTheFailedQuery() {
        val failed = editorState().let { base -> base.copy(
            screen = Screen.Memos,
            records = base.records.copy(
                search = RecordsSearchStateHolder(
                query = "新查询",
                results = listOf(memo()),
                resultQuery = "旧查询",
                failureQuery = "新查询",
                searching = false,
            ),
            ),
            error = "网络错误",
        ) }

        assertEquals(null, failed.currentMemoSearchResults())
        assertTrue(failed.shouldShowMemoSearchFailure())
        assertTrue(failed.copy(error = null).shouldShowMemoSearchFailure())
        assertFalse(
            failed.copy(
                records = failed.records.copy(search = failed.records.search.copy(searching = true)),
            ).shouldShowMemoSearchFailure(),
        )
        assertFalse(
            failed.copy(
                records = failed.records.copy(search = failed.records.search.copy(failureQuery = "旧查询")),
            ).shouldShowMemoSearchFailure(),
        )
        assertFalse(
            failed.copy(
                records = failed.records.copy(search = failed.records.search.copy(resultQuery = "新查询")),
            ).shouldShowMemoSearchFailure(),
        )
        assertFalse(
            failed.copy(records = failed.records.copy(search = failed.records.search.copy(query = ""))).shouldShowMemoSearchFailure(),
        )
    }

    @Test
    fun memoListFiltersMapToApplicationAndSearchQueries() {
        assertEquals(
            RecordsPageQuery(RecordsQueryScope.Unarchived, cursor = "cursor-1"),
            MemoListFilter.Unarchived.recordsPageQuery(cursor = "cursor-1"),
        )
        assertEquals(
            RecordsPageQuery(RecordsQueryScope.Archived),
            MemoListFilter.Archived.recordsPageQuery(),
        )
        assertEquals(
            RecordsPageQuery(RecordsQueryScope.Favorited),
            MemoListFilter.Favorited.recordsPageQuery(),
        )
        assertEquals(
            RecordsPageQuery(RecordsQueryScope.Deleted),
            MemoListFilter.Deleted.recordsPageQuery(),
        )

        assertEquals(
            RecordsSearchQuery("query", RecordsQueryScope.Unarchived),
            MemoListFilter.Unarchived.recordsSearchQuery("query"),
        )
        assertEquals(
            RecordsSearchQuery("query", RecordsQueryScope.Archived),
            MemoListFilter.Archived.recordsSearchQuery("query"),
        )
        assertEquals(
            RecordsSearchQuery("query", RecordsQueryScope.Favorited),
            MemoListFilter.Favorited.recordsSearchQuery("query"),
        )
        assertEquals(
            RecordsSearchQuery("query", RecordsQueryScope.Deleted),
            MemoListFilter.Deleted.recordsSearchQuery("query"),
        )
    }

    @Test
    fun failedEmptyMemoLoadUsesFailureStateInsteadOfBusinessEmptyState() {
        val failed = editorState().let { base -> base.copy(
            screen = Screen.Memos,
            records = base.records.copy(
                collection = RecordsCollectionStateHolder(),
                refresh = RecordsRefreshStateHolder(status = MemoListLoadStatus.Failed),
            ),
        ) }

        assertTrue(failed.shouldShowMemoListLoadFailure())
        assertFalse(
            failed.copy(
                records = failed.records.copy(search = failed.records.search.copy(query = "记录")),
            ).shouldShowMemoListLoadFailure(),
        )
        assertFalse(
            failed.copy(
                records = failed.records.copy(refresh = failed.records.refresh.copy(status = MemoListLoadStatus.Loading)),
            ).shouldShowMemoListLoadFailure(),
        )
        assertFalse(
            failed.copy(
                records = failed.records.copy(refresh = failed.records.refresh.copy(status = MemoListLoadStatus.Idle)),
            ).shouldShowMemoListLoadFailure(),
        )
        assertFalse(
            failed.copy(
                records = failed.records.copy(collection = RecordsCollectionStateHolder(records = listOf(memo()))),
            ).shouldShowMemoListLoadFailure(),
        )
        assertFalse(
            failed.copy(
                records = failed.records.copy(search = failed.records.search.copy(results = emptyList())),
            ).shouldShowMemoListLoadFailure(),
        )
    }

    @Test
    fun autoSummaryRequestIsSingleFlightAndBoundToItsMode() {
        val idle = editorState().let { base -> base.copy(
            screen = Screen.AISettings,
            appMode = SessionStore.MODE_ONLINE,
            settings = base.settings.copy(autoSummary = AIAutoSummaryStateHolder(requestId = 4)),
        ) }
        val request = requireNotNull(idle.nextAIAutoSummaryRequest(true))
        val pending = idle.startAIAutoSummaryRequest(request)

        assertEquals(5L, request.requestId)
        assertFalse(request.previousValue)
        assertTrue(request.targetValue)
        assertTrue(pending.aiAutoSummary)
        assertTrue(pending.aiAutoSummarySaving)
        assertEquals(null, pending.nextAIAutoSummaryRequest(false))
        assertTrue(pending.canApplyAIAutoSummaryRequest(request))
        assertFalse(
            pending.copy(appMode = SessionStore.MODE_OFFLINE)
                .canApplyAIAutoSummaryRequest(request),
        )
        assertFalse(
            pending.copy(clientContextGeneration = pending.clientContextGeneration + 1)
                .canApplyAIAutoSummaryRequest(request),
        )

        val invalidated = pending.invalidateAIAutoSummaryRequest()
        assertFalse(invalidated.aiAutoSummarySaving)
        assertEquals(6L, invalidated.aiAutoSummaryRequestId)
        assertFalse(invalidated.canApplyAIAutoSummaryRequest(request))
        assertEquals(null, idle.nextAIAutoSummaryRequest(false))
        assertEquals(
            null,
            idle.copy(settings = idle.settings.copy(load = idle.settings.load.copy(loading = true)))
                .nextAIAutoSummaryRequest(true),
        )
    }

    @Test
    fun autoSummaryCompletionAndFailurePreserveProfileDrafts() {
        val profiles = listOf(AIProfileDraft(id = "p1", name = "未保存名称"))
        val idle = editorState().let { base -> base.copy(
            screen = Screen.AISettings,
            settings = base.settings.copy(
                profilesMutation = AIProfilesMutationStateHolder(profiles = profiles),
                autoSummary = AIAutoSummaryStateHolder(enabled = false),
            ),
        ) }
        val request = requireNotNull(idle.nextAIAutoSummaryRequest(true))
        val pending = idle.startAIAutoSummaryRequest(request)

        val completed = pending.completeAIAutoSummaryRequest(request, savedValue = true)
        assertTrue(completed.aiAutoSummary)
        assertFalse(completed.aiAutoSummarySaving)
        assertEquals(profiles, completed.aiProfiles)

        val failed = pending.failAIAutoSummaryRequest(request)
        assertFalse(failed.aiAutoSummary)
        assertFalse(failed.aiAutoSummarySaving)
        assertEquals(profiles, failed.aiProfiles)

        val invalidated = pending.invalidateAIAutoSummaryRequest()
        assertEquals(invalidated, invalidated.completeAIAutoSummaryRequest(request, savedValue = true))
        assertEquals(invalidated, invalidated.failAIAutoSummaryRequest(request))
    }

    @Test
    fun autoSummaryCannotStartWhileProfilesAreSaving() {
        val idle = editorState().let { base -> base.copy(screen = Screen.AISettings, settings = base.settings.copy(autoSummary = AIAutoSummaryStateHolder(enabled = false))) }
        val autoSummaryRequest = requireNotNull(idle.nextAIAutoSummaryRequest(true))
        val profiles = listOf(AIProfileDraft(id = "profile-1", name = "新名称"))
        val profilesRequest = requireNotNull(idle.nextAIProfilesMutationRequest(profiles))
        val saving = idle.startAIProfilesMutation(profilesRequest)
        val autoSummarySaving = idle.startAIAutoSummaryRequest(autoSummaryRequest)

        assertEquals(null, saving.nextAIAutoSummaryRequest(true))
        assertEquals(saving, saving.startAIAutoSummaryRequest(autoSummaryRequest))
        assertEquals(null, autoSummarySaving.nextAIProfilesMutationRequest(profiles))
    }

    @Test
    fun aiProfilesMutationIsSingleFlightAndInvalidatesEarlierLoadGeneration() {
        val original = listOf(AIProfileDraft(id = "profile-1", name = "原名称"))
        val edited = listOf(AIProfileDraft(id = "profile-1", name = "新名称"))
        val idle = editorState().let { base -> base.copy(
            screen = Screen.AISettings,
            appMode = SessionStore.MODE_ONLINE,
            settings = base.settings.copy(
                profilesMutation = AIProfilesMutationStateHolder(
                profiles = original,
                requestId = 6,
            ),
            ),
        ) }
        val earlierLoadGeneration = idle.aiSettingsRequestId
        val request = requireNotNull(idle.nextAIProfilesMutationRequest(edited))

        val pending = idle.startAIProfilesMutation(request)

        assertEquals(earlierLoadGeneration + 1, request.requestId)
        assertEquals(request.requestId, pending.aiSettingsRequestId)
        assertEquals(edited, pending.aiProfiles)
        assertTrue(pending.aiSettingsSaving)
        assertTrue(pending.canApplyAIProfilesMutation(request))
        assertEquals(null, pending.nextAIProfilesMutationRequest(original))
        assertEquals(pending, pending.startAIProfilesMutation(request))
        assertFalse(
            pending.copy(appMode = SessionStore.MODE_OFFLINE)
                .canApplyAIProfilesMutation(request),
        )
        assertFalse(
            pending.copy(clientContextGeneration = pending.clientContextGeneration + 1)
                .canApplyAIProfilesMutation(request),
        )
    }

    @Test
    fun aiProfilesFailureOnlyRollsBackItsOwnSnapshot() {
        val original = listOf(AIProfileDraft(id = "profile-1", name = "原名称"))
        val firstPending = listOf(AIProfileDraft(id = "profile-1", name = "首次编辑"))
        val firstSaved = listOf(AIProfileDraft(id = "profile-1", name = "服务端名称"))
        val initial = editorState().let { base -> base.copy(
            screen = Screen.AISettings,
            settings = base.settings.copy(profilesMutation = AIProfilesMutationStateHolder(profiles = original)),
        ) }
        val firstRequest = requireNotNull(initial.nextAIProfilesMutationRequest(firstPending))
        val firstCompleted = initial.startAIProfilesMutation(firstRequest)
            .completeAIProfilesMutation(firstRequest, firstSaved)
        val secondPending = listOf(AIProfileDraft(id = "profile-1", name = "再次编辑"))
        val secondRequest = requireNotNull(
            firstCompleted.nextAIProfilesMutationRequest(secondPending),
        )
        val secondSaving = firstCompleted.startAIProfilesMutation(secondRequest)

        assertEquals(secondSaving, secondSaving.failAIProfilesMutation(firstRequest))

        val secondFailed = secondSaving.failAIProfilesMutation(secondRequest)
        assertEquals(firstSaved, secondFailed.aiProfiles)
        assertFalse(secondFailed.aiSettingsSaving)

        val laterDraft = listOf(AIProfileDraft(id = "profile-1", name = "请求后继续编辑"))
        val changedWhileSaving = secondSaving.copy(
            settings = secondSaving.settings.copy(profilesMutation = secondSaving.settings.profilesMutation.replace(laterDraft)),
        )
        val preserved = changedWhileSaving.failAIProfilesMutation(secondRequest)
        assertEquals(laterDraft, preserved.aiProfiles)
        assertFalse(preserved.aiSettingsSaving)
    }

    @Test
    fun explicitAIProfilesSaveFailurePreservesTheWholeStagedDraft() {
        val staged = listOf(
            AIProfileDraft(id = "profile-2", name = "新的默认档案", active = true),
            AIProfileDraft(name = "尚未保存的新档案"),
        )
        val idle = editorState().let { base -> base.copy(
            screen = Screen.AISettings,
            settings = base.settings.copy(profilesMutation = AIProfilesMutationStateHolder(profiles = staged)),
        ) }
        val request = requireNotNull(
            idle.nextAIProfilesMutationRequest(
                pendingProfiles = staged,
                submittedProfiles = staged,
            ),
        )

        val failed = idle.startAIProfilesMutation(request)
            .failAIProfilesMutation(request)

        assertEquals(staged, failed.aiProfiles)
        assertFalse(failed.aiSettingsSaving)
    }

    @Test
    fun askMemoSaveIsSingleFlightAcrossMessages() {
        val firstAnswer = askMessage(id = "answer-1", content = "第一条回答")
        val secondAnswer = askMessage(id = "answer-2", content = "第二条回答")
        val idle = editorState().let { base -> base.copy(
            screen = Screen.Ask,
            ask = base.ask.copy(
                conversation = AskConversationStateHolder(
                activeConversationId = "conversation-1",
                headMessageId = firstAnswer.id,
                messages = listOf(firstAnswer, secondAnswer),
            ),
            ),
        ) }
        val request = requireNotNull(
            idle.nextAskMemoSaveRequest(firstAnswer, memoContent = "第一条回答"),
        )

        val pending = idle.startAskMemoSave(request)

        assertEquals(firstAnswer.id, pending.askSavingMessageId)
        assertTrue(pending.canApplyAskMemoSave(request))
        assertTrue(pending.withAskLoad(loading = true).canApplyAskMemoSave(request))
        assertEquals(
            null,
            pending.nextAskMemoSaveRequest(firstAnswer, memoContent = "第一条回答"),
        )
        assertEquals(
            null,
            pending.nextAskMemoSaveRequest(secondAnswer, memoContent = "第二条回答"),
        )
        assertEquals(pending, pending.startAskMemoSave(request))
    }

    @Test
    fun askComposerUpdatesFlowThroughAggregateWrappers() {
        val original = editorState().copy(
            screen = Screen.Ask,
            notice = "保留提示",
        )

        val updated = original
            .withAskQuestion("问题")
            .withAskContextScope("recent_30_days")
            .withAskSourceKind("summaries")

        assertEquals("问题", updated.askQuestion)
        assertEquals("recent_30_days", updated.askScope)
        assertEquals("summaries", updated.askSourceKind)
        assertEquals(original.notice, updated.notice)
        assertEquals(original.records, updated.records)
    }

    @Test
    fun lateAskMemoSaveCannotApplyButStillClearsItsBusyState() {
        val answer = askMessage(id = "answer-1", content = "原回答")
        val idle = editorState().let { base -> base.copy(
            screen = Screen.Ask,
            ask = base.ask.copy(
                conversation = AskConversationStateHolder(
                activeConversationId = "conversation-1",
                headMessageId = answer.id,
                messages = listOf(answer),
            ),
                session = AskSessionStateHolder(generation = 4),
            ),
        ) }
        val request = requireNotNull(
            idle.nextAskMemoSaveRequest(answer, memoContent = "保存内容"),
        )
        val pending = idle.startAskMemoSave(request)
        val staleStates = listOf(
            "screen" to pending.copy(screen = Screen.Memos),
            "session" to pending.withAskSession(generation = 5),
            "conversation" to pending.withAskConversation(activeConversationId = "conversation-2"),
            "head" to pending.withAskConversation(headMessageId = "answer-2"),
            "client context" to pending.copy(
                clientContextGeneration = pending.clientContextGeneration + 1,
            ),
            "message" to pending.withAskConversation(
                messages = listOf(answer.copy(content = "替换后的回答")),
            ),
        )

        staleStates.forEach { (context, stale) ->
            assertFalse(context, stale.canApplyAskMemoSave(request))
            assertEquals(context, "", stale.finishAskMemoSave(request).askSavingMessageId)
        }
    }

    @Test
    fun askStreamCallbacksRequireOriginalConversationAndSession() {
        val state = editorState().let { base -> base.copy(
            screen = Screen.Ask,
            ask = base.ask.copy(
                conversation = AskConversationStateHolder(activeConversationId = "conversation-1"),
                session = AskSessionStateHolder(generation = 3),
                stream = AskStreamStateHolder(requestId = 8),
            ),
        ) }
        val request = requireNotNull(state.nextAskStreamRequest())
        val pending = state.withAskStream(sending = true, requestId = request.requestId)

        assertTrue(pending.canApplyAskStream(request))
        assertEquals(null, pending.nextAskStreamRequest())
        assertFalse(
            pending.withAskConversation(activeConversationId = "conversation-2")
                .canApplyAskStream(request),
        )
        assertFalse(pending.withAskSession(generation = 4).canApplyAskStream(request))
        assertFalse(
            pending.withAskStream(requestId = request.requestId + 1)
                .canApplyAskStream(request),
        )
        assertFalse(pending.copy(appMode = SessionStore.MODE_OFFLINE).canApplyAskStream(request))
        assertFalse(
            pending.copy(clientContextGeneration = pending.clientContextGeneration + 1)
                .canApplyAskStream(request),
        )
        assertFalse(pending.withAskStream(sending = false).canApplyAskStream(request))
        assertEquals(null, state.withAskLoad(loading = true).nextAskStreamRequest())
        assertEquals(null, state.withAskVariant(loading = true).nextAskStreamRequest())
    }

    @Test
    fun askVariantCallbacksRequireOriginalRequestConversationSessionAndMode() {
        val state = editorState().let { base -> base.copy(
            screen = Screen.Ask,
            ask = base.ask.copy(
                conversation = AskConversationStateHolder(activeConversationId = "conversation-1"),
                session = AskSessionStateHolder(generation = 3),
                variant = AskVariantStateHolder(requestId = 8),
            ),
        ) }
        val request = requireNotNull(state.nextAskVariantRequest())
        val pending = state.withAskVariant(requestId = request.requestId, loading = true)

        assertTrue(pending.canApplyAskVariant(request))
        assertEquals(null, pending.nextAskVariantRequest())
        assertFalse(
            pending.withAskConversation(activeConversationId = "conversation-2")
                .canApplyAskVariant(request),
        )
        assertFalse(pending.withAskSession(generation = 4).canApplyAskVariant(request))
        assertFalse(pending.copy(appMode = SessionStore.MODE_OFFLINE).canApplyAskVariant(request))
        assertFalse(
            pending.copy(clientContextGeneration = pending.clientContextGeneration + 1)
                .canApplyAskVariant(request),
        )
        assertFalse(
            pending.withAskVariant(requestId = request.requestId + 1)
                .canApplyAskVariant(request),
        )
        assertFalse(pending.copy(screen = Screen.Memos).canApplyAskVariant(request))
    }

    @Test
    fun askVariantRequestCannotStartOutsideAnIdleAskConversation() {
        val ask = editorState().let { base -> base.copy(
            screen = Screen.Ask,
            ask = base.ask.copy(conversation = AskConversationStateHolder(activeConversationId = "conversation-1")),
        ) }

        assertEquals(1L, ask.nextAskVariantRequest()?.requestId)
        assertEquals(null, ask.withAskConversation(activeConversationId = "").nextAskVariantRequest())
        assertEquals(null, ask.copy(screen = Screen.Memos).nextAskVariantRequest())
        assertEquals(null, ask.withAskLoad(loading = true).nextAskVariantRequest())
        assertEquals(null, ask.withAskStream(sending = true).nextAskVariantRequest())
        assertEquals(null, ask.withAskVariant(loading = true).nextAskVariantRequest())
        assertEquals(null, ask.withAskSourceNavigation(loading = true).nextAskVariantRequest())
    }

    @Test
    fun askSourceNavigationRequiresOriginalRequestScreenAndSession() {
        val origin = editorState().let { base -> base.copy(
            screen = Screen.Ask,
            screenHistory = emptyList(),
            ask = base.ask.copy(
                conversation = AskConversationStateHolder(activeConversationId = "conversation-1"),
                session = AskSessionStateHolder(generation = 4),
                sourceNavigation = AskSourceNavigationStateHolder(requestId = 9),
            ),
        ) }
        val request = requireNotNull(origin.nextAskSourceNavigationRequest("memo-1"))
        val pending = origin.withAskSourceNavigation(
            requestId = request.requestId,
            loading = true,
        )

        assertTrue(pending.canApplyAskSourceNavigation(request))
        assertEquals(listOf(Screen.Ask), request.destinationHistory())
        assertFalse(pending.copy(screen = Screen.AISettings).canApplyAskSourceNavigation(request))
        assertFalse(pending.withAskSession(generation = 5).canApplyAskSourceNavigation(request))
        assertFalse(
            pending.withAskSourceNavigation(requestId = 11)
                .canApplyAskSourceNavigation(request),
        )
        assertFalse(
            pending.withAskConversation(activeConversationId = "conversation-2")
                .canApplyAskSourceNavigation(request),
        )
        assertFalse(pending.copy(appMode = SessionStore.MODE_OFFLINE).canApplyAskSourceNavigation(request))
        assertFalse(
            pending.copy(clientContextGeneration = pending.clientContextGeneration + 1)
                .canApplyAskSourceNavigation(request),
        )
        assertFalse(pending.copy(screenHistory = listOf(Screen.Memos)).canApplyAskSourceNavigation(request))
    }

    @Test
    fun askSourceNavigationStartAndFinishFlowThroughAggregateWrappers() {
        val origin = editorState().copy(
            screen = Screen.Ask,
            error = "旧错误",
            notice = "旧提示",
        )
        val request = requireNotNull(origin.nextAskSourceNavigationRequest("memo-1"))

        val pending = origin.startAskSourceNavigation(request)
        val finished = pending.finishAskSourceNavigation(request)

        assertTrue(pending.askSourceLoading)
        assertEquals(request.requestId, pending.askSourceRequestId)
        assertEquals(null, pending.error)
        assertEquals(null, pending.notice)
        assertFalse(finished.askSourceLoading)
        assertEquals(request.requestId, finished.askSourceRequestId)
        assertEquals(finished.records, pending.records)
    }

    @Test
    fun askSourceNavigationCannotStartOutsideAskScreenOrWithoutMemo() {
        val ask = editorState().copy(screen = Screen.Ask)

        assertEquals(null, ask.nextAskSourceNavigationRequest(""))
        assertEquals(null, ask.copy(screen = Screen.AISettings).nextAskSourceNavigationRequest("memo-1"))
        assertEquals(null, ask.copy(loading = true).nextAskSourceNavigationRequest("memo-1"))
        assertEquals(null, ask.withAskStream(sending = true).nextAskSourceNavigationRequest("memo-1"))
        assertEquals(null, ask.withAskVariant(loading = true).nextAskSourceNavigationRequest("memo-1"))
        assertEquals(
            null,
            ask.withAskSourceNavigation(loading = true)
                .nextAskSourceNavigationRequest("memo-1"),
        )
    }

    @Test
    fun nestedSourceAndEditorHistoryReturnsToAskInOrder() {
        val ask = editorState().copy(screen = Screen.Ask)
        val detail = ask.copy(
            screen = Screen.MemoDetail,
            screenHistory = ask.historyFor(Screen.MemoDetail),
        )
        val editor = detail.copy(
            screen = Screen.Editor,
            screenHistory = detail.historyFor(Screen.Editor),
        )

        val detailBack = editor.backNavigation(Screen.Memos)
        assertEquals(Screen.MemoDetail, detailBack.screen)
        assertEquals(listOf(Screen.Ask), detailBack.history)

        val askBack = editor.copy(
            screen = detailBack.screen,
            screenHistory = detailBack.history,
        ).backNavigation(Screen.Memos)
        assertEquals(Screen.Ask, askBack.screen)
        assertTrue(askBack.history.isEmpty())
    }

    @Test
    fun onlyAvailableSuccessfulAskAnswersEmitCompletionEvents() {
        val pending = editorState().let { base -> base.copy(
            screen = Screen.Ask,
            ask = base.ask.copy(
                composer = AskComposerStateHolder(question = "问题"),
                stream = AskStreamStateHolder(
                sending = true,
                streaming = true,
                regeneratingMessageId = "answer-1",
                liveUser = askMessage("question-1", "问题", role = "user"),
                liveAnswer = "回答",
                completionEventId = 4,
            ),
            ),
        ) }

        val completed = pending.finishAskStream(answerAvailable = true, clearQuestion = true)
        assertFalse(completed.askSending)
        assertFalse(completed.askStreaming)
        assertEquals("", completed.askQuestion)
        assertEquals("", completed.askRegeneratingId)
        assertEquals(null, completed.askLiveUser)
        assertEquals("", completed.askLiveAnswer)
        assertEquals(5L, completed.askCompletionEventId)

        val unavailable = pending.finishAskStream(answerAvailable = false, clearQuestion = true)
        assertEquals(4L, unavailable.askCompletionEventId)

        val failed = pending.copy(error = "失败").finishAskStream(answerAvailable = true, clearQuestion = true)
        assertEquals("问题", failed.askQuestion)
        assertEquals(4L, failed.askCompletionEventId)

        val stopped = pending.copy(notice = "已停止").finishAskStream(answerAvailable = true, clearQuestion = true)
        assertEquals("", stopped.askQuestion)
        assertEquals(4L, stopped.askCompletionEventId)
    }

    @Test
    fun completionRequiresANewCompletedAssistantHead() {
        val answer = askMessage("answer-2", "回答")

        assertTrue(
            hasNewCompletedAskAnswer(
                messages = listOf(answer),
                headId = answer.id,
                previousHeadId = "answer-1",
            ),
        )
        assertFalse(hasNewCompletedAskAnswer(listOf(answer), answer.id, answer.id))
        assertFalse(hasNewCompletedAskAnswer(listOf(answer.copy(content = "")), answer.id, "answer-1"))
        assertFalse(hasNewCompletedAskAnswer(listOf(answer.copy(status = "pending")), answer.id, "answer-1"))
        assertFalse(hasNewCompletedAskAnswer(listOf(answer.copy(role = "user")), answer.id, "answer-1"))
    }

    @Test
    fun secondaryMainDestinationsReturnToRecordsOnBack() {
        val state = editorState()

        assertTrue(state.copy(screen = Screen.Ask).shouldReturnToRecordsOnBack())
        assertTrue(state.copy(screen = Screen.AISettings).shouldReturnToRecordsOnBack())
        assertTrue(
            state.copy(
                screen = Screen.Memos,
                records = state.records.copy(browse = state.records.browse.selectViewMode(MemoViewMode.Calendar)),
            ).shouldReturnToRecordsOnBack(),
        )
        assertFalse(
            state.copy(
                screen = Screen.Memos,
                records = state.records.copy(browse = state.records.browse.selectViewMode(MemoViewMode.List)),
            ).shouldReturnToRecordsOnBack(),
        )
        assertFalse(state.copy(screen = Screen.MemoDetail).shouldReturnToRecordsOnBack())
        assertFalse(state.copy(screen = Screen.Editor).shouldReturnToRecordsOnBack())
    }

    private fun SillageUiState.withAIAutoSummary(
        enabled: Boolean = aiAutoSummary,
        saving: Boolean = aiAutoSummarySaving,
        requestId: Long = aiAutoSummaryRequestId,
    ): SillageUiState = copy(
        settings = settings.copy(
            autoSummary = AIAutoSummaryStateHolder(
            enabled = enabled,
            saving = saving,
            requestId = requestId,
        ),
        ),
    )

    private fun SillageUiState.withAskConversation(
        activeConversationId: String = activeAskId,
        headMessageId: String? = askHeadId,
        messages: List<AskMessage> = askMessages,
    ): SillageUiState = withAsk { ask ->
        ask.copy(
            conversation = ask.conversation.copy(
                activeConversationId = activeConversationId,
                headMessageId = headMessageId,
                messages = messages,
            ),
        )
    }

    private fun SillageUiState.withAskLoad(
        loading: Boolean = askLoading,
        errorMessage: String? = askLoadError,
    ): SillageUiState = withAsk { ask ->
        ask.copy(
            load = AskLoadStateHolder(
                loading = loading,
                errorMessage = errorMessage,
            ),
        )
    }

    private fun SillageUiState.withAskVariant(
        requestId: Long = askVariantRequestId,
        loading: Boolean = askVariantLoading,
    ): SillageUiState = withAsk { ask ->
        ask.copy(
            variant = AskVariantStateHolder(
                requestId = requestId,
                loading = loading,
            ),
        )
    }

    private fun SillageUiState.withAskSourceNavigation(
        requestId: Long = askSourceRequestId,
        loading: Boolean = askSourceLoading,
    ): SillageUiState = withAsk { ask ->
        ask.copy(
            sourceNavigation = AskSourceNavigationStateHolder(
                requestId = requestId,
                loading = loading,
            ),
        )
    }

    private fun SillageUiState.withAskSession(
        generation: Long,
    ): SillageUiState = withAsk { ask ->
        ask.copy(session = AskSessionStateHolder(generation = generation))
    }

    private fun SillageUiState.withAskStream(
        sending: Boolean = askSending,
        streaming: Boolean = askStreaming,
        requestId: Long = askStreamRequestId,
        completionEventId: Long = askCompletionEventId,
        regeneratingMessageId: String = askRegeneratingId,
        liveUser: AskMessage? = askLiveUser,
        liveAnswer: String = askLiveAnswer,
    ): SillageUiState = withAsk { ask ->
        ask.copy(
            stream = AskStreamStateHolder(
                sending = sending,
                streaming = streaming,
                requestId = requestId,
                completionEventId = completionEventId,
                regeneratingMessageId = regeneratingMessageId,
                liveUser = liveUser,
                liveAnswer = liveAnswer,
            ),
        )
    }

    private fun editorState(
        draftContent: String = "",
        draftEntryDate: String = "2026-07-10",
        initialDraftContent: String = "",
        initialDraftEntryDate: String = "2026-07-10",
    ): SillageUiState {
        return SillageUiState(
            screen = Screen.Editor,
            baseUrl = "",
            records = defaultRecordsFeatureState().copy(
                editor = RecordsEditorStateHolder(
                    draftContent = draftContent,
                    draftEntryDate = draftEntryDate,
                    initialDraftContent = initialDraftContent,
                    initialDraftEntryDate = initialDraftEntryDate,
                ),
            ),
        )
    }

    private fun memo(id: String = "memo-1"): Memo {
        return Memo(
            id = id,
            content = "记录",
            entryDate = "2026-07-10",
            version = 1,
            createdAt = "2026-07-10T01:00:00Z",
            updatedAt = "2026-07-10T01:00:00Z",
            favoritedAt = null,
            archivedAt = null,
            deletedAt = null,
        )
    }

    private fun memoAI(summary: String): MemoAI {
        return MemoAI(
            memoId = "memo-1",
            summary = summary,
            sentiment = null,
            provider = "openai",
            model = "model",
            profileId = "profile-1",
            promptVersion = "v1",
            sourceMemoIds = "memo-1",
            status = "complete",
            errorCode = null,
            startedAt = null,
            finishedAt = null,
            inputTokens = 1,
            outputTokens = 2,
            totalTokens = 3,
            createdAt = "2026-07-10T01:00:00Z",
            updatedAt = "2026-07-10T01:00:00Z",
        )
    }

    private fun askMessage(
        id: String,
        content: String,
        conversationId: String = "conversation-1",
        role: String = "assistant",
    ): AskMessage {
        return AskMessage(
            id = id,
            conversationId = conversationId,
            role = role,
            content = content,
            parentId = null,
            forkOfId = null,
            status = "complete",
            sourceRefs = emptyList(),
            model = "test-model",
            promptVersion = "test-prompt",
            createdAt = "2026-07-10T01:00:00Z",
            updatedAt = "2026-07-10T01:00:00Z",
            deletedAt = null,
        )
    }
}
