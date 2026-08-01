package app.sillage.core.application.settings

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals

class AIAutoSummaryRepositoryTest {
    @Test
    fun setUseCaseDelegatesAndReturnsPersistedValue() {
        val repository = CapturingRepository(savedValue = false)

        val saved = runSuspend { SetAIAutoSummaryUseCase(repository)(true) }

        assertEquals(true, repository.requestedValue)
        assertEquals(false, saved)
    }

    private class CapturingRepository(
        private val savedValue: Boolean,
    ) : AIAutoSummaryRepository {
        var requestedValue: Boolean? = null

        override suspend fun save(enabled: Boolean): Boolean {
            requestedValue = enabled
            return savedValue
        }
    }
}

private fun <T> runSuspend(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(
        object : Continuation<T> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<T>) {
                outcome = result
            }
        },
    )
    return checkNotNull(outcome) { "Test coroutine did not complete synchronously" }.getOrThrow()
}
