package app.sillage.core.network

/** Minimal public HTTP boundary used before a native client has credentials. */
fun interface SillageHttpTransport {
    suspend fun get(url: String): SillageHttpResponse
}

data class SillageHttpResponse(
    val statusCode: Int,
    val body: String,
)
