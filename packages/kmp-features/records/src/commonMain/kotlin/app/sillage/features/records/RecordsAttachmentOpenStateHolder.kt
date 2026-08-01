package app.sillage.features.records

data class RecordsAttachmentOpenRequest(
    val requestId: Long,
    val path: String,
)

/**
 * Platform-neutral ownership for one attachment-open preparation request.
 *
 * Platform hosts still resolve URIs, stage bytes, and launch native viewers.
 * This holder only guards request identity so a late completion cannot affect a
 * newer attachment or a screen that already invalidated the request.
 */
data class RecordsAttachmentOpenStateHolder(
    val path: String? = null,
    val requestId: Long = 0,
) {
    val opening: Boolean get() = path != null

    fun nextRequest(path: String): RecordsAttachmentOpenRequest? {
        if (opening || path.isBlank()) {
            return null
        }
        return RecordsAttachmentOpenRequest(
            requestId = requestId + 1,
            path = path,
        )
    }

    fun begin(request: RecordsAttachmentOpenRequest): RecordsAttachmentOpenStateHolder? {
        if (
            opening ||
            request.path.isBlank() ||
            request.requestId != requestId + 1
        ) {
            return null
        }
        return copy(path = request.path, requestId = request.requestId)
    }

    fun begin(path: String): RecordsAttachmentOpenStateHolder? {
        val request = nextRequest(path) ?: return null
        return begin(request)
    }

    fun owns(requestId: Long): Boolean {
        return opening && this.requestId == requestId
    }

    fun complete(requestId: Long): RecordsAttachmentOpenStateHolder {
        return if (owns(requestId)) copy(path = null) else this
    }

    fun invalidate(): RecordsAttachmentOpenStateHolder {
        return if (opening) copy(path = null, requestId = requestId + 1) else this
    }
}
