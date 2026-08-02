package app.sillage.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SillageSettingsListTest {
    @Test
    fun loadingPresentationSuppressesRetryContent() {
        val presentation = sillageSettingsListPresentation(
            loading = true,
            errorMessage = "Load failed",
        )

        assertTrue(presentation.loading)
        assertNull(presentation.errorMessage)
    }

    @Test
    fun readyPresentationKeepsLoadError() {
        val presentation = sillageSettingsListPresentation(
            loading = false,
            errorMessage = "Load failed",
        )

        assertEquals("Load failed", presentation.errorMessage)
    }
}
