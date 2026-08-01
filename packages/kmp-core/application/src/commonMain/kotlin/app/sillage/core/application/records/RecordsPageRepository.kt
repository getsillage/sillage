package app.sillage.core.application.records

/**
 * Application-facing port for one server-backed page of records.
 *
 * Implementations translate this semantic query at their transport boundary;
 * HTTP parameters, generated DTOs, and platform clients must not cross the port.
 */
interface RecordsPageRepository {
    suspend fun listPage(query: RecordsPageQuery): RecordsPage
}
