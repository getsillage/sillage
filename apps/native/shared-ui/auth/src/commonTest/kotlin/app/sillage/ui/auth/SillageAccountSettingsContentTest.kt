package app.sillage.ui.auth

import app.sillage.features.auth.AuthFeatureStateHolder
import app.sillage.features.auth.AuthenticationStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SillageAccountSettingsContentTest {
    @Test
    fun presentationReadsPasswordDraftsFromFeatureAggregate() {
        val state = AuthFeatureStateHolder()
            .updateCurrentPassword("current")
            .updateNewPassword("new-secret")
            .updateConfirmPassword("new-secret")

        val presentation = sillageAccountSettingsPresentation(
            state = state,
            mutationBlocked = false,
        )

        assertEquals("current", presentation.currentPassword)
        assertEquals("new-secret", presentation.newPassword)
        assertEquals("new-secret", presentation.confirmPassword)
        assertTrue(presentation.controlsEnabled)
        assertTrue(presentation.signOutEnabled)
    }

    @Test
    fun passwordChangeOrHostMutationLocksAccountActions() {
        val changing = sillageAccountSettingsPresentation(
            state = AuthFeatureStateHolder(
                authentication = AuthenticationStateHolder(passwordChanging = true),
            ),
            mutationBlocked = false,
        )
        val blocked = sillageAccountSettingsPresentation(
            state = AuthFeatureStateHolder(),
            mutationBlocked = true,
        )

        assertTrue(changing.passwordChanging)
        assertFalse(changing.controlsEnabled)
        assertFalse(changing.signOutEnabled)
        assertFalse(blocked.controlsEnabled)
        assertFalse(blocked.signOutEnabled)
    }
}
