package app.sillage.features.auth

import app.sillage.core.application.auth.BootstrapInfo
import app.sillage.core.application.preferences.normalizeBaseUrl

data class InstanceBootstrapContext(
    val clientContextGeneration: Long,
)

data class InstanceBootstrapRequest(
    val requestId: Long,
    val baseUrl: String,
    val clientContextGeneration: Long,
)

/** Owns server-address draft and public bootstrap request identity. */
data class InstanceBootstrapStateHolder(
    val baseUrl: String = "",
    val checking: Boolean = false,
    val requestId: Long = 0,
    val checkedBaseUrl: String? = null,
    val bootstrap: BootstrapInfo? = null,
    val failed: Boolean = false,
) {
    fun updateBaseUrl(value: String): InstanceBootstrapStateHolder {
        return copy(
            baseUrl = value,
            checking = false,
            requestId = requestId + if (checking) 1 else 0,
            checkedBaseUrl = null,
            bootstrap = null,
            failed = false,
        )
    }

    fun nextRequest(context: InstanceBootstrapContext): InstanceBootstrapRequest? {
        val normalized = normalizeBaseUrl(baseUrl)
        if (checking || normalized.isBlank()) return null
        return InstanceBootstrapRequest(
            requestId = requestId + 1,
            baseUrl = normalized,
            clientContextGeneration = context.clientContextGeneration,
        )
    }

    fun begin(
        request: InstanceBootstrapRequest,
        context: InstanceBootstrapContext,
    ): InstanceBootstrapStateHolder? {
        if (nextRequest(context) != request) return null
        return copy(
            baseUrl = request.baseUrl,
            checking = true,
            requestId = request.requestId,
            checkedBaseUrl = null,
            bootstrap = null,
            failed = false,
        )
    }

    fun complete(
        request: InstanceBootstrapRequest,
        context: InstanceBootstrapContext,
        result: BootstrapInfo,
    ): InstanceBootstrapStateHolder? {
        if (!owns(request, context)) return null
        return copy(
            checking = false,
            checkedBaseUrl = request.baseUrl,
            bootstrap = result,
            failed = false,
        )
    }

    fun fail(
        request: InstanceBootstrapRequest,
        context: InstanceBootstrapContext,
    ): InstanceBootstrapStateHolder? {
        if (!owns(request, context)) return null
        return copy(
            checking = false,
            checkedBaseUrl = request.baseUrl,
            bootstrap = null,
            failed = true,
        )
    }

    fun cancel(
        request: InstanceBootstrapRequest,
        context: InstanceBootstrapContext,
    ): InstanceBootstrapStateHolder? {
        if (!owns(request, context)) return null
        return copy(
            checking = false,
            requestId = requestId + 1,
            checkedBaseUrl = null,
            bootstrap = null,
            failed = false,
        )
    }

    private fun owns(
        request: InstanceBootstrapRequest,
        context: InstanceBootstrapContext,
    ): Boolean {
        return checking &&
            requestId == request.requestId &&
            baseUrl == request.baseUrl &&
            request.clientContextGeneration == context.clientContextGeneration
    }
}
