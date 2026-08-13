package app.sillage.core.network

import app.sillage.core.application.ask.AskAnswerStreamEvent
import app.sillage.core.application.ask.AskClient
import app.sillage.core.application.ask.StreamAskAnswerCommand
import app.sillage.core.application.auth.SignInCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class RemoteAskClientTest {
    // ── RemoteAskRepository ──────────────────────────────────────────

    @Test
    fun listConversationsSendsGetAndParsesResponse() = runTest {
        val transport = QueueTransport(
            signInResponse(),
            jsonResponse(conversationsJson("c1", "Q")),
        )
        val client = signInAndCreateAskClient(transport)

        val conversations = client.repository.listConversations()

        assertEquals(1, conversations.size)
        assertEquals("c1", conversations[0].id)
        assertEquals("Q", conversations[0].title)
        assertEquals(SillageHttpMethod.Get, transport.requests[1].method)
        assertEquals(
            "https://example.test/api/v1/ask/conversations?limit=50",
            transport.requests[1].url,
        )
    }

    @Test
    fun listConversationsIncludesAuthorizationHeader() = runTest {
        val transport = QueueTransport(
            signInResponse(),
            jsonResponse(conversationsJson()),
        )
        val client = signInAndCreateAskClient(transport)

        client.repository.listConversations()

        assertEquals("Bearer test-access", transport.requests[1].headers["Authorization"])
    }

    @Test
    fun listMessagesEncodesConversationIdInUrl() = runTest {
        val transport = QueueTransport(
            signInResponse(),
            jsonResponse(messagesJson("m1", "c1/child")),
        )
        val client = signInAndCreateAskClient(transport)

        val messages = client.repository.listMessages("c1/child")

        assertEquals(1, messages.size)
        assertEquals("m1", messages[0].id)
        assertEquals(
            "https://example.test/api/v1/ask/conversations/c1%2Fchild/messages",
            transport.requests[1].url,
        )
    }

    @Test
    fun createConversationPostsContextScope() = runTest {
        val transport = QueueTransport(
            signInResponse(),
            jsonResponse(conversationEnvelopeJson("c2", "all")),
        )
        val client = signInAndCreateAskClient(transport)

        val conversation = client.repository.createConversation("all")

        assertEquals("c2", conversation.id)
        assertEquals("all", conversation.contextScope)
        assertEquals(SillageHttpMethod.Post, transport.requests[1].method)
        assertEquals(
            "https://example.test/api/v1/ask/conversations",
            transport.requests[1].url,
        )
        assertTrue(transport.requests[1].body.orEmpty().contains("\"contextScope\":\"all\""))
    }

    @Test
    fun setHeadPostsMessageId() = runTest {
        val transport = QueueTransport(
            signInResponse(),
            jsonResponse(""),
        )
        val client = signInAndCreateAskClient(transport)

        client.repository.setHead("c1", "m2")

        assertEquals(SillageHttpMethod.Post, transport.requests[1].method)
        assertEquals(
            "https://example.test/api/v1/ask/conversations/c1/head",
            transport.requests[1].url,
        )
        assertTrue(transport.requests[1].body.orEmpty().contains("\"messageId\":\"m2\""))
    }

    @Test
    fun repositoryThrowsOnServerError() = runTest {
        val transport = QueueTransport(
            signInResponse(),
            SillageHttpResponse(statusCode = 500, body = ""),
        )
        val client = signInAndCreateAskClient(transport)

        val error = assertFailsWith<RemoteAskFailureException> {
            client.repository.listConversations()
        }
        assertEquals(RemoteAskFailureReason.ServerUnavailable, error.reason)
        assertEquals(500, error.statusCode)
    }

    @Test
    fun repositoryThrowsOnClientError() = runTest {
        val transport = QueueTransport(
            signInResponse(),
            SillageHttpResponse(statusCode = 403, body = ""),
        )
        val client = signInAndCreateAskClient(transport)

        val error = assertFailsWith<RemoteAskFailureException> {
            client.repository.createConversation("recent")
        }
        assertEquals(RemoteAskFailureReason.RequestRejected, error.reason)
        assertEquals(403, error.statusCode)
    }

    @Test
    fun repositoryThrowsOnMalformedJson() = runTest {
        val transport = QueueTransport(
            signInResponse(),
            SillageHttpResponse(statusCode = 200, body = "not json"),
        )
        val client = signInAndCreateAskClient(transport)

        assertFailsWith<RemoteAskFailureException> {
            client.repository.listConversations()
        }
    }

    @Test
    fun repositoryThrowsOnMissingRequiredField() = runTest {
        val transport = QueueTransport(
            signInResponse(),
            SillageHttpResponse(statusCode = 200, body = """{"conversations":[{}]}"""),
        )
        val client = signInAndCreateAskClient(transport)

        assertFailsWith<RemoteAskFailureException> {
            client.repository.listConversations()
        }
    }

    @Test
    fun repositoryThrowsOnMissingConversationsArray() = runTest {
        val transport = QueueTransport(
            signInResponse(),
            SillageHttpResponse(statusCode = 200, body = """{"items":[]}"""),
        )
        val client = signInAndCreateAskClient(transport)

        assertFailsWith<RemoteAskFailureException> {
            client.repository.listConversations()
        }
    }

    @Test
    fun repositoryParsesConversationWithAllFields() = runTest {
        val json = """{"conversations":[{"id":"c1","title":"My Q","status":"active","contextScope":"recent","headMessageId":"m0","pinnedAt":"2026-08-01T00:00:00Z","archivedAt":null,"createdAt":"2026-08-01T00:00:00Z","updatedAt":"2026-08-02T00:00:00Z","deletedAt":null}]}"""
        val transport = QueueTransport(signInResponse(), jsonResponse(json))
        val client = signInAndCreateAskClient(transport)

        val c = client.repository.listConversations().single()

        assertEquals("c1", c.id)
        assertEquals("My Q", c.title)
        assertEquals("active", c.status)
        assertEquals("recent", c.contextScope)
        assertEquals("m0", c.headMessageId)
        assertEquals("2026-08-01T00:00:00Z", c.pinnedAt)
        assertEquals(null, c.archivedAt)
        assertEquals(null, c.deletedAt)
    }

    @Test
    fun repositoryParsesMessageWithSourceRefs() = runTest {
        val json = """{"messages":[{"id":"m1","conversationId":"c1","role":"assistant","content":"Answer","parentId":null,"forkOfId":"parent","status":"complete","sourceRefs":[{"memoId":"memo-1","entryDate":"2026-08-01","excerpt":"text","rank":1}],"model":"gpt-4","promptVersion":"v2","createdAt":"2026-08-01T00:00:00Z","updatedAt":"2026-08-01T00:00:00Z","deletedAt":null}]}"""
        val transport = QueueTransport(signInResponse(), jsonResponse(json))
        val client = signInAndCreateAskClient(transport)

        val msg = client.repository.listMessages("c1").single()

        assertEquals("m1", msg.id)
        assertEquals("assistant", msg.role)
        assertEquals("Answer", msg.content)
        assertEquals("parent", msg.forkOfId)
        assertEquals("gpt-4", msg.model)
        assertEquals("v2", msg.promptVersion)
        assertEquals(1, msg.sourceRefs.size)
        assertEquals("memo-1", msg.sourceRefs[0].memoId)
        assertEquals("2026-08-01", msg.sourceRefs[0].entryDate)
        assertEquals("text", msg.sourceRefs[0].excerpt)
        assertEquals(1, msg.sourceRefs[0].rank)
    }

    // ── RemoteAskAnswerStreamer ──────────────────────────────────────

    @Test
    fun streamParsesStartDeltaAndEndEvents() = runTest {
        val sse = buildString {
            appendSseEvent("start", startDataJson("um1", regenerate = false))
            appendSseEvent("delta", """{"text":"Hello "}""")
            appendSseEvent("delta", """{"text":"world"}""")
        }
        val transport = streamingQueueTransport(sse)
        val client = signInAndCreateAskClient(transport)

        val events = mutableListOf<AskAnswerStreamEvent>()
        client.answerStreamer.stream(
            StreamAskAnswerCommand("c1", "Q", "recent", "memos"),
            events::add,
        )

        assertEquals(3, events.size)
        val started = events[0] as AskAnswerStreamEvent.Started
        assertEquals("um1", started.userMessage.id)
        assertFalse(started.regenerating)
        assertEquals("Hello ", (events[1] as AskAnswerStreamEvent.Delta).text)
        assertEquals("world", (events[2] as AskAnswerStreamEvent.Delta).text)
    }

    @Test
    fun streamParsesErrorEvent() = runTest {
        val sse = buildString {
            appendSseEvent("error", """{"message":"Rate limited"}""")
        }
        val transport = streamingQueueTransport(sse)
        val client = signInAndCreateAskClient(transport)

        val events = mutableListOf<AskAnswerStreamEvent>()
        client.answerStreamer.stream(
            StreamAskAnswerCommand("c1", "Q", "recent", "memos"),
            events::add,
        )

        assertEquals(1, events.size)
        assertEquals("Rate limited", (events[0] as AskAnswerStreamEvent.Failed).message)
    }

    @Test
    fun streamParsesStartWithRegenerateFlag() = runTest {
        val sse = buildString {
            appendSseEvent("start", startDataJson("um1", regenerate = true))
        }
        val transport = streamingQueueTransport(sse)
        val client = signInAndCreateAskClient(transport)

        val events = mutableListOf<AskAnswerStreamEvent>()
        client.answerStreamer.stream(
            StreamAskAnswerCommand("c1", "Q", "recent", "memos"),
            events::add,
        )

        assertTrue((events.single() as AskAnswerStreamEvent.Started).regenerating)
    }

    @Test
    fun streamHandlesChunkedSseDelivery() = runTest {
        val fullSse = buildString {
            appendSseEvent("start", startDataJson("um1", regenerate = false))
        }
        val chunks = fullSse.encodeToByteArray().chunkedBytes(20)
        val transport = ChunkedStreamingQueueTransport(chunks)
        val factory = RemoteInstanceAuthenticationRepositoryFactory(transport)
        factory.create("https://example.test").signIn(SignInCommand("test", "pass"))
        val client = factory.createAskClient("https://example.test")

        val events = mutableListOf<AskAnswerStreamEvent>()
        client.answerStreamer.stream(
            StreamAskAnswerCommand("c1", "Q", "recent", "memos"),
            events::add,
        )

        assertEquals(1, events.size)
        assertEquals("um1", (events[0] as AskAnswerStreamEvent.Started).userMessage.id)
    }

    @Test
    fun streamPreservesUtf8CodePointsSplitAcrossByteChunks() = runTest {
        val expected = "\u4e2d\u6587 \ud83d\ude80"
        val fullSse = buildString {
            appendSseEvent("delta", """{"text":"$expected"}""")
        }
        val chunks = fullSse.encodeToByteArray().map { byte -> byteArrayOf(byte) }
        val transport = ChunkedStreamingQueueTransport(chunks)
        val factory = RemoteInstanceAuthenticationRepositoryFactory(transport)
        factory.create("https://example.test").signIn(SignInCommand("test", "pass"))
        val client = factory.createAskClient("https://example.test")

        val events = mutableListOf<AskAnswerStreamEvent>()
        client.answerStreamer.stream(
            StreamAskAnswerCommand("c1", "Q", "recent", "memos"),
            events::add,
        )

        assertEquals(expected, (events.single() as AskAnswerStreamEvent.Delta).text)
    }

    @Test
    fun streamSendsCorrectRequestPayload() = runTest {
        val transport = streamingQueueTransport("")
        val client = signInAndCreateAskClient(transport)

        client.answerStreamer.stream(
            StreamAskAnswerCommand(
                conversationId = "c1",
                content = "What?",
                contextScope = "all",
                sourceKind = "memos",
                forkOfMessageId = "parent-1",
            ),
            {},
        )

        val request = transport.requests.last()
        assertEquals(SillageHttpMethod.Post, request.method)
        assertEquals(
            "https://example.test/api/v1/ask/conversations/c1/messages:stream",
            request.url,
        )
        assertTrue(request.headers["Accept"]!!.contains("text/event-stream"))
        val body = request.body.orEmpty()
        assertTrue(body.contains("\"content\":\"What?\""))
        assertTrue(body.contains("\"contextScope\":\"all\""))
        assertTrue(body.contains("\"sourceKind\":\"memos\""))
        assertTrue(body.contains("\"forkOfId\":\"parent-1\""))
    }

    @Test
    fun streamOmitsForkOfIdWhenNull() = runTest {
        val transport = streamingQueueTransport("")
        val client = signInAndCreateAskClient(transport)

        client.answerStreamer.stream(
            StreamAskAnswerCommand("c1", "Q", "recent", "memos"),
            {},
        )

        assertFalse(transport.requests.last().body.orEmpty().contains("forkOfId"))
    }

    @Test
    fun streamThrowsOnServerError() = runTest {
        val transport = QueueTransport(
            signInResponse(),
            SillageHttpResponse(statusCode = 502, body = ""),
        )
        val client = signInAndCreateAskClient(transport)

        val error = assertFailsWith<RemoteAskFailureException> {
            client.answerStreamer.stream(
                StreamAskAnswerCommand("c1", "Q", "recent", "memos"),
                {},
            )
        }
        assertEquals(RemoteAskFailureReason.ServerUnavailable, error.reason)
    }

    @Test
    fun streamThrowsOnInvalidSseData() = runTest {
        val transport = streamingQueueTransport("event: delta\ndata: not-json\n\n")
        val client = signInAndCreateAskClient(transport)

        assertFailsWith<RemoteAskFailureException> {
            client.answerStreamer.stream(
                StreamAskAnswerCommand("c1", "Q", "recent", "memos"),
                {},
            )
        }
    }

    @Test
    fun streamMapsIncompleteUtf8ToInvalidResponse() = runTest {
        val transport = ChunkedStreamingQueueTransport(
            listOf(byteArrayOf(0xf0.toByte(), 0x9f.toByte())),
        )
        val factory = RemoteInstanceAuthenticationRepositoryFactory(transport)
        factory.create("https://example.test").signIn(SignInCommand("test", "pass"))
        val client = factory.createAskClient("https://example.test")

        val error = assertFailsWith<RemoteAskFailureException> {
            client.answerStreamer.stream(
                StreamAskAnswerCommand("c1", "Q", "recent", "memos"),
                {},
            )
        }

        assertEquals(RemoteAskFailureReason.InvalidResponse, error.reason)
    }

    @Test
    fun streamIgnoresBlankDataLines() = runTest {
        val sse = buildString {
            appendLine("event: delta")
            appendLine("data: ")
            appendLine()
        }
        val transport = streamingQueueTransport(sse)
        val client = signInAndCreateAskClient(transport)

        val events = mutableListOf<AskAnswerStreamEvent>()
        client.answerStreamer.stream(
            StreamAskAnswerCommand("c1", "Q", "recent", "memos"),
            events::add,
        )

        assertEquals(0, events.size)
    }

    @Test
    fun streamHandlesWindowsLineEndings() = runTest {
        val sse = "event: delta\r\ndata: {\"text\":\"hi\"}\r\n\r\n"
        val transport = streamingQueueTransport(sse)
        val client = signInAndCreateAskClient(transport)

        val events = mutableListOf<AskAnswerStreamEvent>()
        client.answerStreamer.stream(
            StreamAskAnswerCommand("c1", "Q", "recent", "memos"),
            events::add,
        )

        assertEquals(1, events.size)
        assertEquals("hi", (events[0] as AskAnswerStreamEvent.Delta).text)
    }

    @Test
    fun streamDefaultsDeltaTextToEmpty() = runTest {
        val sse = buildString {
            appendSseEvent("delta", """{}""")
        }
        val transport = streamingQueueTransport(sse)
        val client = signInAndCreateAskClient(transport)

        val events = mutableListOf<AskAnswerStreamEvent>()
        client.answerStreamer.stream(
            StreamAskAnswerCommand("c1", "Q", "recent", "memos"),
            events::add,
        )

        assertEquals("", (events.single() as AskAnswerStreamEvent.Delta).text)
    }

    @Test
    fun streamDefaultsErrorToGenericMessage() = runTest {
        val sse = buildString {
            appendSseEvent("error", """{}""")
        }
        val transport = streamingQueueTransport(sse)
        val client = signInAndCreateAskClient(transport)

        val events = mutableListOf<AskAnswerStreamEvent>()
        client.answerStreamer.stream(
            StreamAskAnswerCommand("c1", "Q", "recent", "memos"),
            events::add,
        )

        assertEquals(
            "Ask answer generation failed.",
            (events.single() as AskAnswerStreamEvent.Failed).message,
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /**
     * Signs in through the factory to populate the shared session store,
     * then creates an AskClient that reuses the same session.
     */
    private suspend fun signInAndCreateAskClient(transport: QueueTransport): AskClient {
        val factory = RemoteInstanceAuthenticationRepositoryFactory(transport)
        factory.create("https://example.test").signIn(SignInCommand("test", "pass"))
        return factory.createAskClient("https://example.test")
    }

    private fun signInResponse(): SillageHttpResponse = SillageHttpResponse(
        statusCode = 200,
        body = """
            {
              "account": {"id": "account-1", "username": "test", "displayName": "Test"},
              "accessToken": "test-access",
              "expiresAt": "2026-12-31T23:59:59Z"
            }
        """.trimIndent(),
        headers = mapOf(
            "Set-Cookie" to listOf(
                "sillage_refresh=test-refresh; Path=/api/v1/auth; HttpOnly",
            ),
        ),
    )

    private fun jsonResponse(body: String): SillageHttpResponse =
        SillageHttpResponse(statusCode = 200, body = body)

    private fun conversationsJson(
        id: String = "c1",
        title: String = "Q",
    ): String =
        """{"conversations":[${conversationJson(id, title)}]}"""

    private fun conversationEnvelopeJson(
        id: String = "c1",
        contextScope: String = "recent",
    ): String =
        """{"conversation":${conversationJson(id, contextScope = contextScope)}}"""

    private fun conversationJson(
        id: String = "c1",
        title: String = "Q",
        contextScope: String = "recent",
    ): String = """{"id":"$id","title":"$title","status":"active","contextScope":"$contextScope","headMessageId":null,"pinnedAt":null,"archivedAt":null,"createdAt":"2026-08-01T00:00:00Z","updatedAt":"2026-08-01T00:00:00Z","deletedAt":null}"""

    private fun messagesJson(
        messageId: String = "m1",
        conversationId: String = "c1",
    ): String =
        """{"messages":[{"id":"$messageId","conversationId":"$conversationId","role":"user","content":"Hi","parentId":null,"forkOfId":null,"status":"complete","sourceRefs":[],"model":"","promptVersion":"","createdAt":"2026-08-01T00:00:00Z","updatedAt":"2026-08-01T00:00:00Z","deletedAt":null}]}"""

    private fun startDataJson(
        userMessageId: String = "um1",
        regenerate: Boolean = false,
    ): String = """{"userMessage":{"id":"$userMessageId","conversationId":"c1","role":"user","content":"Q","parentId":null,"forkOfId":null,"status":"complete","sourceRefs":[],"model":"","promptVersion":"","createdAt":"2026-08-01T00:00:00Z","updatedAt":"2026-08-01T00:00:00Z","deletedAt":null},"regenerate":$regenerate}"""

    private fun StringBuilder.appendSseEvent(event: String, data: String) {
        appendLine("event: $event")
        appendLine("data: $data")
        appendLine()
    }

    private fun ByteArray.chunkedBytes(size: Int): List<ByteArray> {
        require(size > 0)
        return indices.step(size).map { start ->
            copyOfRange(start, minOf(start + size, this.size))
        }
    }

    private fun streamingQueueTransport(sseBody: String): QueueTransport {
        return QueueTransport(
            signInResponse(),
            SillageHttpResponse(statusCode = 200, body = sseBody),
            streaming = true,
        )
    }

    private class QueueTransport(
        private vararg val queuedResponses: SillageHttpResponse,
        private val streaming: Boolean = false,
    ) : SillageHttpTransport {
        val requests = mutableListOf<SillageHttpRequest>()
        private var index = 0

        override suspend fun execute(request: SillageHttpRequest): SillageHttpResponse {
            requests += request
            return nextResponse()
        }

        override suspend fun executeStreaming(
            request: SillageHttpRequest,
            onChunk: suspend (ByteArray) -> Unit,
        ): SillageHttpResponse {
            requests += request
            val response = nextResponse()
            if (streaming && response.statusCode in 200..299 && response.body.isNotEmpty()) {
                onChunk(response.body.encodeToByteArray())
            }
            return response
        }

        private fun nextResponse(): SillageHttpResponse {
            check(index < queuedResponses.size) {
                "QueueTransport exhausted: ${index} requests but only ${queuedResponses.size} responses queued"
            }
            return queuedResponses[index++]
        }
    }

    private class ChunkedStreamingQueueTransport(
        private val chunks: List<ByteArray>,
    ) : SillageHttpTransport {
        val requests = mutableListOf<SillageHttpRequest>()
        private var signInDone = false

        override suspend fun execute(request: SillageHttpRequest): SillageHttpResponse {
            requests += request
            check(!signInDone) { "Unexpected execute after sign-in" }
            signInDone = true
            return SillageHttpResponse(
                statusCode = 200,
                body = """
                    {
                      "account": {"id": "account-1", "username": "test", "displayName": "Test"},
                      "accessToken": "test-access",
                      "expiresAt": "2026-12-31T23:59:59Z"
                    }
                """.trimIndent(),
                headers = mapOf(
                    "Set-Cookie" to listOf(
                        "sillage_refresh=test-refresh; Path=/api/v1/auth; HttpOnly",
                    ),
                ),
            )
        }

        override suspend fun executeStreaming(
            request: SillageHttpRequest,
            onChunk: suspend (ByteArray) -> Unit,
        ): SillageHttpResponse {
            requests += request
            for (chunk in chunks) onChunk(chunk)
            return SillageHttpResponse(statusCode = 200, body = chunks.joinedBytes().decodeToString())
        }
    }
}

private fun List<ByteArray>.joinedBytes(): ByteArray {
    val result = ByteArray(sumOf(ByteArray::size))
    var offset = 0
    for (chunk in this) {
        chunk.copyInto(result, destinationOffset = offset)
        offset += chunk.size
    }
    return result
}
