package app.sillage.ui.auth

import kotlin.test.Test
import kotlin.test.assertEquals

class SillageAccountSettingsSectionTest {
    @Test
    fun presentationPreservesSectionAndContentStrings() {
        val content = SillageAccountSettingsStrings(
            changePasswordTitle = "Change password",
            changePasswordSupporting = "Update your account password",
            currentPasswordLabel = "Current password",
            newPasswordLabel = "New password",
            confirmPasswordLabel = "Confirm password",
            savePassword = "Save password",
            signOut = "Sign out",
        )

        val presentation = sillageAccountSettingsSectionPresentation(
            SillageAccountSettingsSectionStrings(
                sectionTitle = "Account",
                content = content,
            ),
        )

        assertEquals("Account", presentation.sectionTitle)
        assertEquals(content, presentation.content)
    }
}
