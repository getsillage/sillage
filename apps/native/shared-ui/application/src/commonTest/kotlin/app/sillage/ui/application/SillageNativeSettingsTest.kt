package app.sillage.ui.application

import app.sillage.core.application.preferences.ClientPreferenceValues
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SillageNativeSettingsTest {
    @Test
    fun presentationMapsSharedSectionsAndPreservesMetadataOrder() {
        val strings = sillageNativeStrings(ClientPreferenceValues.LANGUAGE_EN)
        val presentation = sillageNativeSettingsPresentation(
            strings = strings,
            platform = SillageNativePlatform(
                name = "macOS",
                dataLocation = "/tmp/sillage.json",
                version = "1.2.3",
            ),
        )

        assertEquals("Appearance", presentation.appearanceStrings.sectionTitle)
        assertEquals("Choose the interface language", presentation.appearanceStrings.language.supporting)
        assertEquals(
            listOf(ClientPreferenceValues.LANGUAGE_ZH_CN, ClientPreferenceValues.LANGUAGE_EN),
            presentation.languageOptions.map { it.value },
        )
        assertEquals("Data", presentation.dataStrings.sectionTitle)
        assertEquals("Restore backup", presentation.dataStrings.importTitle)
        assertEquals("About", presentation.aboutStrings.sectionTitle)
        assertNull(presentation.aboutStrings.licensesTitle)
        assertEquals(
            listOf("Mode", "Platform", "Version"),
            presentation.aboutValues.map { it.label },
        )
        assertEquals(
            listOf(strings.offlineModeValue, "macOS", "1.2.3"),
            presentation.aboutValues.map { it.value },
        )
    }
}
