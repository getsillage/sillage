# 同步 API

`GET /api/v1/sync` 和 `POST /api/v1/sync:push` 供 Android 手动同步及离线客户端收敛数据。契约源是 `proto/api/v1/sync_service.proto`；REST 实现在 `server/sync_routes.go`，共享业务逻辑在 `server/api_service.go`。

## 约束

- 请求使用 `Authorization: Bearer <access_token>`。
- cursor 是服务端生成的不透明字符串，客户端只能原样保存和回传。
- 删除以 `deletedAt` tombstone 同步，不立即物理清除。
- 推送按变更逐条处理，一条失败不会回滚整批。
- `mutationId` 标识一次逻辑修改并关联已保存的处理结果；资源 `version` 用于检测并发冲突。
- 当前只有 memo 支持推送；附件、AI 派生数据和 Ask 数据只支持拉取。

## 拉取

```http
GET /api/v1/sync?cursor=<opaque>&limit=200
```

省略 cursor 表示从头拉取。limit 默认和最大值均为 200。

响应包含五路资源：

- `memos`
- `attachments` metadata
- `memoAi`
- `askConversations`
- `askMessages`

每一路使用独立的 `(updated_at, id)` 位置和 `limit + 1` lookahead。只要任一路还有数据，`hasMore` 就为 `true`；客户端应继续请求 `nextCursor`，直到 `hasMore=false`。

```json
{
  "memos": [
    {
      "id": "019f03a4-0121-7aaf-8b0a-7af8dc1bf0c7",
      "content": "今天的记录",
      "entryDate": "2026-06-26",
      "version": 1,
      "favoritedAt": null,
      "archivedAt": null,
      "updatedAt": "2026-06-26T11:15:07Z",
      "deletedAt": null
    }
  ],
  "attachments": [],
  "memoAi": [],
  "askConversations": [],
  "askMessages": [],
  "cursor": "opaque",
  "nextCursor": "opaque",
  "hasMore": true
}
```

客户端应先把一页内的资源和 tombstone 原子合并到本地，再持久化 `nextCursor`。不能在资源写入失败后单独推进 cursor。

## 推送

```http
POST /api/v1/sync:push
Content-Type: application/json
```

一次最多提交 200 条变更。当前支持 `resourceType=memo` 和 `create`、`update`、`delete`：

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
        "favorited": false,
        "archived": false
      }
    }
  ]
}
```

每条结果的 `status` 为：

- `applied`：写入成功，`resource` 是服务端规范化资源；
- `conflict`：`baseVersion` 过期，返回 `serverResource`、`clientVersion` 和 `serverVersion`；
- `rejected`：字段、资源或动作非法，返回稳定的 `reason` 和可读 `message`。

首次结果成功保存后，同一账号重复提交相同 `mutationId` 会重放该结果，并标记 `idempotent=true`。资源写入与结果保存当前不在同一事务中，因此这不是 exactly-once 保证；收到 `reason=internal` 等不确定结果时，应先拉取服务端状态再决定是否重试。网络重试仍应复用原 ID，新的逻辑修改必须生成新 ID。

## Memo 语义

- ID 可由客户端生成；create 的安全重试依赖同一 `mutationId`，不能只依赖资源 ID。
- `entryDate` 是用户日期，格式为 `YYYY-MM-DD`。
- update 和 delete 的 `baseVersion` 必须大于 0。
- 正文、日期、收藏、归档和删除都会增加 `version`。
- `favorited` 是规范收藏字段。服务端仅为旧客户端兼容读取已弃用的 `pinned`；两者同时出现时以 `favorited` 为准。
- AI 总结不改变 memo 的 `version` 或 `updatedAt`。

遇到 `conflict` 时，客户端应保留本地修改并展示服务端资源，由用户合并、放弃或基于新版本重新提交；不能自动覆盖或对旧 `baseVersion` 无限重试。

## 附件边界

同步只拉取附件 metadata，附件字节不进入 payload，也不接受附件 push。在线上传使用 `POST /api/v1/attachments`，认证下载使用 `/file/attachments/{uid}/{filename}`。Android 离线附件的完整同步尚未实现。

## 修改与验证

契约变化必须同步 Proto、生成物、REST/Connect、Android 同步客户端和测试；共享资源结构受影响时再同步 Web。验证流程统一见[贡献指南](../../../CONTRIBUTING.md)。
