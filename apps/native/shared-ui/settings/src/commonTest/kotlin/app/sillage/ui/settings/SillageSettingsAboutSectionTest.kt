package app.sillage.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class SillageSettingsAboutSectionTest {
    @Test
    fun presentationPreservesHostMetadataOrder() {
        val values = listOf(
            SillageSettingsAboutValue(label = "App version", value = "1.0 (1)"),
            SillageSettingsAboutValue(label = "Server version", value = "2.0"),
            SillageSettingsAboutValue(label = "API version", value = "v1"),
        )

        val presentation = sillageSettingsAboutPresentation(
            strings = SillageSettingsAboutStrings(
                sectionTitle = "About",
                licensesTitle = "Open-source licenses",
                licensesSupporting = "Review third-party notices",
            ),
            values = values,
        )

        assertEquals("About", presentation.sectionTitle)
        assertEquals(values, presentation.values)
        assertEquals("Open-source licenses", presentation.licensesTitle)
    }
}
