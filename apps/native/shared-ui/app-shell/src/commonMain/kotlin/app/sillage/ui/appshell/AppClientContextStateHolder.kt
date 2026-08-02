package app.sillage.ui.appshell

import app.sillage.core.application.preferences.ClientPreferenceValues
import app.sillage.core.application.preferences.normalizeAppMode

/** Platform-neutral application destination and workspace identity. */
data class AppClientContextStateHolder(
    val screen: AppDestination = AppDestination.Loading,
    val history: List<AppDestination> = emptyList(),
    val appMode: String = ClientPreferenceValues.MODE_ONLINE,
    val generation: Long = 0,
    val serverReturnScreen: AppDestination? = null,
) {
    val online: Boolean
        get() = appMode == ClientPreferenceValues.MODE_ONLINE

    fun chooseOnlineMode(): AppClientContextStateHolder {
        return copy(
            screen = AppDestination.Server,
            history = emptyList(),
            appMode = ClientPreferenceValues.MODE_ONLINE,
        )
    }

    fun resetForServerChange(): AppClientContextStateHolder {
        return copy(
            appMode = ClientPreferenceValues.MODE_ONLINE,
            generation = generation + 1,
            serverReturnScreen = null,
        )
    }

    fun switchToOnlineWorkspace(): AppClientContextStateHolder {
        return copy(
            screen = AppDestination.Loading,
            history = emptyList(),
            appMode = ClientPreferenceValues.MODE_ONLINE,
            generation = generation + 1,
        )
    }

    fun enterOfflineWorkspace(): AppClientContextStateHolder {
        return copy(
            screen = AppDestination.Memos,
            history = emptyList(),
            appMode = ClientPreferenceValues.MODE_OFFLINE,
            generation = generation + 1,
        )
    }

    fun afterSignOut(offlineMode: Boolean): AppClientContextStateHolder {
        return copy(
            screen = if (offlineMode) AppDestination.Memos else AppDestination.Login,
            history = emptyList(),
            generation = generation + 1,
        )
    }

    fun openServerSettings(): AppClientContextStateHolder {
        return copy(
            screen = AppDestination.Server,
            serverReturnScreen = screen.takeIf {
                it != AppDestination.Server && it != AppDestination.ModeSelection
            },
        )
    }

    fun cancelServerConnection(
        persistedAppMode: String,
        hasPersistedAppModeSelection: Boolean,
    ): AppClientContextStateHolder {
        val normalizedMode = normalizeAppMode(persistedAppMode)
        val target = when {
            serverReturnScreen != null -> serverReturnScreen
            hasPersistedAppModeSelection &&
                normalizedMode == ClientPreferenceValues.MODE_OFFLINE -> AppDestination.Memos
            else -> AppDestination.ModeSelection
        }
        return copy(
            screen = target,
            appMode = normalizedMode,
            serverReturnScreen = null,
        )
    }

    fun show(screen: AppDestination): AppClientContextStateHolder = copy(screen = screen)

    fun showRoot(screen: AppDestination): AppClientContextStateHolder {
        return copy(screen = screen, history = emptyList())
    }

    fun navigateTo(screen: AppDestination): AppClientContextStateHolder {
        return copy(
            screen = screen,
            history = AppNavigationPolicy.historyFor(this.screen, history, screen),
        )
    }

    fun navigateTo(
        screen: AppDestination,
        history: List<AppDestination>,
    ): AppClientContextStateHolder = copy(screen = screen, history = history)

    fun back(fallback: AppDestination): AppClientContextStateHolder {
        val navigation = AppNavigationPolicy.back(history, fallback)
        return copy(screen = navigation.screen, history = navigation.history)
    }
}
