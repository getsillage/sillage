package app.sillage.core.localdata

/** Platform adapter for one atomically replaced, device-local snapshot. */
interface ClientSnapshotStorage {
    val location: String

    fun read(): String?

    fun write(value: String)
}

/** Validated, credential-free transfer boundary independent of private storage schema. */
interface ClientBackupTransfer {
    fun exportBackup(): String

    fun restoreBackup(value: String)
}

/** Platform values required to create versioned local records. */
interface ClientRuntimeValues {
    fun nextRecordId(): String

    fun currentTimestamp(): String
}
