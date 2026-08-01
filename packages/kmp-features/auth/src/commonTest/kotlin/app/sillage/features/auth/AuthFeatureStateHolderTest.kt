package app.sillage.features.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AuthFeatureStateHolderTest {
    @Test
    fun credentialUpdatesGoThroughAggregate() {
        val updated = AuthFeatureStateHolder()
            .updateUsername("alice")
            .updateDisplayName("Alice")
            .updatePassword("secret")

        assertEquals("alice", updated.username)
        assertEquals("Alice", updated.displayName)
        assertEquals("secret", updated.password)
    }

    @Test
    fun passwordChangeDraftUpdatesGoThroughAggregate() {
        val updated = AuthFeatureStateHolder()
            .updateCurrentPassword("old")
            .updateNewPassword("new")
            .updateConfirmPassword("new")

        assertEquals("old", updated.currentPassword)
        assertEquals("new", updated.newPassword)
        assertEquals("new", updated.confirmPassword)
    }

    @Test
    fun clearPrimaryCredentialsPreservesDisplayNameWhenRequested() {
        val state = AuthFeatureStateHolder(
            authentication = AuthenticationStateHolder(
                username = "alice",
                displayName = "Alice",
                password = "secret",
            ),
        )

        val cleared = state.clearPrimaryCredentials(clearDisplayName = false)

        assertEquals("", cleared.username)
        assertEquals("Alice", cleared.displayName)
        assertEquals("", cleared.password)
        assertFalse(cleared.passwordChanging)
    }
}
