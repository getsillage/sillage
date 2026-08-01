package app.sillage.data

import app.sillage.core.application.settings.AIAutoSummaryRepository

class LocalAIAutoSummaryRepository(
    private val localDataStore: LocalDataStore,
) : AIAutoSummaryRepository {
    override suspend fun save(enabled: Boolean): Boolean {
        localDataStore.saveAutoSummary(enabled)
        return enabled
    }
}
