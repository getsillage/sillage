package app.sillage.data

import app.sillage.core.application.records.AttachmentUploadCommand
import app.sillage.core.application.records.AttachmentUploadRepository
import app.sillage.core.application.records.UploadedAttachment

class RemoteAttachmentUploadRepository(
    private val api: SillageApi,
) : AttachmentUploadRepository {
    override suspend fun upload(command: AttachmentUploadCommand): UploadedAttachment {
        return api.uploadAttachment(command)
    }
}
