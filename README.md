# Sillage

Sillage 是一个单人私密记录空间，用来保存日常片段、查看历史，并基于记录做 AI 总结与问答。

当前仓库已迁移为 memos 风格的 Go 自托管单体：Go 后端、SQLite 文件数据库、本地附件存储、React + TypeScript + Vite 前端、REST API v1 与 Connect/gRPC API v1。旧 Cloudflare Workers 运行路径、内置备份功能、备份 UI、定时任务和备份下载接口已移除。

产品指导文件见 [docs/product/sillage.md](docs/product/sillage.md)，同步契约见 [docs/api/sync.md](docs/api/sync.md)。

## 功能状态

- 唯一账号初始化、登录、refresh、退出。
- memo 创建、列表、搜索、编辑、删除、置顶、归档。
- SQLite FTS5 搜索，中文短语和长查询使用 `LIKE` fallback。
- 本地附件上传与登录后下载，文件保存在数据目录内。
- AI 档案设置，API key 使用 `ENCRYPTION_SECRET` 加密 envelope 保存。
- 单条 memo 本地占位总结，并进入 sync。
- Ask 会话与消息，当前基于最近 7 天、最近 30 天或全部记录生成带来源的本地占位回答。
- `/api/v1/sync` 与 `/api/v1/sync:push` 支持 tombstone、mutation id 幂等和 memo 冲突返回。
- Connect v1 注册 `AuthService`、`MemoService`、`AttachmentService`、`SettingsService`、`AskService` 与 `SyncService`。

后端、数据库、proto 和 API 使用 `memo` 命名；中文界面使用“记录”。首版不引入多用户、公开分享、标签、reaction、relation、RSS、任务系统或知识库能力。

## 本地开发

```bash
go test ./...
go vet ./...
go build ./cmd/sillage

pnpm --dir web install
pnpm --dir web typecheck
pnpm --dir web lint
pnpm --dir web build

buf lint
buf generate

SILLAGE_DATA="$(mktemp -d)" go run ./cmd/sillage
curl http://localhost:5231/healthz
curl http://localhost:5231/readyz
curl http://localhost:5231/api/v1/auth/bootstrap
```

默认监听端口为 `5231`。默认数据目录为 `/var/opt/sillage`；本地没有该目录时会使用当前目录，也可以显式设置 `SILLAGE_DATA`。运行时目录包含：

```text
sillage.db
assets/attachments/
.thumbnail_cache/
runtime/
```

未显式配置 `SESSION_SECRET` / `ENCRYPTION_SECRET` 时，Sillage 会自动生成并持久化到 `runtime/secrets.json`。

## 配置

常用环境变量：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SILLAGE_ADDR` | 空 | HTTP 监听地址 |
| `SILLAGE_PORT` | `5231` | HTTP 监听端口 |
| `SILLAGE_DATA` | `/var/opt/sillage` | 持久数据目录 |
| `SILLAGE_DSN` | `$SILLAGE_DATA/sillage.db` | SQLite 数据库路径 |
| `SILLAGE_MAX_UPLOAD_MB` | `30` | 单文件上传上限 |
| `SILLAGE_INSTANCE_URL` | 空 | 外部访问地址；未设置时从请求推断 |
| `SILLAGE_LOG_FORMAT` | `json` | `json` 或 `text` |
| `SILLAGE_LOG_LEVEL` | `info` | `debug`、`info`、`warn`、`error` |
| `SESSION_SECRET` | 自动生成 | 签名 access/refresh token |
| `ENCRYPTION_SECRET` | 自动生成 | 加密 AI provider API key |

`SILLAGE_DSN_FILE`、`SESSION_SECRET_FILE` 和 `ENCRYPTION_SECRET_FILE` 可用于文件注入 secret。显式变量与对应 `_FILE` 变量不能同时设置。

## API 契约

Protobuf 契约源位于 [proto/](proto/)。未来 Android 工程会放在同仓库 `android/` 下，并直接复用根目录 `proto/`，不复制契约文件。

```bash
buf lint
buf generate
```

生成物提交入库：

- Go protobuf / gRPC / Connect / grpc-gateway：`proto/gen/api/v1/`
- OpenAPI：`proto/gen/openapi/openapi.yaml`
- Web TypeScript proto：`web/src/types/proto/`

REST v1 入口使用 `/api/v1/*`，Connect v1 入口形如 `/sillage.api.v1.MemoService/ListMemos`。REST 与 Connect 共用同一 service 逻辑。

## Docker 自托管

Docker 是主部署方式。镜像内只提供 HTTP 服务，默认监听 `5231`，所有持久数据放在 `/var/opt/sillage`：

```bash
docker build -t sillage:latest -f scripts/Dockerfile .
docker run --rm -p 5231:5231 -v "$HOME/.sillage:/var/opt/sillage" sillage:latest
```

也可以使用 compose：

```bash
docker compose -f scripts/compose.yaml up -d --build sillage
docker compose -f scripts/compose.yaml logs -f sillage
```

首次访问 `http://localhost:5231` 会进入创建唯一账号页面。初始化完成后不允许创建第二个账号。

Cloudflare Tunnel 可使用 compose profile：

```bash
CLOUDFLARED_TOKEN=... docker compose -f scripts/compose.yaml --profile tunnel up -d
```

Tunnel 的服务地址指向 `http://sillage:5231`。如果使用 Nginx、Caddy 或其他反向代理，由外层负责 TLS，并转发 `X-Forwarded-Proto`、`X-Forwarded-Host` 和 `X-Forwarded-For`；需要固定外部地址时设置 `SILLAGE_INSTANCE_URL`。

## 运维数据

本迁移不导入旧 Cloudflare 数据；新 SQLite 数据库从空库初始化。

当前版本不提供内置备份、备份 UI、定时备份、下载接口或导出 CLI。需要备份时，请停止容器后复制整个数据目录，不要只复制 `sillage.db`。同一目录还包含 SQLite WAL/SHM、附件、缩略图缓存和运行时 secret。

更详细的协作、架构和验证说明见 [CLAUDE.md](CLAUDE.md)。
