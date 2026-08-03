package app.sillage.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SillageSettingsDataSectionTest {
    @Test
    fun presentationPreservesLocalizedActionsAndHostGate() {
        val presentation = sillageSettingsDataPresentation(
            strings = SillageSettingsDataStrings(
                sectionTitle = "Data",
                exportTitle = "Export",
                exportSupporting = "Save a JSON backup",
                importTitle = "Import",
                importSupporting = "Restore a JSON backup",
            ),
            enabled = false,
        )

        assertEquals("Data", presentation.sectionTitle)
        assertEquals("Export", presentation.exportTitle)
        assertEquals("Import", presentation.importTitle)
        assertFalse(presentation.exportEnabled)
        assertFalse(presentation.importEnabled)
    }

    @Test
    fun presentationPreservesIndependentActionGates() {
        val presentation = sillageSettingsDataPresentation(
            strings = SillageSettingsDataStrings(
                sectionTitle = "Data",
                exportTitle = "Export",
                exportSupporting = "Save a JSON backup",
                importTitle = "Import",
                importSupporting = "Restore a JSON backup",
            ),
            enabled = true,
            exportEnabled = false,
            importEnabled = true,
        )

        assertFalse(presentation.exportEnabled)
        assertTrue(presentation.importEnabled)
    }
}
