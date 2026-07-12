# CLAUDE.md

本文件只记录编码代理的协作约束。开发流程、架构和运维规则使用下方事实源，不在这里重复维护。

## 协作约定

- 直接提交到 `main` 分支，不创建新分支，也不走 PR 流程。
- 与用户交流一律使用简体中文。
- 本文件除下方 `# RTK (Rust Token Killer)` 部分保留英文原文外，统一使用简体中文。
- 所有实际 shell 命令必须加 `rtk` 前缀；文档代码块保留普通开发者使用的标准命令。
- 工作树可能包含用户改动。保留无关变更，不执行破坏性 Git 命令，不提交真实数据或密钥。

## 工作方式

1. 先确认目标、影响范围和代码事实；旧文档不能替代代码核验。
2. 采用现有 Go store/service、Echo route、Vite Web、Android 和 Proto 组织方式。
3. 只修改完成目标所需的文件，不顺手重构或扩展产品范围。
4. 按风险补测试并运行对应门禁；失败要定位，不能用降低标准规避。
5. 功能、配置、命令、契约或架构变化必须在同一提交更新主文档。

## 项目边界

Sillage 是自托管的单人私密记录与 AI 反思工具：

- Go + Echo 后端，SQLite 与本地附件存储；
- React + TypeScript + Vite Web，构建产物嵌入 Go；
- Kotlin + Jetpack Compose Android 客户端；
- Protobuf 契约，同时提供 REST v1 与 Connect v1；
- Docker 是主要部署方式，持久状态收敛在单一数据目录。

必须保持：

- 一个实例只有一个账号；
- `memo` 是唯一内容单位，中文 UI 使用“记录”；
- AI 总结和回答以来源记录为依据；
- 不引入多人、公开分享、社交、标签、任务、知识库或复杂网盘能力；
- 不增加内置备份 UI、服务端备份 API 或备份 CLI；
- 公网入口、TLS、DNS、隧道、CDN 和边缘网络服务由部署者在项目外独立管理，仓库不内置第三方网络平台连接器、令牌、专属配置或部署流程；
- 边缘网络平台提供的 AI 服务只可由用户作为通用兼容端点自行配置，不增加平台专属 Provider 预设、适配器或默认值；
- Android 同步由用户手动触发，离线附件完整同步尚未实现。

## 事实来源

| 主题 | 入口 |
| --- | --- |
| 开发环境、生成物、验证、提交 | `CONTRIBUTING.md` |
| 模块职责、数据/API 边界 | `docs/development/architecture.md` |
| 产品范围、术语、AI 行为 | `docs/development/product-guidance.md` |
| 安全、认证与外部请求 | `docs/development/security.md` |
| 同步、幂等、冲突 | `docs/development/api/sync.md` |
| Web 视觉与交互 | `docs/development/design/README.md` |
| 部署与有效配置 | `docs/user/deployment.md` |
| 数据、备份与恢复 | `docs/user/data.md` |
| AI 使用与外部数据 | `docs/user/ai.md` |

文档与实现冲突时，以对应代码事实源为准，并修正文档：

- 运行配置：`cmd/sillage/main.go`、`internal/profile/profile.go`
- 数据库：`store/migration/sqlite/LATEST.sql`、`store/migrator.go`
- API：`proto/api/v1/`、`server/*_routes.go`、`server/api_service.go`
- Web 样式：`web/src/styles/app.css`、`web/src/components/ui.ts`
- 安全与密钥：`server/auth/`、`server/auth_routes.go`、`server/attachment_routes.go`、`internal/secret/`
- CI 与容器：`.github/workflows/ci.yml`、`scripts/`

## 改动契约

- Proto 变化：运行 `buf lint`、`buf generate`，提交生成物，并同步 REST、Web、Android 与测试。
- 数据库变化：同时更新新库 `LATEST.sql`、已存在数据库兼容迁移和迁移测试。
- Web 变化：运行 Web lint/typecheck/test/build，提交最新 `server/router/frontend/dist/`。
- 写操作：继续使用版本检查、tombstone 和同步幂等规则，不引入静默覆盖。
- UI 异步流程：隔离迟到响应，进行中状态锁定冲突操作，失败保留用户输入并允许重试。
- 认证、附件、密钥或外部数据流变化：先读安全开发边界，补泄漏、越权或迁移测试，并同步用户文档。
- AI Prompt 或来源选择变化：同步服务端与 Android；语义变化更新 `promptVersion`，并测试来源约束和信息不足分支。
- 部署或数据变化：先核对安全绑定、代理信任边界和可回滚的数据操作。

完整命令和人工验收范围见 `CONTRIBUTING.md`。

## 指导维护

- 本文件是项目代理规则的唯一来源，`AGENTS.md` 只负责引导读取。
- 只记录稳定边界和反复出现的问题；版本、命令和实现事实放在对应工程文档或代码中。
- 用户纠正若揭示稳定、反复的问题，更新对应事实文档或测试；一次性要求不持久化。
- 推送、部署、删除外部资源和轮换密钥必须由用户明确要求。
- 可由测试、lint 或 CI 强制的规则，不在本文重复描述。

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
