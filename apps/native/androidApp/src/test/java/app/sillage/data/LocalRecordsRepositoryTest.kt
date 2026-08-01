package app.sillage.data

import app.sillage.core.application.records.RecordsQueryScope
import app.sillage.features.records.MemoListFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalRecordsRepositoryTest {
    @Test
    fun mapsEveryApplicationScopeToTheLocalFilter() {
        assertEquals(MemoListFilter.Unarchived, RecordsQueryScope.Unarchived.localFilter())
        assertEquals(MemoListFilter.Archived, RecordsQueryScope.Archived.localFilter())
        assertEquals(MemoListFilter.Favorited, RecordsQueryScope.Favorited.localFilter())
        assertEquals(MemoListFilter.Deleted, RecordsQueryScope.Deleted.localFilter())
    }
}
