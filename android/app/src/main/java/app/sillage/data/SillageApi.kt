package app.sillage.data

import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSource
import org.json.JSONArray
import org.json.JSONObject

private const val SERVER_CONFIG_CHANGED = "服务器配置已更改"

class SillageApi(
    private val sessionStore: SessionStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val refreshCoordinator = SessionRefreshCoordinator(
        currentSession = sessionStore::clientSessionSnapshot,
        clearSession = { context, accessToken -> sessionStore.clearSession(context, accessToken) },
        refresh = ::refreshSession,
    )

    suspend fun bootstrap(baseUrl: String = sessionStore.baseUrl()): Boolean {
        val normalized = SessionStore.normalizeBaseUrl(baseUrl)
        val body = execute(
            request = Request.Builder().url("$normalized/api/v1/auth/bootstrap").get().build(),
            authenticated = false,
            sessionScoped = false,
        )
        return body.getBoolean("initialized")
    }

    suspend fun initialize(username: String, displayName: String, password: String): AuthSession {
        val expectedSession = sessionStore.clientSessionSnapshot()
        val payload = JSONObject()
            .put("username", username)
            .put("displayName", displayName)
            .put("password", password)
        return auth("/api/v1/auth/initialize", payload, expectedSession)
    }

    suspend fun signIn(username: String, password: String): AuthSession {
        val expectedSession = sessionStore.clientSessionSnapshot()
        val payload = JSONObject()
            .put("username", username)
            .put("password", password)
        return auth("/api/v1/auth/signin", payload, expectedSession)
    }

    private suspend fun refreshSession(context: ClientRequestContext): AuthSession {
        val request = Request.Builder()
            .url(context.baseUrl.trimEnd('/') + "/api/v1/auth/refresh")
            .post(EMPTY_BODY)
            .build()
            .withSessionContext(context, authenticated = false)
        val session = parseAuthSession(
            executePrepared(request = request, authenticated = false, retryRefresh = false),
        )
        if (!sessionStore.saveRefreshedSession(session, context)) {
            throw ApiException(SERVER_CONFIG_CHANGED)
        }
        return session
    }

    suspend fun signOut() {
        signOut(sessionStore.clientSessionSnapshot())
    }

    internal suspend fun signOut(expectedSession: ClientSessionSnapshot) {
        val context = expectedSession.toClientRequestContext()
        refreshCoordinator.runSessionExclusive {
            val request = Request.Builder()
                .url(context.baseUrl.trimEnd('/') + "/api/v1/auth/signout")
                .post(EMPTY_BODY)
                .build()
                .withSessionContext(context, authenticated = false)
            executePrepared(request = request, authenticated = false, retryRefresh = false)
            if (!sessionStore.clearSession(context)) {
                throw ApiException(SERVER_CONFIG_CHANGED)
            }
        }
    }

    suspend fun me(): Account {
        val request = Request.Builder().url(url("/api/v1/auth/me")).get().build()
        return parseAccount(execute(request).getJSONObject("account"))
    }

    suspend fun listMemos(
        limit: Int = 200,
        cursor: String = "",
        archived: Boolean? = false,
        favorited: Boolean = false,
    ): MemoPage {
        val params = buildList {
            add("limit=$limit")
            archived?.let { add("archived=$it") }
            add("favorited=$favorited")
            if (cursor.isNotBlank()) {
                add("cursor=${cursor.queryParam()}")
            }
        }
        val suffix = "?${params.joinToString("&")}"
        val request = Request.Builder().url(url("/api/v1/memos$suffix")).get().build()
        val body = execute(request)
        return MemoPage(
            memos = body.getJSONArray("memos").toMemoList(),
            nextCursor = body.optString("nextCursor"),
        )
    }

    suspend fun pullFullSync(limit: Int = 200): SillageExportData {
        val memos = mutableListOf<Memo>()
        val memoAI = mutableListOf<MemoAI>()
        val askConversations = mutableListOf<AskConversation>()
        val askMessages = mutableListOf<AskMessage>()
        val aiSettings = runCatching { getAISettings() }.getOrDefault(AISettings(emptyList(), false))
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
            autoSummary = aiSettings.autoSummary,
            memos = memos,
            memoAI = memoAI,
            aiProfiles = aiSettings.profiles.map { it.toDraft() },
            askConversations = askConversations,
            askMessages = askMessages,
        )
    }

    suspend fun pushMemos(items: List<PendingMemoSync>): SyncPushSummary {
        var applied = 0
        var conflict = 0
        var rejected = 0
        val appliedMemoSyncs = mutableListOf<AppliedMemoSync>()
        for (chunk in items.chunked(200)) {
            val changes = JSONArray()
            for (item in chunk) {
                changes.put(pendingMemoSyncToJson(item))
            }
            val request = Request.Builder()
                .url(url("/api/v1/sync:push"))
                .post(JSONObject().put("changes", changes).toString().jsonBody())
                .build()
            val results = execute(request).optJSONArray("results") ?: JSONArray()
            val chunkSummary = syncPushSummaryFromResults(results)
            applied += chunkSummary.applied
            conflict += chunkSummary.conflict
            rejected += chunkSummary.rejected
            appliedMemoSyncs += chunkSummary.appliedMemoSyncs
        }
        return SyncPushSummary(
            applied = applied,
            conflict = conflict,
            rejected = rejected,
            appliedMemoSyncs = appliedMemoSyncs,
        )
    }

    suspend fun searchMemos(
        query: String,
        limit: Int = 100,
        archived: Boolean? = false,
        favorited: Boolean = false,
    ): List<Memo> {
        val filter = buildList {
            archived?.let { add("archived=$it") }
            add("favorited=$favorited")
        }.joinToString("&")
        val request = Request.Builder()
            .url(url("/api/v1/memos?query=${query.queryParam()}&limit=$limit&$filter"))
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

    suspend fun setMemoFavorited(memo: Memo, favorited: Boolean): Memo {
        val payload = JSONObject()
            .put("expectedVersion", memo.version)
            .put("favorited", favorited)
        val request = Request.Builder()
            .url(url("/api/v1/memos/${memo.id.pathSegment()}:setFavorited"))
            .post(payload.toString().jsonBody())
            .build()
        return parseMemo(execute(request).getJSONObject("memo"))
    }

    suspend fun setMemoArchived(memo: Memo, archived: Boolean): Memo {
        val payload = JSONObject()
            .put("expectedVersion", memo.version)
            .put("archived", archived)
        val request = Request.Builder()
            .url(url("/api/v1/memos/${memo.id.pathSegment()}:setArchived"))
            .post(payload.toString().jsonBody())
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

    suspend fun downloadAttachment(
        target: MarkdownLinkTarget.ProtectedAttachment,
        tempDestination: File,
    ): DownloadedAttachment {
        val validatedTarget = resolveMarkdownLinkTarget(target.path, sessionStore.baseUrl())
            as? MarkdownLinkTarget.ProtectedAttachment
            ?: throw ApiException("附件地址无效")
        val request = Request.Builder()
            .url(url(validatedTarget.path))
            .get()
            .build()
        return executeAttachmentDownload(request, tempDestination)
    }

    suspend fun getAISettings(): AISettings {
        val request = Request.Builder()
            .url(url("/api/v1/settings/ai"))
            .get()
            .build()
        return parseAISettings(execute(request))
    }

    suspend fun patchAISettings(profiles: List<AIProfileInput>): AISettings {
        val payloadProfiles = JSONArray()
        for (profile in profiles) {
            payloadProfiles.put(profile.toJson())
        }
        val request = Request.Builder()
            .url(url("/api/v1/settings/ai"))
            .patch(JSONObject().put("profiles", payloadProfiles).toString().jsonBody())
            .build()
        return parseAISettings(execute(request))
    }

    suspend fun setAIAutoSummary(enabled: Boolean): Boolean {
        val request = Request.Builder()
            .url(url("/api/v1/settings/ai:setAutoSummary"))
            .post(JSONObject().put("autoSummary", enabled).toString().jsonBody())
            .build()
        return execute(request).getBoolean("autoSummary")
    }

    suspend fun testAIConnection(profileId: String): String {
        val request = Request.Builder()
            .url(url("/api/v1/settings/ai:test"))
            .post(JSONObject().put("id", profileId).toString().jsonBody())
            .build()
        return execute(request).optString("model")
    }

    suspend fun testAIConnection(input: AIProfileInput): String {
        val request = Request.Builder()
            .url(url("/api/v1/settings/ai:test"))
            .post(input.toJson().toString().jsonBody())
            .build()
        return execute(request).optString("model")
    }

    suspend fun listAIModels(input: AIProfileInput): List<String> {
        val request = Request.Builder()
            .url(url("/api/v1/settings/ai:models"))
            .post(input.toJson().toString().jsonBody())
            .build()
        val models = execute(request).optJSONArray("models") ?: JSONArray()
        return buildList {
            for (index in 0 until models.length()) {
                add(models.optString(index))
            }
        }
    }

    private fun AIProfileInput.toJson(): JSONObject {
        val item = JSONObject()
            .put("name", name)
            .put("provider", provider)
            .put("baseUrl", baseUrl)
            .put("model", model)
            .put("enabled", enabled)
            .put("active", active)
        temperature?.let { item.put("temperature", it) }
        maxTokens?.let { item.put("maxTokens", it) }
        id?.let { item.put("id", it) }
        apiKey?.let { item.put("apiKey", it) }
        return item
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
            .withSessionSnapshot(authenticated = true)
        executeAskStream(request, onStart, onDelta, onError)
    }

    private suspend fun auth(
        path: String,
        payload: JSONObject,
        expectedSession: ClientSessionSnapshot,
    ): AuthSession {
        val context = expectedSession.toClientRequestContext()
        return refreshCoordinator.runSessionExclusive {
            val request = Request.Builder()
                .url(context.baseUrl.trimEnd('/') + path)
                .post(payload.toString().jsonBody())
                .build()
                .withSessionContext(context, authenticated = false)
            val session = parseAuthSession(
                executePrepared(request = request, authenticated = false, retryRefresh = false),
            )
            if (!sessionStore.saveAuthenticatedSession(session, request.requireClientRequestContext())) {
                throw ApiException(SERVER_CONFIG_CHANGED)
            }
            session
        }
    }

    private suspend fun execute(
        request: Request,
        authenticated: Boolean = true,
        retryRefresh: Boolean = true,
        sessionScoped: Boolean = true,
    ): JSONObject {
        val preparedRequest = if (sessionScoped) {
            request.withSessionSnapshot(authenticated)
        } else {
            request
        }
        return executePrepared(preparedRequest, authenticated, retryRefresh)
    }

    private suspend fun executePrepared(
        request: Request,
        authenticated: Boolean,
        retryRefresh: Boolean,
    ): JSONObject {
        return executeSnapshot(request, request, authenticated, retryRefresh)
    }

    @OptIn(InternalCoroutinesApi::class)
    private suspend fun executeSnapshot(
        originalRequest: Request,
        request: Request,
        authenticated: Boolean,
        retryRefresh: Boolean,
    ): JSONObject {
        return withContext(ioDispatcher) {
            val call = newCall(request)
            val cancellation = currentCoroutineContext()[Job]?.invokeOnCompletion(
                onCancelling = true,
                invokeImmediately = true,
            ) { cause ->
                if (cause is CancellationException) call.cancel()
            }
            try {
                val response = call.execute()
                response.use { res ->
                    currentCoroutineContext().ensureActive()
                    if (res.code == 401 && authenticated && retryRefresh) {
                        refreshSessionForRetry(request)
                        val retryRequest = originalRequest.withCurrentAuthForSameContext()
                        return@withContext executeSnapshot(
                            originalRequest = originalRequest,
                            request = retryRequest,
                            authenticated = true,
                            retryRefresh = false,
                        )
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
            } catch (error: IOException) {
                currentCoroutineContext().ensureActive()
                throw error
            } finally {
                cancellation?.dispose()
            }
        }
    }

    private suspend fun executeAttachmentDownload(
        request: Request,
        tempDestination: File,
        retryRefresh: Boolean = true,
        authenticatedRequest: Request = request.withSessionSnapshot(authenticated = true),
    ): DownloadedAttachment {
        return when (val result = executeAttachmentDownloadAttempt(authenticatedRequest, tempDestination)) {
            is AttachmentDownloadAttempt.Success -> result.attachment
            is AttachmentDownloadAttempt.Unauthorized -> {
                if (!retryRefresh) {
                    throw ApiException(result.message)
                }
                refreshSessionForRetry(authenticatedRequest)
                currentCoroutineContext().ensureActive()
                executeAttachmentDownload(
                    request = request,
                    tempDestination = tempDestination,
                    retryRefresh = false,
                    authenticatedRequest = authenticatedRequest.withCurrentAuthForSameContext(),
                )
            }
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    private suspend fun executeAttachmentDownloadAttempt(
        request: Request,
        tempDestination: File,
    ): AttachmentDownloadAttempt = suspendCancellableCoroutine { continuation ->
        val call = newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        val callback = object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                val token = continuation.tryResumeWithException(ApiException("附件下载失败"))
                if (token != null) {
                    continuation.completeResume(token)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { res ->
                    if (!continuation.isActive) {
                        return
                    }
                    try {
                        val result = when {
                            res.code == 401 -> AttachmentDownloadAttempt.Unauthorized(
                                message = parseErrorMessage(res.body?.string().orEmpty()),
                            )
                            !res.isSuccessful -> throw ApiException(
                                parseErrorMessage(res.body?.string().orEmpty()),
                            )
                            else -> {
                                val body = res.body ?: throw ApiException("附件内容为空")
                                tempDestination.outputStream().use { output ->
                                    body.byteStream().use { input -> input.copyTo(output) }
                                }
                                AttachmentDownloadAttempt.Success(
                                    DownloadedAttachment(
                                        contentType = res.header("Content-Type"),
                                        contentDisposition = res.header("Content-Disposition"),
                                        urlFilename = res.request.url.pathSegments.lastOrNull().orEmpty(),
                                    ),
                                )
                            }
                        }
                        val token = continuation.tryResume(result)
                        if (token != null) {
                            continuation.completeResume(token)
                        }
                    } catch (error: Throwable) {
                        val failure = if (error is IOException) {
                            ApiException("附件下载失败")
                        } else {
                            error
                        }
                        val token = continuation.tryResumeWithException(failure)
                        if (token != null) {
                            continuation.completeResume(token)
                        }
                    }
                }
            }
        }
        try {
            call.enqueue(callback)
        } catch (error: Throwable) {
            val token = continuation.tryResumeWithException(error)
            if (token != null) {
                continuation.completeResume(token)
            }
        }
    }

    private sealed interface AttachmentDownloadAttempt {
        data class Success(val attachment: DownloadedAttachment) : AttachmentDownloadAttempt

        data class Unauthorized(val message: String) : AttachmentDownloadAttempt
    }

    @OptIn(InternalCoroutinesApi::class)
    private suspend fun executeAskStream(
        request: Request,
        onStart: (AskMessage, Boolean) -> Unit,
        onDelta: (String) -> Unit,
        onError: (String) -> Unit,
        retryRefresh: Boolean = true,
    ): Unit = withContext(Dispatchers.IO) {
        val call = newCall(request, readTimeoutMillis = 0)
        val cancellation = currentCoroutineContext()[Job]?.invokeOnCompletion(
            onCancelling = true,
            invokeImmediately = true,
        ) { cause ->
            if (cause is CancellationException) call.cancel()
        }
        try {
            val response = call.execute()
            response.use { res ->
                if (res.code == 401 && retryRefresh) {
                    refreshSessionForRetry(request)
                    return@withContext executeAskStream(
                        request.withCurrentAuthForSameContext(),
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
        } catch (error: IOException) {
            currentCoroutineContext().ensureActive()
            throw error
        } finally {
            cancellation?.dispose()
        }
    }

    private suspend fun refreshSessionForRetry(request: Request) {
        refreshCoordinator.refreshAfterUnauthorized(
            context = request.requireClientRequestContext(),
            failedAccessToken = request.requireBearerAccessToken(),
        )
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

    private fun Request.withSessionSnapshot(authenticated: Boolean): Request {
        val snapshot = sessionStore.clientSessionSnapshot()
        val context = snapshot.toClientRequestContext()
        return withSessionContext(context, authenticated, snapshot)
    }

    private fun Request.withSessionContext(
        context: ClientRequestContext,
        authenticated: Boolean,
    ): Request {
        val snapshot = sessionStore.clientSessionSnapshot()
        if (!snapshot.matches(context)) {
            throw ApiException(SERVER_CONFIG_CHANGED)
        }
        return withSessionContext(context, authenticated, snapshot)
    }

    private fun Request.withSessionContext(
        context: ClientRequestContext,
        authenticated: Boolean,
        snapshot: ClientSessionSnapshot,
    ): Request {
        val baseUrl = context.baseUrl.toHttpUrlOrNull()
            ?: throw ApiException(SERVER_CONFIG_CHANGED)
        if (!url.matchesSessionBase(baseUrl)) {
            throw ApiException(SERVER_CONFIG_CHANGED)
        }
        val builder = newBuilder()
            .tag(ClientRequestContext::class.java, context)
        if (authenticated) {
            val token = snapshot.accessToken ?: throw ApiException("请先登录")
            builder.header("Authorization", "Bearer $token")
        } else {
            builder.removeHeader("Authorization")
        }
        return builder.build()
    }

    private fun Request.withCurrentAuthForSameContext(): Request {
        val context = requireClientRequestContext()
        return withSessionContext(context, authenticated = true)
    }

    private fun Request.requireClientRequestContext(): ClientRequestContext {
        return tag(ClientRequestContext::class.java)
            ?: throw ApiException(SERVER_CONFIG_CHANGED)
    }

    private fun ClientSessionSnapshot.toClientRequestContext(): ClientRequestContext {
        val parsedBaseUrl = baseUrl.toHttpUrlOrNull()
            ?: throw ApiException(SERVER_CONFIG_CHANGED)
        return ClientRequestContext(
            baseUrl = baseUrl,
            contextGeneration = contextGeneration,
            serverBaseKey = parsedBaseUrl.serverBaseKey(),
        )
    }

    private fun newCall(request: Request, readTimeoutMillis: Long? = null): Call {
        val builder = client.newBuilder()
        request.tag(ClientRequestContext::class.java)?.let { context ->
            builder.cookieJar(StoredCookieJar(sessionStore, context))
            builder.addNetworkInterceptor { chain ->
                val baseUrl = context.baseUrl.toHttpUrlOrNull()
                    ?: throw IOException(SERVER_CONFIG_CHANGED)
                if (!chain.request().url.matchesSessionBase(baseUrl)) {
                    throw IOException(SERVER_CONFIG_CHANGED)
                }
                chain.proceed(chain.request())
            }
        }
        if (readTimeoutMillis != null) {
            builder.readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
        }
        return builder.build().newCall(request)
    }

    private fun Request.requireBearerAccessToken(): String {
        return header("Authorization")
            ?.removePrefix("Bearer ")
            ?.takeIf(String::isNotBlank)
            ?: throw ApiException("请先登录")
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

    private fun parseMemo(body: JSONObject): Memo = apiMemoFromJson(body)

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

    private fun parseAISettings(body: JSONObject): AISettings {
        val profiles = body.getJSONArray("profiles")
        return AISettings(
            profiles = buildList {
                for (index in 0 until profiles.length()) {
                    add(parseAIProfile(profiles.getJSONObject(index)))
                }
            },
            autoSummary = body.optBoolean("autoSummary"),
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
            promptVersion = body.optString("promptVersion"),
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

    private class StoredCookieJar(
        private val sessionStore: SessionStore,
        private val context: ClientRequestContext,
    ) : CookieJar {
        private val baseUrl = context.baseUrl.toHttpUrlOrNull()

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val configuredBase = baseUrl ?: return emptyList()
            if (!url.matchesSessionBase(configuredBase)) {
                return emptyList()
            }
            val now = System.currentTimeMillis()
            return sessionStore.cookieHeadersFor(context)
                .mapNotNull { Cookie.parse(url, it) }
                .filter { it.expiresAt > now && it.matches(url) }
        }

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val configuredBase = baseUrl ?: return
            if (!url.matchesSessionBase(configuredBase)) {
                return
            }
            sessionStore.updateCookieHeaders(context) { storedHeaders ->
                val now = System.currentTimeMillis()
                val merged = storedHeaders
                    .mapNotNull { Cookie.parse(url, it) }
                    .filter { it.expiresAt > now }
                    .associateByTo(mutableMapOf()) { it.storageKey() }
                cookies.forEach { cookie ->
                    if (cookie.expiresAt <= now) {
                        merged.remove(cookie.storageKey())
                    } else {
                        merged[cookie.storageKey()] = cookie
                    }
                }
                merged.values.map(Cookie::toString)
            }
        }

        private fun Cookie.storageKey(): String = "$name|$domain|$path"
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val EMPTY_BODY = ByteArray(0).toRequestBody(JSON)
    }
}

private fun HttpUrl.matchesSessionBase(baseUrl: HttpUrl): Boolean {
    if (scheme != baseUrl.scheme || host != baseUrl.host || port != baseUrl.port) {
        return false
    }
    val routeIndex = SESSION_ROUTE_PREFIXES.maxOf { prefix ->
        encodedPath.lastIndexOf(prefix).takeIf { index ->
            index >= 0 && (index + prefix.length == encodedPath.length || encodedPath[index + prefix.length] == '/')
        } ?: -1
    }
    if (routeIndex < 0) {
        return false
    }
    val requestBasePath = encodedPath.substring(0, routeIndex).trimEnd('/')
    val configuredBasePath = baseUrl.encodedPath.trimEnd('/')
    return requestBasePath == configuredBasePath
}

private fun HttpUrl.serverBaseKey(): String = newBuilder()
    .query(null)
    .fragment(null)
    .build()
    .toString()
    .trimEnd('/')

private fun ClientSessionSnapshot.matches(context: ClientRequestContext): Boolean {
    return baseUrl == context.baseUrl && contextGeneration == context.contextGeneration
}

private val SESSION_ROUTE_PREFIXES = listOf("/api/v1", "/file/attachments")

internal fun pendingMemoSyncToJson(pending: PendingMemoSync): JSONObject {
    val memo = pending.memo
    val action = when {
        memo.deletedAt != null -> "delete"
        pending.baseVersion == null -> "create"
        else -> "update"
    }
    val memoPayload = JSONObject()
        .put("id", memo.id)
        .put("content", memo.content)
        .put("entryDate", memo.entryDate)
    if (action != "delete") {
        val favorited = memo.favoritedAt != null
        memoPayload
            .put("favorited", favorited)
            .put("pinned", favorited)
            .put("archived", memo.archivedAt != null)
    }
    return JSONObject()
        .put("mutationId", pending.mutationId)
        .put("resourceType", "memo")
        .put("resourceId", memo.id)
        .put("action", action)
        .put("memo", memoPayload)
        .apply {
            if (action != "create") {
                put("baseVersion", pending.baseVersion)
            }
        }
}

internal class SessionRefreshCoordinator(
    private val currentSession: () -> ClientSessionSnapshot,
    private val clearSession: (ClientRequestContext, String) -> Boolean,
    private val refresh: suspend (ClientRequestContext) -> Unit,
) {
    private val mutex = SESSION_MUTEX

    suspend fun refreshAfterUnauthorized(
        context: ClientRequestContext,
        failedAccessToken: String,
    ) {
        mutex.withLock {
            val current = currentSession()
            if (!current.matches(context)) {
                throw ApiException(SERVER_CONFIG_CHANGED)
            }
            val currentToken = current.accessToken
                ?: throw ApiException("请先登录")
            if (currentToken != failedAccessToken) {
                return
            }
            try {
                refresh(context)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                clearSession(context, failedAccessToken)
                throw error
            }
        }
    }

    suspend fun <T> runSessionExclusive(block: suspend () -> T): T {
        return mutex.withLock { block() }
    }

    private companion object {
        val SESSION_MUTEX = Mutex()
    }
}

data class SyncPushSummary(
    val applied: Int,
    val conflict: Int,
    val rejected: Int,
    val appliedMemoSyncs: List<AppliedMemoSync> = emptyList(),
)

internal fun syncPushSummaryFromResults(results: JSONArray): SyncPushSummary {
    var applied = 0
    var conflict = 0
    var rejected = 0
    val appliedMemoSyncs = mutableListOf<AppliedMemoSync>()
    for (index in 0 until results.length()) {
        val result = results.getJSONObject(index)
        when (result.optString("status")) {
            "applied" -> {
                applied += 1
                appliedMemoSyncs += AppliedMemoSync(
                    mutationId = result.getString("mutationId"),
                    memo = apiMemoFromJson(result.getJSONObject("resource")),
                )
            }
            "conflict" -> conflict += 1
            else -> rejected += 1
        }
    }
    return SyncPushSummary(
        applied = applied,
        conflict = conflict,
        rejected = rejected,
        appliedMemoSyncs = appliedMemoSyncs,
    )
}

internal fun apiMemoFromJson(body: JSONObject): Memo {
    return Memo(
        id = body.getString("id"),
        content = body.getString("content"),
        entryDate = body.getString("entryDate"),
        version = body.getLong("version"),
        createdAt = body.getString("createdAt"),
        updatedAt = body.getString("updatedAt"),
        favoritedAt = body.apiNullableString("favoritedAt") ?: body.apiNullableString("pinnedAt"),
        archivedAt = body.apiNullableString("archivedAt"),
        deletedAt = body.apiNullableString("deletedAt"),
    )
}

private fun JSONObject.apiNullableString(name: String): String? {
    return if (isNull(name)) null else optString(name)
}
