package app.sillage.ui.appshell

/** Product-level destinations shared by native application shells. */
enum class AppDestination {
    Loading,
    ModeSelection,
    Server,
    Initialize,
    Login,
    Memos,
    MemoDetail,
    Editor,
    AISettings,
    Ask,
}

data class AppBackNavigation(
    val screen: AppDestination,
    val history: List<AppDestination>,
)

/** Platform-neutral root navigation history and system-back policy. */
object AppNavigationPolicy {
    fun historyFor(
        current: AppDestination,
        history: List<AppDestination>,
        destination: AppDestination,
    ): List<AppDestination> {
        return if (current == destination) history else history + current
    }

    fun back(
        history: List<AppDestination>,
        fallback: AppDestination,
    ): AppBackNavigation {
        return AppBackNavigation(
            screen = history.lastOrNull() ?: fallback,
            history = if (history.isEmpty()) emptyList() else history.dropLast(1),
        )
    }

    fun shouldReturnToRecords(
        current: AppDestination,
        recordsCalendarActive: Boolean,
    ): Boolean {
        return current == AppDestination.Ask ||
            current == AppDestination.AISettings ||
            (current == AppDestination.Memos && recordsCalendarActive)
    }
}
