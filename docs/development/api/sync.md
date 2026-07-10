# 同步 API

> 新的自托管版本使用 `/api/v1/sync` 和 `/api/v1/sync:push`，不再保留旧
> Cloudflare Workers 版本的 `/api/sync`。该契约已供 Android 手动同步使用，并继续面向后续更完整的离线同步阶段扩展，支持
> tombstone、mutation id 幂等、逐条冲突返回和附件 metadata 同步扩展。

Protobuf 契约源位于 [`../../../proto/api/v1`](../../../proto/api/v1)，生成物提交入库：

- Go protobuf / gRPC / Connect / grpc-gateway：`proto/gen/api/v1/`
- OpenAPI：`proto/gen/openapi/openapi.yaml`

> Web 前端走 REST，并在 `web/src/lib/api.ts` 维护对应的手写 TypeScript 类型；不再从 proto 生成前端 TS（已移除 `web/src/types/proto/`），以避免双轨漂移。

Android 客户端走 REST v1 包装层，并在 `android/app/src/main/java/app/sillage/data/SillageApi.kt` 维护接口调用与 JSON 解析。当前 Android 已使用 `/api/v1/sync` 拉取服务端快照，并通过 `/api/v1/sync:push` 手动推送本机离线记录；仍不提交 Android proto 生成物。Room 本地镜像、WorkManager 后台同步和从根目录 `proto/` 生成 Android 客户端属于后续阶段。

当前 Connect v1 已注册 `AuthService`、`MemoService`、`AttachmentService`、`SettingsService`、`AskService`
与 `SyncService`。REST v1 与 Connect v1 的鉴权、AI 设置、memo 创建/更新/删除、附件 metadata 读取、
Ask 基础会话/消息、AI 总结、sync pull 和 sync push 共用同一 service 逻辑。

## 鉴权

当前 Go 后端使用唯一账号初始化后返回的 access token：

```http
Authorization: Bearer <access_token>
```

刷新登录状态走 `POST /api/v1/auth/refresh`，refresh token 保存在 HttpOnly cookie 中。

## 拉取

```http
GET /api/v1/sync?cursor=<opaque>&limit=200
```

- `cursor` 是不透明字符串，客户端必须原样回传。
- 省略 `cursor` 表示从头拉取。
- `limit` 省略时默认为 200，单次最大也是 200；传入大于 200 的值时按 200 处理。
- 响应按资源类型分组，当前阶段已实现 `memos`、`attachments` metadata、`memoAi` 派生数据、`askConversations` 和 `askMessages`；后续里程碑会扩展 `summaries`。
- 每个 stream 使用 `(updated_at, id)` keyset 游标，避免同一毫秒的记录在分页边界被跳过。
- 删除使用 `deletedAt` tombstone 返回，客户端据此删除本地镜像。

示例响应：

```json
{
  "memos": [
    {
      "id": "019f03a4-0121-7aaf-8b0a-7af8dc1bf0c7",
      "content": "今天开始写新的 memo",
      "entryDate": "2026-06-26",
      "version": 1,
      "pinnedAt": null,
      "archivedAt": null,
      "createdAt": "2026-06-26T11:15:07Z",
      "updatedAt": "2026-06-26T11:15:07Z",
      "deletedAt": null
    }
  ],
  "attachments": [
    {
      "id": "019f03a4-0121-7aaf-8b0a-7af8dc1bf0c8",
      "uid": "019f03a4-0121-7aaf-8b0a-7af8dc1bf0c9",
      "memoId": null,
      "url": "/file/attachments/019f03a4-0121-7aaf-8b0a-7af8dc1bf0c9/photo.jpg",
      "filename": "photo.jpg",
      "contentType": "image/jpeg",
      "size": 12345,
      "sha256": "hex",
      "width": null,
      "height": null,
      "status": "stored",
      "createdAt": "2026-06-26T11:15:07Z",
      "updatedAt": "2026-06-26T11:15:07Z",
      "deletedAt": null
    }
  ],
  "memoAi": [
    {
      "memoId": "019f03a4-0121-7aaf-8b0a-7af8dc1bf0c7",
      "summary": "基于配置 AI 档案生成的记录总结",
      "sentiment": null,
      "provider": "openai",
      "model": "gpt-4.1-mini",
      "profileId": "",
      "promptVersion": "memo-summary-v2",
      "sourceMemoIds": "[\"019f03a4-0121-7aaf-8b0a-7af8dc1bf0c7\"]",
      "status": "complete",
      "errorCode": null,
      "startedAt": "2026-06-26T11:15:07Z",
      "finishedAt": "2026-06-26T11:15:07Z",
      "inputTokens": 120,
      "outputTokens": 42,
      "totalTokens": 162,
      "createdAt": "2026-06-26T11:15:07Z",
      "updatedAt": "2026-06-26T11:15:07Z"
    }
  ],
  "askConversations": [
    {
      "id": "019f03a4-0121-7aaf-8b0a-7af8dc1bf0d0",
      "title": "最近状态有什么变化？",
      "status": "active",
      "contextScope": "recent_30_days",
      "headMessageId": "019f03a4-0121-7aaf-8b0a-7af8dc1bf0d2",
      "pinnedAt": null,
      "archivedAt": null,
      "createdAt": "2026-06-26T11:15:07Z",
      "updatedAt": "2026-06-26T11:15:07Z",
      "deletedAt": null
    }
  ],
  "askMessages": [
    {
      "id": "019f03a4-0121-7aaf-8b0a-7af8dc1bf0d2",
      "conversationId": "019f03a4-0121-7aaf-8b0a-7af8dc1bf0d0",
      "role": "assistant",
      "content": "根据当前范围内的记录，睡眠更稳定。",
      "parentId": "019f03a4-0121-7aaf-8b0a-7af8dc1bf0d1",
      "forkOfId": null,
      "status": "complete",
      "sourceRefs": [
        {
          "memoId": "019f03a4-0121-7aaf-8b0a-7af8dc1bf0c7",
          "entryDate": "2026-06-26",
          "excerpt": "今天开始写新的 memo",
          "rank": 1
        }
      ],
      "model": "gpt-4.1-mini",
      "createdAt": "2026-06-26T11:15:07Z",
      "updatedAt": "2026-06-26T11:15:07Z",
      "deletedAt": null
    }
  ],
  "cursor": "opaque",
  "nextCursor": "opaque",
  "hasMore": false
}
```

## 推送

```http
POST /api/v1/sync:push
```

请求按变更列表逐条处理；一条失败不会回滚整批。

```json
{
  "changes": [
    {
      "mutationId": "client-generated-id",
      "resourceType": "memo",
      "resourceId": "019f03a4-0121-7aaf-8b0a-7af8dc1bf0c7",
      "action": "update",
      "baseVersion": 1,
      "memo": {
        "id": "019f03a4-0121-7aaf-8b0a-7af8dc1bf0c7",
        "content": "更新后的内容",
        "entryDate": "2026-06-26",
        "pinned": false,
        "archived": false
      }
    }
  ]
}
```

返回：

```json
{
  "results": [
    {
      "mutationId": "client-generated-id",
      "resourceType": "memo",
      "resourceId": "019f03a4-0121-7aaf-8b0a-7af8dc1bf0c7",
      "status": "applied",
      "resource": {}
    }
  ]
}
```

`status` 取值：

- `applied`：已写入，返回服务端规范化资源。
- `conflict`：版本冲突，返回 `serverResource`、`clientVersion`、`serverVersion`。
- `rejected`：字段非法、资源不存在、动作不支持等，返回稳定 `reason`。

同一账号下重复提交相同 `mutationId` 会返回第一次处理结果，并带 `idempotent: true`。

## Memo 语义

- 后端/API/数据库使用 `memo` 命名；中文 UI 显示“记录”。
- `entryDate` 是用户语义日期，格式为 `YYYY-MM-DD`。
- `version` 是 CAS 字段。正文、日期、置顶、归档、删除都必须带客户端已知版本；过期版本返回 `conflict`。
- `deletedAt != null` 表示 tombstone。首版不清理 tombstone。
- Android 当前离线模式可预生成 memo UUID，并通过 `sync:push` 创建；在线模式直接创建 memo，使用普通 `POST /api/v1/memos`。

## Memo 列表

```http
GET /api/v1/memos?limit=50
GET /api/v1/memos?limit=50&cursor=<opaque>
```

普通列表先返回全部置顶记录；置顶与非置顶两个分组内都按 `entryDate`、`createdAt`、`id` 倒序返回。首次请求不传 `cursor`；后续请求原样传入上一页的
`nextCursor`，返回空值表示已到末页。REST 使用 JSON 字段 `nextCursor`，Connect/proto 对应
`ListMemosRequest.cursor` 与 `ListMemosResponse.next_cursor`。`cursor` 是不透明值，客户端不能解析或自行构造。

当 `query` 非空时接口切换到搜索语义：搜索不分页、忽略 `cursor`，且不产生下一页 cursor。

## Memo 搜索

```http
GET /api/v1/memos?query=<keyword>&limit=50
GET /api/v1/memos?query=<keyword>&limit=50&archived=true
```

搜索首选 SQLite FTS5，范围包括 memo Markdown 正文和 memo AI summary。中文短语、长自然语言查询或 FTS
不可用时会降级到 `LIKE` fallback。搜索时可选传 `archived=true` 只查归档记录，或传 `archived=false`
只查当前记录；未传时兼容原有行为，同时搜索两种状态。状态条件在 `limit` 前应用，删除 tombstone 始终不会
出现在搜索结果中。搜索索引是本地派生数据，不进入 sync payload，也不会修改 `memo.updatedAt` 或
`version`。`query` 为空时返回按时间倒序的最近记录，并忽略 `archived`。

## Memo 动作接口（REST）

除创建 / 更新 / 删除外，memo 的状态变更和总结生成走以下动作端点。置顶与归档修改 memo 本身，必须带
`expectedVersion`；总结生成只写派生 AI 数据，不带 `expectedVersion`：

```http
POST /api/v1/memos/{id}:setPinned        # body: { expectedVersion, pinned }
POST /api/v1/memos/{id}:setArchived      # body: { expectedVersion, archived }
POST /api/v1/memos/{id}:generate-summary # 生成 / 重新生成单条记录总结
```

`GET /api/v1/memos/{id}` 在存在总结时会内联返回最新 `ai`（含 `status`、`errorCode`、来源记录数），客户端无需重新生成即可展示已有总结。

## 附件预留

附件字节不进入 sync payload。Android 在线模式已使用普通上传接口上传附件并把返回地址插入 memo Markdown；
受保护附件由 App 携带当前认证下载后交给系统查看器。离线附件字节与 metadata 的完整同步仍属于后续阶段。

当前普通上传接口：

```http
POST /api/v1/attachments
```

它支持 multipart 表单字段：

- `file`：文件内容。
- `memo_id`：可选，关联 memo。
- `mutation_id`：可选，幂等键。
- `idempotency_key`：可选，幂等键。

下载路径为：

```http
GET /file/attachments/{uid}/{filename}
```

附件下载受 Bearer token 鉴权保护。未来断点续传预留 `/api/v1/attachment-uploads` 命名空间。

## AI 设置与同步

AI profile 通过 `GET /api/v1/settings/ai` 和 `PATCH /api/v1/settings/ai` 管理。API key 使用
`ENCRYPTION_SECRET` 经 HKDF 派生 AES-256-GCM key 后以 envelope 保存；浏览器和 sync 只看到
`hasApiKey` / `keyUnavailable`，不会收到明文 key。`PATCH` 的 `apiKey` 省略表示保留原 key。

`autoSummary` 是账户级开关，通过 `POST /api/v1/settings/ai:setAutoSummary` 独立更新，不会改动任何
AI profile。开启后，新建记录会在后台尽力生成单条总结（带超时、不阻塞写入、失败仅告警）。

```http
POST /api/v1/settings/ai:setAutoSummary # body: { autoSummary: true }
POST /api/v1/settings/ai:test     # body: { id }，对已存 profile 做最小调用校验连接，返回 { ok, model }
```

如果更换 `ENCRYPTION_SECRET` 导致旧 key 解不开，服务不会崩溃；相关 profile 会标记为
`keyUnavailable: true`，用户需要重新输入 API key。

## Ask 同步与接口

Ask 当前 REST 接口：

```http
GET  /api/v1/ask/conversations                                  # query / archived 筛选
POST /api/v1/ask/conversations
GET  /api/v1/ask/conversations/{conversation}
POST /api/v1/ask/conversations/{conversation}:setArchived       # body: { archived }
GET  /api/v1/ask/conversations/{conversation}/messages
POST /api/v1/ask/conversations/{conversation}/messages          # 单轮（一次性返回）
POST /api/v1/ask/conversations/{conversation}/messages:stream    # 流式（SSE）
```

会话列表的 `query` 会搜索会话标题与未删除消息正文；`archived` 缺省或为 `false` 时只返回未归档会话，为 `true` 时只返回已归档会话。归档状态在应用 `limit` 前过滤。单会话 GET 不受归档列表筛选影响，供已归档会话刷新或深链接读取元数据。`setArchived` 是可逆的显式布尔动作，更新会话的 `archivedAt` 与 `updatedAt`；变化会进入既有 Ask conversation 增量同步流。

新会话支持 `contextScope`：`recent_7_days`、`recent_30_days`、`all`，默认 `recent_30_days`。单条消息可用
`sourceKind` 选择问答依据：`records`（原始记录，默认）、`memo_summary` / `summaries`（用记录的已存
总结作为来源）。回答由服务端根据所选范围内的记录和对话历史调用当前启用的 AI 档案生成，记录不足时返回
“现有记录不足以判断”，不会编造分析。

`messages:stream` 以 Server-Sent Events 流式返回，事件：

- `start`：`{ userMessage, sources }`，用户消息与本轮来源。
- `delta`：`{ text }`，回答的增量片段。
- `done`：`{ message }`，持久化后的完整助手消息。
- `error`：`{ message }`，开流后发生的错误。

开流前的失败（鉴权、校验、未配置 AI）走普通 HTTP 错误码；客户端断开（停止生成）时，服务端用后台
context 持久化已生成的部分回答。

**分支与重新生成**：单轮和流式两条 POST 都支持 `parentId`（新问题挂在哪条消息下，缺省取会话
`headMessageId`）与 `forkOfId`。**重新生成** = 传 `forkOfId`（要重生成的助手消息）且 `content` 为空：
不新建用户消息，而是为该回答所属的问题再生成一个兄弟回答（`forkOfId` 指向原回答，`parentId` 同为该
问题）。消息因此构成一棵树：历史按祖先链构建，互为兄弟的分支互不影响。

```http
POST /api/v1/ask/conversations/{conversation}/head    # body: { messageId }，切换活跃分支叶子
```

`/head` 把会话的活跃叶子指向某条消息（用户切换重生成分支时调用），便于跟随消息挂到正确分支、
刷新后恢复同一分支。

`askMessages.sourceRefs` 是结构化数组，至少包含 `memoId`、`entryDate`、`excerpt` 和 `rank`，供 Web 和 Android Ask 界面跳回来源 memo。流式与单轮路径都写入同一 `askConversations` / `askMessages` sync stream。
