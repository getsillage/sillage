package app.sillage.data

import app.sillage.core.domain.settings.AIProfile
import app.sillage.features.settings.AIProfileDraft

internal fun AIProfileDraft.toDomainProfile(autoSummary: Boolean): AIProfile {
    return AIProfile(
        id = id,
        name = name,
        provider = provider,
        baseUrl = baseUrl,
        model = model,
        temperature = temperature,
        maxTokens = maxTokens,
        enabled = enabled,
        active = active,
        hasApiKey = hasApiKey,
        keyUnavailable = keyUnavailable,
        autoSummary = autoSummary,
        createdAt = "",
        updatedAt = "",
    )
}
