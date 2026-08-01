package app.sillage.data

import app.sillage.core.application.settings.AIProfileConfigurationCommand
import app.sillage.core.application.settings.AIProfileConnectionTester

class LocalAIProfileConnectionTester(
    private val client: LocalAiClient,
) : AIProfileConnectionTester {
    override suspend fun test(configuration: AIProfileConfigurationCommand): String {
        return client.testConnection(configuration.toLocalDraft())
    }
}
