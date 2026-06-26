# Sillage memos 风格自托管迁移计划

本文档定义 Sillage 从 Cloudflare Workers 专用架构迁移到 memos 风格自托管架构的完整计划。后续 agent 执行迁移时，应优先参考本机 memos 仓库：

```text
~/Projects/memos
```

## 产品定位与设计边界

Sillage 的目标不是复刻 memos 的完整产品形态，而是借鉴 memos 的自托管 Go 单体架构，重写为 Sillage 自己的私人记录产品。

一句话定位：

> Sillage 是一个单人私密记录与 AI 反思工具，用来低成本记录日常片段、状态变化和身边小事，并通过 AI 帮助用户总结、回顾、分析状态与获得基于记录的行动建议。

设计边界：

- **记录要轻**：默认是短 memo 风格，允许很碎、很随意；长文也支持，但不把长文编辑作为主体验。
- **写作优先于整理**：打开后应能马上写，不强迫标题、分类、标签、模板或复杂元数据。
- **历史要能回看也能看趋势**：既能找回某一天发生了什么，也能看到一段时间内状态和主题的变化；首版优先日期时间线、日历/活动热力图、周/月 AI 总结。
- **AI 是核心但必须有依据**：AI 同时承担“日记总结助手”“状态分析助手”“生活决策顾问”三种角色；默认入口偏向总结和状态分析，建议必须基于原始 memo，不做心理诊断或凭空判断。
- **Ask 是基于记录的对话工作台**：体验尽量接近 ChatGPT Web，但上下文默认由用户选择范围，例如最近 7 天、最近 30 天、自定义日期范围、手动选中的 memo；全量历史只能作为明确选择，不作为每次默认上下文。
- **附件是记录现场的辅助材料**：支持图片和任意文件，但不发展成复杂网盘。
- **memos 是架构参考，不是产品目标**：不要引入 memos 的公开探索、社交、多人协作、标签、relation、reaction 等会让 Sillage 偏离私人记录的软件能力。

Sillage 不应该变成：

- 知识库。
- 任务或项目管理系统。
- 正式日记写作平台。
- 情绪打卡或心理诊断工具。
- 社交发布平台。
- 文件网盘。

## 目标

- 将 Sillage 迁移为单体自托管服务：Go 后端 + Vite React 前端 + SQLite 文件数据库 + 本地文件系统附件。
- 采用“完全重写为 memos 风格 Go 单体”的路线，不保留 React Router SSR、Workers runtime 或旧 TypeScript 服务端作为中间态。
- 产品定位保持 Sillage 当前“单人私密空间”：记录、历史、问答、设置是主结构；不加入公开探索、公开 memo、RSS、sitemap、公开分享等公开内容能力。
- 底层架构完全参考 memos；可引入 memos 中适合私密个人空间的优秀功能，但不能改变 Sillage 的私密 AI 记录产品定位。
- 认证改为 memos 风格首个账号初始化，不再使用 `APP_PASSWORD_HASH` 单口令模式；初始化后实例只允许这一个账号使用，不引入多用户、成员管理或角色管理。
- 删除 Cloudflare Workers、D1、R2、KV、Wrangler 作为主运行路径。
- 使用 Protobuf 作为 API 契约源，同时提供 REST API v1 和 gRPC/Connect API v1。
- 使用 Docker 作为主部署方式，所有持久状态收敛到单一数据目录 `/var/opt/sillage`。
- 本阶段不迁移现有 Cloudflare 数据，按全新部署处理。
- 本阶段完全移除内置备份功能，不实现替代备份。
- 首版不提供备份 UI、定时备份、下载备份接口、`sillage export` 或 `sillage doctor` 等备份/导出 CLI；只提供运维文档说明。

## 已确认决策

- **迁移路线**：完全重写为 memos 风格 Go 单体，不做旧架构渐进兼容。
- **产品模型**：保持 Sillage 单人私密空间，不加入公开功能；可参考 memos 的数据层、API 层、附件层、前端工程组织和部分私密场景可用功能。
- **产品定位**：低压力私人生活片段记录 + AI 反思；AI 同时承担总结、状态分析和基于记录的建议，不把产品做成知识库、任务系统或正式写作平台。
- **认证模型**：使用首个账号初始化流程；废弃 `APP_PASSWORD_HASH` 单口令。初始化后不允许注册第二个账号，也不提供成员管理。
- **附件 URL**：改为 memos 风格 `/file/attachments/{uid}/{filename}`，不兼容旧 `/attachments/:id`。
- **同步 API**：直接以新的 `/api/v1/sync` Protobuf 契约为准，不保留旧 `/api/sync` 兼容别名。
- **数据迁移**：不迁移 Cloudflare 旧数据；新 SQLite 数据库从空库初始化。
- **AI 能力**：保留 Sillage 核心 AI 功能，包括单条 memo AI 总结、周期/主题总结、AI 设置档案，以及 ChatGPT Web 风格的 `/ask` 多轮对话工作台。
- **前端结构**：保留 Sillage 当前“记录 / 历史 / 问答 / 设置”的产品结构，底层按 memos 重写。
- **附件存储**：改为 memos 风格本地明文文件，不再对附件做应用层 AES-GCM 加密。
- **同步模型**：`/api/v1/sync` 从第一版开始支持双向同步、写入和冲突解决；同步范围包括当前账号资料与设置；删除语义使用 `deleted_at` tombstone。
- **生成代码**：Go protobuf、gRPC、grpc-gateway、Connect 代码提交入库；TypeScript proto 生成物也提交入库。
- **前端包管理器**：`web/` 使用 pnpm，参考 memos 的 web 工程组织。
- **首版前端功能**：引入附件库、日历/活动热力图、memo 置顶、memo 归档、Markdown 所见即所得编辑器。
- **不引入标签**：Sillage 定位是个人记录 + AI 总结，不引入标签等会显著增加复杂度的组织功能。
- **memo 日期模型**：同时保留 `created_at` 和 `entry_date`；`created_at` 是创建时间，`entry_date` 是记录归属的日历日期。
- **初始化体验**：首次启动无账号时，访问首页跳转到创建唯一账号的初始化页。
- **Secret 策略**：`SESSION_SECRET` 和 `ENCRYPTION_SECRET` 未配置时自动生成并持久化，不要求用户手动提供。
- **Docker 命名**：镜像名、compose 服务名和容器名使用 `sillage`。
- **反向代理**：Sillage 自身只提供 HTTP 服务；HTTPS 由 Cloudflare Tunnel、反向代理或平台层处理。服务端需要正确处理 forwarded headers 和 cookie secure 策略。
- **可观测性**：使用 Go `slog` 结构化 JSON 日志；启用请求日志 middleware；提供 `/healthz` 和 `/readyz`。
- **安全限制**：保留登录失败限流；上传文件名必须清理并防路径穿越；AI 请求必须限制并发。
- **命名边界**：后端、数据库、proto/API 使用 `memo`；前端中文 UI 显示“记录”。

## 待确认决策

- 暂无。若未来要引入多人、公开分享、任务管理、知识库、标签体系等与当前定位冲突的能力，必须先重新做产品定位评审，再补充权限、同步和数据模型设计。

## 产品范围

Sillage 迁移后仍是个人记录软件，核心是低摩擦记录、历史回看、AI 总结、状态分析和基于记录的问答。memos 的优秀功能只能在不破坏这个定位的前提下引入。

首版引入：

- 附件库页面，方便集中查看和管理上传过的文件。
- 日历/活动热力图，强化个人记录回看和状态趋势观察。
- memo 置顶。
- memo 归档。
- Markdown 所见即所得编辑器。

首版不引入：

- 标签。
- 公开探索。
- 公开 memo 详情。
- RSS / sitemap。
- 公开分享链接。
- reaction。
- memo relation。

说明：

- 不引入标签是明确产品决策。不要因为 memos 支持标签，就把 `#tag` 解析、标签管理、标签筛选带入 Sillage 首版。
- 置顶和归档是轻量组织能力，允许引入。
- 附件库和活动热力图服务于个人回看，不改变私密空间定位。
- Ask 的上下文选择是核心交互之一，应避免每次默认读取全部历史导致噪音、成本和隐私边界不清。

## memos 参考路径

执行迁移时重点参考以下文件和目录：

```text
~/Projects/memos/cmd/memos/main.go
~/Projects/memos/internal/profile/profile.go
~/Projects/memos/server/server.go
~/Projects/memos/server/router/frontend/frontend.go
~/Projects/memos/server/router/fileserver/fileserver.go
~/Projects/memos/server/router/api/v1/v1.go
~/Projects/memos/server/router/api/v1/connect_handler.go
~/Projects/memos/server/router/api/v1/connect_services.go
~/Projects/memos/store/store.go
~/Projects/memos/store/driver.go
~/Projects/memos/store/migrator.go
~/Projects/memos/store/db/db.go
~/Projects/memos/store/db/sqlite/sqlite.go
~/Projects/memos/store/migration/sqlite/LATEST.sql
~/Projects/memos/store/attachment.go
~/Projects/memos/server/router/api/v1/attachment_service.go
~/Projects/memos/proto/buf.yaml
~/Projects/memos/proto/buf.gen.yaml
~/Projects/memos/proto/api/v1/*.proto
~/Projects/memos/web/package.json
~/Projects/memos/web/vite.config.mts
~/Projects/memos/web/src/connect.ts
~/Projects/memos/web/src/router/index.tsx
~/Projects/memos/scripts/Dockerfile
~/Projects/memos/scripts/entrypoint.sh
~/Projects/memos/scripts/compose.yaml
```

## 目标架构

```text
cmd/sillage/                  # Go CLI 启动入口
internal/profile/             # 运行配置、数据目录、DSN 默认值
server/                       # Echo HTTP server
  auth/                       # token/session 鉴权
  router/
    api/v1/                   # Protobuf service 实现 + REST/Connect 注册
    fileserver/               # 附件下载/流式读取/权限/MIME 防护
    frontend/                 # Go embed Vite build 产物
store/                        # 业务存储层
  driver.go                   # SQLite driver 接口
  store.go                    # Store 聚合入口
  db/sqlite/                  # SQLite 实现
  migration/sqlite/           # SQL 迁移与 LATEST.sql
proto/
  api/v1/                     # REST/gRPC API 契约
  store/                      # 复杂设置 payload 的内部 proto，可选
web/                          # React + TypeScript + Vite SPA
scripts/
  Dockerfile
  entrypoint.sh
  compose.yaml
```

## 运行时与数据目录

默认持久目录：

```text
/var/opt/sillage
```

目录布局：

```text
/var/opt/sillage/sillage.db
/var/opt/sillage/assets/attachments/
/var/opt/sillage/.thumbnail_cache/
/var/opt/sillage/runtime/
```

环境变量：

```text
SILLAGE_ADDR=
SILLAGE_PORT=5231
SILLAGE_DATA=/var/opt/sillage
SILLAGE_DSN=/var/opt/sillage/sillage.db
SILLAGE_MAX_UPLOAD_MB=30
SILLAGE_INSTANCE_URL=      # 可选；未设置时从请求和 forwarded headers 推断
SILLAGE_LOG_FORMAT=json
SILLAGE_LOG_LEVEL=info
SESSION_SECRET=...      # 可选；未设置时自动生成并持久化
ENCRYPTION_SECRET=...   # 可选；未设置时自动生成并持久化
```

参考 memos 的配置读取方式：

- `viper.SetEnvPrefix("memos")`
- `viper.SetEnvKeyReplacer(strings.NewReplacer("-", "_"))`
- `viper.AutomaticEnv()`

Sillage 对应使用 `SILLAGE_` 前缀。

## 横切工程约束

以下约束贯穿所有阶段，后续 agent 不能只实现“能跑通”的最小路径而跳过这些边界。

### ID、时间与版本

- 业务聚合 ID 使用可客户端生成、可按时间排序的稳定 ID，推荐 UUIDv7；如果参考 memos 使用 `uid`，也必须保证服务端和未来离线客户端都能无冲突创建。
- 服务端创建的资源必须允许客户端在 sync push 时提交预生成 ID，便于离线创建后同步。
- `created_at`、`updated_at`、`deleted_at` 在数据库中统一使用 UTC Unix milliseconds 或 SQLite INTEGER；API 层统一输出 RFC3339/Protobuf timestamp，不混用本地时区字符串。
- `entry_date` 是用户语义日期，建议存 `YYYY-MM-DD` 文本，不附带时区；按用户当前设置的本地日期解释。
- `updated_at` 由服务端写入，客户端本地时间只能作为辅助字段，不可作为冲突判断的权威时间。
- 可编辑聚合需要 `version` 或等价 CAS 字段；memo 正文、entry_date、归档、置顶等会影响用户内容的变更必须走版本检查。
- AI 派生数据、缩略图缓存、搜索索引不应修改 memo 的 `updated_at` 和 `version`。

### SQLite 与迁移

- SQLite 连接必须启用 `foreign_keys=ON`，并设置 `busy_timeout`、`journal_mode=WAL`。
- 所有跨表写入必须放在事务中，例如创建 memo + revision、上传附件元数据 + memo 引用、写 ask message + source references。
- 高并发写入应按 SQLite 单写者模型设计，后台 AI summary 和缩略图生成不要长时间占用写事务。
- 必须为 sync 查询建立复合索引，例如 `(updated_at, id)` 或 `(updated_at, uid)`；为 `entry_date`、`deleted_at`、`archived_at`、`pinned_at` 建必要索引。
- FTS 表和触发器属于本地派生索引，不进入 sync payload。
- 迁移过程必须幂等、可重复启动；容器启动时如果迁移失败，服务应退出并在日志中说明迁移版本和错误。
- schema version 只保存在 SQLite 内，不依赖外部文件；`LATEST.sql` 只用于新库初始化，后续版本迁移单独存放。

### API 语义

- REST 和 Connect/gRPC 必须共用同一 service 实现，不允许出现 REST 行为和 Connect 行为不一致。
- API 错误需要统一结构：机器可读 code、用户可读 message、可选 field violations、request id。
- REST 状态码约定：
  - `400`：请求字段非法。
  - `401`：未登录或 token 无效。
  - `403`：已登录但不允许执行。
  - `404`：资源不存在、已删除或不应暴露。
  - `409`：版本冲突。
  - `413`：上传超过限制。
  - `429`：限流。
  - `500`：未预期服务端错误。
- PATCH 类接口必须使用 update mask 或明确 patch schema，避免空字段误覆盖。
- 列表接口必须有分页上限，默认 50，最大 200；不允许无界返回全部数据。
- OpenAPI YAML 要与 proto 生成物一起提交，供未来客户端和调试工具使用。

### 安全默认值

- 生产环境 CORS 默认只允许同源；不得配置 `Access-Control-Allow-Origin: *` 且带 credentials。
- Cookie 默认 `HttpOnly`、`SameSite=Lax` 或更严格；HTTPS/forwarded HTTPS 下必须 `Secure`。
- 如果 Web 使用 refresh cookie，所有会修改状态的 cookie-auth REST 请求必须具备 CSRF 防护。推荐采用同源限制 + CSRF token header；Connect/Bearer token 请求也不能绕过认证。
- access token 只放内存，不写入 localStorage；刷新页面后通过 refresh cookie 静默换取新 access token。
- refresh token 只保存哈希值，支持轮换；登出时服务端失效当前 refresh token/session。
- 密码哈希使用成熟算法，推荐 Argon2id；若参考 memos 使用 bcrypt，也必须设置足够成本并保留算法版本字段，便于后续升级。
- 日志禁止记录密码、refresh token、access token、AI API key、完整 memo 正文、完整 ask prompt、附件原始内容。
- AI API key、`SESSION_SECRET`、`ENCRYPTION_SECRET` 在日志和错误返回中必须脱敏。
- HTTP 安全响应头至少包含 `X-Content-Type-Options: nosniff`；前端静态页建议配置基本 CSP，避免 Markdown/附件渲染引入 XSS 风险。

### 后台任务

- 后台任务需要统一 runner，支持 context cancellation、并发限制、panic recovery 和优雅退出。
- 容器收到 SIGTERM 后，停止接收新请求，取消 ask streaming 和未开始的后台任务，等待正在写 DB 的短事务完成。
- AI 总结、缩略图生成、FTS 重建、孤儿文件清理都必须可重试；失败只记录状态，不应导致用户保存 memo 失败。

## 阶段 1：建立 Go 单体骨架

新增 Go module 和启动入口：

```text
go.mod
cmd/sillage/main.go
internal/profile/profile.go
```

实现要求：

- CLI 支持 env 和 flag。
- 默认 driver 固定为 `sqlite`，不引入 MySQL/Postgres。
- `Profile.Validate()` 负责：
  - 默认数据目录选择 `/var/opt/sillage`。
  - 本地开发没有 `/var/opt/sillage` 时允许使用当前目录或显式 `SILLAGE_DATA`。
  - 创建数据目录。
  - 默认 DSN 为 `$SILLAGE_DATA/sillage.db`。
- 启动链路：
  - 初始化 profile。
  - 创建 SQLite driver。
  - 创建 store。
  - 自动迁移。
  - 创建 server。
  - 启动 Echo。
  - SIGINT/SIGTERM 优雅退出。

参考：

```text
~/Projects/memos/cmd/memos/main.go
~/Projects/memos/internal/profile/profile.go
```

## 阶段 2：Store 与 SQLite

新增：

```text
store/store.go
store/driver.go
store/db/db.go
store/db/sqlite/sqlite.go
store/migrator.go
store/migration/sqlite/LATEST.sql
```

SQLite 要求：

- 使用 Go `database/sql`。
- 优先参考 memos 使用 `modernc.org/sqlite`，便于静态构建。
- 打开 SQLite 时设置：
  - `busy_timeout(10000)`
  - `journal_mode(WAL)`
  - `mmap_size(0)`
- 只实现 SQLite driver。

数据表：

```text
system_setting
account
account_setting
memo
attachments
summaries
ask_conversations
ask_messages
runtime_kv
```

说明：

- `memo`、`account`、`account_setting` 参考 memos 的 memo/user/user_setting 存储思路，但产品上只有一个账号；默认所有 memo 都属于私密空间，不实现公开探索或公开详情。
- Sillage 旧 `entries` 语义并入 `memo`，不要在新 schema 中继续保留 `entries` 命名。
- 不使用 `entry` 或 `record` 作为后端/API/数据库主模型名；这些只允许作为 UI 文案中的“记录”概念出现。
- `summaries`、`ask_conversations`、`ask_messages` 是 Sillage 相对 memos 的扩展模型。
- 不引入 `memo_relation`、`reaction`、`memo_share` 等 memos 社交/关系模型。
- `runtime_kv` 只用于短期 runtime 状态或简单设置；如果某类数据有稳定业务语义，应建专表或使用 proto payload 存入 setting 表。
- `account` 表只保存唯一账号；不提供创建第二个账号、成员列表、角色切换或团队权限。
- 所有需要进入离线同步的业务表必须具备 `updated_at` 和 `deleted_at`。不要用 memos 的 `row_status=ARCHIVED` 承担离线删除语义；归档如果需要，应是独立业务字段。
- `memo` 需要支持 `pinned_at` 或 `pinned`，以及 `archived_at`。
- `memo` 必须同时具备：
  - `created_at`：服务端创建时间。
  - `updated_at`：服务端更新时间。
  - `deleted_at`：离线同步 tombstone。
  - `entry_date`：用户可编辑的记录归属日期，格式建议为 `YYYY-MM-DD`。
- 日历、活动热力图、历史回看、周期总结默认基于 `entry_date`，不是 `created_at`。
- 普通列表排序可按 `pinned_at DESC`、`entry_date DESC`、`created_at DESC`。
- 不要新增 tag/tag_relation 表，也不要在 proto 中暴露标签管理能力。
- `system_setting` 负责保存实例级 secret、schema version、上传大小限制等；自动生成的 `SESSION_SECRET` 和 `ENCRYPTION_SECRET` 应持久化在实例设置中或数据目录下受权限保护的 runtime 文件中，优先参考 memos 的 instance basic setting。

`runtime_kv`：

```sql
CREATE TABLE runtime_kv (
  namespace TEXT NOT NULL,
  key TEXT NOT NULL,
  value TEXT NOT NULL,
  expires_at INTEGER,
  PRIMARY KEY (namespace, key)
);
CREATE INDEX idx_runtime_kv_expires_at ON runtime_kv (expires_at);
```

迁移要求：

- 新库直接应用 `LATEST.sql`。
- 后续迁移按版本目录管理。
- schema version 存在 SQLite 中，不依赖外部文件。

参考：

```text
~/Projects/memos/store/store.go
~/Projects/memos/store/driver.go
~/Projects/memos/store/db/sqlite/sqlite.go
~/Projects/memos/store/migrator.go
~/Projects/memos/store/migration/sqlite/LATEST.sql
```

## 阶段 3：后端 Server

新增 Echo server：

```text
server/server.go
server/router/api/v1/
server/router/fileserver/
server/router/frontend/
server/auth/
```

Server 挂载顺序：

1. recovery/cors/request log middleware。
2. `/healthz`。
3. `/readyz`。
4. 前端静态资源。
5. 文件服务 `/file/...`。
6. REST API `/api/v1/...`。
7. Connect API `/sillage.api.v1.*`。
8. SPA fallback。

注意：

- 前端静态服务必须跳过 `/api`、`/file`、Connect endpoint。
- 文件服务应在 API gateway 前注册，便于处理 Range。
- shutdown 时关闭长连接、后台任务和 DB。
- 应用自身只监听 HTTP，不直接负责 TLS。
- 部署在 Cloudflare Tunnel 或反向代理后时，读取 `X-Forwarded-Proto`、`X-Forwarded-Host`、`X-Forwarded-For` 以推断外部 URL 和客户端 IP。
- 如果设置了 `SILLAGE_INSTANCE_URL`，优先使用该值生成外部 URL；否则从请求 host/proto 推断。
- Cookie `Secure` 策略：
  - `localhost` / `127.0.0.1` / 明确 HTTP 开发访问允许非 Secure cookie。
  - forwarded proto 或请求协议为 HTTPS 时使用 Secure cookie。
- 反向代理信任范围应保守实现：只消费标准 forwarded headers，不基于它们绕过认证。
- 日志使用 Go `slog`：
  - Docker/生产默认 JSON。
  - 本地开发可选 text。
  - 记录 request id、method、path、status、duration、client ip、account id（如已认证）。
- `/healthz` 只表示进程存活，不依赖数据库。
- `/readyz` 检查 SQLite 可打开/可 ping，必要时检查迁移已完成。

参考：

```text
~/Projects/memos/server/server.go
~/Projects/memos/server/router/frontend/frontend.go
~/Projects/memos/server/router/fileserver/fileserver.go
~/Projects/memos/server/router/api/v1/v1.go
```

## 阶段 4：Protobuf API 契约

新增：

```text
proto/buf.yaml
proto/buf.gen.yaml
proto/api/v1/common.proto
proto/api/v1/auth_service.proto
proto/api/v1/memo_service.proto
proto/api/v1/attachment_service.proto
proto/api/v1/account_service.proto
proto/api/v1/settings_service.proto
proto/api/v1/summary_service.proto
proto/api/v1/ask_service.proto
proto/api/v1/sync_service.proto
```

生成目标：

- Go protobuf。
- Go gRPC server interface。
- Go Connect handler。
- grpc-gateway REST handler。
- OpenAPI YAML。
- TypeScript protobuf 到 `web/src/types/proto`。

生成物入库策略：

- `proto/gen/**` 提交入库。
- `web/src/types/proto/**` 提交入库。
- `proto/gen/openapi.yaml` 提交入库。
- CI 和 Docker build 不应依赖临时生成代码才能编译，但需要提供 `proto:generate` 命令用于更新契约。

参考 `buf.gen.yaml`：

```text
~/Projects/memos/proto/buf.gen.yaml
```

REST v1 建议路径：

```text
POST   /api/v1/auth/signin
POST   /api/v1/auth/signout
POST   /api/v1/auth/refresh
GET    /api/v1/auth/me

GET    /api/v1/account
PATCH  /api/v1/account

GET    /api/v1/memos
POST   /api/v1/memos
GET    /api/v1/memos/{memo}
PATCH  /api/v1/memos/{memo}
DELETE /api/v1/memos/{memo}
POST   /api/v1/memos/{memo}:archive
POST   /api/v1/memos/{memo}:unarchive
POST   /api/v1/memos/{memo}:pin
POST   /api/v1/memos/{memo}:unpin

POST   /api/v1/attachments
GET    /api/v1/attachments/{attachment}
DELETE /api/v1/attachments/{attachment}

GET    /api/v1/settings/ai
PATCH  /api/v1/settings/ai

GET    /api/v1/sync
POST   /api/v1/sync:push
```

兼容要求：

- 不要求兼容旧 Web 路径、旧 `/attachments/:id`、旧 `/api/sync`。
- 新客户端和未来 Android 客户端直接使用 `/api/v1/sync` 与 `/api/v1/sync:push`。
- 不提供公开 REST 读取、公开分享、RSS、sitemap 等公开内容接口。
- 旧备份 URL 返回 404。

参考 proto 风格：

```text
~/Projects/memos/proto/api/v1/memo_service.proto
~/Projects/memos/proto/api/v1/attachment_service.proto
~/Projects/memos/proto/api/v1/auth_service.proto
```

## 阶段 4.1：双向同步与冲突解决

同步不是后续附加能力，而是首版 API 契约的一部分。未来 Android 客户端从一开始要能读写并处理离线冲突。

同步 API：

```text
GET  /api/v1/sync?cursor=<opaque>
POST /api/v1/sync:push
```

游标设计：

- cursor 必须是不透明字符串，客户端不能依赖内部格式。
- cursor 内部至少包含每个 stream 的 `(updated_at, id)` 位置，避免某一类资源过多时阻塞其他资源同步。
- 服务端返回 `next_cursor` 和 `has_more`；客户端在 `has_more=true` 时继续拉取。
- 首版同步 response 应按聚合类型分组返回，便于未来 Android 客户端逐类应用。
- sync 拉取必须包含 tombstone；删除资源也要返回最小必要字段：id、deleted_at、updated_at、version。
- sync 查询必须稳定排序，建议 `(updated_at ASC, id ASC)`。

同步范围：

- `account`：唯一账号资料。
- `account_setting`：外观、偏好、AI 设置视图等；AI API key 明文永不下发。
- `system_setting`：客户端需要知道的实例级设置；服务端密钥类设置不同步。
- `memo`。
- `attachments` 元数据；附件字节通过 fileserver 上传/下载，不直接塞进 sync payload。
- `summaries`。
- `ask_conversations`。
- `ask_messages`。
- memo AI 派生数据。

同步字段要求：

- 每个同步聚合必须有稳定 `id` 或 `uid`。
- 每个同步聚合必须有 `created_at`、`updated_at`、`deleted_at`。
- `deleted_at IS NOT NULL` 是 tombstone，客户端必须据此删除本地镜像。
- tombstone 保留策略需后续单独定义；首版不要硬删除会被离线客户端感知的聚合。

推送语义：

- 客户端提交本地变更列表，每条变更携带：
  - 聚合类型。
  - 聚合 id。
  - 客户端已知的 base version 或 base updated_at。
  - patch 或完整资源。
  - 本地变更时间。
- 每条变更必须带 `mutation_id`，服务端用它实现幂等；同一账号下重复提交同一 `mutation_id` 必须返回第一次处理结果。
- 服务端逐条应用，逐条返回 `applied`、`conflict`、`rejected`；不要整批失败回滚。
- 服务端成功写入后返回服务端版本、`updated_at` 和规范化后的资源。
- `rejected` 必须包含稳定 reason code，例如 `invalid_field`、`not_found`、`deleted`、`permission_denied`、`too_large`。
- `conflict` 必须返回 server resource、client base version、server version 和可展示的冲突字段。

冲突策略：

- `memo` 正文编辑必须使用版本/CAS，避免静默覆盖。
- `memo` 正文冲突时，前端显示本地版本与远端版本对比，并允许用户选择：
  - 保留本地。
  - 保留远端。
  - 手动合并后保存。
- 非关键偏好设置可采用 last-write-wins，但必须记录服务端 `updated_at`。
- AI 派生数据由服务端生成，客户端不应直接覆盖。
- ask 消息树使用追加式写入；编辑问题产生新分支，不覆盖旧消息。
- 删除和更新冲突时，默认 tombstone 优先；如果客户端要恢复，必须显式发送 restore 类动作。

离线创建与附件同步：

- memo 离线创建时客户端可预生成 memo id；服务器接受该 id 后返回规范化资源。
- 附件字节不放入 sync payload；未来移动端必须先上传附件字节得到 attachment uid，再在 sync push 中关联 memo。
- Web 首版使用普通附件上传接口 `POST /api/v1/attachments`，不实现完整断点续传。
- `POST /api/v1/attachments` 必须支持 `mutation_id` 或 `idempotency_key`，避免网络超时重试后重复创建附件。
- 上传成功后返回 `attachment.uid`、规范附件 URL、size、sha256 和 content type；memo 正文只引用 attachment uid 或规范 URL。
- 如果 memo 引用了尚未成功上传的附件，服务端应返回 `rejected` 或保留 pending 状态，不能生成坏链接。
- 如果采用 `rejected`，reason code 建议为 `pending_attachment` 或 `missing_attachment`。
- 附件删除用 tombstone 同步元数据；文件字节可延迟清理，但 fileserver 对 tombstone 必须返回 404。

未来 Android 断点续传预留：

- 首版不实现以下接口，但 proto/API 命名应避免与未来上传会话冲突：
  - `POST /api/v1/attachment-uploads`
  - `GET /api/v1/attachment-uploads/{upload}`
  - `PUT /api/v1/attachment-uploads/{upload}/chunks`
  - `POST /api/v1/attachment-uploads/{upload}:complete`
  - `DELETE /api/v1/attachment-uploads/{upload}:abort`
- 未来分片临时文件放在 `$SILLAGE_DATA/runtime/uploads/`。
- 完成时校验总 size 和 sha256，再原子 rename 到 `$SILLAGE_DATA/assets/attachments/`。
- 默认 30MB 附件规模下，首版普通重试 + 幂等足够；不要在 Go 重写首版引入 tus、S3 multipart 或复杂分片协议。

Tombstone 保留策略：

- 首版不清理 tombstone。
- 等 Android 客户端写入、离线同步和冲突恢复流程稳定后，再制定 tombstone 清理策略。
- 在清理策略确定前，不要 hard delete 会被离线客户端同步的聚合。

API key 与同步：

- AI profile 元数据可同步，例如 provider、base URL、模型、是否启用、是否存在 key。
- AI API key 明文不同步。
- 服务端可同步 `has_api_key=true/false`。
- 移动端如需独立配置 API key，应走专门设置接口，不通过普通 sync payload 明文传输。

与 memos 的差异：

- memos 的 `row_status=ARCHIVED` 不适合作为离线删除语义。
- Sillage 同步聚合统一使用 `deleted_at` tombstone。
- 如果需要“归档”，另建 `archived_at` 或业务状态字段，不替代 `deleted_at`。

## 阶段 5：API Service 实现

新增 `server/router/api/v1/APIV1Service`：

- 一套业务方法实现。
- 同时注册到 grpc-gateway REST 和 Connect。
- 不手写两套业务 handler。

认证：

- 参考 memos 的 `Authenticator` 和 Connect interceptor。
- 参考 memos 的 user/auth 实现方式，但 Sillage 命名为 account 且只允许一个账号。
- 使用首个账号初始化流程。
- 首个账号初始化实例。
- 无账号时，前端首页必须跳转到初始化页。
- 初始化完成后进入登录/应用首页。
- 初始化完成后不允许注册第二个账号。
- 不提供成员管理、角色管理、邀请、公开注册或匿名自助注册入口。
- 个人访问令牌可作为未来 Android/API 客户端认证能力评估，但不应引入多用户或公开内容权限模型。
- 登录成功返回 access token，并设置 HttpOnly refresh cookie。
- refresh token/session 存 SQLite。
- access token 用 `SESSION_SECRET` 签名。
- 登录失败限流必须保留，按客户端 IP + 账号维度计数，存 SQLite 或 `runtime_kv`。
- 默认策略可沿用当前 Sillage：15 分钟 10 次失败后暂时拒绝。

参考：

```text
~/Projects/memos/server/auth/
~/Projects/memos/server/router/api/v1/connect_handler.go
~/Projects/memos/server/router/api/v1/connect_services.go
~/Projects/memos/server/router/api/v1/connect_interceptors.go
```

## 阶段 6：附件与本地 assets

迁移目标：

- 不再依赖 R2。
- SQLite 存附件元数据。
- 文件字节写入 `$SILLAGE_DATA/assets/attachments/`。
- 下载必须经过后端文件服务，不直接暴露目录。
- 附件 URL 使用 memos 风格 `/file/attachments/{uid}/{filename}`。
- 文件内容按 memos 风格明文落盘，不再使用 `ATTACH_ENCRYPTION_KEY`。
- 默认单文件上传上限为 30MB，可通过实例设置或 `SILLAGE_MAX_UPLOAD_MB` 调整。
- 允许上传任意文件类型。
- 图片附件需要生成缩略图。
- 缩略图缓存目录使用 `$SILLAGE_DATA/.thumbnail_cache/`，可参考 memos 的 `ThumbnailCacheFolder`。
- 上传文件名必须清理：
  - 去除路径分隔符。
  - 拒绝 `..`、绝对路径、控制字符和空文件名。
  - 保留安全显示名，同时实际落盘路径使用服务端生成的 uid/template。
  - 不信任客户端传入的 MIME，必要时用内容嗅探修正。

建议 schema 字段：

```text
attachments.id
attachments.uid
attachments.creator_id
attachments.memo_id
attachments.storage_type
attachments.storage_ref
attachments.filename
attachments.content_type
attachments.size
attachments.sha256
attachments.width
attachments.height
attachments.status
attachments.created_at
attachments.updated_at
attachments.deleted_at
```

兼容策略：

- 不保留旧 `r2_key` 字段。
- 不兼容旧 `/attachments/:id`。
- 不向客户端暴露 storage ref。

文件写入：

```text
assets/attachments/{timestamp}_{uuid}_{filename}
```

可直接复用 memos 的 filepath template 思路，但默认目录固定在 `assets/attachments/` 下。

写入一致性：

- 上传时先写入同目录临时文件，fsync/close 成功后再原子 rename 到最终路径。
- 数据库 metadata 与文件落盘需要有补偿逻辑：DB 写入失败时删除临时/最终文件；文件写入失败时不创建 metadata。
- 计算 `sha256` 后再提交 metadata，用于去重、校验和未来移动端断点策略。
- 不要求首版做内容去重；即使 sha256 相同也可保留多条附件记录。
- memo Markdown 中的附件引用必须引用 attachment uid 或规范 URL，不能引用本地 storage path。
- 删除 memo 不应立即硬删除附件字节；如果附件仍被其他 memo 引用，必须保留。首版可以用引用计数或通过 memo Markdown 解析反查。
- orphan 文件清理只能清理没有 metadata 或 metadata 已 tombstone 且超过安全窗口的文件；首版如果不实现清理，也不能误删。

安全边界：

- 本地附件文件明文存储在 `$SILLAGE_DATA/assets/attachments/`。
- 文件目录不能由静态文件服务器直接暴露，只能经过后端 fileserver。
- 后端 fileserver 必须校验登录态和附件归属/权限后返回文件。
- Docker volume、宿主机目录权限、备份介质权限共同构成磁盘侧安全边界。
- memo 正文、账号资料、AI 派生内容默认不做应用层加密，服务端保持可读，以支持搜索和 AI。
- 由于 Sillage 不提供公开内容，附件下载默认都需要已登录用户授权。

文件服务要求：

- 登录校验。
- 404 missing/deleted。
- XSS unsafe MIME 降级或强制下载。
- `Content-Disposition` 对非图片/非安全预览类型默认 attachment，并使用清理后的 filename。
- 大文件使用 streaming。
- 支持 Range 请求；如果首版来不及实现，必须明确返回普通 200 且不声明 Accept-Ranges。
- 图片请求支持缩略图参数，例如 `/file/attachments/{uid}/{filename}?thumbnail=true`。
- 缩略图生成需要限制并发，避免大图导致内存压力。
- 缩略图只对安全图片类型生成；对 SVG、超大尺寸图片、解码失败图片跳过生成并返回原文件或占位状态。
- 删除附件时同步删除原文件和缩略图缓存。
- fileserver 解析本地路径时必须确保最终路径仍在 `$SILLAGE_DATA` 下，防止路径穿越。

参考：

```text
~/Projects/memos/store/attachment.go
~/Projects/memos/server/router/api/v1/attachment_service.go
~/Projects/memos/server/router/fileserver/fileserver.go
```

## 阶段 7：前端迁移到 Vite SPA

当前 React Router framework SSR 应迁移为：

```text
web/
  package.json
  pnpm-lock.yaml
  pnpm-workspace.yaml
  vite.config.ts
  src/
```

技术栈：

- React。
- TypeScript。
- Vite。
- Tailwind CSS。
- `react-router-dom`。
- `@connectrpc/connect-web`。
- `@tanstack/react-query` 可选，但建议用于 API cache。
- pnpm。

构建：

```text
pnpm --dir web install
pnpm --dir web build       # web 本地构建
pnpm --dir web release     # 输出到 ../server/router/frontend/dist
```

Go 后端用 `embed.FS` 打包前端产物。

Vite dev proxy：

```text
/api       -> http://localhost:5231
/file      -> http://localhost:5231
/sillage.api.v1 -> http://localhost:5231
```

前端 API 客户端：

- 使用生成的 TS proto。
- 使用 Connect Web。
- baseUrl 为 `window.location.origin`。
- fetch 带 `credentials: "include"`。
- auth interceptor 自动带 Bearer token，401 时 refresh。
- 对 mutation 请求统一处理 409 冲突、429 限流、401 refresh 失败和 request id 展示。
- 前端状态管理以服务器数据为准；离线写入能力主要留给未来 Android 客户端，Web 首版不实现复杂离线编辑队列。

首版页面结构：

- 记录：默认首页，提供快速记录、今日 memo、最近 memo、Markdown 所见即所得编辑、置顶入口；页面不应要求用户先分类或整理。
- 历史：基于 `entry_date` 的时间线、日历、活动热力图、归档入口、周/月总结入口；目标是既能找回某一天，也能观察一段时间的状态变化。
- 附件库：按时间/类型浏览附件，进入关联 memo；作为辅助入口，不做复杂网盘能力。
- 问答：ChatGPT Web 风格会话工作台，默认先选择上下文范围再提问。
- 设置：账号、AI 档案、生成偏好、外观；不作为主要写作动线的一部分。

编辑器要求：

- 引入 Markdown 所见即所得编辑器，可参考 memos 的 Tiptap 方案。
- 不实现标签自动解析和标签筛选。
- 保留 Markdown 源文本存储，避免编辑器格式绑定数据模型。
- 数据库只保存 Markdown 文本；所见即所得编辑器只是 UI 层，不引入富文本专用存储格式。
- 支持附件拖拽上传、粘贴上传，并在 memo Markdown 中自动插入附件引用。
- 首版只支持基础 Markdown 能力：段落、标题、列表、引用、代码块、链接、图片/附件。
- 首版不引入任务列表、表格、数学公式、Mermaid、复杂嵌入块等高级扩展。
- 编辑器默认服务短 memo 记录；长文编辑可用即可，不把文档级排版、目录、块数据库等能力作为目标。

前端体验约束：

- 页面必须适配桌面和手机宽度；移动端至少能完成初始化、登录、新建 memo、查看历史、Ask 提问和附件上传。
- 记录页首屏优先展示输入入口，不用营销式 hero 或说明文案。
- Ask 工作台布局参考 ChatGPT Web：会话列表、消息区、输入区、停止/重生成操作，但视觉上保持 Sillage 的安静私密气质。
- 所有 AI 输出区域都要展示来源入口；没有来源时展示原因，而不是隐藏引用区。
- 上传进度、AI 生成中、冲突对比、保存失败、会话停止等状态必须有明确 UI。
- 所有 destructive action，例如删除 memo、删除附件、删除 ask 会话，需要二次确认或可撤销机制。
- 键盘可达性不能倒退：主要按钮、编辑器、对话输入和冲突选择都应可通过键盘操作。

参考：

```text
~/Projects/memos/web/package.json
~/Projects/memos/web/vite.config.mts
~/Projects/memos/web/src/connect.ts
~/Projects/memos/web/src/router/index.tsx
~/Projects/memos/web/src/contexts/AuthContext.tsx
```

## 阶段 8：移除 Cloudflare 与备份功能

删除或废弃：

```text
workers/
wrangler.jsonc
tsconfig.cloudflare.json
worker-configuration.d.ts
app/lib/backup/
app/components/BackupSection.tsx
app/routes/download-backup.tsx
```

从设置页移除：

- 数据备份 tab。
- 立即备份按钮。
- 备份列表。
- 备份下载链接。

测试删除：

```text
tests/backup.test.ts
tests/backup-list.test.ts
```

文档说明：

- Docker v1 不提供内置备份。
- 用户自行备份整个 `/var/opt/sillage`，不要只备份 `sillage.db`。
- `/var/opt/sillage` 包含 SQLite 数据库、WAL/SHM、附件、缩略图缓存、runtime 文件和自动生成的实例 secret。
- 推荐停止容器后再备份整个目录，避免 SQLite WAL 与附件写入处于不一致状态。
- 首版不提供 `sillage export`、`sillage doctor` 或其他备份/导出 CLI。

## 阶段 9：Docker 与 compose

新增：

```text
scripts/Dockerfile
scripts/entrypoint.sh
scripts/compose.yaml
```

Dockerfile 参考 memos：

- frontend stage：安装 web 依赖并构建到 Go embed 目录。
- backend stage：`go build ./cmd/sillage`。
- runtime stage：Alpine + `tzdata` + `ca-certificates` + `su-exec`。
- 创建非 root 用户。
- `WORKDIR /var/opt/sillage`。
- `VOLUME /var/opt/sillage`。
- `EXPOSE 5231`。

entrypoint：

- root 启动时修复 `/var/opt/sillage` 权限。
- 支持 `SILLAGE_DSN_FILE` 等 `_FILE` secret。
- 降权运行。

compose：

```yaml
services:
  sillage:
    image: sillage:latest
    container_name: sillage
    volumes:
      - ~/.sillage:/var/opt/sillage
    ports:
      - 5231:5231
    environment:
      SILLAGE_PORT: "5231"
      SILLAGE_DATA: "/var/opt/sillage"
      # SESSION_SECRET / ENCRYPTION_SECRET are optional.
      # When omitted, Sillage generates and persists them on first start.

  cloudflared:
    image: cloudflare/cloudflared:latest
    profiles: ["tunnel"]
    command: tunnel --no-autoupdate run --token ${CLOUDFLARED_TOKEN}
    depends_on:
      - sillage
```

Tunnel 指向：

```text
http://sillage:5231
```

反向代理约定：

- Sillage 容器内只暴露 HTTP `5231`。
- Cloudflare Tunnel、Nginx、Caddy 等外层组件负责 TLS。
- 外层代理应转发 `X-Forwarded-Proto`、`X-Forwarded-Host`、`X-Forwarded-For`。
- 应用根据 forwarded proto/host 或 `SILLAGE_INSTANCE_URL` 生成附件 URL、回调 URL 和 cookie secure 属性。

参考：

```text
~/Projects/memos/scripts/Dockerfile
~/Projects/memos/scripts/entrypoint.sh
~/Projects/memos/scripts/compose.yaml
```

## 阶段 10：搜索与 FTS

搜索采用确定的降级策略，不把 trigram 作为硬依赖。

迁移要求：

- 首选 SQLite FTS5。
- 中文搜索不强依赖 trigram。
- 如果当前 SQLite driver 支持 trigram tokenizer，可以启用 trigram。
- 如果 trigram 不可用，使用普通 FTS5 + `LIKE` fallback。
- 搜索范围只包括 memo Markdown 正文和 memo AI summary。
- 不搜索标签，因为首版不引入标签能力。
- 历史、日历、活动热力图按 `entry_date` 筛选，不走全文搜索。
- 中文短语搜索必须有测试覆盖。

验收：

- 中文短语可搜。
- 软删除后不可搜。
- 恢复后可搜。
- 长自然语言 query 有 fallback。
- AI summary 命中可返回对应 memo。
- `entry_date` 日期筛选不依赖 FTS。

## 阶段 11：AI、问答、总结

迁移原则：

- Go 后端负责 AI provider 调用。
- AI 设置允许继续在网页设置中保存。
- AI provider API key 使用 `ENCRYPTION_SECRET` 做静态加密后存入 SQLite；不再复用附件加密密钥。
- 保留多套 AI 配置档案，支持 OpenAI、Anthropic、OpenAI-compatible endpoint 等 profile 并可切换当前活动档案。
- AI 设置建议使用专门 settings 表或 `account_setting`/`system_setting` 的 proto payload；只有短期 runtime 状态才放 `runtime_kv`。
- 单条 memo 保存后的 AI pipeline 改成 Go goroutine/background runner。
- 保留单条 memo AI 总结，这是核心能力。
- 保留周期/主题总结，这是核心能力。
- `/ask` 是核心功能，应实现为尽可能接近 ChatGPT Web 的对话工作台。
- AI 的默认角色是总结、状态分析和基于记录的建议；不能输出医学/心理诊断式结论，不能脱离来源 memo 编造事实。
- 问答流使用 SSE 或 Connect streaming；前端需要稳定支持流式渲染、停止生成和错误恢复。
- AI 输出需要纳入 `/api/v1/sync`，供未来 Android 客户端同步；AI 派生数据由服务端生成，客户端不直接覆盖。
- AI 请求必须限制并发，至少区分：
  - 单条 memo 后台总结并发。
  - `/ask` 流式回答并发。
- 超过并发限制时返回可恢复错误，前端显示“当前生成任务较多，请稍后再试”。

`/ask` 工作台目标能力：

- 会话列表。
- 会话搜索。
- 上下文范围选择：最近 7 天、最近 30 天、自定义日期范围、手动选择 memo、明确选择全量历史。
- 多轮上下文。
- 流式回答。
- 停止生成。
- 重新生成。
- 编辑用户问题后生成分支。
- 分支切换。
- 引用来源展示。
- 将回答保存为 memo。
- 导出当前对话文本。
- 删除/归档/重命名/置顶会话。

Ask 默认行为：

- 新会话默认提示用户选择上下文范围，推荐最近 30 天。
- 若用户没有选择范围，只允许基于当前输入和少量最近 memo 提供回答，并在 UI 中明确显示使用的来源范围。
- 回答必须带来源引用；没有足够记录支撑时，应说明“现有记录不足以判断”。
- “帮我分析最近状态”类问题默认使用最近 30 天；“今天/本周发生了什么”按 `entry_date` 推断范围。
- 不默认读取全量历史，除非用户明确选择。

AI 同步范围：

- `memo` 的 AI 总结、情绪、模型、生成时间等派生字段。
- `summaries` 周期/主题总结。
- `ask_conversations`。
- `ask_messages`。
- 消息引用来源和分支信息。

同步注意：

- AI 派生字段不应 bump `memo.updated_at`，避免普通 memo 同步流被 AI 重生成扰动。
- AI 派生表需要自己的更新时间或纳入对应 sync stream。
- `/api/v1/sync` 游标需要覆盖 memo、attachment、summary、ask conversation、ask message、AI 派生数据。

AI 工程细节：

- AI profile 表或 setting payload 必须记录 provider、base URL、model、temperature、max tokens、启用状态、是否存在 key、创建/更新时间。
- API key 加密存储时必须使用结构化 envelope；`ENCRYPTION_SECRET` 轮换方案首版可不实现，但数据结构要允许未来重加密。
- `ENCRYPTION_SECRET` 只用于加密 AI provider API key，不用于附件、memo 正文或 AI 输出。
- 加密时通过 HKDF 从 `ENCRYPTION_SECRET` 派生 AES-256-GCM key，不直接把原始字符串当作对称密钥。
- 加密 envelope 至少包含：
  - `algorithm`，例如 `AES-256-GCM+HKDF-SHA256`。
  - `key_id`。
  - `nonce`。
  - `ciphertext`。
  - `created_at`。
- 原始 secret 不存 SQLite；secret 来源优先级为：
  - 用户显式配置的 `ENCRYPTION_SECRET` 或 `_FILE`。
  - 自动生成并保存到 `$SILLAGE_DATA/runtime/secrets.json`。
- 自动生成的 `runtime/secrets.json` 权限必须尽量设为 `0600`，容器内由非 root 运行用户读取。
- 如果用户手动更换 `ENCRYPTION_SECRET` 导致旧 AI key 无法解密，应用不能崩溃；设置页显示“需要重新输入 API key”，相关 profile 标记为 `key_unavailable`。
- 首版不提供自动轮换 UI 或 CLI，但预留未来离线命令：`sillage secrets rotate-encryption --old-secret-file ... --new-secret-file ...`。
- 未来轮换流程是：用旧 secret 解密所有 AI key，用新 secret 重加密并更新 `key_id`；轮换失败不得破坏旧 ciphertext。
- 每次 AI 生成应记录 provider、model、profile id、prompt version、source memo ids、token usage（如果 provider 返回）、started_at、finished_at、status、error code。
- Prompt 模板要版本化；修改 prompt 后不会覆盖旧 AI 输出的来源和版本信息。
- Ask source references 建议结构化保存：memo id、entry_date、摘录、score 或 rank、引用在回答中的位置。
- 回答中的引用必须能跳回对应 memo；如果 memo 后续删除，引用仍显示最小必要信息并标注来源已删除。
- 停止生成必须能取消正在进行的 provider 请求或至少停止继续写入流；已生成 partial message 应标记为 stopped。
- 失败消息应保存为 error 状态，允许用户重试；不要把失败回答当作正常 assistant message。
- AI 请求需要超时设置，避免 provider 长时间挂起占用 goroutine。
- Prompt 中只包含用户选择范围内的 memo 和必要系统说明，不默认包含账号设置、secret、附件原文或无关历史。
- 如果选中 memo 太多，应先按时间/相关性裁剪，或提示用户缩小范围；不能无界拼接 prompt。

参考 memos 后台 runner 和 SSE：

```text
~/Projects/memos/server/server.go
~/Projects/memos/server/router/api/v1/sse_handler.go
~/Projects/memos/server/router/api/v1/sse_hub.go
```

## 阶段 12：测试计划

Go 测试：

- profile 默认目录和 DSN。
- migration fresh install。
- SQLite store memo/account/attachment CRUD。
- memo 私密访问权限。
- memo `entry_date` 创建、编辑、日历查询和同步。
- 历史页按 `entry_date` 查询某一天、某周、某月，并能生成对应周期总结。
- memo update mask 行为。
- memo 置顶和归档。
- 不存在标签表、标签 API 和标签 UI。
- `deleted_at` tombstone 删除、恢复和同步。
- runtime KV put/get/delete/TTL。
- attachment 明文写入、登录鉴权读取、删除本地文件。
- attachment 普通上传支持 `mutation_id` / `idempotency_key` 幂等重试，重复提交返回同一 attachment。
- upload size 默认 30MB，可配置。
- 任意文件类型上传。
- 文件名清理和路径穿越防护。
- 图片缩略图生成、读取、删除缓存。
- 唯一账号初始化。
- 无账号访问首页跳转初始化页。
- 初始化后禁止创建第二个账号。
- `SESSION_SECRET` / `ENCRYPTION_SECRET` 自动生成并持久化。
- auth signin/refresh/signout。
- 登录失败限流。
- localhost HTTP cookie 可用；HTTPS/forwarded HTTPS 下 cookie 自动 Secure。
- forwarded headers 生成正确外部 URL。
- `/healthz` 进程存活检查。
- `/readyz` DB readiness 检查。
- JSON request log 包含 method/path/status/duration。
- `/api/v1/sync` cursor 分页。
- `/api/v1/sync:push` 写入、冲突、拒绝和返回规范化资源。
- 账号与设置进入同步；AI API key 明文不进入同步。
- AI 设置多档案保存、切换、删除；浏览器端不返回明文 API key。
- AI API key 加密 envelope 包含 algorithm、key_id、nonce、ciphertext、created_at。
- 自动生成的 `runtime/secrets.json` 可持久化并用于重启后解密。
- 更换 `ENCRYPTION_SECRET` 导致旧 key 无法解密时，设置页/API 返回 `key_unavailable`，服务不崩溃。
- 单条 memo AI 总结后台生成。
- summaries 创建、更新、同步。
- ask conversation/message 分支、停止、重生成、导出。
- ask 上下文范围选择和来源引用。
- AI 在记录不足时返回“不足以判断”类结果，而不是编造分析。
- AI 生成并发限制。
- AI 输出进入 `/api/v1/sync`。
- 旧备份路由 404。

前端测试：

- Connect client auth interceptor。
- route guard。
- initialization route guard。
- settings 无备份入口。
- memo create/edit/delete flow。
- memo pin/archive flow。
- 附件库页面。
- 日历/活动热力图。
- Markdown 所见即所得编辑器。
- 不出现标签管理入口。
- 记录页默认支持短 memo 快速输入，长文可编辑但不出现文档管理式功能。
- 历史页能按日期找回记录，并能观察周/月状态趋势。
- Ask 新会话可以选择最近 7 天、最近 30 天、自定义日期范围、手动 memo 和全量历史。
- ask ChatGPT Web 风格对话工作台核心交互。
- AI 设置多档案管理。
- attachment upload/download link rendering。

集成测试：

- 启动 Go server 使用临时 data dir。
- REST v1 和 Connect v1 都调用同一 service 并返回一致结果。
- Go 静态前端 fallback 正常。
- CSRF、CORS、cookie secure、refresh token 轮换。
- OpenAPI 生成物存在且与 proto 生成流程一致。
- `proto:generate` 后工作区无意外 diff。
- 日志脱敏：登录、AI 设置保存、ask 请求不会打印 secret 或完整 prompt。

容器验收：

- 空 `~/.sillage` 启动后生成：
  - `sillage.db`
  - `assets/attachments`
  - `runtime`
- 容器重启数据保留。
- 进程非 root。
- volume 权限不会导致写入失败。
- `cloudflared` profile 能访问 `http://sillage:5231`。
- SIGTERM 后能优雅退出，不留下明显损坏的 SQLite WAL 或半写入附件。

文档验收：

- README 更新 Docker compose、Cloudflare Tunnel、首次初始化、数据目录和备份说明。
- `docs/product/sillage.md` 更新为新定位：单人私密、低压力短 memo、AI 总结/状态分析/基于记录建议。
- `docs/api/sync.md` 更新为 `/api/v1/sync` 和 `/api/v1/sync:push` 的新 Protobuf 契约，不再描述旧 `/api/sync`。
- 新增或更新开发文档，说明 Go、pnpm、buf、Docker 的本地开发命令。
- 明确写入“不迁移 Cloudflare 旧数据”，避免后续执行阶段误做旧数据导入。

## 推荐执行顺序

按里程碑推进，不要一次性重写全部功能。每个里程碑必须有可独立运行的验证命令或手工验收路径。

1. **Go skeleton + SQLite migration + healthz/readyz**
   - 建立 Go module、`cmd/sillage`、profile、SQLite driver、迁移器。
   - 写 `LATEST.sql`，让空库可启动。
   - 实现 `/healthz`、`/readyz`、JSON request log。
   - 验收：`go test ./...`，`go run ./cmd/sillage` 能启动空库。

2. **account/auth 初始化**
   - 实现唯一账号初始化。
   - 实现登录、refresh、退出。
   - 实现 secret 自动生成和持久化。
   - 实现登录失败限流。
   - 验收：无账号跳初始化页；初始化后禁止第二账号；登录链路通过。

3. **memo CRUD + 双向 sync**
   - 实现 memo store、proto/API、CAS、tombstone、置顶、归档、`entry_date`。
   - 实现 `GET /api/v1/sync` 和 `POST /api/v1/sync:push`。
   - 实现冲突返回和 memo 冲突对比所需 payload。
   - 验收：memo CRUD、push 部分成功、冲突、tombstone 同步测试通过。

4. **attachment/fileserver/thumbnail**
   - 实现附件元数据、任意文件上传、30MB 默认上限、可配置。
   - 实现本地明文 `assets/attachments` 存储。
   - 实现普通上传的 `mutation_id` / `idempotency_key` 幂等重试。
   - 实现 fileserver、路径穿越防护、MIME 防护、缩略图和缓存删除。
   - 不实现完整 Android 断点续传，但保留未来 `attachment-uploads` 命名空间。
   - 验收：上传/重复上传幂等/下载/缩略图/删除/路径攻击测试通过。

5. **Vite SPA 基础壳**
   - 建立 `web/`、pnpm、Vite、Tailwind、React Router、Connect Web client。
   - Go embed 前端 dist，SPA fallback。
   - 实现初始化、登录、基础布局和路由守卫。
   - 验收：`pnpm --dir web build`，Go server 能提供前端。

6. **memo UI + WYSIWYG**
   - 实现“记录 / 历史 / 附件库 / 问答 / 设置”基础导航。
   - 实现 memo 列表、快速记录、编辑、置顶、归档。
   - 引入 Markdown 所见即所得编辑器，只保存 Markdown。
   - 支持拖拽/粘贴附件上传并插入 Markdown。
   - 实现日历/活动热力图。
   - 验收：核心记录流、编辑器、日历、附件库前端测试通过。

7. **AI settings + memo summary**
   - 实现 AI 多档案设置、`ENCRYPTION_SECRET` 加密 API key。
   - 使用可轮换的加密 envelope 格式保存 API key，支持自动生成 `runtime/secrets.json`。
   - 实现 provider 测试、模型配置、生成偏好。
   - 实现单条 memo AI 总结后台 pipeline 和并发限制。
   - 实现 summary 数据进入 sync。
   - 不实现 secret 自动轮换 UI/CLI，但预留未来 `sillage secrets rotate-encryption`。
   - 验收：AI 设置不回传明文 key；重启后可解密；换 secret 后标记 `key_unavailable`；memo summary 生成和同步测试通过。

8. **ask 工作台**
   - 实现 ChatGPT Web 风格 `/ask`。
   - 支持会话列表、搜索、上下文范围选择、多轮上下文、流式回答、停止、重生成、编辑问题后分支、分支切换、引用来源、保存为 memo、导出当前对话文本、归档/重命名/置顶。
   - ask conversations/messages 进入 sync。
   - 验收：ask 核心交互、上下文范围选择、来源引用、流式、停止、分支、同步测试通过。

9. **Docker/compose/tunnel docs**
   - 添加 Dockerfile、entrypoint、compose。
   - 验证非 root、volume 权限、secret 自动生成、Cloudflare Tunnel 指向 `http://sillage:5231`。
   - 更新 README 部署说明和运维备份说明。
   - 验收：`docker compose up -d sillage` 后可访问并完成初始化。

10. **删除旧 Workers/备份/Cloudflare 依赖**
    - 删除 Workers/Wrangler/Cloudflare 测试池和旧 D1/R2/KV 代码。
    - 移除备份功能、备份 UI、备份测试。
    - 更新 `docs/api/sync.md`、`docs/product/sillage.md`。
    - 验收：旧备份 URL 404；代码中无业务层 Cloudflare 运行时依赖；完整测试通过。

## 成功标准

- `go test ./...` 通过。
- `go vet ./...` 通过。
- `pnpm --dir web build` 通过。
- `pnpm --dir web typecheck` 通过。
- `pnpm --dir web lint` 通过，若项目未配置 lint，应在迁移时补齐或明确替代命令。
- `go build ./cmd/sillage` 通过。
- `buf lint` 和 `buf generate` 通过，生成物无遗漏。
- Docker 镜像可构建。
- `docker compose up -d sillage` 后可访问 `http://localhost:5231`。
- 首次访问进入创建唯一账号页面。
- 唯一账号初始化、登录、新建 memo、编辑 memo、附件上传下载、设置保存、AI 总结、问答工作台、`/api/v1/sync` 可用。
- 旧备份页面和接口不可访问。
- 代码中不再出现业务层直接依赖 Cloudflare D1/R2/KV/Workers 的路径。
