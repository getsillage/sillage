# 同步 API

> 迁移中契约。新的自托管版本使用 `/api/v1/sync` 和 `/api/v1/sync:push`，不再保留旧
> Cloudflare Workers 版本的 `/api/sync`。该契约面向未来 Android 离线客户端设计，支持
> tombstone、mutation id 幂等、逐条冲突返回和附件 metadata 同步扩展。

Protobuf 契约源位于 [`../../proto/api/v1`](../../proto/api/v1)，生成物提交入库：

- Go protobuf / gRPC / Connect / grpc-gateway：`proto/gen/api/v1/`
- OpenAPI：`proto/gen/openapi/openapi.yaml`
- Web TypeScript proto：`web/src/types/proto/`

当前 Connect v1 已注册 `MemoService` 与 `SyncService`。REST v1 与 Connect v1 的 memo 创建、更新、删除、
AI 总结、sync pull 和 sync push 共用同一 service 逻辑；Ask、settings 等 proto 已生成，运行时接入会随后续
里程碑继续补齐。

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
      "summary": "本地生成的记录总结",
      "sentiment": null,
      "provider": "local",
      "model": "local-summary",
      "profileId": "",
      "promptVersion": "memo-summary-v1",
      "sourceMemoIds": "[\"019f03a4-0121-7aaf-8b0a-7af8dc1bf0c7\"]",
      "status": "complete",
      "errorCode": null,
      "startedAt": "2026-06-26T11:15:07Z",
      "finishedAt": "2026-06-26T11:15:07Z",
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
      "content": "根据当前范围内的记录，可以先看这些来源：...",
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
      "model": "local-grounded-answer",
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
- 未来 Android 可离线预生成 memo UUIDv7，并通过 `sync:push` 创建。

## 附件预留

附件字节不进入 sync payload。未来 Android 同步流程是先上传附件字节，再在 sync payload 中同步附件 metadata 与 memo 引用。

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
`hasApiKey` / `keyUnavailable`，不会收到明文 key。

如果更换 `ENCRYPTION_SECRET` 导致旧 key 解不开，服务不会崩溃；相关 profile 会标记为
`keyUnavailable: true`，用户需要重新输入 API key。

## Ask 同步与接口

Ask 当前 REST 接口：

```http
GET /api/v1/ask/conversations
POST /api/v1/ask/conversations
GET /api/v1/ask/conversations/{conversation}/messages
POST /api/v1/ask/conversations/{conversation}/messages
```

新会话支持 `contextScope`：`recent_7_days`、`recent_30_days`、`all`，默认 `recent_30_days`。当前迁移阶段的回答由服务端基于范围内最近 memo 生成本地、带来源的占位回答；记录不足时返回“现有记录不足以判断”，不会编造分析。后续接入真实 provider、流式、停止、重生成和分支时，仍使用同一 `askConversations` / `askMessages` sync stream。

`askMessages.sourceRefs` 是结构化数组，至少包含 `memoId`、`entryDate`、`excerpt` 和 `rank`，供 Web 和未来 Android 客户端跳回来源 memo。
