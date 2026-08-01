package app.sillage.data

import app.sillage.core.application.settings.AIAutoSummaryRepository

class RemoteAIAutoSummaryRepository(
    private val api: SillageApi,
) : AIAutoSummaryRepository {
    override suspend fun save(enabled: Boolean): Boolean = api.setAIAutoSummary(enabled)
}
