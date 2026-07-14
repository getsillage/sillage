package app.sillage.ui

import app.sillage.data.AIProfileDraft
import app.sillage.data.AskMessage
import app.sillage.data.Memo
import app.sillage.data.MemoAI
import app.sillage.data.MemoDetail
import app.sillage.data.MemoListFilter
import app.sillage.data.SessionStore
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
        assertFalse(editorState().copy(uploadingAttachment = true).canRunMemoEditorAction())
        assertFalse(editorState().copy(screen = Screen.Memos).canRunMemoEditorAction())
        assertFalse(
            editorState().copy(
                selectedMemo = memo(),
                memoMutationIds = setOf("memo-1"),
            ).canRunMemoEditorAction(),
        )
    }

    @Test
    fun memoEditorBusyReasonOnlyCoversBlockingOperations() {
        val selected = editorState().copy(selectedMemo = memo())

        assertEquals(null, selected.memoEditorBusyReason())
        assertEquals(
            MemoEditorBusyReason.AttachmentUpload,
            selected.copy(uploadingAttachment = true).memoEditorBusyReason(),
        )
        assertEquals(
            MemoEditorBusyReason.Operation,
            editorState().copy(loading = true).memoEditorBusyReason(),
        )
        assertEquals(
            MemoEditorBusyReason.Operation,
            selected.copy(memoMutationIds = setOf("memo-1")).memoEditorBusyReason(),
        )
        assertEquals(null, selected.copy(memoMutationIds = setOf("memo-2")).memoEditorBusyReason())
        assertEquals(null, selected.copy(summaryLoading = true).memoEditorBusyReason())
        assertEquals(null, selected.copy(openingAttachmentPath = "/attachments/file-1").memoEditorBusyReason())
        assertEquals(null, selected.copy(screen = Screen.Memos, loading = true).memoEditorBusyReason())
    }

    @Test
    fun memoEditorBackBlockedNoticeClearsOldErrorAndKeepsIdleStateUnchanged() {
        val uploading = editorState().copy(
            uploadingAttachment = true,
            error = "旧错误",
            notice = "旧提示",
        )
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
        assertTrue(idle.copy(summaryLoading = true).hasClientContextOperationInProgress())
        assertTrue(idle.copy(memoMutationIds = setOf("memo-1")).hasClientContextOperationInProgress())
        assertTrue(idle.copy(askSavingMessageId = "answer-1").hasClientContextOperationInProgress())
        assertTrue(idle.copy(aiSettingsSaving = true).hasClientContextOperationInProgress())
        assertTrue(idle.copy(aiAutoSummarySaving = true).hasClientContextOperationInProgress())
    }

    @Test
    fun attachmentResultOnlyAppliesToActiveUploadingEditorSession() {
        val uploading = editorState().copy(editorSessionId = 7, uploadingAttachment = true)

        assertTrue(uploading.canApplyAttachmentUpload(7))
        assertFalse(uploading.canApplyAttachmentUpload(6))
        assertFalse(uploading.copy(editorSessionId = 8).canApplyAttachmentUpload(7))
        assertFalse(uploading.copy(screen = Screen.Memos).canApplyAttachmentUpload(7))
        assertFalse(uploading.copy(uploadingAttachment = false).canApplyAttachmentUpload(7))
    }

    @Test
    fun leavingAttachmentContextInvalidatesAQueuedOpenEvent() {
        val opening = editorState().copy(
            openingAttachmentPath = "/api/v1/attachments/file-1",
            attachmentOpenRequestId = 8,
        )

        assertTrue(opening.canHandleAttachmentOpen(8))
        val invalidated = opening.invalidateAttachmentOpenRequest()

        assertEquals(null, invalidated.openingAttachmentPath)
        assertEquals(9L, invalidated.attachmentOpenRequestId)
        assertFalse(invalidated.canHandleAttachmentOpen(8))
        assertEquals(invalidated, invalidated.invalidateAttachmentOpenRequest())
    }

    @Test
    fun stoppingAskKeepsGeneratedContentAndAddsFeedback() {
        val streaming = editorState().copy(
            screen = Screen.Ask,
            askSending = true,
            askStreaming = true,
            askLiveAnswer = "已生成的部分",
            error = "旧错误",
        )

        val stopped = streaming.withAskStreamingStoppedNotice("已停止生成")

        assertEquals("已生成的部分", stopped.askLiveAnswer)
        assertTrue(stopped.askSending)
        assertTrue(stopped.askStreaming)
        assertEquals(null, stopped.error)
        assertEquals("已停止生成", stopped.notice)
        val idle = streaming.copy(askSending = false)
        assertEquals(idle, idle.withAskStreamingStoppedNotice("已停止生成"))
    }

    @Test
    fun memoPageRequestIsSingleFlightAndBoundToItsCursor() {
        val state = editorState().copy(
            screen = Screen.Memos,
            appMode = SessionStore.MODE_ONLINE,
            memoNextCursor = "cursor-1",
            memoPageRequestId = 4,
        )
        val request = requireNotNull(state.nextMemoPageRequest())
        val pending = state.copy(
            loadingMoreMemos = true,
            memoPageRequestId = request.requestId,
        )

        assertEquals("cursor-1", request.cursor)
        assertEquals(MemoListFilter.Unarchived, request.filter)
        assertEquals(null, pending.nextMemoPageRequest())
        assertTrue(pending.canApplyMemoPage(request))
        assertFalse(pending.copy(memoNextCursor = "cursor-2").canApplyMemoPage(request))
        assertFalse(pending.copy(memoPageRequestId = request.requestId + 1).canApplyMemoPage(request))
        assertFalse(pending.copy(appMode = SessionStore.MODE_OFFLINE).canApplyMemoPage(request))
        assertFalse(
            pending.copy(clientContextGeneration = pending.clientContextGeneration + 1)
                .canApplyMemoPage(request),
        )
        assertFalse(pending.copy(memoListFilter = MemoListFilter.Archived).canApplyMemoPage(request))
        assertFalse(pending.copy(memoCacheGeneration = 1).canApplyMemoPage(request))
        assertFalse(pending.copy(loadingMoreMemos = false).canApplyMemoPage(request))
    }

    @Test
    fun canonicalMemoInvalidatesEarlierRefreshAndSearchRequests() {
        val original = memo()
        val initial = editorState().copy(
            screen = Screen.Memos,
            appMode = SessionStore.MODE_ONLINE,
            memos = listOf(original),
            searchQuery = "记录",
            searchResults = listOf(original),
            searchResultQuery = "记录",
            searchCompletionEventId = 4,
        )
        val refresh = initial.memoRefreshRequest()
        val search = requireNotNull(initial.nextMemoSearchRequest())
        val pending = initial.startMemoSearch(search)

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
        val initial = editorState().copy(
            screen = Screen.MemoDetail,
            appMode = SessionStore.MODE_ONLINE,
            memos = listOf(original),
            selectedMemo = original,
            summaryLoading = true,
        )
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
            MemoDetail(memo = original, ai = null),
        )

        assertEquals(canonical, completed.selectedMemo)
        assertTrue(completed.memos.isEmpty())
        assertEquals(mutated.memoCacheGeneration, completed.memoCacheGeneration)
        assertFalse(completed.summaryLoading)
    }

    @Test
    fun currentMemoDetailAppliesMemoAndSummaryInOneStateTransition() {
        val original = memo()
        val initial = editorState().copy(
            screen = Screen.MemoDetail,
            appMode = SessionStore.MODE_ONLINE,
            memos = listOf(original),
            selectedMemo = original,
        )
        val request = requireNotNull(initial.nextMemoDetailRequest(original.id))
        val pending = initial.startMemoDetailRequest(request)
        val canonical = original.copy(
            version = 2,
            updatedAt = "2026-07-10T02:00:00Z",
            archivedAt = "2026-07-10T02:00:00Z",
        )

        val completed = pending.completeMemoDetailRequest(
            request,
            MemoDetail(memo = canonical, ai = null),
        )

        assertEquals(canonical, completed.selectedMemo)
        assertTrue(completed.memos.isEmpty())
        assertEquals(pending.memoCacheGeneration + 1, completed.memoCacheGeneration)
        assertFalse(completed.summaryLoading)
    }

    @Test
    fun supersededMemoDetailFailureOnlyStopsItsLoadingState() {
        val original = memo()
        val initial = editorState().copy(
            screen = Screen.MemoDetail,
            appMode = SessionStore.MODE_ONLINE,
            selectedMemo = original,
        )
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
        val initial = editorState().copy(
            screen = Screen.MemoDetail,
            appMode = SessionStore.MODE_OFFLINE,
            clientContextGeneration = 3,
            selectedMemo = original,
            editorSessionId = 7,
            memoDetailRequestId = 11,
        )
        val request = requireNotNull(initial.nextMemoSummaryRequest())
        val pending = initial.startMemoSummaryRequest(request)

        assertTrue(pending.canApplyMemoSummaryRequest(request))
        assertEquals(null, pending.nextMemoSummaryRequest())
        assertFalse(
            pending.copy(selectedMemo = original.copy(id = "memo-2"))
                .canApplyMemoSummaryRequest(request),
        )
        assertFalse(
            pending.copy(selectedMemo = original.copy(version = 2))
                .canApplyMemoSummaryRequest(request),
        )
        assertFalse(pending.copy(screen = Screen.Memos).canApplyMemoSummaryRequest(request))
        assertFalse(
            pending.copy(clientContextGeneration = 4)
                .canApplyMemoSummaryRequest(request),
        )
        assertFalse(
            pending.copy(memoDetailRequestId = 12)
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

        val versionChanged = pending.copy(selectedMemo = original.copy(version = 2))
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
        val initial = editorState().copy(
            screen = Screen.Memos,
            searchQuery = "记录",
            searchResults = loaded,
            searchResultQuery = "记录",
            searchCompletionEventId = 4,
        )
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
        val initial = editorState().copy(
            screen = Screen.Memos,
            searchQuery = "记录",
        )
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
        val initial = editorState().copy(
            screen = Screen.Memos,
            searchQuery = "新查询",
            searchResults = oldResults,
            searchResultQuery = "旧查询",
            searchCompletionEventId = 4,
        )
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
        assertEquals(null, completed.copy(searchQuery = "又一查询").currentMemoSearchResults())
        assertEquals(null, completed.copy(searchQuery = "又一查询").completedMemoSearch())
        assertEquals(null, completed.copy(searching = true).completedMemoSearch())
        assertEquals(completed.completedMemoSearch(), completed.copy(error = "无关错误").completedMemoSearch())

        val empty = pending.completeMemoSearch(request, emptyList())
        assertEquals(CompletedMemoSearch(query = "新查询", resultCount = 0), empty.completedMemoSearch())
        assertEquals(5L, empty.searchCompletionEventId)

        val stale = pending.copy(searchQuery = "其他查询")
        assertEquals(stale, stale.completeMemoSearch(request, results))
    }

    @Test
    fun failedSearchStateIsBoundToTheFailedQuery() {
        val failed = editorState().copy(
            screen = Screen.Memos,
            searchQuery = "新查询",
            searchResults = listOf(memo()),
            searchResultQuery = "旧查询",
            searchFailureQuery = "新查询",
            searching = false,
            error = "网络错误",
        )

        assertEquals(null, failed.currentMemoSearchResults())
        assertTrue(failed.shouldShowMemoSearchFailure())
        assertTrue(failed.copy(error = null).shouldShowMemoSearchFailure())
        assertFalse(failed.copy(searching = true).shouldShowMemoSearchFailure())
        assertFalse(failed.copy(searchFailureQuery = "旧查询").shouldShowMemoSearchFailure())
        assertFalse(failed.copy(searchResultQuery = "新查询").shouldShowMemoSearchFailure())
        assertFalse(failed.copy(searchQuery = "").shouldShowMemoSearchFailure())
    }

    @Test
    fun memoListFiltersMapToMutuallyExclusiveApiQueries() {
        assertEquals(
            MemoApiQuery(archived = false, favorited = false),
            MemoListFilter.Unarchived.apiQuery(),
        )
        assertEquals(
            MemoApiQuery(archived = true, favorited = false),
            MemoListFilter.Archived.apiQuery(),
        )
        assertEquals(
            MemoApiQuery(archived = null, favorited = true),
            MemoListFilter.Favorited.apiQuery(),
        )
    }

    @Test
    fun failedEmptyMemoLoadUsesFailureStateInsteadOfBusinessEmptyState() {
        val failed = editorState().copy(
            screen = Screen.Memos,
            memos = emptyList(),
            memoListLoadStatus = MemoListLoadStatus.Failed,
        )

        assertTrue(failed.shouldShowMemoListLoadFailure())
        assertFalse(failed.copy(searchQuery = "记录").shouldShowMemoListLoadFailure())
        assertFalse(failed.copy(memoListLoadStatus = MemoListLoadStatus.Loading).shouldShowMemoListLoadFailure())
        assertFalse(failed.copy(memoListLoadStatus = MemoListLoadStatus.Idle).shouldShowMemoListLoadFailure())
        assertFalse(failed.copy(memos = listOf(memo())).shouldShowMemoListLoadFailure())
        assertFalse(failed.copy(searchResults = emptyList()).shouldShowMemoListLoadFailure())
    }

    @Test
    fun autoSummaryRequestIsSingleFlightAndBoundToItsMode() {
        val idle = editorState().copy(
            screen = Screen.AISettings,
            appMode = SessionStore.MODE_ONLINE,
            aiAutoSummary = false,
            aiAutoSummaryRequestId = 4,
        )
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
        assertEquals(null, idle.copy(aiSettingsLoading = true).nextAIAutoSummaryRequest(true))
    }

    @Test
    fun autoSummaryCompletionAndFailurePreserveProfileDrafts() {
        val profiles = listOf(AIProfileDraft(id = "p1", name = "未保存名称"))
        val idle = editorState().copy(
            screen = Screen.AISettings,
            aiProfiles = profiles,
            aiAutoSummary = false,
        )
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
        val idle = editorState().copy(
            screen = Screen.AISettings,
            aiAutoSummary = false,
        )
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
        val idle = editorState().copy(
            screen = Screen.AISettings,
            appMode = SessionStore.MODE_ONLINE,
            aiProfiles = original,
            aiSettingsRequestId = 6,
        )
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
        val initial = editorState().copy(
            screen = Screen.AISettings,
            aiProfiles = original,
        )
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
        val changedWhileSaving = secondSaving.copy(aiProfiles = laterDraft)
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
        val idle = editorState().copy(
            screen = Screen.AISettings,
            aiProfiles = staged,
        )
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
        val idle = editorState().copy(
            screen = Screen.Ask,
            activeAskId = "conversation-1",
            askHeadId = firstAnswer.id,
            askMessages = listOf(firstAnswer, secondAnswer),
        )
        val request = requireNotNull(
            idle.nextAskMemoSaveRequest(firstAnswer, memoContent = "第一条回答"),
        )

        val pending = idle.startAskMemoSave(request)

        assertEquals(firstAnswer.id, pending.askSavingMessageId)
        assertTrue(pending.canApplyAskMemoSave(request))
        assertTrue(pending.copy(askLoading = true).canApplyAskMemoSave(request))
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
    fun lateAskMemoSaveCannotApplyButStillClearsItsBusyState() {
        val answer = askMessage(id = "answer-1", content = "原回答")
        val idle = editorState().copy(
            screen = Screen.Ask,
            activeAskId = "conversation-1",
            askHeadId = answer.id,
            askScreenSessionId = 4,
            askMessages = listOf(answer),
        )
        val request = requireNotNull(
            idle.nextAskMemoSaveRequest(answer, memoContent = "保存内容"),
        )
        val pending = idle.startAskMemoSave(request)
        val staleStates = listOf(
            "screen" to pending.copy(screen = Screen.Memos),
            "session" to pending.copy(askScreenSessionId = 5),
            "conversation" to pending.copy(activeAskId = "conversation-2"),
            "head" to pending.copy(askHeadId = "answer-2"),
            "client context" to pending.copy(
                clientContextGeneration = pending.clientContextGeneration + 1,
            ),
            "message" to pending.copy(
                askMessages = listOf(answer.copy(content = "替换后的回答")),
            ),
        )

        staleStates.forEach { (context, stale) ->
            assertFalse(context, stale.canApplyAskMemoSave(request))
            assertEquals(context, "", stale.finishAskMemoSave(request).askSavingMessageId)
        }
    }

    @Test
    fun askStreamCallbacksRequireOriginalConversationAndSession() {
        val state = editorState().copy(
            screen = Screen.Ask,
            activeAskId = "conversation-1",
            askScreenSessionId = 3,
            askStreamRequestId = 8,
        )
        val request = requireNotNull(state.nextAskStreamRequest())
        val pending = state.copy(
            askSending = true,
            askStreamRequestId = request.requestId,
        )

        assertTrue(pending.canApplyAskStream(request))
        assertEquals(null, pending.nextAskStreamRequest())
        assertFalse(pending.copy(activeAskId = "conversation-2").canApplyAskStream(request))
        assertFalse(pending.copy(askScreenSessionId = 4).canApplyAskStream(request))
        assertFalse(pending.copy(askStreamRequestId = request.requestId + 1).canApplyAskStream(request))
        assertFalse(pending.copy(appMode = SessionStore.MODE_OFFLINE).canApplyAskStream(request))
        assertFalse(
            pending.copy(clientContextGeneration = pending.clientContextGeneration + 1)
                .canApplyAskStream(request),
        )
        assertFalse(pending.copy(askSending = false).canApplyAskStream(request))
        assertEquals(null, state.copy(askLoading = true).nextAskStreamRequest())
        assertEquals(null, state.copy(askVariantLoading = true).nextAskStreamRequest())
    }

    @Test
    fun askVariantCallbacksRequireOriginalRequestConversationSessionAndMode() {
        val state = editorState().copy(
            screen = Screen.Ask,
            activeAskId = "conversation-1",
            askScreenSessionId = 3,
            askVariantRequestId = 8,
        )
        val request = requireNotNull(state.nextAskVariantRequest())
        val pending = state.copy(
            askVariantRequestId = request.requestId,
            askVariantLoading = true,
        )

        assertTrue(pending.canApplyAskVariant(request))
        assertEquals(null, pending.nextAskVariantRequest())
        assertFalse(pending.copy(activeAskId = "conversation-2").canApplyAskVariant(request))
        assertFalse(pending.copy(askScreenSessionId = 4).canApplyAskVariant(request))
        assertFalse(pending.copy(appMode = SessionStore.MODE_OFFLINE).canApplyAskVariant(request))
        assertFalse(
            pending.copy(clientContextGeneration = pending.clientContextGeneration + 1)
                .canApplyAskVariant(request),
        )
        assertFalse(pending.copy(askVariantRequestId = request.requestId + 1).canApplyAskVariant(request))
        assertFalse(pending.copy(screen = Screen.Memos).canApplyAskVariant(request))
    }

    @Test
    fun askVariantRequestCannotStartOutsideAnIdleAskConversation() {
        val ask = editorState().copy(
            screen = Screen.Ask,
            activeAskId = "conversation-1",
        )

        assertEquals(1L, ask.nextAskVariantRequest()?.requestId)
        assertEquals(null, ask.copy(activeAskId = "").nextAskVariantRequest())
        assertEquals(null, ask.copy(screen = Screen.Memos).nextAskVariantRequest())
        assertEquals(null, ask.copy(askLoading = true).nextAskVariantRequest())
        assertEquals(null, ask.copy(askSending = true).nextAskVariantRequest())
        assertEquals(null, ask.copy(askVariantLoading = true).nextAskVariantRequest())
        assertEquals(null, ask.copy(askSourceLoading = true).nextAskVariantRequest())
    }

    @Test
    fun askSourceNavigationRequiresOriginalRequestScreenAndSession() {
        val origin = editorState().copy(
            screen = Screen.Ask,
            screenHistory = emptyList(),
            activeAskId = "conversation-1",
            askScreenSessionId = 4,
            askSourceRequestId = 9,
        )
        val request = requireNotNull(origin.nextAskSourceNavigationRequest("memo-1"))
        val pending = origin.copy(
            askSourceRequestId = request.requestId,
            askSourceLoading = true,
        )

        assertTrue(pending.canApplyAskSourceNavigation(request))
        assertEquals(listOf(Screen.Ask), request.destinationHistory())
        assertFalse(pending.copy(screen = Screen.AISettings).canApplyAskSourceNavigation(request))
        assertFalse(pending.copy(askScreenSessionId = 5).canApplyAskSourceNavigation(request))
        assertFalse(pending.copy(askSourceRequestId = 11).canApplyAskSourceNavigation(request))
        assertFalse(pending.copy(activeAskId = "conversation-2").canApplyAskSourceNavigation(request))
        assertFalse(pending.copy(appMode = SessionStore.MODE_OFFLINE).canApplyAskSourceNavigation(request))
        assertFalse(
            pending.copy(clientContextGeneration = pending.clientContextGeneration + 1)
                .canApplyAskSourceNavigation(request),
        )
        assertFalse(pending.copy(screenHistory = listOf(Screen.Memos)).canApplyAskSourceNavigation(request))
    }

    @Test
    fun askSourceNavigationCannotStartOutsideAskScreenOrWithoutMemo() {
        val ask = editorState().copy(screen = Screen.Ask)

        assertEquals(null, ask.nextAskSourceNavigationRequest(""))
        assertEquals(null, ask.copy(screen = Screen.AISettings).nextAskSourceNavigationRequest("memo-1"))
        assertEquals(null, ask.copy(loading = true).nextAskSourceNavigationRequest("memo-1"))
        assertEquals(null, ask.copy(askSending = true).nextAskSourceNavigationRequest("memo-1"))
        assertEquals(null, ask.copy(askVariantLoading = true).nextAskSourceNavigationRequest("memo-1"))
        assertEquals(null, ask.copy(askSourceLoading = true).nextAskSourceNavigationRequest("memo-1"))
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
        val pending = editorState().copy(
            screen = Screen.Ask,
            askQuestion = "问题",
            askSending = true,
            askStreaming = true,
            askRegeneratingId = "answer-1",
            askLiveUser = askMessage("question-1", "问题", role = "user"),
            askLiveAnswer = "回答",
            askCompletionEventId = 4,
        )

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
                memoViewMode = MemoViewMode.Calendar,
            ).shouldReturnToRecordsOnBack(),
        )
        assertFalse(
            state.copy(
                screen = Screen.Memos,
                memoViewMode = MemoViewMode.List,
            ).shouldReturnToRecordsOnBack(),
        )
        assertFalse(state.copy(screen = Screen.MemoDetail).shouldReturnToRecordsOnBack())
        assertFalse(state.copy(screen = Screen.Editor).shouldReturnToRecordsOnBack())
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
            draftContent = draftContent,
            draftEntryDate = draftEntryDate,
            initialDraftContent = initialDraftContent,
            initialDraftEntryDate = initialDraftEntryDate,
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
