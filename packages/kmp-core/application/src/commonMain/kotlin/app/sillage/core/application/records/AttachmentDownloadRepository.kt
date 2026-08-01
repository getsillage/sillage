package app.sillage.core.application.records

data class AttachmentDownloadCommand(
    val path: String,
)

data class DownloadedAttachmentMetadata(
    val contentType: String?,
    val contentDisposition: String?,
    val urlFilename: String,
)

/**
 * Downloads authenticated attachment content into a host-provided destination.
 *
 * The generic destination keeps filesystem and streaming implementations outside
 * common code while allowing adapters to write directly without materializing
 * the entire response in shared state.
 */
fun interface AttachmentDownloadRepository<Destination> {
    suspend fun download(
        command: AttachmentDownloadCommand,
        destination: Destination,
    ): DownloadedAttachmentMetadata
}

class DownloadAttachmentUseCase<Destination>(
    private val repository: AttachmentDownloadRepository<Destination>,
) {
    suspend operator fun invoke(
        command: AttachmentDownloadCommand,
        destination: Destination,
    ): DownloadedAttachmentMetadata {
        return repository.download(command, destination)
    }
}
