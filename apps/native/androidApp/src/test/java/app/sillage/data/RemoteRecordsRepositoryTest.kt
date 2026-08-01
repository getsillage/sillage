package app.sillage.data

import app.sillage.core.application.records.RecordsQueryScope
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteRecordsRepositoryTest {
    @Test
    fun mapsEveryApplicationScopeToTheRestQuery() {
        assertEquals(
            RecordsTransportQuery(archived = false, favorited = false, deleted = false),
            RecordsQueryScope.Unarchived.transportQuery(),
        )
        assertEquals(
            RecordsTransportQuery(archived = true, favorited = false, deleted = false),
            RecordsQueryScope.Archived.transportQuery(),
        )
        assertEquals(
            RecordsTransportQuery(archived = null, favorited = true, deleted = false),
            RecordsQueryScope.Favorited.transportQuery(),
        )
        assertEquals(
            RecordsTransportQuery(archived = null, favorited = false, deleted = true),
            RecordsQueryScope.Deleted.transportQuery(),
        )
    }
}
