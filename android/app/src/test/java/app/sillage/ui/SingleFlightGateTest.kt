package app.sillage.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SingleFlightGateTest {
    @Test
    fun rejectsReentryUntilTheLeaseIsReleased() {
        val gate = SingleFlightGate()
        val first = gate.tryAcquire()

        assertNotNull(first)
        assertNull(gate.tryAcquire())

        first?.release()

        assertNotNull(gate.tryAcquire())
    }

    @Test
    fun failureIsReportedAndReleasesTheGateForRetry() = runBlocking {
        val gate = SingleFlightGate()
        val lease = requireNotNull(gate.tryAcquire())
        val failure = IllegalStateException("request failed")
        var reported: Throwable? = null
        var finished = 0

        runSingleFlightOperation(
            lease = lease,
            onFailure = { reported = it },
            onFinished = { finished += 1 },
        ) {
            assertNull(gate.tryAcquire())
            throw failure
        }

        assertSame(failure, reported)
        assertEquals(1, finished)
        assertNotNull(gate.tryAcquire())
    }

    @Test
    fun cancellationSkipsFailureFeedbackAndStillReleasesTheGate() = runBlocking {
        val gate = SingleFlightGate()
        val lease = requireNotNull(gate.tryAcquire())
        val cancellation = CancellationException("cancelled")
        var reported: Throwable? = null
        var finished = 0

        val caught = runCatching {
            runSingleFlightOperation(
                lease = lease,
                onFailure = { reported = it },
                onFinished = { finished += 1 },
            ) {
                throw cancellation
            }
        }.exceptionOrNull()

        assertSame(cancellation, caught)
        assertNull(reported)
        assertEquals(1, finished)
        assertNotNull(gate.tryAcquire())
    }

    @Test
    fun keyedGateRejectsTheSameKeyWhileAllowingIndependentKeys() {
        val gate = KeyedSingleFlightGate<String>()
        val firstMemo = requireNotNull(gate.tryAcquire("memo-1"))

        assertNull(gate.tryAcquire("memo-1"))
        val secondMemo = requireNotNull(gate.tryAcquire("memo-2"))

        firstMemo.release()
        val reacquiredMemo = requireNotNull(gate.tryAcquire("memo-1"))
        assertNotNull(reacquiredMemo)
        reacquiredMemo.release()
        secondMemo.release()
    }
}
