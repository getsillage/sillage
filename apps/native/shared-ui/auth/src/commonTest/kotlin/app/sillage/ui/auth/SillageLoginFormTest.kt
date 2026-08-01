package app.sillage.ui.auth

import app.sillage.features.auth.AuthFeatureStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SillageLoginFormTest {
    private val strings = SillageLoginFormStrings(
        usernameLabel = "Username",
        password = SillagePasswordFieldStrings(
            label = "Password",
            showPassword = "Show password",
            hidePassword = "Hide password",
        ),
        submit = "Sign in",
        submitting = "Signing in",
    )

    @Test
    fun presentationReadsDraftsFromFeatureAggregate() {
        val state = AuthFeatureStateHolder()
            .updateUsername("alice")
            .updatePassword("secret")

        val presentation = sillageLoginFormPresentation(
            state = state,
            loading = false,
            strings = strings,
        )

        assertEquals("alice", presentation.username)
        assertEquals("secret", presentation.password)
        assertTrue(presentation.controlsEnabled)
        assertEquals("Sign in", presentation.actionText)
    }

    @Test
    fun loadingLocksControlsAndUsesProgressActionText() {
        val presentation = sillageLoginFormPresentation(
            state = AuthFeatureStateHolder(),
            loading = true,
            strings = strings,
        )

        assertFalse(presentation.controlsEnabled)
        assertEquals("Signing in", presentation.actionText)
    }
}
