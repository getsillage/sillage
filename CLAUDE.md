# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在本仓库工作时提供指导。

## 协作约定

- 直接提交到 `main` 分支，不创建新分支，也不走 PR 流程。
- 与用户交流一律使用简体中文。
- 本文件除下方 `# RTK (Rust Token Killer)` 部分保留英文原文外，统一使用简体中文。
- 所有 shell 命令必须加 `rtk` 前缀。

## 行为规范

整体倾向谨慎而非速度；琐碎任务可以自行判断。

### 1. 编码前思考

- 明确说明假设；不确定就问，不要猜。
- 有多种理解时摆出来，不要默默选一个。
- 用户不是技术专家。需求本身有风险或不符合当前产品边界时，先指出问题，给出更规范的做法与权衡。
- 不清楚就停下来，说清困惑再继续。

### 2. 简洁优先

- 不加要求之外的功能。
- 不为一次性代码造抽象。
- 不加没有实际需求的灵活配置。
- 沿用现有 Go store/service、Echo route、Vite web、proto 生成物的组织方式。

### 3. 精准修改

- 只碰必须碰的文件。
- 不顺手重构无关代码。
- 看到无关死代码先提，不要直接删。
- 因本次改动产生的孤儿 import、变量、函数要清理。

### 4. 目标驱动执行

- 把任务转成可验证目标。
- 多步任务先给简短计划。
- 每次提交前按风险运行对应验证命令。
- 功能、命令、架构、API 契约变化要同步更新 `README.md`、`CLAUDE.md` 或 `docs/`。

## Commit 规范

提交遵循 Conventional Commits：

```text
<type>(<scope>): <subject>
```

常用类型：

- `feat`：新功能或新增资料
- `fix`：修复问题
- `docs`：文档变更
- `style`：格式调整
- `refactor`：结构整理或重构
- `perf`：性能优化
- `test`：添加或修改测试
- `chore`：构建过程、辅助工具或仓库维护变更
- `ci`：CI 配置变更
- `revert`：回滚提交

示例：

```text
feat(api): add sync push conflict result
fix(auth): reject second account initialization
docs(sync): document mutation id semantics
chore(cleanup): remove legacy workers runtime
```

## 项目

Sillage 是单人私密记录与 AI 反思工具。当前主架构是 memos 风格 Go 自托管单体：

- Go 后端：`cmd/sillage`、`server/`、`store/`、`internal/`
- SQLite 文件数据库：`store/migration/sqlite/LATEST.sql`
- 本地附件目录：`$SILLAGE_DATA/assets/attachments/`
- React + TypeScript + Vite 前端：`web/`
- Protobuf API 契约：`proto/api/v1/`
- Docker 部署：`scripts/Dockerfile`、`scripts/compose.yaml`

产品边界：

- 后端、数据库、proto 和 API 使用 `memo` 命名；中文 UI 显示“记录”。
- 保持单人私密空间，不引入多用户、成员管理、公开分享、公开探索、RSS、sitemap、标签、reaction、relation、社交 feed、知识库、任务系统或复杂网盘能力。
- 不迁移旧 Cloudflare 数据；新 SQLite 数据库从空库初始化。
- 不提供内置备份功能、备份 UI、定时任务、下载接口或导出 CLI。
- Docker 是主部署方式，所有持久状态收敛到 `/var/opt/sillage`。
- 未来 Android 工程放在同仓库 `android/` 下，共用根目录 `proto/`；当前阶段不创建 Android 工程。

产品指导见 `docs/product/sillage.md`，同步契约见 `docs/api/sync.md`，迁移计划见 `docs/migration/memos-style-self-hosted-plan.md`。

## 常用命令

```bash
go test ./...
go vet ./...
go build ./cmd/sillage

pnpm --dir web install
pnpm --dir web typecheck
pnpm --dir web lint
pnpm --dir web test
pnpm --dir web build

buf lint
buf generate

SILLAGE_DATA="$(mktemp -d)" go run ./cmd/sillage
```

Docker 构建：

```bash
docker build -t sillage:latest -f scripts/Dockerfile .
docker compose -f scripts/compose.yaml up -d --build sillage
```

如果网络不稳定，可按用户提示使用本机 `7897` 代理端口，例如：

```bash
docker build --network=host \
  --build-arg HTTP_PROXY=http://127.0.0.1:7897 \
  --build-arg HTTPS_PROXY=http://127.0.0.1:7897 \
  --build-arg NO_PROXY=localhost,127.0.0.1 \
  --build-arg NPM_CONFIG_REGISTRY=https://registry.npmmirror.com \
  --build-arg GOPROXY=https://goproxy.cn,direct \
  -t sillage:latest -f scripts/Dockerfile .
```

## 运行时配置

常用环境变量：

```text
SILLAGE_ADDR=
SILLAGE_PORT=5231
SILLAGE_DATA=/var/opt/sillage
SILLAGE_DSN=/var/opt/sillage/sillage.db
SILLAGE_MAX_UPLOAD_MB=30
SILLAGE_INSTANCE_URL=
SILLAGE_LOG_FORMAT=json
SILLAGE_LOG_LEVEL=info
SESSION_SECRET=
ENCRYPTION_SECRET=
```

`SESSION_SECRET` 和 `ENCRYPTION_SECRET` 可省略；首次启动会生成到 `$SILLAGE_DATA/runtime/secrets.json`。也支持 `SILLAGE_DSN_FILE`、`SESSION_SECRET_FILE`、`ENCRYPTION_SECRET_FILE` 文件注入。

数据目录布局：

```text
/var/opt/sillage/sillage.db
/var/opt/sillage/assets/attachments/
/var/opt/sillage/.thumbnail_cache/
/var/opt/sillage/runtime/
```

## 架构说明

启动链路：

- `cmd/sillage/main.go` 读取 flag/env，创建 profile。
- `internal/profile` 规范化端口、数据目录、DSN 和运行目录。
- `store/db/sqlite` 使用 `modernc.org/sqlite` 打开 SQLite。
- `store/migrator.go` 对新库应用 `LATEST.sql`。
- `server.New` 创建 Echo server，注册健康检查、REST、Connect、附件下载和前端静态资源。

存储层：

- `store.Store` 聚合 account、session、memo、attachment、AI profile、memo AI、Ask、sync、runtime KV 等操作。
- `memo` 是唯一内容单位，包含 `entry_date`、`version`、`pinned_at`、`archived_at`、`deleted_at`。
- 删除使用 tombstone；可同步聚合必须保留 `updated_at` 与 `deleted_at`。
- AI 派生数据写入独立表，不 bump memo `updated_at` 或 `version`。
- 搜索使用 SQLite FTS5，中文短语和长查询使用 fallback。

认证：

- 首次无账号时进入唯一账号初始化流程。
- 初始化后不允许创建第二个账号。
- 登录返回 access token，并设置 HttpOnly refresh cookie。
- refresh token 存服务端哈希，登出时失效。
- 登录失败限流保留在服务端。

API：

- REST v1 路径使用 `/api/v1/*`。
- Connect v1 路径形如 `/sillage.api.v1.MemoService/ListMemos`。
- REST 和 Connect 共用 `server/api_service.go` 里的业务方法，避免两套行为。
- Protobuf 源在 `proto/api/v1/`，生成物必须提交入库。
- `/api/v1/sync` 和 `/api/v1/sync:push` 面向未来 Android 离线客户端，支持 tombstone、mutation id 幂等、冲突返回和附件 metadata 同步。

附件：

- 字节写入 `$SILLAGE_DATA/assets/attachments/`，下载路径为 `/file/attachments/{uid}/{filename}`。
- 文件下载需要登录，不直接暴露宿主目录。
- 文件名必须清理，禁止路径穿越。
- 默认上传上限为 30MB，可通过 `SILLAGE_MAX_UPLOAD_MB` 调整。

AI 与 Ask：

- AI profile 保存在 SQLite。
- API key 使用 `ENCRYPTION_SECRET` 经 HKDF 派生 AES-256-GCM key 后加密为 envelope。
- key 解不开时标记 `key_unavailable`，服务不能崩溃。
- 当前单条 memo 总结和 Ask 回答由配置的 AI 档案生成，必须基于来源 memo，不编造没有记录支撑的分析。
- Ask 默认上下文范围推荐最近 30 天，不默认读取全量历史。

前端：

- `web/` 使用 pnpm、Vite、React、TypeScript、Tailwind。
- `pnpm --dir web build` 输出到 `server/router/frontend/dist`，由 Go embed 提供静态资源。
- Web 首屏是可用应用界面，不做营销落地页。
- UI 文案使用“记录”，不要把 `memo` 暴露给中文用户。

## 测试与验收

提交前按改动范围运行：

```bash
go test ./...
go vet ./...
go build ./cmd/sillage
buf lint
buf generate
pnpm --dir web lint
pnpm --dir web typecheck
pnpm --dir web test
pnpm --dir web build
```

前端测试用 Vitest + @testing-library（jsdom）；E2E 用 Playwright（`pnpm --dir web test:e2e`，需先
`pnpm --dir web exec playwright install` 安装浏览器，并有可访问的运行实例）。

影响部署时还要验证 Docker 镜像可构建，必要时使用 compose 启动后访问 `http://localhost:5231`。

关键验收点：

- 首次访问进入创建唯一账号页面。
- 初始化、登录、新建记录、编辑记录、附件上传下载、AI 设置、AI 总结、Ask 和 `/api/v1/sync` 可用。
- 旧备份页面和接口不可访问。
- 业务代码中不再出现 Cloudflare Workers、D1、R2、KV 或 Wrangler 运行时依赖。

## 部署说明

Sillage 自身只提供 HTTP 服务。TLS 由 Cloudflare Tunnel、Nginx、Caddy 或平台层负责。外层代理应转发：

```text
X-Forwarded-Proto
X-Forwarded-Host
X-Forwarded-For
```

需要固定外部地址时设置 `SILLAGE_INSTANCE_URL`。

当前版本没有内置备份能力。运维侧需要停止容器后复制整个 `/var/opt/sillage`，不要只复制 `sillage.db`，因为同一目录还包含 SQLite WAL/SHM、附件、缩略图缓存和运行时 secret。

<!-- headroom:rtk-instructions -->
# RTK (Rust Token Killer) - Token-Optimized Commands

When running shell commands, **always prefix with `rtk`**. This reduces context
usage by 60-90% with zero behavior change. If rtk has no filter for a command,
it passes through unchanged — so it is always safe to use.

## Key Commands
```bash
# Git (59-80% savings)
rtk git status          rtk git diff            rtk git log

# Files & Search (60-75% savings)
rtk ls <path>           rtk read <file>         rtk grep <pattern>
rtk find <pattern>      rtk diff <file>

# Test (90-99% savings) — shows failures only
rtk pytest tests/       rtk cargo test          rtk test <cmd>

# Build & Lint (80-90% savings) — shows errors only
rtk tsc                 rtk lint                rtk cargo build
rtk prettier --check    rtk mypy                rtk ruff check

# Analysis (70-90% savings)
rtk err <cmd>           rtk log <file>          rtk json <file>
rtk summary <cmd>       rtk deps                rtk env

# GitHub (26-87% savings)
rtk gh pr view <n>      rtk gh run list         rtk gh issue list

# Infrastructure (85% savings)
rtk docker ps           rtk kubectl get         rtk docker logs <c>

# Package managers (70-90% savings)
rtk pip list            rtk pnpm install        rtk npm run <script>
```

## Rules
- In command chains, prefix each segment: `rtk git add . && rtk git commit -m "msg"`
- For debugging, use raw command without rtk prefix
- `rtk proxy <cmd>` runs command without filtering but tracks usage
<!-- /headroom:rtk-instructions -->
