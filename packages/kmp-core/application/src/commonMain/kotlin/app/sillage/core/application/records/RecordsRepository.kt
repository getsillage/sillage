package app.sillage.core.application.records

import app.sillage.core.domain.records.Memo

/**
 * Application-facing access to one consistent record snapshot.
 *
 * Implementations own storage transactions and model mapping. They must not
 * expose database, transport, or platform types through this port.
 */
interface RecordsRepository {
    fun listRecords(): List<Memo>
}
