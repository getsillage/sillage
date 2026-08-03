package app.sillage.ui.application

import app.sillage.ui.appshell.AppDestination
import kotlin.test.Test
import kotlin.test.assertEquals

class SillageNativeNavigationTest {
    @Test
    fun primaryNavigationContainsOnlyImplementedDestinations() {
        val items = sillageNativePrimaryNavigationItems(AppDestination.Memos)

        assertEquals(
            listOf(
                SillageNativePrimaryDestination.Records,
                SillageNativePrimaryDestination.Settings,
            ),
            items.map { it.destination },
        )
        assertEquals(listOf(true, false), items.map { it.selected })
    }

    @Test
    fun settingsSelectionIsExclusive() {
        val items = sillageNativePrimaryNavigationItems(AppDestination.AISettings)

        assertEquals(listOf(false, true), items.map { it.selected })
    }
}
