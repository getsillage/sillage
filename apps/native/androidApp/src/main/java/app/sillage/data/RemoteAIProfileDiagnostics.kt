package app.sillage.data

import app.sillage.core.application.settings.AIProfileConfigurationCommand
import app.sillage.core.application.settings.AIProfileConnectionTester
import app.sillage.core.application.settings.AIProfileModelCatalog

class RemoteAIProfileDiagnostics(
    private val api: SillageApi,
) : AIProfileConnectionTester, AIProfileModelCatalog {
    override suspend fun test(configuration: AIProfileConfigurationCommand): String {
        return configuration.id?.let { api.testAIConnection(it) }
            ?: api.testAIConnection(configuration.toRemoteInput())
    }

    override suspend fun listModels(
        configuration: AIProfileConfigurationCommand,
    ): List<String> {
        return api.listAIModels(configuration.toRemoteInput())
    }
}
