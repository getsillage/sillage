# 同步 API

面向非 web 客户端(例如移动 app)的增量同步端点。它返回自某个游标以来的所有变更——**包括软删除的行**——因此客户端可以用一个可重复调用把本地的 Sillage 镜像保持最新。

> 状态:目前**只读**。记录已带有用于乐观并发的 `version`(见 [写回(未来)](#写回未来)),但写入端点尚未纳入本 API。

## 鉴权

与守护 web 应用相同的单密码会话也守护本端点。

- 通过向 `/login` 发送 `POST` 密码进行鉴权;服务端会设置一个 **HttpOnly** 会话 cookie(不透明 id;密钥永不到达客户端)。
- 每次请求 `/api/sync` 都要带上该 cookie。
- 未鉴权的请求会被重定向(`302 → /login`)。把任何非 `200` 响应都当作“未鉴权 / 需要重新登录”,而不是同步数据。

## 端点

```
GET /api/sync?cursor=<token>
```

### `cursor` 令牌

| 形式 | 含义 |
|------|------|
| 省略 / 为空 | 全量快照(从头开始的全部数据) |
| `?cursor=<token>` | 上一次响应返回的**不透明**令牌 |

游标是一个**不透明字符串**——原样回传上一次响应里的 `cursor`,并把其内容视为私有。(其内部是按流编码的 base64 keyset `(updatedAt, id)`;格式非法的令牌会被当作“从头开始”,触发一次全量重新同步,而不是报错。)

相对于某个游标,投递是**每个变更恰好一次**:重发上一个游标不会重复投递你已有的行;而且——因为游标键是 `(updatedAt, id)` 而非仅 `updatedAt`——共享同一毫秒的行在翻页边界绝不会被跳过。

## 响应

`200 OK`,`application/json`:

```jsonc
{
  "entries": [ /* EntryDto,变更最早的在前 */ ],
  "attachments": [ /* AttachmentDto */ ],
  "cursor": "eyJlbnRyaWVzIjp...", // 不透明;下次作为 ?cursor= 回传
  "hasMore": false                // true => 某一页已满;请立即再次拉取
}
```

- `entries` 与 `attachments` 各自按 `(updatedAt, id)` 升序排列。
- 每页最多返回 **200** 条 entries 和 **200** 条 attachments。当 `hasMore` 为 `true` 时,立即用返回的 `cursor` 再次请求,把积压排空后再进入空闲。

### EntryDto

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | string | UUIDv7(可按时间排序);跨客户端稳定 |
| `entryDate` | string | `YYYY-MM-DD`,该条目“所属”的日历日期 |
| `title` | string | |
| `body` | string | Markdown 明文 |
| `kind` | string | `fragment` / `note` / `draft` |
| `noteType` | string \| null | `daily` / `weekly` / `monthly` / `topic` / `freeform`;非笔记通常为 null |
| `mood` | number \| null | 1–5 |
| `moodText` | string \| null | 自由文本细腻感受 |
| `weather` | string \| null | |
| `location` | string \| null | 地点 |
| `people` | string[] | 人物 |
| `relationships` | string[] | 关系 |
| `isPinned` | boolean | |
| `utcOffsetMinutes` | number \| null | 保存时写入者的 UTC 偏移;用于解析 `entryDate` 的本地含义 |
| `metadata` | object \| null | 向前兼容的客户端附加字段;由存储的 JSON 解析而来 |
| `version` | number | 乐观并发令牌;每次内容编辑递增 |
| `tags` | string[] | 已排序、去重的标签名 |
| `ai` | object | `{ summary: string \| null, sentiment: string \| null }`(机器派生) |
| `createdAt` | string | ISO 8601 |
| `updatedAt` | string | ISO 8601;游标追踪的字段 |
| `deletedAt` | string \| null | **非空 ⇒ 墓碑**:在本地删除该条目 |

### AttachmentDto

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | string | UUIDv7 |
| `entryId` | string \| null | 所属条目;若在其条目存在之前上传则为 null |
| `url` | string | `"/attachments/<id>"`——受会话守护;解密并流式返回字节 |
| `filename` | string | |
| `contentType` | string | |
| `size` | number | 字节数(明文) |
| `sha256` | string \| null | 明文的十六进制摘要——用于完整性校验与去重 |
| `width` / `height` | number \| null | 已知时为图片尺寸 |
| `status` | string | `"stored"`(上传中为 `"pending"`) |
| `createdAt` / `updatedAt` | string | ISO 8601 |
| `deletedAt` | string \| null | 非空 ⇒ 墓碑(字节已回收) |

内部的 R2 对象 key **永不**暴露;通过 `url` 获取字节。

## 客户端同步算法

```text
cursor = load_saved_cursor()            // 首次运行为 null
loop:
  res = GET /api/sync?cursor=cursor       // cursor 为 null 时省略该参数
  for entry in res.entries:
    if entry.deletedAt != null: delete_local(entry.id)
    else:                       upsert_local(entry)   // 含其 tags + ai
  for att in res.attachments:
    if att.deletedAt != null: delete_local_attachment(att.id)
    else:                     upsert_local_attachment(att)
  cursor = res.cursor
  save_cursor(cursor)
  if not res.hasMore: break               // 已追平
```

之后按你喜欢的节奏轮询(前台刷新、推送唤醒、定时器);每次调用只传送 `cursor` 之后变更的内容。

### 注意事项与保证

- **墓碑,而非空缺。** 删除以带非空 `deletedAt` 的行返回,而不是悄无声息地消失,因此离线客户端可以镜像删除。(若服务端曾执行硬清除,则属例外。)
- **AI 更新是安静的。** 重新生成总结只写 `entry_ai` 侧表,**不会** bump `entries.updatedAt`——所以总结刷新本身不会重新投递该条目。如需立刻拿到最新 `ai` 字段,请单独重新获取该条目。
- **Keyset 游标。** 分页键是 `(updatedAt, id)`,因此共享同一毫秒的行会被正确翻页而非跳过。该游标仍是按流的高水位线,面向本单用户 Sillage 设计,而非并发多写者的扇出场景。

## 示例

```bash
# 1. 登录,保存会话 cookie。
curl -c jar.txt -X POST https://<host>/login \
  --data-urlencode "password=$SILLAGE_PASSWORD"

# 2. 全量快照。
curl -b jar.txt "https://<host>/api/sync"

# 3. 增量:回传上一次响应里的不透明 `cursor`。
curl -b jar.txt "https://<host>/api/sync?cursor=eyJlbnRyaWVzIjp..."
```

## 写回(未来)

目前还没有写入端点,但 schema 已为此准备就绪:

- 客户端可在本地生成 UUIDv7 `id`(离线创建,无需服务端往返)。
- 回传你上次读到的 `version`;服务端的 `updateEntry` 会以**冲突**(当前版本 vs 期望版本)拒绝陈旧写入,而不是覆盖更新的副本。处理方式:重新获取后再次应用。
- 当某个写入者省略 `metadata` / `utcOffsetMinutes` 时,它们会被保留,因此来自某个客户端的部分更新不会抹掉另一个客户端的字段。
