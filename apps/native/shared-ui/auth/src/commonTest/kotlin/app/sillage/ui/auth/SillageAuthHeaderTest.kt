package app.sillage.ui.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SillageAuthHeaderTest {
    @Test
    fun presentationPreservesHostBrandAndLanguageDescription() {
        val presentation = sillageAuthHeaderPresentation(
            appName = "Sillage",
            tagline = "Private knowledge, connected",
            languageContentDescription = "Switch to Chinese",
            languageEnabled = true,
        )

        assertEquals("Sillage", presentation.appName)
        assertEquals("Private knowledge, connected", presentation.tagline)
        assertEquals("Switch to Chinese", presentation.languageContentDescription)
        assertTrue(presentation.languageEnabled)
    }

    @Test
    fun presentationDisablesLanguageActionDuringLoading() {
        val presentation = sillageAuthHeaderPresentation(
            appName = "Sillage",
            tagline = "Private knowledge, connected",
            languageContentDescription = "Switch language",
            languageEnabled = false,
        )

        assertFalse(presentation.languageEnabled)
    }
}
