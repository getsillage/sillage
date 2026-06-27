package com.miofelix.sillage.data

import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
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

    suspend fun pullFullSync(limit: Int = 200): SillageExportData {
        val memos = mutableListOf<Memo>()
        val memoAI = mutableListOf<MemoAI>()
        val askConversations = mutableListOf<AskConversation>()
        val askMessages = mutableListOf<AskMessage>()
        var cursor = ""
        do {
            val suffix = if (cursor.isBlank()) {
                "?limit=$limit"
            } else {
                "?limit=$limit&cursor=${cursor.queryParam()}"
            }
            val request = Request.Builder().url(url("/api/v1/sync$suffix")).get().build()
            val body = execute(request)
            memos += body.optJSONArray("memos").toMemoListOrEmpty()
            memoAI += body.optJSONArray("memoAi").toMemoAIListOrEmpty()
            askConversations += body.optJSONArray("askConversations").toAskConversationListOrEmpty()
            askMessages += body.optJSONArray("askMessages").toAskMessageListOrEmpty()
            cursor = body.optString("nextCursor")
            val hasMore = body.optBoolean("hasMore")
        } while (hasMore && cursor.isNotBlank())
        return SillageExportData(
            formatVersion = SillageExportCodec.FORMAT_VERSION,
            exportedAt = java.time.Instant.now().toString(),
            themeMode = "",
            memoViewMode = "",
            memos = memos,
            memoAI = memoAI,
            aiProfiles = runCatching { getAISettings().map { it.toDraft() } }.getOrDefault(emptyList()),
            askConversations = askConversations,
            askMessages = askMessages,
        )
    }

    suspend fun searchMemos(query: String, limit: Int = 100): List<Memo> {
        val request = Request.Builder()
            .url(url("/api/v1/memos?query=${query.queryParam()}&limit=$limit"))
            .get()
            .build()
        val memos = execute(request).getJSONArray("memos")
        return memos.toMemoList()
    }

    suspend fun getMemo(id: String): MemoDetail {
        val request = Request.Builder()
            .url(url("/api/v1/memos/${id.pathSegment()}"))
            .get()
            .build()
        val body = execute(request)
        return MemoDetail(
            memo = parseMemo(body.getJSONObject("memo")),
            ai = if (body.isNull("ai")) null else parseMemoAI(body.getJSONObject("ai")),
        )
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

    suspend fun setMemoPinned(memo: Memo, pinned: Boolean): Memo {
        val action = if (pinned) "pin" else "unpin"
        val request = Request.Builder()
            .url(url("/api/v1/memos/${memo.id.pathSegment()}:$action?expectedVersion=${memo.version}"))
            .post(EMPTY_BODY)
            .build()
        return parseMemo(execute(request).getJSONObject("memo"))
    }

    suspend fun setMemoArchived(memo: Memo, archived: Boolean): Memo {
        val action = if (archived) "archive" else "unarchive"
        val request = Request.Builder()
            .url(url("/api/v1/memos/${memo.id.pathSegment()}:$action?expectedVersion=${memo.version}"))
            .post(EMPTY_BODY)
            .build()
        return parseMemo(execute(request).getJSONObject("memo"))
    }

    suspend fun generateMemoSummary(memo: Memo): MemoAI {
        val request = Request.Builder()
            .url(url("/api/v1/memos/${memo.id.pathSegment()}:generate-summary"))
            .post(EMPTY_BODY)
            .build()
        return parseMemoAI(execute(request).getJSONObject("ai"))
    }

    suspend fun uploadAttachment(input: AttachmentUpload): Attachment {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("mutation_id", UUID.randomUUID().toString())
            .addFormDataPart(
                "file",
                input.filename,
                input.bytes.toRequestBody(input.contentType.toMediaTypeOrNull()),
            )
            .build()
        val request = Request.Builder()
            .url(url("/api/v1/attachments"))
            .post(body)
            .build()
        return parseAttachment(execute(request).getJSONObject("attachment"))
    }

    suspend fun getAISettings(): List<AIProfile> {
        val request = Request.Builder()
            .url(url("/api/v1/settings/ai"))
            .get()
            .build()
        val profiles = execute(request).getJSONArray("profiles")
        return buildList {
            for (index in 0 until profiles.length()) {
                add(parseAIProfile(profiles.getJSONObject(index)))
            }
        }
    }

    suspend fun patchAISettings(profiles: List<AIProfileInput>): List<AIProfile> {
        val payloadProfiles = JSONArray()
        for (profile in profiles) {
            val item = JSONObject()
                .put("name", profile.name)
                .put("provider", profile.provider)
                .put("baseUrl", profile.baseUrl)
                .put("model", profile.model)
                .put("temperature", profile.temperature)
                .put("maxTokens", profile.maxTokens)
                .put("enabled", profile.enabled)
                .put("active", profile.active)
                .put("autoSummary", profile.autoSummary)
            profile.id?.let { item.put("id", it) }
            profile.apiKey?.let { item.put("apiKey", it) }
            payloadProfiles.put(item)
        }
        val request = Request.Builder()
            .url(url("/api/v1/settings/ai"))
            .patch(JSONObject().put("profiles", payloadProfiles).toString().jsonBody())
            .build()
        val saved = execute(request).getJSONArray("profiles")
        return buildList {
            for (index in 0 until saved.length()) {
                add(parseAIProfile(saved.getJSONObject(index)))
            }
        }
    }

    suspend fun testAIConnection(profileId: String): String {
        val request = Request.Builder()
            .url(url("/api/v1/settings/ai:test"))
            .post(JSONObject().put("id", profileId).toString().jsonBody())
            .build()
        return execute(request).optString("model")
    }

    suspend fun listAskConversations(limit: Int = 50): List<AskConversation> {
        val request = Request.Builder()
            .url(url("/api/v1/ask/conversations?limit=$limit"))
            .get()
            .build()
        val conversations = execute(request).getJSONArray("conversations")
        return buildList {
            for (index in 0 until conversations.length()) {
                add(parseAskConversation(conversations.getJSONObject(index)))
            }
        }
    }

    suspend fun createAskConversation(contextScope: String): AskConversation {
        val payload = JSONObject().put("contextScope", contextScope)
        val request = Request.Builder()
            .url(url("/api/v1/ask/conversations"))
            .post(payload.toString().jsonBody())
            .build()
        return parseAskConversation(execute(request).getJSONObject("conversation"))
    }

    suspend fun listAskMessages(conversationId: String): List<AskMessage> {
        val request = Request.Builder()
            .url(url("/api/v1/ask/conversations/${conversationId.pathSegment()}/messages"))
            .get()
            .build()
        val messages = execute(request).getJSONArray("messages")
        return messages.toAskMessageList()
    }

    suspend fun createAskMessage(
        conversationId: String,
        content: String,
        contextScope: String,
        sourceKind: String,
        forkOfId: String? = null,
    ): List<AskMessage> {
        val payload = JSONObject()
            .put("content", content)
            .put("contextScope", contextScope)
            .put("sourceKind", sourceKind)
        if (!forkOfId.isNullOrBlank()) {
            payload.put("forkOfId", forkOfId)
        }
        val request = Request.Builder()
            .url(url("/api/v1/ask/conversations/${conversationId.pathSegment()}/messages"))
            .post(payload.toString().jsonBody())
            .build()
        return execute(request).getJSONArray("messages").toAskMessageList()
    }

    suspend fun setAskHead(conversationId: String, messageId: String) {
        val request = Request.Builder()
            .url(url("/api/v1/ask/conversations/${conversationId.pathSegment()}/head"))
            .post(JSONObject().put("messageId", messageId).toString().jsonBody())
            .build()
        execute(request)
    }

    suspend fun streamAskMessage(
        conversationId: String,
        content: String,
        contextScope: String,
        sourceKind: String,
        forkOfId: String? = null,
        onStart: (AskMessage, Boolean) -> Unit,
        onDelta: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val payload = JSONObject()
            .put("content", content)
            .put("contextScope", contextScope)
            .put("sourceKind", sourceKind)
        if (!forkOfId.isNullOrBlank()) {
            payload.put("forkOfId", forkOfId)
        }
        val request = Request.Builder()
            .url(url("/api/v1/ask/conversations/${conversationId.pathSegment()}/messages:stream"))
            .post(payload.toString().jsonBody())
            .build()
            .withAuth(true)
        executeAskStream(request, onStart, onDelta, onError)
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

    private suspend fun executeAskStream(
        request: Request,
        onStart: (AskMessage, Boolean) -> Unit,
        onDelta: (String) -> Unit,
        onError: (String) -> Unit,
        retryRefresh: Boolean = true,
    ): Unit = withContext(Dispatchers.IO) {
        val streamClient = client.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        val call = streamClient.newCall(request)
        val cancellation = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                call.cancel()
            }
        }
        try {
            val response = call.execute()
            response.use { res ->
                if (res.code == 401 && retryRefresh) {
                    runCatching { refresh() }.onFailure { sessionStore.clearSession() }
                    return@withContext executeAskStream(
                        request.withAuth(true),
                        onStart,
                        onDelta,
                        onError,
                        retryRefresh = false,
                    )
                }
                if (!res.isSuccessful) {
                    throw ApiException(parseErrorMessage(res.body?.string().orEmpty()))
                }
                val source = res.body?.source() ?: throw ApiException("生成回答失败")
                consumeAskStream(source, onStart, onDelta, onError)
            }
        } finally {
            cancellation?.dispose()
        }
    }

    private fun consumeAskStream(
        source: BufferedSource,
        onStart: (AskMessage, Boolean) -> Unit,
        onDelta: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val block = StringBuilder()
        while (true) {
            val line = source.readUtf8Line() ?: break
            if (line.isEmpty()) {
                dispatchAskEvent(block.toString(), onStart, onDelta, onError)
                block.clear()
            } else {
                block.append(line).append('\n')
            }
        }
        if (block.isNotEmpty()) {
            dispatchAskEvent(block.toString(), onStart, onDelta, onError)
        }
    }

    private fun dispatchAskEvent(
        block: String,
        onStart: (AskMessage, Boolean) -> Unit,
        onDelta: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val parsed = parseAskStreamEvent(block) ?: return
        val data = runCatching { JSONObject(parsed.data) }.getOrNull() ?: return
        when (parsed.event) {
            "start" -> onStart(
                parseAskMessage(data.getJSONObject("userMessage")),
                data.optBoolean("regenerate"),
            )
            "delta" -> onDelta(data.optString("text"))
            "error" -> onError(data.optString("message", "生成回答失败"))
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

    private fun JSONArray?.toMemoListOrEmpty(): List<Memo> = buildList {
        val array = this@toMemoListOrEmpty ?: return@buildList
        for (index in 0 until array.length()) {
            add(parseMemo(array.getJSONObject(index)))
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
            pinnedAt = body.nullableString("pinnedAt"),
            archivedAt = body.nullableString("archivedAt"),
            deletedAt = body.nullableString("deletedAt"),
        )
    }

    private fun parseMemoAI(body: JSONObject): MemoAI {
        return MemoAI(
            memoId = body.getString("memoId"),
            summary = body.nullableString("summary"),
            sentiment = body.nullableString("sentiment"),
            provider = body.optString("provider"),
            model = body.optString("model"),
            profileId = body.optString("profileId"),
            promptVersion = body.optString("promptVersion"),
            sourceMemoIds = body.optString("sourceMemoIds"),
            status = body.optString("status"),
            errorCode = body.nullableString("errorCode"),
            startedAt = body.nullableString("startedAt"),
            finishedAt = body.nullableString("finishedAt"),
            inputTokens = body.optLong("inputTokens"),
            outputTokens = body.optLong("outputTokens"),
            totalTokens = body.optLong("totalTokens"),
            createdAt = body.optString("createdAt"),
            updatedAt = body.optString("updatedAt"),
        )
    }

    private fun JSONArray?.toMemoAIListOrEmpty(): List<MemoAI> = buildList {
        val array = this@toMemoAIListOrEmpty ?: return@buildList
        for (index in 0 until array.length()) {
            add(parseMemoAI(array.getJSONObject(index)))
        }
    }

    private fun parseAttachment(body: JSONObject): Attachment {
        return Attachment(
            uid = body.getString("uid"),
            url = body.getString("url"),
            filename = body.getString("filename"),
            contentType = body.optString("contentType"),
            size = body.optLong("size"),
            sha256 = body.nullableString("sha256"),
        )
    }

    private fun parseAIProfile(body: JSONObject): AIProfile {
        return AIProfile(
            id = body.getString("id"),
            name = body.getString("name"),
            provider = body.getString("provider"),
            baseUrl = body.optString("baseUrl"),
            model = body.optString("model"),
            temperature = body.optDouble("temperature"),
            maxTokens = body.optLong("maxTokens"),
            enabled = body.optBoolean("enabled"),
            active = body.optBoolean("active"),
            hasApiKey = body.optBoolean("hasApiKey"),
            keyUnavailable = body.optBoolean("keyUnavailable"),
            autoSummary = body.optBoolean("autoSummary"),
            createdAt = body.optString("createdAt"),
            updatedAt = body.optString("updatedAt"),
        )
    }

    private fun parseAskConversation(body: JSONObject): AskConversation {
        return AskConversation(
            id = body.getString("id"),
            title = body.optString("title"),
            status = body.optString("status"),
            contextScope = body.optString("contextScope"),
            headMessageId = body.nullableString("headMessageId"),
            pinnedAt = body.nullableString("pinnedAt"),
            archivedAt = body.nullableString("archivedAt"),
            createdAt = body.optString("createdAt"),
            updatedAt = body.optString("updatedAt"),
            deletedAt = body.nullableString("deletedAt"),
        )
    }

    private fun JSONArray?.toAskConversationListOrEmpty(): List<AskConversation> = buildList {
        val array = this@toAskConversationListOrEmpty ?: return@buildList
        for (index in 0 until array.length()) {
            add(parseAskConversation(array.getJSONObject(index)))
        }
    }

    private fun JSONArray.toAskMessageList(): List<AskMessage> = buildList {
        for (index in 0 until length()) {
            add(parseAskMessage(getJSONObject(index)))
        }
    }

    private fun JSONArray?.toAskMessageListOrEmpty(): List<AskMessage> = buildList {
        val array = this@toAskMessageListOrEmpty ?: return@buildList
        for (index in 0 until array.length()) {
            add(parseAskMessage(array.getJSONObject(index)))
        }
    }

    private fun parseAskMessage(body: JSONObject): AskMessage {
        val refs = body.optJSONArray("sourceRefs") ?: JSONArray()
        return AskMessage(
            id = body.getString("id"),
            conversationId = body.getString("conversationId"),
            role = body.optString("role"),
            content = body.optString("content"),
            parentId = body.nullableString("parentId"),
            forkOfId = body.nullableString("forkOfId"),
            status = body.optString("status"),
            sourceRefs = buildList {
                for (index in 0 until refs.length()) {
                    add(parseAskSourceRef(refs.getJSONObject(index)))
                }
            },
            model = body.optString("model"),
            createdAt = body.optString("createdAt"),
            updatedAt = body.optString("updatedAt"),
            deletedAt = body.nullableString("deletedAt"),
        )
    }

    private fun parseAskSourceRef(body: JSONObject): AskSourceRef {
        return AskSourceRef(
            memoId = body.optString("memoId"),
            entryDate = body.optString("entryDate"),
            excerpt = body.optString("excerpt"),
            rank = body.optInt("rank"),
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

    private fun String.queryParam(): String = URLEncoder.encode(this, "UTF-8")

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
