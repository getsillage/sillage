# 贡献指南

本文是本仓库开发环境、生成物和质量门禁的统一入口。产品边界见[产品指导](docs/development/product-guidance.md)，模块职责见[架构说明](docs/development/architecture.md)。

## 环境

| 范围 | 要求 |
| --- | --- |
| Go | Go 1.25 |
| Web | Node.js 24、pnpm 11 |
| Proto | Buf CLI |
| Android | JDK 17、Android SDK 35 |
| 容器 | Docker；Compose 可选 |

## 本地启动

安装 Web 依赖：

```bash
pnpm --dir web install
```

启动后端；`.data-dev/` 已被 Git 忽略：

```bash
export SILLAGE_ADDR=127.0.0.1
export SILLAGE_DATA="$PWD/.data-dev"
export SILLAGE_LOG_FORMAT=text
go run ./cmd/sillage
```

另开终端启动 Web 开发服务器：

```bash
pnpm --dir web dev
```

访问 `http://localhost:5173`。Vite 会把 API、附件和 Connect 请求代理到 `http://localhost:5231`。

## 修改规则

- 保持单人私密记录产品边界，不引入多人、公开分享、社交、标签、任务或知识库能力。
- 只修改完成当前目标所需的文件；功能、契约、配置或架构变化必须同步文档。
- 后端、数据库和 API 使用 `memo`；中文界面使用“记录”。
- 不直接编辑 `proto/gen/` 或 `server/router/frontend/dist/`。
- 不提交真实密钥、数据库、附件、APK/AAB、keystore、`local.properties` 或设备缓存。

### API 契约

1. 修改 `proto/api/v1/`。
2. 运行 `buf lint` 和 `buf generate`，提交 `proto/gen/` 生成物。
3. 同步受影响的手写 REST 路由、`web/src/lib/api.ts` 和 Android `SillageApi.kt`。
4. 同时覆盖 REST 与 Connect 行为测试。

OpenAPI 只覆盖 Proto 声明的接口；手写的上传和 SSE 等扩展以 `server/*_routes.go` 为准。

### 数据库结构

新库结构写在 `store/migration/sqlite/LATEST.sql`，已存在数据库的兼容升级写在 `store/migrator.go`。结构变化必须同时更新两处并补迁移测试；不要把 `LATEST.sql` 当作增量脚本执行。

### Web 产物

`pnpm --dir web build` 会覆盖 `server/router/frontend/dist/`。Web 源码与最新嵌入产物必须一同提交；构建 Go 二进制前先生成 Web 产物。

## 验证

按影响范围至少运行对应命令。CI 会运行 Go test/vet/build、Buf lint/generate、Web lint/typecheck/test/build、Android test/lint/build，并检查 Proto/Web 生成物与 Markdown 链接；E2E、Docker 与人工验收仍是本地门禁。

| 范围 | 命令 |
| --- | --- |
| Go | `go test -count=1 ./...`、`go vet ./...`、`go build ./cmd/sillage` |
| Web | `pnpm --dir web lint`、`pnpm --dir web typecheck`、`pnpm --dir web test`、`pnpm --dir web build` |
| Proto | `buf lint`、`buf generate`，然后检查生成物 diff |
| Android | `cd android && ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` |
| 文档与收尾 | `node scripts/check-markdown-links.mjs`、`git diff --check` |
| 部署 | `docker build -t sillage:latest -f scripts/Dockerfile .` |

Web E2E 针对已运行的实例执行。先准备浏览器和嵌入产物：

```bash
pnpm --dir web exec playwright install
pnpm --dir web build
```

在第一个终端启动空实例：

```bash
SILLAGE_DATA="$(mktemp -d)" SILLAGE_ADDR=127.0.0.1 go run ./cmd/sillage
```

实例 ready 后，在第二个终端执行：

```bash
pnpm --dir web test:e2e
```

涉及 UI 的改动还要按 [Web 设计规范](docs/development/design/README.md)做明暗主题与桌面/移动端人工检查。Android 的编辑、附件和网络状态改动必须在模拟器或真机检查系统返回、软键盘、慢网络取消和外部文件查看器。

## 提交

提交信息遵循 Conventional Commits：

```text
<type>(<scope>): <subject>
```

常用 `type`：`feat`、`fix`、`docs`、`refactor`、`test`、`chore`、`ci`。一次提交应保持单一目的，并包含对应测试、生成物和文档。
