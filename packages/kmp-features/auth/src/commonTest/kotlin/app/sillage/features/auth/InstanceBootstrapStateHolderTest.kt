package app.sillage.features.auth

import app.sillage.core.application.auth.BootstrapInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InstanceBootstrapStateHolderTest {
    private val context = InstanceBootstrapContext(clientContextGeneration = 4)
    private val bootstrap = BootstrapInfo(
        initialized = true,
        serverVersion = "0.3.1",
        serverRevision = "abc",
        apiVersion = "v1",
        minimumAndroidVersionCode = 1,
    )

    @Test
    fun normalizesAddressAndPublishesOwnedResult() {
        val draft = InstanceBootstrapStateHolder(baseUrl = "example.test/")
        val request = requireNotNull(draft.nextRequest(context))
        val started = requireNotNull(draft.begin(request, context))
        val completed = requireNotNull(started.complete(request, context, bootstrap))

        assertEquals("https://example.test", completed.baseUrl)
        assertEquals("https://example.test", completed.checkedBaseUrl)
        assertEquals(bootstrap, completed.bootstrap)
        assertFalse(completed.checking)
        assertFalse(completed.failed)
    }

    @Test
    fun editDuringRequestInvalidatesLateCompletion() {
        val draft = InstanceBootstrapStateHolder(baseUrl = "first.test")
        val request = requireNotNull(draft.nextRequest(context))
        val started = requireNotNull(draft.begin(request, context))
        val edited = started.updateBaseUrl("second.test")

        assertNull(edited.complete(request, context, bootstrap))
        assertFalse(edited.checking)
        assertEquals("second.test", edited.baseUrl)
    }

    @Test
    fun contextChangeRejectsLateFailure() {
        val draft = InstanceBootstrapStateHolder(baseUrl = "example.test")
        val request = requireNotNull(draft.nextRequest(context))
        val started = requireNotNull(draft.begin(request, context))

        assertNull(
            started.fail(
                request,
                InstanceBootstrapContext(clientContextGeneration = 5),
            ),
        )
        assertTrue(started.checking)
    }

    @Test
    fun failureBelongsToCheckedAddress() {
        val draft = InstanceBootstrapStateHolder(baseUrl = "example.test")
        val request = requireNotNull(draft.nextRequest(context))
        val failed = requireNotNull(
            requireNotNull(draft.begin(request, context)).fail(request, context),
        )

        assertTrue(failed.failed)
        assertEquals("https://example.test", failed.checkedBaseUrl)
        assertNull(failed.bootstrap)
    }

    @Test
    fun cancellationUnlocksRetryWithoutPublishingFailure() {
        val draft = InstanceBootstrapStateHolder(baseUrl = "example.test")
        val request = requireNotNull(draft.nextRequest(context))
        val started = requireNotNull(draft.begin(request, context))
        val cancelled = requireNotNull(started.cancel(request, context))

        assertFalse(cancelled.checking)
        assertFalse(cancelled.failed)
        assertNull(cancelled.checkedBaseUrl)
        assertTrue(cancelled.nextRequest(context)!!.requestId > request.requestId)
    }
}
