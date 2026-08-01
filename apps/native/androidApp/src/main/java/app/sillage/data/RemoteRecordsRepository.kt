package app.sillage.data

import app.sillage.core.application.records.RecordsPage
import app.sillage.core.application.records.RecordsPageQuery
import app.sillage.core.application.records.RecordsPageRepository
import app.sillage.core.application.records.RecordsQueryScope

/** Android HTTP adapter for the shared records page application port. */
class RemoteRecordsRepository(
    private val api: SillageApi,
) : RecordsPageRepository {
    override suspend fun listPage(query: RecordsPageQuery): RecordsPage {
        val transportQuery = query.scope.transportQuery()
        val page = api.listMemos(
            cursor = query.cursor,
            archived = transportQuery.archived,
            favorited = transportQuery.favorited,
            deleted = transportQuery.deleted,
        )
        return RecordsPage(
            memos = page.memos,
            nextCursor = page.nextCursor,
        )
    }
}

internal data class RecordsTransportQuery(
    val archived: Boolean?,
    val favorited: Boolean,
    val deleted: Boolean,
)

internal fun RecordsQueryScope.transportQuery(): RecordsTransportQuery {
    return when (this) {
        RecordsQueryScope.Unarchived ->
            RecordsTransportQuery(archived = false, favorited = false, deleted = false)
        RecordsQueryScope.Archived ->
            RecordsTransportQuery(archived = true, favorited = false, deleted = false)
        RecordsQueryScope.Favorited ->
            RecordsTransportQuery(archived = null, favorited = true, deleted = false)
        RecordsQueryScope.Deleted ->
            RecordsTransportQuery(archived = null, favorited = false, deleted = true)
    }
}
