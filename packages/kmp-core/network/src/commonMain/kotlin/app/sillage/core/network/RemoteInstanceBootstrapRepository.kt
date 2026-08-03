package app.sillage.core.network

import app.sillage.core.application.auth.BootstrapInfo
import app.sillage.core.application.auth.InstanceBootstrapRepository
import app.sillage.core.application.preferences.normalizeBaseUrl
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

sealed class SillageNetworkException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

class InvalidServerAddressException :
    SillageNetworkException("The Sillage server address is invalid.")

class SillageHttpStatusException(
    val statusCode: Int,
) : SillageNetworkException("The Sillage server returned HTTP $statusCode.")

class InvalidServerResponseException(
    message: String,
    cause: Throwable? = null,
) : SillageNetworkException(message, cause)

class RemoteInstanceBootstrapRepository(
    private val transport: SillageHttpTransport,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : InstanceBootstrapRepository {
    private companion object {
        const val MaxBootstrapResponseCharacters = 64 * 1024
    }

    override suspend fun load(baseUrl: String): BootstrapInfo {
        val normalized = validatedBaseUrl(baseUrl)
        val response = transport.get("$normalized/api/v1/auth/bootstrap")
        if (response.statusCode !in 200..299) {
            throw SillageHttpStatusException(response.statusCode)
        }
        if (response.body.length > MaxBootstrapResponseCharacters) {
            throw InvalidServerResponseException(
                "The Sillage bootstrap response is too large.",
            )
        }

        val body = try {
            json.parseToJsonElement(response.body).jsonObject
        } catch (error: SerializationException) {
            throw InvalidServerResponseException(
                "The Sillage bootstrap response is not valid JSON.",
                error,
            )
        } catch (error: IllegalArgumentException) {
            throw InvalidServerResponseException(
                "The Sillage bootstrap response is invalid.",
                error,
            )
        }

        val initialized = (body["initialized"] as? JsonPrimitive)?.booleanOrNull
            ?: throw InvalidServerResponseException(
                "The Sillage bootstrap response is missing initialized.",
            )

        return BootstrapInfo(
            initialized = initialized,
            serverVersion = body.stringValue("serverVersion"),
            serverRevision = body.stringValue("serverRevision"),
            apiVersion = body.stringValue("apiVersion"),
            minimumAndroidVersionCode = (body["minimumAndroidVersionCode"] as? JsonPrimitive)
                ?.intOrNull
                ?: 0,
        )
    }
}

private fun validatedBaseUrl(value: String): String {
    val normalized = normalizeBaseUrl(value)
    val authority = when {
        normalized.startsWith("https://") -> normalized.removePrefix("https://")
        normalized.startsWith("http://") -> normalized.removePrefix("http://")
        else -> throw InvalidServerAddressException()
    }
    val authorityOnly = authority.substringBefore('/')
    if (
        authorityOnly.isBlank() ||
        '@' in authorityOnly ||
        '?' in authority ||
        '#' in authority ||
        authority.any(Char::isWhitespace)
    ) {
        throw InvalidServerAddressException()
    }
    return normalized
}

private fun Map<String, JsonElement>.stringValue(key: String): String {
    return (this[key] as? JsonPrimitive)?.content.orEmpty()
}
