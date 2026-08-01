package app.sillage.ui

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException

internal class SingleFlightGate {
    private val occupied = AtomicBoolean(false)

    fun tryAcquire(): Lease? {
        if (!occupied.compareAndSet(false, true)) {
            return null
        }
        return Lease { occupied.set(false) }
    }

    internal class Lease(private val releaseGate: () -> Unit) {
        private val released = AtomicBoolean(false)

        fun release() {
            if (released.compareAndSet(false, true)) {
                releaseGate()
            }
        }
    }
}

internal class KeyedSingleFlightGate<K : Any> {
    private val occupied = ConcurrentHashMap.newKeySet<K>()

    fun tryAcquire(key: K): SingleFlightGate.Lease? {
        if (!occupied.add(key)) {
            return null
        }
        return SingleFlightGate.Lease { occupied.remove(key) }
    }
}

internal suspend fun runSingleFlightOperation(
    lease: SingleFlightGate.Lease,
    onFailure: (Throwable) -> Unit,
    onFinished: () -> Unit,
    block: suspend () -> Unit,
) {
    try {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            onFailure(error)
        }
    } finally {
        try {
            onFinished()
        } finally {
            lease.release()
        }
    }
}
