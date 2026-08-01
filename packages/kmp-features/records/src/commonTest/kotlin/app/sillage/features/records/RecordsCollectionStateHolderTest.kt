package app.sillage.features.records

import app.sillage.core.domain.records.Memo
import kotlin.test.Test
import kotlin.test.assertEquals

class RecordsCollectionStateHolderTest {
    @Test
    fun replacementPreservesMutationGeneration() {
        val replacement = listOf(memo("memo-2"))
        val state = RecordsCollectionStateHolder(
            records = listOf(memo("memo-1")),
            cacheGeneration = 4,
        )

        assertEquals(replacement, state.replace(replacement).records)
        assertEquals(4, state.replace(replacement).cacheGeneration)
        assertEquals(4, state.clear().cacheGeneration)
    }

    @Test
    fun canonicalMutationReplacesIdentityFiltersAndAdvancesGeneration() {
        val original = memo("memo-1")
        val archived = original.copy(archivedAt = "2026-08-01T01:00:00Z", version = 2)
        val state = RecordsCollectionStateHolder(
            records = listOf(original, memo("memo-2")),
            cacheGeneration = 7,
        )

        val updated = state.applyMemo(archived, MemoListFilter.Unarchived)

        assertEquals(listOf("memo-2"), updated.records.map(Memo::id))
        assertEquals(8, updated.cacheGeneration)
    }

    private fun memo(id: String): Memo {
        return Memo(
            id = id,
            content = id,
            entryDate = "2026-08-01",
            version = 1,
            createdAt = "2026-08-01T00:00:00Z",
            updatedAt = "2026-08-01T00:00:00Z",
            favoritedAt = null,
            archivedAt = null,
            deletedAt = null,
        )
    }
}
