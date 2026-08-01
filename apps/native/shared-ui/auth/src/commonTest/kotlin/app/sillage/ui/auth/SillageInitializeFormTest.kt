package app.sillage.ui.auth

import app.sillage.features.auth.AuthFeatureStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SillageInitializeFormTest {
    private val strings = SillageInitializeFormStrings(
        usernameLabel = "Username",
        displayNameLabel = "Display name",
        password = SillagePasswordFieldStrings(
            label = "Password",
            showPassword = "Show password",
            hidePassword = "Hide password",
        ),
        submit = "Create and continue",
        submitting = "Creating",
    )

    @Test
    fun presentationReadsDraftsFromFeatureAggregate() {
        val state = AuthFeatureStateHolder()
            .updateUsername("alice")
            .updateDisplayName("Alice")
            .updatePassword("secret")

        val presentation = sillageInitializeFormPresentation(
            state = state,
            loading = false,
            strings = strings,
        )

        assertEquals("alice", presentation.username)
        assertEquals("Alice", presentation.displayName)
        assertEquals("secret", presentation.password)
        assertTrue(presentation.controlsEnabled)
        assertEquals("Create and continue", presentation.actionText)
    }

    @Test
    fun loadingLocksControlsAndUsesProgressActionText() {
        val presentation = sillageInitializeFormPresentation(
            state = AuthFeatureStateHolder(),
            loading = true,
            strings = strings,
        )

        assertFalse(presentation.controlsEnabled)
        assertEquals("Creating", presentation.actionText)
    }
}
