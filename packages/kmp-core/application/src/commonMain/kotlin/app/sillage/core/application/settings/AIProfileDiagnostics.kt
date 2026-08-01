package app.sillage.core.application.settings

/** Tests whether one profile configuration can reach its provider. */
interface AIProfileConnectionTester {
    suspend fun test(configuration: AIProfileConfigurationCommand): String
}

/** Lists provider models available to one profile configuration. */
interface AIProfileModelCatalog {
    suspend fun listModels(configuration: AIProfileConfigurationCommand): List<String>
}

class TestAIProfileConnectionUseCase(
    private val tester: AIProfileConnectionTester,
) {
    suspend operator fun invoke(configuration: AIProfileConfigurationCommand): String {
        return tester.test(configuration)
    }
}

class ListAIProfileModelsUseCase(
    private val catalog: AIProfileModelCatalog,
) {
    suspend operator fun invoke(configuration: AIProfileConfigurationCommand): List<String> {
        return catalog.listModels(configuration)
    }
}
