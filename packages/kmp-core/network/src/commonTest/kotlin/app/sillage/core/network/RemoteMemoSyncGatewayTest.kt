package app.sillage.core.network

import app.sillage.core.application.auth.AuthenticationFailureException
import app.sillage.core.application.auth.AuthenticationFailureReason
import app.sillage.core.application.auth.SignInCommand
import app.sillage.core.domain.records.Memo
import app.sillage.core.sync.PendingMemoSync
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class RemoteMemoSyncGatewayTest {
    @Test
    fun pullsEveryMemoPageAndEncodesOpaqueCursor() = runTest {
        val cursor = "cursor +/="
        val transport = SyncQueueTransport(
            authenticatedResponse("access-1", "refresh-1"),
            SillageHttpResponse(
                statusCode = 200,
                body = pullPage(
                    memos = listOf(memoResource("memo-1", "first", 1)),
                    nextCursor = cursor,
                    hasMore = true,
                ),
            ),
            SillageHttpResponse(
                statusCode = 200,
                body = pullPage(
                    memos = listOf(
                        memoResource("memo-1", "first updated concurrently", 2),
                        memoResource("memo-2", "second", 2),
                    ),
                    nextCursor = "",
                    hasMore = false,
                ),
            ),
        )
        val factory = RemoteInstanceAuthenticationRepositoryFactory(transport)
        factory.create("https://example.test")
            .signIn(SignInCommand("felix", "password"))

        val memos = factory.createMemoSyncGateway("https://example.test/").pullMemos()

        assertEquals(listOf("memo-1", "memo-2"), memos.map(Memo::id))
        assertEquals("first updated concurrently", memos.first().content)
        assertEquals(SillageHttpMethod.Get, transport.requests[1].method)
        assertEquals(
            "https://example.test/api/v1/sync?limit=200",
            transport.requests[1].url,
        )
        assertEquals(
            "https://example.test/api/v1/sync?limit=200&cursor=cursor%20%2B%2F%3D",
            transport.requests[2].url,
        )
        assertEquals("Bearer access-1", transport.requests[2].headers["Authorization"])
        assertEquals(null, transport.requests[1].body)
    }

    @Test
    fun rejectsPullPageThatCannotAdvanceItsCursor() = runTest {
        val transport = SyncQueueTransport(
            authenticatedResponse("access-1", "refresh-1"),
            SillageHttpResponse(
                statusCode = 200,
                body = pullPage(emptyList(), nextCursor = "", hasMore = true),
            ),
        )
        val factory = RemoteInstanceAuthenticationRepositoryFactory(transport)
        factory.create("https://example.test")
            .signIn(SignInCommand("felix", "password"))

        assertFailsWith<InvalidServerResponseException> {
            factory.createMemoSyncGateway("https://example.test").pullMemos()
        }
    }

    @Test
    fun sharesAuthenticationSessionRefreshesOnceAndMapsEveryResultKind() = runTest {
        val credentialStore = RecordingCredentialStore()
        val transport = SyncQueueTransport(
            authenticatedResponse("access-old", "refresh-old"),
            SillageHttpResponse(statusCode = 401, body = "private expired detail"),
            authenticatedResponse("access-new", "refresh-new"),
            SillageHttpResponse(statusCode = 200, body = mixedSyncResponse()),
        )
        val factory = RemoteInstanceAuthenticationRepositoryFactory(
            transport = transport,
            credentialStore = credentialStore,
        )
        factory.create("https://example.test/")
            .signIn(SignInCommand("felix", "correct horse battery staple"))
        val gateway = factory.createMemoSyncGateway("https://example.test")
        val pending = listOf(
            pendingMemo("memo-1", "mutation-applied", "update", 1),
            pendingMemo("memo-2", "mutation-conflict", "update", 2),
            pendingMemo("memo-3", "mutation-rejected", "delete", 3),
        )

        val summary = gateway.pushMemos(pending)

        assertEquals(1, summary.applied)
        assertEquals(1, summary.conflict)
        assertEquals(1, summary.rejected)
        assertEquals("mutation-applied", summary.appliedMemoSyncs.single().mutationId)
        assertEquals("server normalized", summary.appliedMemoSyncs.single().memo.content)
        assertEquals(
            "2026-08-03T02:00:00Z",
            summary.appliedMemoSyncs.single().memo.favoritedAt,
        )
        val conflict = summary.conflictMemoSyncs.single()
        assertEquals("mutation-conflict", conflict.mutationId)
        assertEquals(2L, conflict.clientVersion)
        assertEquals(3L, conflict.serverVersion)
        assertEquals("server conflict", conflict.serverMemo?.content)

        assertEquals(listOf("refresh-old", "refresh-new"), credentialStore.writes)
        assertEquals(4, transport.requests.size)
        assertEquals("Bearer access-old", transport.requests[1].headers["Authorization"])
        assertEquals("sillage_refresh=refresh-old", transport.requests[2].headers["Cookie"])
        assertFalse(transport.requests[2].headers.containsKey("Authorization"))
        assertEquals("Bearer access-new", transport.requests[3].headers["Authorization"])
        assertEquals(transport.requests[1].body, transport.requests[3].body)
    }

    @Test
    fun batchesAtTwoHundredAndMapsCreateUpdateDeleteRestoreAndPurge() = runTest {
        val actions = listOf("create", "update", "delete", "restore", "purge")
        val pending = (0 until 205).map { index ->
            val action = actions[index % actions.size]
            pendingMemo(
                id = "memo-$index",
                mutationId = "mutation-$index",
                action = action,
                baseVersion = if (action == "create") null else index.toLong() + 1,
            )
        }
        val transport = SyncQueueTransport(
            authenticatedResponse("access-1", "refresh-1"),
            SillageHttpResponse(200, rejectedSyncResponse(pending.take(200))),
            SillageHttpResponse(200, rejectedSyncResponse(pending.drop(200))),
        )
        val factory = RemoteInstanceAuthenticationRepositoryFactory(transport)
        factory.create("https://example.test")
            .signIn(SignInCommand("felix", "password"))

        val summary = factory.createMemoSyncGateway("https://example.test/")
            .pushMemos(pending)

        assertEquals(0, summary.applied)
        assertEquals(0, summary.conflict)
        assertEquals(205, summary.rejected)
        assertEquals(3, transport.requests.size)
        val firstRequest = transport.requests[1]
        val secondRequest = transport.requests[2]
        assertEquals("https://example.test/api/v1/sync:push", firstRequest.url)
        assertEquals(SillageHttpMethod.Post, firstRequest.method)
        assertEquals("application/json", firstRequest.headers["Accept"])
        assertEquals("Bearer access-1", firstRequest.headers["Authorization"])
        assertEquals("Bearer access-1", secondRequest.headers["Authorization"])

        val firstChanges = changes(firstRequest)
        val secondChanges = changes(secondRequest)
        assertEquals(200, firstChanges.size)
        assertEquals(5, secondChanges.size)
        assertEquals(actions, firstChanges.take(5).map {
            it.jsonObject.getValue("action").jsonPrimitive.content
        })

        val create = firstChanges[0].jsonObject
        val update = firstChanges[1].jsonObject
        val delete = firstChanges[2].jsonObject
        val restore = firstChanges[3].jsonObject
        val purge = firstChanges[4].jsonObject
        assertFalse(create.containsKey("baseVersion"))
        assertEquals("2", update.getValue("baseVersion").jsonPrimitive.content)
        assertEquals("3", delete.getValue("baseVersion").jsonPrimitive.content)
        assertEquals("4", restore.getValue("baseVersion").jsonPrimitive.content)
        assertEquals("5", purge.getValue("baseVersion").jsonPrimitive.content)

        val createMemo = create.getValue("memo").jsonObject
        val updateMemo = update.getValue("memo").jsonObject
        assertEquals("true", createMemo.getValue("favorited").jsonPrimitive.content)
        assertEquals("true", createMemo.getValue("archived").jsonPrimitive.content)
        assertFalse(createMemo.containsKey("pinned"))
        assertEquals("private content memo-1", updateMemo.getValue("content").jsonPrimitive.content)
        assertFalse(delete.containsKey("memo"))
        assertFalse(restore.containsKey("memo"))
        assertFalse(purge.containsKey("memo"))
    }

    @Test
    fun emptyPushSkipsSessionAndTransport() = runTest {
        val transport = SyncQueueTransport()
        val gateway = RemoteInstanceAuthenticationRepositoryFactory(transport)
            .createMemoSyncGateway("https://example.test")

        val summary = gateway.pushMemos(emptyList())

        assertTrue(summary.empty)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun activeSessionIsRequiredBeforeRecordContentIsSent() = runTest {
        val transport = SyncQueueTransport()
        val gateway = RemoteInstanceAuthenticationRepositoryFactory(transport)
            .createMemoSyncGateway("https://example.test")

        val error = assertFailsWith<AuthenticationFailureException> {
            gateway.pushMemos(listOf(pendingMemo("memo-1", "mutation-1", "create", null)))
        }

        assertEquals(AuthenticationFailureReason.SessionExpired, error.reason)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun rejectsMismatchedAndOversizedResultEnvelopes() = runTest {
        val pending = pendingMemo("memo-1", "mutation-1", "update", 1)
        val mismatchedTransport = SyncQueueTransport(
            authenticatedResponse("access-1", "refresh-1"),
            SillageHttpResponse(
                statusCode = 200,
                body = rejectedSyncResponse(
                    listOf(pending.copy(mutationId = "different-mutation")),
                ),
            ),
        )
        val mismatchedFactory = RemoteInstanceAuthenticationRepositoryFactory(mismatchedTransport)
        mismatchedFactory.create("https://example.test")
            .signIn(SignInCommand("felix", "password"))

        assertFailsWith<InvalidServerResponseException> {
            mismatchedFactory.createMemoSyncGateway("https://example.test")
                .pushMemos(listOf(pending))
        }

        val oversizedTransport = SyncQueueTransport(
            authenticatedResponse("access-1", "refresh-1"),
            SillageHttpResponse(
                statusCode = 200,
                body = rejectedSyncResponse(listOf(pending)) +
                    " ".repeat(8 * 1024 * 1024),
            ),
        )
        val oversizedFactory = RemoteInstanceAuthenticationRepositoryFactory(oversizedTransport)
        oversizedFactory.create("https://example.test")
            .signIn(SignInCommand("felix", "password"))

        assertFailsWith<InvalidServerResponseException> {
            oversizedFactory.createMemoSyncGateway("https://example.test")
                .pushMemos(listOf(pending))
        }
    }

    @Test
    fun invalidResponseAndHttpFailureDoNotSurfacePrivateBodies() = runTest {
        val malformedTransport = SyncQueueTransport(
            authenticatedResponse("access-1", "refresh-1"),
            SillageHttpResponse(
                statusCode = 200,
                body = """{"results":[{"mutationId":"mutation-1","resourceType":"memo","resourceId":"memo-1","status":"applied","resource":{"content":"private record body"}}]}""",
            ),
        )
        val malformedFactory = RemoteInstanceAuthenticationRepositoryFactory(malformedTransport)
        malformedFactory.create("https://example.test")
            .signIn(SignInCommand("felix", "password"))
        val malformedError = assertFailsWith<InvalidServerResponseException> {
            malformedFactory.createMemoSyncGateway("https://example.test")
                .pushMemos(listOf(pendingMemo("memo-1", "mutation-1", "update", 1)))
        }
        assertFalse(malformedError.message.orEmpty().contains("private record body"))

        val failedTransport = SyncQueueTransport(
            authenticatedResponse("access-1", "refresh-1"),
            SillageHttpResponse(statusCode = 503, body = "private server detail"),
        )
        val failedFactory = RemoteInstanceAuthenticationRepositoryFactory(failedTransport)
        failedFactory.create("https://example.test")
            .signIn(SignInCommand("felix", "password"))
        val statusError = assertFailsWith<SillageHttpStatusException> {
            failedFactory.createMemoSyncGateway("https://example.test")
                .pushMemos(listOf(pendingMemo("memo-1", "mutation-1", "update", 1)))
        }
        assertEquals(503, statusError.statusCode)
        assertFalse(statusError.message.orEmpty().contains("private server detail"))
    }

    private fun pullPage(
        memos: List<JsonObject>,
        nextCursor: String,
        hasMore: Boolean,
    ): String = buildJsonObject {
        put("memos", buildJsonArray { memos.forEach(::add) })
        put("nextCursor", nextCursor)
        put("hasMore", hasMore)
    }.toString()

    private fun changes(request: SillageHttpRequest) = Json.parseToJsonElement(
        request.body ?: error("missing sync request body"),
    ).jsonObject.getValue("changes").jsonArray
}

private class SyncQueueTransport(
    vararg responses: SillageHttpResponse,
) : SillageHttpTransport {
    private val responses = responses.toMutableList()
    val requests = mutableListOf<SillageHttpRequest>()

    override suspend fun execute(request: SillageHttpRequest): SillageHttpResponse {
        requests += request
        return responses.removeAt(0)
    }
}

private class RecordingCredentialStore : AuthenticationCredentialStore {
    override val persistsAcrossLaunches: Boolean = true
    val writes = mutableListOf<String>()
    private var refreshCookie: String? = null

    override fun read(baseUrl: String): AuthenticationCredentialReadResult {
        return refreshCookie?.let(AuthenticationCredentialReadResult::Available)
            ?: AuthenticationCredentialReadResult.Missing
    }

    override fun write(baseUrl: String, refreshCookie: String) {
        writes += refreshCookie
        this.refreshCookie = refreshCookie
    }

    override fun delete(baseUrl: String) {
        refreshCookie = null
    }
}

private fun pendingMemo(
    id: String,
    mutationId: String,
    action: String,
    baseVersion: Long?,
): PendingMemoSync {
    return PendingMemoSync(
        memo = Memo(
            id = id,
            content = "private content $id",
            entryDate = "2026-08-03",
            version = (baseVersion ?: 0) + 1,
            createdAt = "2026-08-03T01:00:00Z",
            updatedAt = "2026-08-03T01:30:00Z",
            favoritedAt = "2026-08-03T01:10:00Z",
            archivedAt = "2026-08-03T01:20:00Z",
            deletedAt = if (action == "delete" || action == "purge") {
                "2026-08-03T01:40:00Z"
            } else {
                null
            },
            purgedAt = if (action == "purge") "2026-08-03T01:50:00Z" else null,
        ),
        baseVersion = baseVersion,
        mutationId = mutationId,
        action = action,
    )
}

private fun rejectedSyncResponse(pending: List<PendingMemoSync>): String {
    return buildJsonObject {
        put("results", buildJsonArray {
            pending.forEach { item ->
                add(buildJsonObject {
                    put("mutationId", item.mutationId)
                    put("resourceType", "memo")
                    put("resourceId", item.memo.id)
                    put("status", "rejected")
                    put("reason", "invalid")
                })
            }
        })
    }.toString()
}

private fun mixedSyncResponse(): String {
    return buildJsonObject {
        put("results", buildJsonArray {
            add(buildJsonObject {
                put("mutationId", "mutation-applied")
                put("resourceType", "memo")
                put("resourceId", "memo-1")
                put("status", "applied")
                put("resource", memoResource(
                    id = "memo-1",
                    content = "server normalized",
                    version = 2,
                    legacyPinnedAt = "2026-08-03T02:00:00Z",
                ))
            })
            add(buildJsonObject {
                put("mutationId", "mutation-conflict")
                put("resourceType", "memo")
                put("resourceId", "memo-2")
                put("status", "conflict")
                put("clientVersion", 2)
                put("serverVersion", 3)
                put("serverResource", memoResource(
                    id = "memo-2",
                    content = "server conflict",
                    version = 3,
                ))
            })
            add(buildJsonObject {
                put("mutationId", "mutation-rejected")
                put("resourceType", "memo")
                put("resourceId", "memo-3")
                put("status", "rejected")
                put("reason", "invalid_action")
            })
        })
    }.toString()
}

private fun memoResource(
    id: String,
    content: String,
    version: Long,
    legacyPinnedAt: String? = null,
) = buildJsonObject {
    put("id", id)
    put("content", content)
    put("entryDate", "2026-08-03")
    put("version", version)
    put("createdAt", "2026-08-03T01:00:00Z")
    put("updatedAt", "2026-08-03T02:00:00Z")
    put("favoritedAt", JsonNull)
    legacyPinnedAt?.let { put("pinnedAt", it) }
    put("archivedAt", JsonNull)
    put("deletedAt", JsonNull)
    put("purgedAt", JsonNull)
}

private fun authenticatedResponse(
    accessToken: String,
    refreshToken: String,
): SillageHttpResponse {
    return SillageHttpResponse(
        statusCode = 200,
        body = """
            {
              "account": {
                "id": "account-1",
                "username": "felix",
                "displayName": "Felix"
              },
              "accessToken": "$accessToken",
              "expiresAt": "2026-08-03T12:00:00Z"
            }
        """.trimIndent(),
        headers = mapOf(
            "Set-Cookie" to listOf(
                "sillage_refresh=$refreshToken; Path=/api/v1/auth; HttpOnly; SameSite=Lax",
            ),
        ),
    )
}
