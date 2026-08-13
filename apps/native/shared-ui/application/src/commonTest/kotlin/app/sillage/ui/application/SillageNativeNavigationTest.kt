package app.sillage.ui.application

import app.sillage.ui.appshell.AppDestination
import kotlin.test.Test
import kotlin.test.assertEquals

class SillageNativeNavigationTest {
    @Test
    fun primaryNavigationContainsImplementedDestinations() {
        val items = sillageNativePrimaryNavigationItems(
            screen = AppDestination.Memos,
            askAvailable = true,
        )

        assertEquals(
            listOf(
                SillageNativePrimaryDestination.Records,
                SillageNativePrimaryDestination.Ask,
                SillageNativePrimaryDestination.Settings,
            ),
            items.map { it.destination },
        )
        assertEquals(listOf(true, false, false), items.map { it.selected })
        assertEquals(listOf(true, true, true), items.map { it.enabled })
    }

    @Test
    fun askAndSettingsSelectionsAreExclusive() {
        val askItems = sillageNativePrimaryNavigationItems(
            screen = AppDestination.Ask,
            askAvailable = true,
        )
        val settingsItems = sillageNativePrimaryNavigationItems(
            screen = AppDestination.AISettings,
            askAvailable = true,
        )

        assertEquals(listOf(false, true, false), askItems.map { it.selected })
        assertEquals(listOf(false, false, true), settingsItems.map { it.selected })
    }

    @Test
    fun askNavigationIsDisabledUntilAnAuthenticatedClientIsAvailable() {
        val items = sillageNativePrimaryNavigationItems(
            screen = AppDestination.Memos,
            askAvailable = false,
        )

        assertEquals(listOf(true, false, true), items.map { it.enabled })
    }
}
