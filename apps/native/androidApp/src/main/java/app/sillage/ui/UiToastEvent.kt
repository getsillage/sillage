package app.sillage.ui

import app.sillage.ui.appshell.AppFeedbackEvent
import app.sillage.ui.appshell.AppFeedbackEventEmitter
import app.sillage.ui.appshell.AppFeedbackSnapshot
import app.sillage.ui.appshell.AppFeedbackType

internal typealias UiToastType = AppFeedbackType
internal typealias UiToastEvent = AppFeedbackEvent
internal typealias UiToastEventEmitter = AppFeedbackEventEmitter

internal fun UiToastEventEmitter.onStateChanged(
    before: SillageUiState,
    after: SillageUiState,
    forceFeedback: Boolean = false,
    noticeType: UiToastType = UiToastType.SUCCESS,
) {
    onStateChanged(
        before = before.feedbackSnapshot(),
        after = after.feedbackSnapshot(),
        forceFeedback = forceFeedback,
        noticeType = noticeType,
    )
}

private fun SillageUiState.feedbackSnapshot(): AppFeedbackSnapshot {
    return AppFeedbackSnapshot(
        error = error,
        notice = notice,
        languageMode = languageMode,
    )
}
