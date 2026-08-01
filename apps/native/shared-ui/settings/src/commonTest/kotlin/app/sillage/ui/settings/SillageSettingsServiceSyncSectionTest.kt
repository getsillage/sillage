package app.sillage.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SillageSettingsServiceSyncSectionTest {
    private val strings = SillageSettingsServiceSyncStrings(
        sectionTitle = "Service and sync",
        refreshTitle = "Refresh records",
        refreshSupporting = "Reload current records",
        onlineCurrent = "Online mode",
        onlineSwitch = "Switch online",
        serverNotConfigured = "Server not configured",
        offlineCurrent = "Offline mode",
        offlineSwitch = "Switch offline",
        offlineSupporting = "Use local storage",
        serverTitle = "Server",
        serverSupporting = "Configure the server",
        syncLocalTitle = "Download",
        syncLocalSupporting = "Replace local data",
        syncCloudTitle = "Upload",
        syncCloudSupporting = "Replace server data",
        syncBothTitle = "Sync both ways",
        syncBothSupporting = "Merge local and server data",
    )

    @Test
    fun onlinePresentationEnablesOnlineActionsAndUsesServerFallback() {
        val presentation = sillageSettingsServiceSyncPresentation(
            online = true,
            baseUrl = "",
            strings = strings,
            loading = false,
            clientContextBlocked = false,
        )

        assertEquals("Online mode", presentation.onlineTitle)
        assertEquals("Server not configured", presentation.onlineSupporting)
        assertFalse(presentation.onlineEnabled)
        assertTrue(presentation.offlineEnabled)
        assertTrue(presentation.contextActionsEnabled)
        assertTrue(presentation.refreshEnabled)
    }

    @Test
    fun offlineAndBlockedPresentationDisablesContextChanges() {
        val presentation = sillageSettingsServiceSyncPresentation(
            online = false,
            baseUrl = "https://example.test",
            strings = strings,
            loading = true,
            clientContextBlocked = true,
        )

        assertEquals("Switch online", presentation.onlineTitle)
        assertEquals("Offline mode", presentation.offlineTitle)
        assertFalse(presentation.onlineEnabled)
        assertFalse(presentation.offlineEnabled)
        assertFalse(presentation.contextActionsEnabled)
        assertFalse(presentation.refreshEnabled)
    }
}
