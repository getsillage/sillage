package app.sillage.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    @Test
    fun presentationAllowsMetadataWithoutLicenseAction() {
        val presentation = sillageSettingsAboutPresentation(
            strings = SillageSettingsAboutStrings(sectionTitle = "About"),
            values = listOf(SillageSettingsAboutValue(label = "Platform", value = "macOS")),
        )

        assertEquals("About", presentation.sectionTitle)
        assertNull(presentation.licensesTitle)
        assertNull(presentation.licensesSupporting)
    }
}
