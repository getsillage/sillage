package app.sillage.core.application.records

data class AttachmentUploadCommand(
    val filename: String,
    val contentType: String,
    val bytes: ByteArray,
)

data class UploadedAttachment(
    val uid: String,
    val url: String,
    val filename: String,
    val contentType: String,
    val size: Long,
    val sha256: String?,
)

fun interface AttachmentUploadRepository {
    suspend fun upload(command: AttachmentUploadCommand): UploadedAttachment
}

class UploadAttachmentUseCase(
    private val repository: AttachmentUploadRepository,
) {
    suspend operator fun invoke(command: AttachmentUploadCommand): UploadedAttachment {
        return repository.upload(command)
    }
}
