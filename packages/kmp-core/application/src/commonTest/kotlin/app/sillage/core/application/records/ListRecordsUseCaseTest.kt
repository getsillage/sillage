package app.sillage.core.application.records

import app.sillage.core.domain.records.Memo
import kotlin.test.Test
import kotlin.test.assertEquals

class ListRecordsUseCaseTest {
    @Test
    fun returnsTheRepositorySnapshotWithoutPlatformTypes() {
        val expected = listOf(memo("memo-1"), memo("memo-2"))
        val useCase = ListRecordsUseCase(
            repository = object : RecordsRepository {
                override fun listRecords(): List<Memo> = expected
            },
        )

        assertEquals(expected, useCase())
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
