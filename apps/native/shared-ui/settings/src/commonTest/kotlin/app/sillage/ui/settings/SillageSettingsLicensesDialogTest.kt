package app.sillage.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SillageSettingsLicensesDialogTest {
    @Test
    fun presentationPreservesLocalizedCopyAndNoticeText() {
        val notices = "Package inventory\n\nApache License 2.0\n"
        val presentation = sillageSettingsLicensesDialogPresentation(
            notices = notices,
            strings = SillageSettingsLicensesDialogStrings(
                title = "Open-source licenses",
                close = "Close",
            ),
        )

        assertEquals("Open-source licenses", presentation.title)
        assertEquals(notices, presentation.notices)
        assertEquals("Close", presentation.close)
    }

    @Test
    fun presentationRejectsMissingNoticeContent() {
        assertFailsWith<IllegalArgumentException> {
            sillageSettingsLicensesDialogPresentation(
                notices = "  ",
                strings = SillageSettingsLicensesDialogStrings(
                    title = "Licenses",
                    close = "Close",
                ),
            )
        }
    }
}
