package app.sillage.features.settings

import app.sillage.core.domain.settings.AIProfile

/**
 * Editable AI-profile state shared by native clients.
 *
 * [draftKey] is presentation-only identity for profiles that have not been
 * persisted. It must not be serialized by platform storage or transport
 * adapters. Raw numeric inputs remain strings until a save or test command is
 * assembled so intermediate editor values such as an empty string or `0.` are
 * not coerced while the user types.
 */
data class AIProfileDraft(
    val id: String = "",
    val draftKey: String = "",
    val name: String = "",
    val provider: String = "anthropic",
    val baseUrl: String = "",
    val model: String = "",
    val temperature: Double = 0.3,
    val maxTokens: Long = 1000,
    val enabled: Boolean = true,
    val active: Boolean = false,
    val hasApiKey: Boolean = false,
    val keyUnavailable: Boolean = false,
    val apiKeyInput: String = "",
    val temperatureInput: String = temperature.toString(),
    val maxTokensInput: String = maxTokens.toString(),
)

/** Create editable, secret-free state from canonical profile metadata. */
fun AIProfile.toDraft(): AIProfileDraft {
    return AIProfileDraft(
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
        temperatureInput = temperature.toString(),
        maxTokensInput = maxTokens.toString(),
    )
}

fun firstBlankAIProfileNameIndex(profiles: List<AIProfileDraft>): Int? {
    return profiles.indexOfFirst { it.name.isBlank() }.takeIf { it >= 0 }
}

/** Enforce the persisted invariant that exactly one non-empty profile is active. */
fun normalizeAIProfilesForSave(profiles: List<AIProfileDraft>): List<AIProfileDraft> {
    if (profiles.isEmpty()) {
        return profiles
    }
    val activeIndex = profiles.indexOfFirst { it.active }.takeIf { it >= 0 } ?: 0
    return profiles.mapIndexed { index, profile ->
        profile.copy(enabled = true, active = index == activeIndex)
    }
}

/**
 * Restore locally held secret input after a secret-free save response.
 *
 * Persisted profile IDs are matched before positional fallback. Positional
 * matching is permitted only for a newly created draft without an ID, so a
 * reordered response cannot associate one profile's key with another profile.
 */
fun mergeSavedAIProfilesForLocalStorage(
    currentProfiles: List<AIProfileDraft>,
    remoteProfiles: List<AIProfileDraft>,
    submittedProfiles: List<AIProfileDraft>,
): List<AIProfileDraft> {
    val currentById = currentProfiles
        .filter { it.id.isNotBlank() }
        .associateBy { it.id }
    val submittedById = submittedProfiles
        .filter { it.id.isNotBlank() }
        .associateBy { it.id }

    return remoteProfiles.mapIndexed { index, profile ->
        val submitted = submittedById[profile.id]
            ?: submittedProfiles.getOrNull(index)?.takeIf { it.id.isBlank() }
        val existing = currentById[profile.id]
        val apiKeyInput = when {
            submitted?.apiKeyInput.orEmpty().isNotBlank() -> submitted?.apiKeyInput?.trim().orEmpty()
            existing?.apiKeyInput.orEmpty().isNotBlank() -> existing?.apiKeyInput?.trim().orEmpty()
            else -> ""
        }
        profile.copy(
            hasApiKey = profile.hasApiKey || apiKeyInput.isNotBlank(),
            apiKeyInput = apiKeyInput,
            keyUnavailable = false,
        )
    }
}
