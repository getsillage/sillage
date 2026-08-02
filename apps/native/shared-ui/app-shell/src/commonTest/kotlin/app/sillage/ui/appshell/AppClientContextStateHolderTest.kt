package app.sillage.ui.appshell

import app.sillage.core.application.preferences.ClientPreferenceValues
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppClientContextStateHolderTest {
    @Test
    fun modeSelectionAndWorkspaceSwitchesResetOwnedNavigation() {
        val original = AppClientContextStateHolder(
            screen = AppDestination.Ask,
            history = listOf(AppDestination.Memos),
            appMode = ClientPreferenceValues.MODE_OFFLINE,
            generation = 4,
        )

        val selected = original.chooseOnlineMode()
        assertEquals(AppDestination.Server, selected.screen)
        assertTrue(selected.history.isEmpty())
        assertEquals(ClientPreferenceValues.MODE_ONLINE, selected.appMode)
        assertEquals(4, selected.generation)

        val online = selected.switchToOnlineWorkspace()
        assertEquals(AppDestination.Loading, online.screen)
        assertTrue(online.history.isEmpty())
        assertEquals(5, online.generation)
        assertTrue(online.online)

        val offline = online.enterOfflineWorkspace()
        assertEquals(AppDestination.Memos, offline.screen)
        assertTrue(offline.history.isEmpty())
        assertEquals(ClientPreferenceValues.MODE_OFFLINE, offline.appMode)
        assertEquals(6, offline.generation)
        assertFalse(offline.online)
    }

    @Test
    fun serverSettingsRestoreEligibleDestinationOrPersistedModeFallback() {
        val records = AppClientContextStateHolder(screen = AppDestination.MemoDetail)
            .openServerSettings()
        assertEquals(AppDestination.Server, records.screen)
        assertEquals(AppDestination.MemoDetail, records.serverReturnScreen)

        val restored = records.cancelServerConnection(
            persistedAppMode = ClientPreferenceValues.MODE_ONLINE,
            hasPersistedAppModeSelection = true,
        )
        assertEquals(AppDestination.MemoDetail, restored.screen)
        assertNull(restored.serverReturnScreen)

        val offline = AppClientContextStateHolder(screen = AppDestination.ModeSelection)
            .openServerSettings()
            .cancelServerConnection(
                persistedAppMode = ClientPreferenceValues.MODE_OFFLINE,
                hasPersistedAppModeSelection = true,
            )
        assertEquals(AppDestination.Memos, offline.screen)
        assertEquals(ClientPreferenceValues.MODE_OFFLINE, offline.appMode)

        val unselected = AppClientContextStateHolder(screen = AppDestination.Server)
            .cancelServerConnection(
                persistedAppMode = "unexpected",
                hasPersistedAppModeSelection = false,
            )
        assertEquals(AppDestination.ModeSelection, unselected.screen)
        assertEquals(ClientPreferenceValues.MODE_ONLINE, unselected.appMode)
    }

    @Test
    fun serverChangeAndSignOutAdvanceWorkspaceGeneration() {
        val original = AppClientContextStateHolder(
            screen = AppDestination.AISettings,
            appMode = ClientPreferenceValues.MODE_OFFLINE,
            generation = 8,
            serverReturnScreen = AppDestination.Memos,
        )

        val changedServer = original.resetForServerChange()
        assertEquals(ClientPreferenceValues.MODE_ONLINE, changedServer.appMode)
        assertEquals(9, changedServer.generation)
        assertNull(changedServer.serverReturnScreen)

        val signedOutOnline = changedServer.afterSignOut(offlineMode = false)
        assertEquals(AppDestination.Login, signedOutOnline.screen)
        assertEquals(10, signedOutOnline.generation)

        val signedOutOffline = changedServer.afterSignOut(offlineMode = true)
        assertEquals(AppDestination.Memos, signedOutOffline.screen)
        assertEquals(10, signedOutOffline.generation)
    }

    @Test
    fun navigationAndContextMatchingRemainAtomic() {
        val records = AppClientContextStateHolder(screen = AppDestination.Memos)
        val detail = records.navigateTo(AppDestination.MemoDetail)
        val editor = detail.navigateTo(AppDestination.Editor)

        assertEquals(
            listOf(AppDestination.Memos, AppDestination.MemoDetail),
            editor.history,
        )
        assertEquals(detail, editor.back(AppDestination.Memos))
    }
}
