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
    fun restoreDoesNotRequireDraftAndUnlocksFormWhenCredentialIsMissing() {
        val context = context(initialized = true)
        val state = InstanceAuthenticationStateHolder()
        val request = requireNotNull(state.nextRestoreRequest(context))
        val started = requireNotNull(state.begin(request, context))

        val missing = requireNotNull(started.completeRestoreWithoutSession(request, context))

        assertEquals(InstanceAuthenticationOperation.Restore, request.operation)
        assertFalse(missing.loading)
        assertNull(missing.account)
        assertTrue(missing.nextAuthenticationRequest(context) == null)
        assertEquals(
            InstanceAuthenticationFailure.RequiredFields,
            missing.showValidationFailure(context).failure,
        )
    }

    @Test
    fun restorePublishesAccountOnlyForOwnedContext() {
        val context = context(initialized = true)
        val state = InstanceAuthenticationStateHolder()
        val request = requireNotNull(state.nextRestoreRequest(context))
        val started = requireNotNull(state.begin(request, context))

        assertNull(
            started.completeAuthentication(
                request,
                context.copy(clientContextGeneration = context.clientContextGeneration + 1),
                account(),
            ),
        )
        val completed = requireNotNull(started.completeAuthentication(request, context, account()))
        assertEquals(account(), completed.account)
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
    fun passwordChangeIsSingleFlightContextBoundAndUpdatesAccountOnSuccess() {
        val passwordContext = PasswordChangeContext(
            appMode = "online",
            clientContextGeneration = 4,
            online = true,
            anotherOperationInProgress = false,
        )
        val draft = InstanceAuthenticationStateHolder(account = account())
            .updateCurrentPassword("current password")
            .updateNewPassword("new password")
            .updateConfirmPassword("new password")
        val request = requireNotNull(draft.nextPasswordChangeRequest(passwordContext))
        val started = requireNotNull(draft.beginPasswordChange(request, passwordContext))

        assertTrue(started.form.passwordChanging)
        assertNull(started.nextPasswordChangeRequest(passwordContext))
        assertNull(started.nextSignOutRequest(context(initialized = true)))
        assertEquals(
            "current password",
            started.updateCurrentPassword("ignored").form.currentPassword,
        )
        assertNull(
            started.completePasswordChange(
                request,
                passwordContext.copy(clientContextGeneration = 5),
                account(),
            ),
        )

        val failed = requireNotNull(
            started.failPasswordChange(
                request,
                passwordContext,
                InstanceAuthenticationFailure.InvalidCredentials,
            ),
        )
        assertEquals("current password", failed.form.currentPassword)
        assertEquals("new password", failed.form.newPassword)
        assertEquals(InstanceAuthenticationFailure.InvalidCredentials, failed.failure)

        val retry = requireNotNull(failed.nextPasswordChangeRequest(passwordContext))
        val retrying = requireNotNull(failed.beginPasswordChange(retry, passwordContext))
        val changedAccount = account().copy(displayName = "Updated")
        val completed = requireNotNull(
            retrying.completePasswordChange(retry, passwordContext, changedAccount),
        )
        assertEquals(changedAccount, completed.account)
        assertEquals("", completed.form.currentPassword)
        assertEquals("", completed.form.newPassword)
        assertEquals("", completed.form.confirmPassword)
        assertFalse(completed.form.passwordChanging)
        assertNull(completed.failure)
    }

    @Test
    fun passwordChangeValidationPublishesSpecificFailure() {
        val mismatch = InstanceAuthenticationStateHolder(account = account())
            .updateCurrentPassword("current password")
            .updateNewPassword("new password")
            .updateConfirmPassword("different password")

        assertEquals(
            InstanceAuthenticationFailure.PasswordConfirmationMismatch,
            mismatch.showPasswordChangeValidationFailure().failure,
        )
        assertEquals(
            InstanceAuthenticationFailure.PasswordUnchanged,
            mismatch
                .updateNewPassword("current password")
                .updateConfirmPassword("current password")
                .showPasswordChangeValidationFailure()
                .failure,
        )
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
