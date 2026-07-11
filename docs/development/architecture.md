# 架构说明

本文描述 Sillage 的稳定工程边界。具体字段和行为以文末列出的代码事实源为准。

## 系统边界

Sillage 是单人自托管单体：一个 Go 进程提供 REST、Connect、附件下载和嵌入式 Web；业务数据写入 SQLite，附件字节写入本地文件系统。Android 作为独立客户端通过 REST 访问同一实例，也可在设备上离线保存数据。

```text
Web SPA -------- REST / SSE --------┐
Android -------- REST --------------+--> Echo adapters --> service / route orchestration --> Store --> SQLite
Connect client -- Connect ----------┘                                               └--> attachments/
```

REST 与 Connect 适配器复用同一业务服务。记录的校验、分页、搜索、详情和写入集中在 `server/memo/`，REST、Connect 与同步只负责把各自传输模型转换为服务输入；创建后的 AI 自动总结仍由根 `server` 跨特性编排。其他领域目前继续复用 `server/api_service.go` 中的业务方法，并按特性渐进迁移。附件上传、Ask SSE 等手写扩展会在 route 中编排并直接调用 Store；它们仍须复用相同的鉴权和领域约束，不能另造冲突语义。

## 启动链路

1. `cmd/sillage/main.go` 读取 flag 和环境变量。
2. `internal/profile` 规范化监听地址、数据目录、SQLite DSN 和运行目录。
3. `store/migrator.go` 初始化空库或执行受支持的兼容升级。
4. `internal/secret` 读取或生成运行密钥。
5. `server.New` 注册探针、REST、Connect、附件和 Web 路由。
6. 收到 SIGINT/SIGTERM 后，服务停止接收请求并关闭数据库。

## 模块职责

| 路径 | 职责 |
| --- | --- |
| `cmd/sillage/` | 进程入口、配置绑定、生命周期 |
| `internal/profile/` | 运行配置与目录规范化 |
| `internal/secret/` | 会话密钥、AI key 加密密钥与 envelope |
| `server/` | HTTP/Connect 适配、跨特性编排和 AI 调用 |
| `server/auth/` | 账号认证、会话和 token 服务 |
| `server/memo/` | 记录校验、查询分页和写入业务服务 |
| `store/` | SQLite 查询、事务、迁移与领域持久化 |
| `proto/api/v1/` | Protobuf API 契约源 |
| `web/` | React Web 源码、测试与构建配置 |
| `android/` | Kotlin/Compose 客户端与本地离线数据 |
| `scripts/` | 容器构建、启动和 Compose |

### Web 内部边界

| 路径 | 职责 |
| --- | --- |
| `web/src/app/` | 应用启动、路由装配、Provider 顺序和全局导航壳 |
| `web/src/features/auth/` | 初始化与登录界面 |
| `web/src/features/memos/` | 记录列表、详情、编辑、筛选和记录状态 |
| `web/src/features/ask/` | 问答会话、消息树和流式回答状态 |
| `web/src/features/settings/` | AI 档案与界面设置 |
| `web/src/components/` | 跨特性复用的展示与交互组件 |
| `web/src/lib/` | API、认证 token 和日期等底层能力 |

`app/` 负责组合各特性；特性可以依赖共享 `components/`、`lib/`，问答可调用记录特性保存回答，但记录特性不反向依赖问答。`web/src/lib/api.ts` 暂时维持统一的传输客户端，目录整理不改变 API、路由或浏览器存储契约。

### Android 内部边界

| 路径 | 职责 |
| --- | --- |
| `android/app/src/main/java/app/sillage/ui/` | 应用壳、共享界面状态、ViewModel 和附件缓存生命周期 |
| `android/app/src/main/java/app/sillage/ui/auth/` | 使用模式选择、服务连接、初始化与登录界面 |
| `android/app/src/main/java/app/sillage/ui/memos/` | 记录列表、详情、编辑和 Markdown 展示 |
| `android/app/src/main/java/app/sillage/ui/ask/` | 问答会话与流式回答界面 |
| `android/app/src/main/java/app/sillage/ui/settings/` | AI、外观、数据和同步设置界面 |
| `android/app/src/main/java/app/sillage/ui/common/` | 跨特性复用的展示组件 |
| `android/app/src/main/java/app/sillage/ui/navigation/` | 主导航组件 |
| `android/app/src/main/java/app/sillage/data/` | REST 客户端、会话、本地存储和数据模型 |

`SillageApp` 只组合界面并处理附件查看器交接；特性界面依赖根 `SillageUiState`、`SillageViewModel` 和共享 UI，状态与数据层不反向依赖特性界面。目录整理不改变手动同步、导航历史、请求 ID 或在线与离线模式语义。

## 核心不变量

- 一个实例只有一个账号；初始化后拒绝创建第二个账号。
- `memo` 是唯一内容单位，中文 UI 显示“记录”。
- `entry_date` 表示用户选择的日期，不能用 `created_at` 替代。
- 正文、日期、收藏、归档和删除使用 `version` 做乐观并发控制。
- 删除保留 tombstone，供同步客户端收敛。
- AI 派生数据独立存储，不增加 memo 的 `version` 或 `updated_at`。
- 附件下载必须鉴权，文件名必须清理；附件字节不进入同步 payload。
- AI API key 只以加密 envelope 保存，接口和同步不得返回明文。

详细分页、幂等和冲突规则见[同步 API](api/sync.md)。产品范围见[产品指导](product-guidance.md)，认证、附件、密钥和外部请求约束见[安全开发边界](security.md)。

## 数据与生成物

默认数据单元是一个完整的 `SILLAGE_DATA` 目录：

```text
sillage.db
sillage.db-wal
sillage.db-shm
assets/attachments/
.thumbnail_cache/
runtime/secrets.json
```

WAL/SHM 只在 SQLite 使用时出现。`.thumbnail_cache/` 是当前未使用的预留目录，启动时会确保目录存在；`runtime/` 不是缓存。备份规则见[数据与备份](../user/data.md)。

仓库提交两类生成物：

- `proto/gen/` 由 `buf generate` 生成；不得手改。
- `server/router/frontend/dist/` 由 `pnpm --dir web build` 生成，并嵌入 Go 二进制；不得手改。

## API 边界

- REST v1：`/api/v1/*`。
- Connect v1：`/sillage.api.v1.<Service>/<Method>`。
- Protobuf 和 OpenAPI 覆盖规范服务；上传、附件读取和 Ask SSE 等手写扩展以 `server/*_routes.go` 为准。
- Web 在 `web/src/lib/api.ts` 维护手写类型；Android 在 `SillageApi.kt` 维护 REST 映射。

契约变化必须同步 Proto、生成物、受影响的 REST/Connect 适配、客户端和测试，具体步骤见[贡献指南](../../CONTRIBUTING.md)。

## 事实来源

| 主题 | 事实来源 |
| --- | --- |
| 运行配置 | `cmd/sillage/main.go`、`internal/profile/profile.go` |
| 数据库结构与升级 | `store/migration/sqlite/LATEST.sql`、`store/migrator.go` |
| REST 路由 | `server/*_routes.go` |
| 业务服务 | `server/memo/`、`server/auth/`、`server/api_service.go` |
| Connect / OpenAPI 契约 | `proto/api/v1/`、`proto/gen/openapi/openapi.yaml` |
| Web 主题和组件样式 | `web/src/styles/app.css`、`web/src/components/ui.ts` |
| 自动化门禁 | `.github/workflows/ci.yml` |
| 容器行为 | `scripts/Dockerfile`、`scripts/entrypoint.sh`、`scripts/compose.yaml` |
