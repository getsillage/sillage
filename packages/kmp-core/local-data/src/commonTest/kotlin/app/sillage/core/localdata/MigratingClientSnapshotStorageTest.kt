package app.sillage.core.localdata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MigratingClientSnapshotStorageTest {
    @Test
    fun copiesLegacyValueThenClearsMigrationSource() {
        val primary = MigrationMemoryStorage()
        val legacy = MigrationMemorySource("legacy")
        val storage = MigratingClientSnapshotStorage(primary, legacy)

        assertEquals("legacy", storage.read())
        assertEquals("legacy", primary.value)
        assertTrue(legacy.cleared)
        assertNull(legacy.value)
        assertEquals(primary.location, storage.location)
    }

    @Test
    fun existingPrimaryValueWinsWithoutTouchingLegacyRecoveryCopy() {
        val primary = MigrationMemoryStorage("primary")
        val legacy = MigrationMemorySource("legacy")
        val storage = MigratingClientSnapshotStorage(primary, legacy)

        assertEquals("primary", storage.read())
        assertEquals("legacy", legacy.value)
        assertFalse(legacy.cleared)
    }

    @Test
    fun failedPrimaryWriteKeepsLegacyValue() {
        val primary = MigrationMemoryStorage(failWrites = true)
        val legacy = MigrationMemorySource("legacy")
        val storage = MigratingClientSnapshotStorage(primary, legacy)

        assertFailsWith<IllegalStateException> { storage.read() }
        assertEquals("legacy", legacy.value)
        assertFalse(legacy.cleared)
    }
}

private class MigrationMemoryStorage(
    var value: String? = null,
    private val failWrites: Boolean = false,
) : ClientSnapshotStorage {
    override val location: String = "memory://primary"

    override fun read(): String? = value

    override fun write(value: String) {
        check(!failWrites) { "write failed" }
        this.value = value
    }
}

private class MigrationMemorySource(
    var value: String?,
) : ClientSnapshotMigrationSource {
    var cleared: Boolean = false
        private set

    override fun read(): String? = value

    override fun clear() {
        value = null
        cleared = true
    }
}
