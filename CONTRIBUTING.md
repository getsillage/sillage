# 贡献指南

本文是本仓库开发环境、生成物和质量门禁的统一入口。产品边界见[产品指导](docs/development/product-guidance.md)，模块职责见[架构说明](docs/development/architecture.md)。

## 环境

| 范围 | 要求 |
| --- | --- |
| Go | Go 1.25 |
| Web | Node.js 24、pnpm 11.9 |
| Proto | Buf CLI 1.71 |
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

访问 `http://localhost:5173`。Vite 仅监听 `127.0.0.1`，并把 API、附件和 Connect 请求代理到 `http://localhost:5231`。受信任局域网调试才使用 `pnpm --dir web dev:lan`；它会将本机后端间接暴露给局域网设备，空实例尤其不能使用。

## 修改规则

- 保持单人私密记录产品边界，不引入多人、公开分享、社交、标签、任务或知识库能力。
- 只修改完成当前目标所需的文件；功能、契约、配置或架构变化必须同步文档。
- 后端、数据库和 API 使用 `memo`；中文界面使用“记录”。
- 不直接编辑 `proto/gen/` 或 `server/router/frontend/dist/`。
- 不提交真实密钥、数据库、附件、APK/AAB、keystore、`local.properties` 或设备缓存。

### API 契约

1. 修改 `proto/api/v1/`。
2. 运行 `buf lint`、`buf breaking --against '.git#branch=main'` 和 `buf generate`，提交 `proto/gen/` 生成物。
3. 同步受影响的手写 REST 路由、[REST API 说明](docs/development/api/README.md)、`web/src/lib/api.ts` 和 Android `SillageApi.kt`。
4. 同时覆盖 REST 与 Connect 行为测试。

`proto/gen/openapi/openapi.yaml` 是 Proto HTTP 注解的生成投影，不是 Echo REST 的完整契约，不能直接用于 REST SDK codegen。上传和 SSE 等扩展以 REST API 说明与 `server/*_routes.go` 为准。

### 数据库结构

新库结构写在 `store/migration/sqlite/LATEST.sql`，已存在数据库的兼容升级按 `store/migrator.go` 中的顺序执行。兼容步骤必须幂等、可重入，并在成功后把 `schema_version` 升到当前版本；新二进制拒绝低于最低支持版本或高于当前版本的数据库。结构变化必须同步更新当前/最低 schema 版本、两处结构与从最低支持版本升级的测试；不要把 `LATEST.sql` 当作增量脚本执行。降级始终要求恢复升级前的完整数据备份。

### Web 产物

`pnpm --dir web build` 会覆盖 `server/router/frontend/dist/`。Web 源码与最新嵌入产物必须一同提交；构建 Go 二进制前先生成 Web 产物。

## 验证

按影响范围至少运行对应命令。CI 会运行 Go test/vet/build、Buf lint/breaking/generate、Web lint/typecheck/test/build、Android test/lint/build、空实例 E2E、Docker build 与 Compose 解析，并检查依赖元数据、Docker 上下文策略、Proto/Web 生成物、Markdown 链接和提交范围空白。Dependabot 每周检查 Go、Web、Android、Docker 与 GitHub Actions 依赖；安全更新仍须经过同一套门禁。Docker 构建前必须先检查上下文策略，确保 Git 忽略的本地数据、密钥和构建产物不会发送给构建器。

| 范围 | 命令 |
| --- | --- |
| Go | `go mod tidy -diff`、`go test -count=1 ./...`、`go vet ./...`、`go build ./cmd/sillage` |
| Web | `pnpm --dir web lint`、`pnpm --dir web typecheck`、`pnpm --dir web test`、`pnpm --dir web build` |
| Proto | `buf lint`、`buf breaking --against '.git#branch=main'`、`buf generate`，然后检查生成物 diff |
| Android | `cd android && ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` |
| 文档与收尾 | `node scripts/check-markdown-links.mjs`、`git diff --check` |
| 部署 | `node scripts/check-docker-context.mjs`、`docker build --build-arg VERSION=dev --build-arg REVISION="$(git rev-parse HEAD)" -t sillage:dev -f scripts/Dockerfile .`、`docker compose -f scripts/compose.yaml config` |

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
E2E_FRESH_INSTANCE=1 pnpm --dir web test:e2e
```

涉及 UI 的改动还要按 [Web 设计规范](docs/development/design/README.md)做明暗主题与桌面/移动端人工检查。Android 的编辑、附件和网络状态改动必须在模拟器或真机检查系统返回、软键盘、慢网络取消和外部文件查看器。

## 发布

GitHub Releases 是用户可见变更记录的唯一来源，不另行维护 `CHANGELOG.md`。发布从已通过 CI 的 `main` 提交进行；README 和文档中心必须链接 Releases。

1. 整理用户可见变化，并明确数据库、配置、同步或数据格式的兼容性影响；需要特殊升级步骤时同步更新部署与数据文档。
2. 发布 Android APK 时递增 `android/app/build.gradle.kts` 的 `versionCode`，并让 `versionName` 与 `vX.Y.Z` tag 一致。
3. 运行“验证”章节的对应门禁和 Docker 构建。Android release 还要按 [Android 说明](android/README.md)完成签名、`apksigner` 与 `zipalign` 校验。
4. 提交发布准备后创建带说明的 `vX.Y.Z` tag，再从该 tag 创建 GitHub Release。发布构建使用 `VERSION=vX.Y.Z`、`REVISION=$(git rev-parse HEAD)`，二进制的 `--version` 与镜像 OCI labels 必须能映射回 tag 与提交。
5. 发布说明至少包含主要变化、已知限制和升级/回滚要求；存在可下载产物时还要包含校验值。不得提交 keystore、签名配置或构建产物。

## 提交

提交信息遵循 Conventional Commits：

```text
<type>(<scope>): <subject>
```

常用 `type`：`feat`、`fix`、`docs`、`refactor`、`test`、`chore`、`ci`。一次提交应保持单一目的，并包含对应测试、生成物和文档。
