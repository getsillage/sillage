package app.sillage.core.localdata

/** Platform adapter for one atomically replaced, device-local snapshot. */
interface ClientSnapshotStorage {
    val location: String

    fun read(): String?

    fun write(value: String)
}

/** Read-and-clear source used only while moving a host from legacy persistence. */
interface ClientSnapshotMigrationSource {
    fun read(): String?

    fun clear()
}

/** Moves a legacy snapshot only after the primary adapter accepts the full value. */
class MigratingClientSnapshotStorage(
    private val primary: ClientSnapshotStorage,
    private val legacy: ClientSnapshotMigrationSource,
) : ClientSnapshotStorage {
    override val location: String
        get() = primary.location

    override fun read(): String? {
        primary.read()?.let { return it }
        val legacyValue = legacy.read() ?: return null
        primary.write(legacyValue)
        legacy.clear()
        return legacyValue
    }

    override fun write(value: String) {
        primary.write(value)
    }
}

/** Validated, credential-free transfer boundary independent of private storage schema. */
interface ClientBackupTransfer {
    fun exportBackup(): String

    fun restoreBackup(value: String)
}

/** Platform values required to create versioned local records. */
interface ClientRuntimeValues {
    fun nextRecordId(): String

    fun nextMutationId(): String

    fun currentTimestamp(): String
}
