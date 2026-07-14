package app.sillage.ui

import app.sillage.data.SyncPushSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiToastEventTest {
    @Test
    fun repeatedMessagesReceiveIncreasingIdsAndKeepEmissionOrder() {
        val events = mutableListOf<UiToastEvent>()
        val emitter = UiToastEventEmitter(events::add)
        var state = SillageUiState(screen = Screen.Memos, baseUrl = "")

        fun update(next: SillageUiState) {
            emitter.onStateChanged(state, next)
            state = next
        }

        update(state.copy(notice = "已保存"))
        update(state.copy(notice = null))
        update(state.copy(notice = "已保存"))
        update(state.copy(error = "保存失败", notice = null))

        assertEquals(listOf(1L, 2L, 3L), events.map(UiToastEvent::id))
        assertEquals(
            listOf(UiToastType.SUCCESS, UiToastType.SUCCESS, UiToastType.ERROR),
            events.map(UiToastEvent::type),
        )
        assertEquals(listOf("已保存", "已保存", "保存失败"), events.map(UiToastEvent::message))
    }

    @Test
    fun clearingFeedbackDoesNotEmitOrRemoveAnAlreadyQueuedEvent() {
        val events = mutableListOf<UiToastEvent>()
        val emitter = UiToastEventEmitter(events::add)
        val initial = SillageUiState(screen = Screen.Memos, baseUrl = "")
        val withNotice = initial.copy(notice = "同步完成")

        emitter.onStateChanged(initial, withNotice)
        emitter.onStateChanged(withNotice, withNotice.copy(notice = null))

        assertEquals(
            listOf(
                UiToastEvent(
                    id = 1L,
                    type = UiToastType.SUCCESS,
                    message = "同步完成",
                    languageMode = "zh-CN",
                ),
            ),
            events,
        )
    }

    @Test
    fun errorTakesPriorityOverANoticeFromTheSameStateChange() {
        val events = mutableListOf<UiToastEvent>()
        val emitter = UiToastEventEmitter(events::add)
        val initial = SillageUiState(screen = Screen.Memos, baseUrl = "")

        emitter.onStateChanged(
            initial,
            initial.copy(error = "保存失败", notice = "已保存"),
        )

        assertEquals(
            listOf(
                UiToastEvent(
                    id = 1L,
                    type = UiToastType.ERROR,
                    message = "保存失败",
                    languageMode = "zh-CN",
                ),
            ),
            events,
        )
    }

    @Test
    fun forcedFeedbackEmitsAnUnchangedValidationErrorAgain() {
        val events = mutableListOf<UiToastEvent>()
        val emitter = UiToastEventEmitter(events::add)
        val initial = SillageUiState(screen = Screen.Memos, baseUrl = "")
        val invalid = initial.copy(error = "请先填写服务器地址")

        emitter.onStateChanged(initial, invalid)
        emitter.onStateChanged(invalid, invalid, forceFeedback = true)

        assertEquals(2, events.size)
        assertEquals(listOf(1L, 2L), events.map(UiToastEvent::id))
        assertEquals(listOf(UiToastType.ERROR, UiToastType.ERROR), events.map(UiToastEvent::type))
    }

    @Test
    fun memoEditorBackFeedbackClearsOldErrorAndEmitsRepeatableWarnings() {
        val events = mutableListOf<UiToastEvent>()
        val emitter = UiToastEventEmitter(events::add)
        val busy = SillageUiState(
            screen = Screen.Editor,
            baseUrl = "",
            uploadingAttachment = true,
            error = "旧错误",
        )
        val warning = busy.withMemoEditorBackBlockedNotice(
            attachmentUploadNotice = "附件仍在上传",
            operationNotice = "操作仍在进行",
        )

        emitter.onStateChanged(
            before = busy,
            after = warning,
            forceFeedback = true,
            noticeType = UiToastType.WARNING,
        )
        emitter.onStateChanged(
            before = warning,
            after = warning,
            forceFeedback = true,
            noticeType = UiToastType.WARNING,
        )

        assertEquals(listOf(UiToastType.WARNING, UiToastType.WARNING), events.map(UiToastEvent::type))
        assertEquals(listOf("附件仍在上传", "附件仍在上传"), events.map(UiToastEvent::message))
    }

    @Test
    fun persistentAuthenticationErrorsDoNotEmitDuplicateGlobalFeedback() {
        val events = mutableListOf<UiToastEvent>()
        val emitter = UiToastEventEmitter(events::add)
        val initial = SillageUiState(screen = Screen.Login, baseUrl = "")

        emitter.onStateChanged(initial, initial.copy(authError = "Sign-in failed"))

        assertTrue(events.isEmpty())
    }

    @Test
    fun syncProblemsUseWarningFeedback() {
        assertEquals(
            UiToastType.SUCCESS,
            syncPushToastType(SyncPushSummary(applied = 2, conflict = 0, rejected = 0)),
        )
        assertEquals(
            UiToastType.WARNING,
            syncPushToastType(SyncPushSummary(applied = 1, conflict = 1, rejected = 0)),
        )
        assertEquals(
            UiToastType.WARNING,
            syncPushToastType(SyncPushSummary(applied = 1, conflict = 0, rejected = 1)),
        )
    }

    @Test
    fun toastEventsOnlyMatchTheLanguageThatCreatedThem() {
        val event = UiToastEvent(
            id = 1L,
            type = UiToastType.SUCCESS,
            message = "已保存",
            languageMode = "zh-CN",
        )

        assertTrue(event.matchesLanguage("zh-CN"))
        assertFalse(event.matchesLanguage("en"))
    }
}
