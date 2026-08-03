package app.sillage.features.auth

import app.sillage.core.domain.auth.Account
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InstanceAuthenticationStateHolderTest {
    @Test
    fun initializationCompletesOnlyForOwnedRequestAndClearsPassword() {
        val context = context(initialized = false)
        val draft = InstanceAuthenticationStateHolder()
            .updateUsername(" felix ")
            .updateDisplayName(" Felix ")
            .updatePassword("secret password")
        val request = requireNotNull(draft.nextAuthenticationRequest(context))
        val started = requireNotNull(draft.begin(request, context))

        val completed = requireNotNull(
            started.completeAuthentication(request, context, account()),
        )

        assertEquals(InstanceAuthenticationOperation.Initialize, request.operation)
        assertEquals("felix", request.username)
        assertEquals("Felix", request.displayName)
        assertEquals(account(), completed.account)
        assertEquals("", completed.form.password)
        assertFalse(completed.loading)
    }

    @Test
    fun editingDuringRequestInvalidatesLateCompletionAndRetainsNewDraft() {
        val context = context(initialized = true)
        val draft = InstanceAuthenticationStateHolder()
            .updateUsername("felix")
            .updatePassword("old password")
        val request = requireNotNull(draft.nextAuthenticationRequest(context))
        val started = requireNotNull(draft.begin(request, context))

        val edited = started.updatePassword("new password")

        assertNull(edited.completeAuthentication(request, context, account()))
        assertEquals("new password", edited.form.password)
        assertFalse(edited.loading)
    }

    @Test
    fun failureRetainsCredentialsAndCancellationUnlocksRetry() {
        val context = context(initialized = true)
        val draft = InstanceAuthenticationStateHolder()
            .updateUsername("felix")
            .updatePassword("password")
        val request = requireNotNull(draft.nextAuthenticationRequest(context))
        val started = requireNotNull(draft.begin(request, context))
        val failed = requireNotNull(
            started.fail(request, context, InstanceAuthenticationFailure.InvalidCredentials),
        )

        assertEquals("felix", failed.form.username)
        assertEquals("password", failed.form.password)
        assertEquals(InstanceAuthenticationFailure.InvalidCredentials, failed.failure)

        val retry = requireNotNull(failed.nextAuthenticationRequest(context))
        val retrying = requireNotNull(failed.begin(retry, context))
        val cancelled = requireNotNull(retrying.cancel(retry, context))
        assertFalse(cancelled.loading)
        assertTrue(cancelled.nextAuthenticationRequest(context) != null)
    }

    @Test
    fun signOutRejectsChangedAccountContext() {
        val context = context(initialized = true)
        val signedIn = InstanceAuthenticationStateHolder(account = account())
        val request = requireNotNull(signedIn.nextSignOutRequest(context))
        val started = requireNotNull(signedIn.begin(request, context))
        val replaced = started.copy(account = account().copy(id = "account-2"))

        assertNull(replaced.completeSignOut(request, context))
    }

    @Test
    fun requiredFieldsPublishLocalValidationWithoutStartingRequest() {
        val state = InstanceAuthenticationStateHolder()
        val context = context(initialized = true)

        assertNull(state.nextAuthenticationRequest(context))
        assertEquals(
            InstanceAuthenticationFailure.RequiredFields,
            state.showValidationFailure(context).failure,
        )
    }

    private fun context(initialized: Boolean) = InstanceAuthenticationContext(
        baseUrl = "https://example.test",
        initialized = initialized,
        clientContextGeneration = 4,
    )

    private fun account() = Account(
        id = "account-1",
        username = "felix",
        displayName = "Felix",
    )
}
