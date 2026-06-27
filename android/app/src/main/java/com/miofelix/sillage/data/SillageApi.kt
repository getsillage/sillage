package com.miofelix.sillage.data

import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class SillageApi(private val sessionStore: SessionStore) {
    private val client = OkHttpClient.Builder()
        .cookieJar(StoredCookieJar(sessionStore))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun bootstrap(baseUrl: String = sessionStore.baseUrl()): Boolean {
        val normalized = SessionStore.normalizeBaseUrl(baseUrl)
        val body = execute(
            request = Request.Builder().url("$normalized/api/v1/auth/bootstrap").get().build(),
            authenticated = false,
        )
        return body.getBoolean("initialized")
    }

    suspend fun initialize(username: String, displayName: String, password: String): AuthSession {
        val payload = JSONObject()
            .put("username", username)
            .put("displayName", displayName)
            .put("password", password)
        return auth("/api/v1/auth/initialize", payload)
    }

    suspend fun signIn(username: String, password: String): AuthSession {
        val payload = JSONObject()
            .put("username", username)
            .put("password", password)
        return auth("/api/v1/auth/signin", payload)
    }

    suspend fun refresh(): AuthSession {
        val request = Request.Builder()
            .url(url("/api/v1/auth/refresh"))
            .post(EMPTY_BODY)
            .build()
        val body = execute(request = request, authenticated = false)
        return parseAuthSession(body).also(sessionStore::saveSession)
    }

    suspend fun signOut() {
        val request = Request.Builder()
            .url(url("/api/v1/auth/signout"))
            .post(EMPTY_BODY)
            .build()
        execute(request = request, authenticated = false)
        sessionStore.clearSession()
    }

    suspend fun me(): Account {
        val request = Request.Builder().url(url("/api/v1/auth/me")).get().build()
        return parseAccount(execute(request).getJSONObject("account"))
    }

    suspend fun listMemos(limit: Int = 300): List<Memo> {
        val request = Request.Builder().url(url("/api/v1/memos?limit=$limit")).get().build()
        val memos = execute(request).getJSONArray("memos")
        return memos.toMemoList()
    }

    suspend fun createMemo(content: String, entryDate: String): Memo {
        val payload = JSONObject()
            .put("content", content)
            .put("entryDate", entryDate)
        val request = Request.Builder()
            .url(url("/api/v1/memos"))
            .post(payload.toString().jsonBody())
            .build()
        return parseMemo(execute(request).getJSONObject("memo"))
    }

    suspend fun updateMemo(memo: Memo, content: String, entryDate: String): Memo {
        val payload = JSONObject()
            .put("content", content)
            .put("entryDate", entryDate)
            .put("expectedVersion", memo.version)
        val request = Request.Builder()
            .url(url("/api/v1/memos/${memo.id.pathSegment()}"))
            .patch(payload.toString().jsonBody())
            .build()
        return parseMemo(execute(request).getJSONObject("memo"))
    }

    suspend fun deleteMemo(memo: Memo): Memo {
        val request = Request.Builder()
            .url(url("/api/v1/memos/${memo.id.pathSegment()}?expectedVersion=${memo.version}"))
            .delete()
            .build()
        return parseMemo(execute(request).getJSONObject("memo"))
    }

    private suspend fun auth(path: String, payload: JSONObject): AuthSession {
        val request = Request.Builder()
            .url(url(path))
            .post(payload.toString().jsonBody())
            .build()
        return parseAuthSession(execute(request, authenticated = false)).also(sessionStore::saveSession)
    }

    private suspend fun execute(
        request: Request,
        authenticated: Boolean = true,
        retryRefresh: Boolean = true,
    ): JSONObject = withContext(Dispatchers.IO) {
        val response = client.newCall(request.withAuth(authenticated)).execute()
        response.use { res ->
            if (res.code == 401 && authenticated && retryRefresh) {
                runCatching { refresh() }.onFailure { sessionStore.clearSession() }
                return@withContext execute(request, authenticated = true, retryRefresh = false)
            }
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                throw ApiException(parseErrorMessage(body))
            }
            if (body.isBlank()) {
                JSONObject()
            } else {
                JSONObject(body)
            }
        }
    }

    private fun Request.withAuth(authenticated: Boolean): Request {
        if (!authenticated) {
            return this
        }
        val token = sessionStore.accessToken() ?: throw ApiException("请先登录")
        return newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
    }

    private fun url(path: String): String = sessionStore.baseUrl().trimEnd('/') + path

    private fun parseAuthSession(body: JSONObject): AuthSession {
        return AuthSession(
            account = parseAccount(body.getJSONObject("account")),
            accessToken = body.getString("accessToken"),
            expiresAt = body.getString("expiresAt"),
        )
    }

    private fun parseAccount(body: JSONObject): Account {
        return Account(
            id = body.getString("id"),
            username = body.getString("username"),
            displayName = body.optString("displayName", body.getString("username")),
        )
    }

    private fun JSONArray.toMemoList(): List<Memo> = buildList {
        for (index in 0 until length()) {
            add(parseMemo(getJSONObject(index)))
        }
    }

    private fun parseMemo(body: JSONObject): Memo {
        return Memo(
            id = body.getString("id"),
            content = body.getString("content"),
            entryDate = body.getString("entryDate"),
            version = body.getLong("version"),
            createdAt = body.getString("createdAt"),
            updatedAt = body.getString("updatedAt"),
            deletedAt = body.nullableString("deletedAt"),
        )
    }

    private fun parseErrorMessage(rawBody: String): String {
        if (rawBody.isBlank()) {
            return "请求失败"
        }
        return runCatching {
            JSONObject(rawBody).getJSONObject("error").optString("message", "请求失败")
        }.getOrElse { "请求失败" }
    }

    private fun String.jsonBody() = toRequestBody(JSON)

    private fun String.pathSegment(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")

    private fun JSONObject.nullableString(name: String): String? {
        return if (isNull(name)) null else optString(name)
    }

    private class StoredCookieJar(private val sessionStore: SessionStore) : CookieJar {
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            return sessionStore.cookieHeaders()
                .mapNotNull { Cookie.parse(url, it) }
                .filter { it.expiresAt > now && it.matches(url) }
        }

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val current = loadForRequest(url).associateBy { "${it.name}|${it.domain}|${it.path}" }
            val merged = current.toMutableMap()
            cookies.forEach { cookie ->
                val key = "${cookie.name}|${cookie.domain}|${cookie.path}"
                if (cookie.expiresAt <= System.currentTimeMillis()) {
                    merged.remove(key)
                } else {
                    merged[key] = cookie
                }
            }
            sessionStore.saveCookieHeaders(merged.values.map(Cookie::toString))
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val EMPTY_BODY = ByteArray(0).toRequestBody(JSON)
    }
}
