package app.sillage.ui.appshell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppFeedbackEventTest {
    @Test
    fun changedAndForcedMessagesReceiveOrderedEventIds() {
        val events = mutableListOf<AppFeedbackEvent>()
        val emitter = AppFeedbackEventEmitter(events::add)
        val initial = AppFeedbackSnapshot(languageMode = "zh-CN")
        val notice = initial.copy(notice = "已保存")

        emitter.onStateChanged(initial, notice)
        emitter.onStateChanged(notice, notice)
        emitter.onStateChanged(notice, notice, forceFeedback = true)

        assertEquals(listOf(1L, 2L), events.map(AppFeedbackEvent::id))
        assertEquals(listOf("已保存", "已保存"), events.map(AppFeedbackEvent::message))
    }

    @Test
    fun errorPreemptsNoticeAndPersistentErrorSuppressesNotice() {
        val events = mutableListOf<AppFeedbackEvent>()
        val emitter = AppFeedbackEventEmitter(events::add)
        val initial = AppFeedbackSnapshot(languageMode = "en")
        val failed = initial.copy(error = "Failed", notice = "Saved")

        emitter.onStateChanged(initial, failed)
        emitter.onStateChanged(failed, failed.copy(notice = "Another notice"))

        assertEquals(1, events.size)
        assertEquals(AppFeedbackType.ERROR, events.single().type)
        assertEquals("Failed", events.single().message)
    }

    @Test
    fun noticeTypeAndLanguageStayBoundToEvent() {
        val events = mutableListOf<AppFeedbackEvent>()
        val emitter = AppFeedbackEventEmitter(events::add)

        emitter.onStateChanged(
            before = AppFeedbackSnapshot(languageMode = "zh-CN"),
            after = AppFeedbackSnapshot(notice = "部分完成", languageMode = "zh-CN"),
            noticeType = AppFeedbackType.WARNING,
        )

        val event = events.single()
        assertEquals(AppFeedbackType.WARNING, event.type)
        assertTrue(event.matchesLanguage("zh-CN"))
        assertFalse(event.matchesLanguage("en"))
    }
}
