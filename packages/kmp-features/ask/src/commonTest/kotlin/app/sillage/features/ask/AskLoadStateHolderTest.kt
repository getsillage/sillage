package app.sillage.features.ask

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AskLoadStateHolderTest {
    @Test
    fun beginAndCompleteClearPreviousFailure() {
        val failed = AskLoadStateHolder(errorMessage = "failed")

        val loading = failed.begin()
        val completed = loading.complete()

        assertTrue(loading.loading)
        assertNull(loading.errorMessage)
        assertFalse(completed.loading)
        assertNull(completed.errorMessage)
    }

    @Test
    fun failureRetainsRetryMessageUntilNextTransition() {
        val failed = AskLoadStateHolder(loading = true).fail("retry")

        assertFalse(failed.loading)
        assertEquals("retry", failed.errorMessage)
        assertNull(failed.cancel().errorMessage)
    }
}
