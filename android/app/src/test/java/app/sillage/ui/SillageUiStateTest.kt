package app.sillage.ui

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
        assertEquals(null, pending.nextMemoPageRequest())
        assertTrue(pending.canApplyMemoPage(request))
        assertFalse(pending.copy(memoNextCursor = "cursor-2").canApplyMemoPage(request))
        assertFalse(pending.copy(memoPageRequestId = request.requestId + 1).canApplyMemoPage(request))
        assertFalse(pending.copy(appMode = SessionStore.MODE_OFFLINE).canApplyMemoPage(request))
        assertFalse(pending.copy(loadingMoreMemos = false).canApplyMemoPage(request))
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
}
