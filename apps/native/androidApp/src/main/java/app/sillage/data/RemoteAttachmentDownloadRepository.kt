package app.sillage.data

import app.sillage.core.application.records.AttachmentDownloadCommand
import app.sillage.core.application.records.AttachmentDownloadRepository
import app.sillage.core.application.records.DownloadedAttachmentMetadata
import java.io.File

class RemoteAttachmentDownloadRepository(
    private val api: SillageApi,
) : AttachmentDownloadRepository<File> {
    override suspend fun download(
        command: AttachmentDownloadCommand,
        destination: File,
    ): DownloadedAttachmentMetadata {
        return api.downloadAttachment(command.path, destination)
    }
}
