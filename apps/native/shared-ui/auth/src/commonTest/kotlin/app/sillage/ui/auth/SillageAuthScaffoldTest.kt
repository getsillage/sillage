package app.sillage.ui.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SillageAuthScaffoldTest {
    @Test
    fun presentationPreservesLocalizedHeadingAndError() {
        val presentation = sillageAuthScaffoldPresentation(
            title = "Sign in",
            supporting = "Continue to your server",
            errorMessage = "Credentials rejected",
        )

        assertEquals("Sign in", presentation.title)
        assertEquals("Continue to your server", presentation.supporting)
        assertEquals("Credentials rejected", presentation.errorMessage)
    }

    @Test
    fun presentationKeepsErrorOptional() {
        val presentation = sillageAuthScaffoldPresentation(
            title = "Choose mode",
            supporting = "Work offline or connect",
            errorMessage = null,
        )

        assertNull(presentation.errorMessage)
    }
}
