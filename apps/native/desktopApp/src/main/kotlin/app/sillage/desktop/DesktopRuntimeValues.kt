package app.sillage.desktop

import app.sillage.core.localdata.ClientRuntimeValues
import java.time.Instant
import java.util.UUID

internal class DesktopRuntimeValues : ClientRuntimeValues {
    override fun nextRecordId(): String = UUID.randomUUID().toString()

    override fun nextMutationId(): String = UUID.randomUUID().toString()

    override fun currentTimestamp(): String = Instant.now().toString()
}
