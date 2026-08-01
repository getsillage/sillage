package app.sillage.core.application.records

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AttachmentUploadRepositoryTest {
    @Test
    fun uploadUseCaseDelegatesPlatformNeutralContent() {
        val command = AttachmentUploadCommand(
            filename = "photo.jpg",
            contentType = "image/jpeg",
            bytes = byteArrayOf(1, 2, 3),
        )
        var captured: AttachmentUploadCommand? = null
        val expected = UploadedAttachment(
            uid = "attachment-1",
            url = "/file/attachments/attachment-1/photo.jpg",
            filename = "photo.jpg",
            contentType = "image/jpeg",
            size = 3,
            sha256 = "digest",
        )
        val repository = AttachmentUploadRepository {
            captured = it
            expected
        }

        val actual = runAttachmentSuspend { UploadAttachmentUseCase(repository)(command) }

        assertEquals(expected, actual)
        assertEquals(command.filename, captured?.filename)
        assertEquals(command.contentType, captured?.contentType)
        assertTrue(command.bytes.contentEquals(checkNotNull(captured).bytes))
    }
}

private fun <T> runAttachmentSuspend(block: suspend () -> T): T {
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
