package app.sillage.core.network

enum class SillageHttpMethod {
    Get,
    Post,
}

data class SillageHttpRequest(
    val method: SillageHttpMethod,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
) {
    override fun toString(): String {
        return "SillageHttpRequest(" +
            "method=$method, url=$url, headerNames=${headers.keys.sorted()}, " +
            "bodyCharacters=${body?.length ?: 0})"
    }
}

/** Host HTTP boundary used by public bootstrap and native authentication. */
fun interface SillageHttpTransport {
    suspend fun execute(request: SillageHttpRequest): SillageHttpResponse
}

data class SillageHttpResponse(
    val statusCode: Int,
    val body: String,
    val headers: Map<String, List<String>> = emptyMap(),
) {
    fun headerValues(name: String): List<String> {
        return headers.entries
            .filter { (key, _) -> key.equals(name, ignoreCase = true) }
            .flatMap(Map.Entry<String, List<String>>::value)
    }

    override fun toString(): String {
        return "SillageHttpResponse(" +
            "statusCode=$statusCode, headerNames=${headers.keys.sorted()}, " +
            "bodyCharacters=${body.length})"
    }
}
