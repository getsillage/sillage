package app.sillage.features.records

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

    fun begin(path: String): RecordsAttachmentOpenStateHolder? {
        if (opening || path.isBlank()) {
            return null
        }
        return copy(path = path, requestId = requestId + 1)
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
