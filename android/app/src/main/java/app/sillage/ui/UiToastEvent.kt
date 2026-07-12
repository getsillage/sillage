package app.sillage.ui

internal enum class UiToastType {
    SUCCESS,
    WARNING,
    ERROR,
}

internal data class UiToastEvent(
    val id: Long,
    val type: UiToastType,
    val message: String,
    val languageMode: String,
)

internal fun UiToastEvent.matchesLanguage(languageMode: String): Boolean {
    return this.languageMode == languageMode
}

internal class UiToastEventEmitter(
    private val emit: (UiToastEvent) -> Unit,
) {
    private var nextId = 0L

    @Synchronized
    fun onStateChanged(
        before: SillageUiState,
        after: SillageUiState,
        forceFeedback: Boolean = false,
        noticeType: UiToastType = UiToastType.SUCCESS,
    ) {
        val error = after.error?.takeIf { forceFeedback || it != before.error }
        if (error != null) {
            emit(nextEvent(UiToastType.ERROR, error, after.languageMode))
            return
        }
        if (after.error != null) {
            return
        }
        after.notice
            ?.takeIf { forceFeedback || it != before.notice }
            ?.let { emit(nextEvent(noticeType, it, after.languageMode)) }
    }

    private fun nextEvent(type: UiToastType, message: String, languageMode: String): UiToastEvent {
        nextId += 1
        return UiToastEvent(
            id = nextId,
            type = type,
            message = message,
            languageMode = languageMode,
        )
    }
}
