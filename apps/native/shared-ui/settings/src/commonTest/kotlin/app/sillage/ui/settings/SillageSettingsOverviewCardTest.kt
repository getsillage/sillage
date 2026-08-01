package app.sillage.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class SillageSettingsOverviewCardTest {
    @Test
    fun presentationPreservesLocalizedOverviewOrder() {
        val items = listOf(
            SillageSettingsOverviewItem(label = "Online", value = "https://example.test"),
            SillageSettingsOverviewItem(label = "Theme", value = "Dark"),
            SillageSettingsOverviewItem(label = "AI", value = "Automatic summary"),
        )

        val presentation = sillageSettingsOverviewPresentation(
            title = "Current status",
            items = items,
        )

        assertEquals("Current status", presentation.title)
        assertEquals(items, presentation.items)
    }
}
