package app.sillage.core.domain.settings

/** Platform-neutral AI profile metadata returned by a Sillage instance. */
data class AIProfile(
    val id: String,
    val name: String,
    val provider: String,
    val baseUrl: String,
    val model: String,
    val temperature: Double,
    val maxTokens: Long,
    val enabled: Boolean,
    val active: Boolean,
    val hasApiKey: Boolean,
    val keyUnavailable: Boolean,
    val autoSummary: Boolean,
    val createdAt: String,
    val updatedAt: String,
)

/** Complete AI settings response; secret API-key material is never included. */
data class AISettings(
    val profiles: List<AIProfile>,
    val autoSummary: Boolean,
)
