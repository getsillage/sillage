package app.sillage.core.localdata

/** Platform adapter for one atomically replaced, device-local snapshot. */
interface ClientSnapshotStorage {
    val location: String

    fun read(): String?

    fun write(value: String)
}

/** Platform values required to create versioned local records. */
interface ClientRuntimeValues {
    fun nextRecordId(): String

    fun currentTimestamp(): String
}
