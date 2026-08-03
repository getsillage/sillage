package app.sillage.ui.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SillageServerFormTest {
    private val strings = SillageServerFormStrings(
        addressLabel = "Server address",
        addressPlaceholder = "https://sillage.example",
        submit = "Save and connect",
        submitting = "Connecting",
        useOffline = "Use offline",
    )

    @Test
    fun presentationExposesDraftAndConnectAction() {
        val presentation = sillageServerFormPresentation(
            baseUrl = "https://example.test",
            loading = false,
            strings = strings,
        )

        assertEquals("https://example.test", presentation.baseUrl)
        assertTrue(presentation.controlsEnabled)
        assertTrue(presentation.submitEnabled)
        assertEquals("Save and connect", presentation.actionText)
    }

    @Test
    fun loadingLocksControlsAndUsesProgressActionText() {
        val presentation = sillageServerFormPresentation(
            baseUrl = "",
            loading = true,
            strings = strings,
        )

        assertFalse(presentation.controlsEnabled)
        assertFalse(presentation.submitEnabled)
        assertEquals("Connecting", presentation.actionText)
    }

    @Test
    fun blankAddressDisablesSubmitWithoutLockingDraftControl() {
        val presentation = sillageServerFormPresentation(
            baseUrl = "   ",
            loading = false,
            strings = strings,
        )

        assertTrue(presentation.controlsEnabled)
        assertFalse(presentation.submitEnabled)
    }

    @Test
    fun externalSessionLockDisablesAddressAndSubmit() {
        val presentation = sillageServerFormPresentation(
            baseUrl = "https://example.test",
            loading = false,
            enabled = false,
            strings = strings,
        )

        assertFalse(presentation.controlsEnabled)
        assertFalse(presentation.submitEnabled)
    }
}
