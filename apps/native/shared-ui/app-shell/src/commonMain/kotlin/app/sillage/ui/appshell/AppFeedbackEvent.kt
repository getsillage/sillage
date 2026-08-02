package app.sillage.ui.appshell

enum class AppFeedbackType {
    SUCCESS,
    WARNING,
    ERROR,
}

data class AppFeedbackEvent(
    val id: Long,
    val type: AppFeedbackType,
    val message: String,
    val languageMode: String,
) {
    fun matchesLanguage(languageMode: String): Boolean {
        return this.languageMode == languageMode
    }
}

data class AppFeedbackSnapshot(
    val error: String? = null,
    val notice: String? = null,
    val languageMode: String,
)

/**
 * Turns root-state feedback changes into one-shot presentation events.
 *
 * Hosts serialize calls with their state-update mechanism and decide how to
 * render each event.
 */
class AppFeedbackEventEmitter(
    private val emit: (AppFeedbackEvent) -> Unit,
) {
    private var nextId = 0L

    fun onStateChanged(
        before: AppFeedbackSnapshot,
        after: AppFeedbackSnapshot,
        forceFeedback: Boolean = false,
        noticeType: AppFeedbackType = AppFeedbackType.SUCCESS,
    ) {
        val error = after.error?.takeIf { forceFeedback || it != before.error }
        if (error != null) {
            emit(nextEvent(AppFeedbackType.ERROR, error, after.languageMode))
            return
        }
        if (after.error != null) {
            return
        }
        after.notice
            ?.takeIf { forceFeedback || it != before.notice }
            ?.let { emit(nextEvent(noticeType, it, after.languageMode)) }
    }

    private fun nextEvent(
        type: AppFeedbackType,
        message: String,
        languageMode: String,
    ): AppFeedbackEvent {
        nextId += 1
        return AppFeedbackEvent(
            id = nextId,
            type = type,
            message = message,
            languageMode = languageMode,
        )
    }
}
