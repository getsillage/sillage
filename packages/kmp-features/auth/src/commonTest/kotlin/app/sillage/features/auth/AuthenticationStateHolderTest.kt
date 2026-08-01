package app.sillage.features.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthenticationStateHolderTest {
    private val context = PasswordChangeContext(
        appMode = "online",
        clientContextGeneration = 4,
        online = true,
        anotherOperationInProgress = false,
    )

    @Test
    fun passwordValidationIsPlatformIndependent() {
        assertEquals(
            PasswordChangeValidation.RequiredFields,
            AuthenticationStateHolder().passwordChangeValidation(),
        )
        assertEquals(
            PasswordChangeValidation.ConfirmationMismatch,
            validDraft().copy(confirmPassword = "different").passwordChangeValidation(),
        )
        assertEquals(
            PasswordChangeValidation.Unchanged,
            validDraft().copy(newPassword = "current", confirmPassword = "current")
                .passwordChangeValidation(),
        )
        assertNull(validDraft().passwordChangeValidation())
    }

    @Test
    fun passwordChangeIsSingleFlightAndContextBound() {
        val draft = validDraft().copy(passwordChangeRequestId = 6)
        val request = requireNotNull(draft.nextPasswordChangeRequest(context))
        val changing = requireNotNull(draft.beginPasswordChange(request, context))

        assertEquals(7, request.requestId)
        assertTrue(changing.passwordChanging)
        assertNull(changing.nextPasswordChangeRequest(context))
        assertTrue(changing.canApplyPasswordChange(request, context))
        assertFalse(
            changing.canApplyPasswordChange(
                request,
                context.copy(clientContextGeneration = context.clientContextGeneration + 1),
            ),
        )
    }

    @Test
    fun completionClearsSecretsWhileFailurePreservesDraft() {
        val draft = validDraft()
        val request = requireNotNull(draft.nextPasswordChangeRequest(context))
        val changing = requireNotNull(draft.beginPasswordChange(request, context))

        val completed = requireNotNull(changing.completePasswordChange(request, context))
        assertEquals("", completed.currentPassword)
        assertEquals("", completed.newPassword)
        assertEquals("", completed.confirmPassword)
        assertFalse(completed.passwordChanging)

        val failed = requireNotNull(changing.failPasswordChange(request, context))
        assertEquals("current", failed.currentPassword)
        assertEquals("next", failed.newPassword)
        assertFalse(failed.passwordChanging)
    }

    @Test
    fun offlineOrAnotherOperationBlocksPasswordChange() {
        val draft = validDraft()
        assertNull(draft.nextPasswordChangeRequest(context.copy(online = false)))
        assertNull(draft.nextPasswordChangeRequest(context.copy(anotherOperationInProgress = true)))
    }

    private fun validDraft(): AuthenticationStateHolder = AuthenticationStateHolder(
        currentPassword = "current",
        newPassword = "next",
        confirmPassword = "next",
    )
}
