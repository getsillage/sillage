# 同步 API

> 迁移中契约。新的自托管版本使用 `/api/v1/sync` 和 `/api/v1/sync:push`，不再保留旧
> Cloudflare Workers 版本的 `/api/sync`。该契约面向未来 Android 离线客户端设计，支持
> tombstone、mutation id 幂等、逐条冲突返回和附件 metadata 同步扩展。

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
- 响应按资源类型分组，当前阶段已实现 `memos`、`attachments` metadata 和 `memoAi` 派生数据；后续里程碑会扩展 `summaries`、`ask_conversations` 和 `ask_messages`。
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
