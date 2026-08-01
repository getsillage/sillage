package app.sillage.core.application.records

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals

class AttachmentDownloadRepositoryTest {
    @Test
    fun downloadUseCaseDelegatesPathAndOpaqueHostDestination() {
        val destination = TestDestination("cache-token")
        val expected = DownloadedAttachmentMetadata(
            contentType = "application/pdf",
            contentDisposition = "attachment; filename=report.pdf",
            urlFilename = "report.pdf",
        )
        var capturedCommand: AttachmentDownloadCommand? = null
        var capturedDestination: TestDestination? = null
        val repository = AttachmentDownloadRepository<TestDestination> { command, sink ->
            capturedCommand = command
            capturedDestination = sink
            expected
        }

        val actual = runAttachmentDownloadSuspend {
            DownloadAttachmentUseCase(repository)(
                AttachmentDownloadCommand("/file/attachments/id/report.pdf"),
                destination,
            )
        }

        assertEquals(expected, actual)
        assertEquals("/file/attachments/id/report.pdf", capturedCommand?.path)
        assertEquals(destination, capturedDestination)
    }
}

private data class TestDestination(val token: String)

private fun <T> runAttachmentDownloadSuspend(block: suspend () -> T): T {
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
