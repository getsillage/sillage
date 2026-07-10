<p align="center">
  <img src="web/public/sillage-icon.svg" alt="Sillage" width="96" height="96" />
</p>

<h1 align="center">Sillage</h1>

Sillage 是一个自托管的个人记录空间，用来保存日常片段、查看历史，并基于自己的记录做 AI 总结与问答。

它面向单人使用。首次打开时创建唯一账号，之后需要登录才能访问记录、附件、总结和问答。

## 你可以用它做什么

- 使用 Markdown 写下每天发生的事、想法和感受，通过速记随时捕捉片段，并上传图片或文件。
- 搜索记录，按列表或日历回看历史，查看「那年今日」，并用置顶和归档整理内容。
- 在桌面或移动浏览器中使用响应式界面；首次跟随系统主题，也可以手动切换浅色或深色。
- 按记录恢复未提交草稿；离开未保存页面或遇到服务器版本冲突时，由界面明确确认如何处理。
- 配置 Anthropic、OpenAI 或 OpenAI 兼容服务，测试连接、选择模型，并手动或自动生成记录总结。
- 根据已有记录进行问答、追问或重新生成回答，查看引用来源，并把回答保存为新记录。
- 使用原生 Android 客户端在线连接实例，或在本机离线记录；需要时再手动拉取、推送或双向同步。

Sillage 不提供公开主页、多人协作、社交分享、公开探索或官方托管云服务，也不在后台自动同步或发送推送通知。在线数据保存在你自己的实例中；Android 离线数据加密保存在设备本地，只有手动同步后才会写入实例。

## 快速开始

最简单的方式是使用 Docker：

```bash
docker build -t sillage:latest -f scripts/Dockerfile .
docker run --rm -p 127.0.0.1:5231:5231 -v "$HOME/.sillage:/var/opt/sillage" sillage:latest
```

然后打开：

```text
http://localhost:5231
```

首次访问会进入初始化页面。创建账号后，这个实例就只允许这个账号登录。

上面的端口映射只允许本机访问。需要让局域网设备直接连接时，可改为 `-p 5231:5231`，并自行配置防火墙；公网使用应放在 HTTPS 反向代理或 Tunnel 后面。

服务提供两个无需登录的探针：

```bash
curl http://localhost:5231/healthz
curl http://localhost:5231/readyz
```

`/healthz` 检查进程是否存活，`/readyz` 还会检查 SQLite 是否可用。

也可以使用 Compose：

```bash
docker compose -f scripts/compose.yaml up -d --build sillage
docker compose -f scripts/compose.yaml logs -f sillage
```

Compose 默认把端口发布到宿主机可用接口；请只在可信网络中使用，或调整端口绑定后交给本机反向代理。

## 数据目录

Docker 默认把持久数据放在容器内 `/var/opt/sillage`。上面的命令会把它映射到本机：

```text
$HOME/.sillage
```

目录中包含：

```text
sillage.db
assets/attachments/
.thumbnail_cache/
runtime/
```

`runtime/` 里保存自动生成的运行密钥。备份时请复制整个数据目录，不要只复制 `sillage.db`。

记录正文、附件和 SQLite 文件不会额外做静态加密，请保护数据目录和备份的文件权限。备份前应先停止服务，完整步骤见 [数据与备份](docs/user/data.md)。

## 常用配置

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SILLAGE_ADDR` | 空（Docker 为 `0.0.0.0`） | HTTP 绑定地址；空值同样监听可用接口，需要仅本机访问时应显式限制端口映射或绑定地址 |
| `SILLAGE_PORT` | `5231` | HTTP 监听端口 |
| `SILLAGE_DATA` | Docker 为 `/var/opt/sillage` | 持久数据目录；本机直接运行时若 `/var/opt/sillage` 不存在则回退到当前目录 |
| `SILLAGE_DSN` | `$SILLAGE_DATA/sillage.db` | SQLite 数据库路径 |
| `SILLAGE_MAX_UPLOAD_MB` | `30` | 单个附件上传上限 |
| `SILLAGE_INSTANCE_URL` | 空 | 外部访问地址，反向代理或 Tunnel 场景可设置 |
| `SILLAGE_LOG_FORMAT` | `json` | `json` 或 `text` |
| `SILLAGE_LOG_LEVEL` | `info` | `debug`、`info`、`warn`、`error` |
| `SESSION_SECRET` | 自动生成 | 登录会话签名密钥 |
| `ENCRYPTION_SECRET` | 自动生成 | AI API key 加密密钥 |

`SESSION_SECRET` 和 `ENCRYPTION_SECRET` 可以省略；Sillage 会在首次启动时生成并保存到数据目录。也支持通过 `SILLAGE_DSN_FILE`、`SESSION_SECRET_FILE`、`ENCRYPTION_SECRET_FILE` 注入文件内容；同一个变量不能同时使用普通值和 `_FILE` 形式。

## 使用 AI

登录 Web 端后，在设置里添加 AI 档案。API key 会用 `ENCRYPTION_SECRET` 加密后保存，页面不会再次显示明文 key。

AI 功能只基于你的记录生成总结或回答问题。回答中会展示来源记录，方便回到原文确认。

生成总结或回答时，相关记录内容会发送到你配置的 AI 服务。Sillage 不提供托管模型，也无法替代该服务提供方的隐私政策；请只配置你信任的服务。

## Android 客户端

Android 客户端位于 [android/](android/)，支持 Android 8.0 及以上版本，适合在手机上连接自己的 Sillage 实例或离线使用。

- 在线和离线模式都支持记录列表、日历、搜索、新建、编辑、删除、置顶、归档、AI 总结和问答。
- 在线模式支持上传附件；受保护附件由 App 携带认证下载，再以只读方式交给系统查看器。
- 本地数据可以导入和导出；在线数据与离线数据之间支持手动拉取、推送或双向同步。
- 当前不提供后台自动同步或推送通知，离线附件字节与元数据的完整同步仍未实现。
- 会话和离线数据使用 Android Keystore 加密保存，但仍应启用设备锁屏并保护导出的数据文件。

- Android 模拟器访问本机 Docker 服务时，服务器地址填 `http://10.0.2.2:5231`。
- 真机需要填写手机可以访问到的局域网或公网地址。
- 生产使用建议通过 HTTPS 反向代理或 Cloudflare Tunnel 暴露服务。

更多说明见 [Android 使用说明](android/README.md)。

## 架构与 API

Sillage 是一个 Go 单体服务：React Web 构建后嵌入二进制，业务数据保存在 SQLite，附件字节保存在同一数据目录。REST 和 Connect 复用同一套服务端业务边界，Web 与 Android 通过这些接口访问实例。

- REST v1：`/api/v1/*`
- Connect v1：`/sillage.api.v1.<Service>/<Method>`
- Protobuf 源：[proto/api/v1/](proto/api/v1/)
- Proto 生成的 OpenAPI 契约：[proto/gen/openapi/openapi.yaml](proto/gen/openapi/openapi.yaml)
- 分页、版本冲突和手动同步语义：[同步 API](docs/development/api/sync.md)

OpenAPI 文件描述 Proto 覆盖的接口；附件上传、问答流式响应等手写 REST 扩展以服务端实现和开发文档为准。

## 本地开发

后端需要 Go 1.25；Web 推荐使用 Node.js 24 和 pnpm 11。修改 Proto 需要安装 Buf，构建 Android 还需要 JDK 17 和 Android SDK。

### 启动前后端

首次开发先安装 Web 依赖：

```bash
pnpm --dir web install
```

在仓库根目录启动后端。`.data-dev/` 已加入 `.gitignore`，账号、记录和运行密钥会在重启后继续保留：

```bash
export SILLAGE_ADDR="127.0.0.1"
export SILLAGE_DATA="$PWD/.data-dev"
export SILLAGE_LOG_FORMAT="text"
mkdir -p "$SILLAGE_DATA"
go run ./cmd/sillage
```

需要一次性空实例时，可以改用临时目录；服务启动日志会打印实际数据路径：

```bash
SILLAGE_ADDR="127.0.0.1" \
  SILLAGE_DATA="$(mktemp -d)" \
  SILLAGE_LOG_FORMAT="text" \
  go run ./cmd/sillage
```

在另一个终端启动 Web 开发服务器：

```bash
pnpm --dir web dev
```

打开 `http://localhost:5173`。Vite 会热更新前端，并把 API、附件和 Connect 请求代理到 `http://localhost:5231`；修改 Go 代码后需要重启后端。

生产构建会写入 Go 的嵌入目录。验证内嵌页面时，应先构建 Web，再编译或启动 Go 服务：

```bash
pnpm --dir web build
go build ./cmd/sillage
```

### 管理本地数据

使用默认 `SILLAGE_DSN` 时，`SILLAGE_DATA` 是一个实例的完整持久化单元，主要包含：

```text
sillage.db
sillage.db-wal
sillage.db-shm
assets/attachments/
.thumbnail_cache/
runtime/secrets.json
```

WAL/SHM 文件只会在 SQLite 使用对应模式时出现。记录、AI 设置和会话等业务数据保存在 SQLite；附件字节、缩略图缓存和运行密钥分别保存在其他目录。

如果把 `SILLAGE_DSN` 显式指向 `SILLAGE_DATA` 外部，备份、迁移或重置时还必须单独处理该 SQLite 文件及其 WAL/SHM 伴随文件。为避免遗漏，普通部署和本地开发建议沿用默认 DSN。

- 修改单条记录、归档、AI 设置等业务数据时，使用 Web、Android 或 API，不要直接写 SQLite。直接 SQL 会绕过版本号、tombstone、全文索引和附件引用等业务约束。
- `.thumbnail_cache/` 是可再生缓存，可以在服务停止后单独删除；`runtime/` 不是缓存，保留数据库时不要单独删除它，否则现有会话会失效，已加密的 AI API key 也可能无法解开。

需要只读检查时，先停止后端，再使用系统的 `sqlite3`。

查看开发实例及附件占用空间：

```bash
du -sh "$SILLAGE_DATA" "$SILLAGE_DATA/assets/attachments"
```

检查数据库结构版本和完整性：

```bash
sqlite3 -readonly "$SILLAGE_DATA/sillage.db" \
  "SELECT key, value FROM system_setting WHERE key = 'schema_version';"
sqlite3 -readonly "$SILLAGE_DATA/sillage.db" "PRAGMA integrity_check;"
```

### 数据库结构迁移

Sillage 没有独立的 `migrate` 或 rollback CLI，也没有按版本依次回放的通用迁移链。每次启动都会在监听端口前运行内置迁移逻辑：

- 空数据库使用 `store/migration/sqlite/LATEST.sql` 一次性创建最新结构。
- 已初始化且仍受支持的数据库执行 `store/migrator.go` 中的兼容检查和增量修补。
- 迁移失败时服务不会开始监听。先保留现场和日志，不要通过手工改表或改 `schema_version` 绕过失败。

`LATEST.sql` 只用于空库初始化，不是增量升级脚本，绝不能手工对已有数据库执行。升级 Sillage 前应停止服务并备份整个数据目录。开发新的结构变化时，要同时更新空库结构、现有数据库的兼容迁移和对应测试；不能只修改 `LATEST.sql`。当前不支持从旧 Cloudflare/D1 部署自动导入数据。

### 迁移整个实例

在本机目录、磁盘或另一台主机之间迁移时，先停止源实例和目标实例，再复制完整数据目录。目标路径应事先不存在：

```bash
SOURCE="/absolute/path/to/old-sillage-data"
TARGET="/absolute/path/to/new-sillage-data"

if [ -e "$TARGET" ]; then
  printf '目标已存在，停止复制：%s\n' "$TARGET"
else
  cp -a "$SOURCE" "$TARGET"
fi
```

确认目标目录同时包含数据库、附件和 `runtime/` 后，再让新实例使用它：

```bash
SILLAGE_ADDR="127.0.0.1" SILLAGE_DATA="$TARGET" go run ./cmd/sillage
```

在另一个终端确认数据库已经就绪：

```bash
curl http://localhost:5231/readyz
```

Docker 默认的宿主机源目录是 `$HOME/.sillage`；迁移到其他位置后，将新路径挂载到容器的 `/var/opt/sillage`。不要只复制 `sillage.db`，也不要让源实例和目标实例同时写同一份数据库。通过网络传输的数据目录或压缩包本身没有额外加密，应使用受保护的传输通道并限制文件权限。

### Android 导入导出与同步

Android 客户端的数据文件、手动同步和服务端整实例迁移是三件不同的事：

- 「导入完整数据」按资源 ID 合并到客户端本机，不会先清空现有数据，也不会直接写入服务端；导入后仍需由用户明确触发向实例同步。
- 在线模式「导出完整数据」会先拉取服务端可同步资源并与本机数据合并，再导出 JSON。该 JSON 是明文，不包含 API key、账号、token/cookie、服务器地址或附件字节，应按敏感文件保护。
- 「同步到本地」拉取并合并服务端记录、记录总结、问答和 AI 设置；「同步到云端」当前只推送待同步记录；「双向同步」先推送记录，再拉取服务端资源。
- 离线附件字节和元数据的完整同步仍未实现，因此 Android JSON 和手动同步都不能替代服务端数据目录备份。

### 备份与恢复开发数据

先停止后端，再把整个开发目录复制到仓库外，并限制新文件的默认权限：

```bash
umask 077
BACKUP="$HOME/.sillage-backups/dev-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$(dirname "$BACKUP")"
cp -a "$SILLAGE_DATA" "$BACKUP"
printf '备份已写入：%s\n' "$BACKUP"
```

备份目录本身没有额外加密。恢复时同样先停止后端，把当前目录移开，再将某个确认过的完整备份复制回 `SILLAGE_DATA`。更完整的 Docker 备份与恢复步骤见 [数据与备份](docs/user/data.md)。

### 删除或重置数据

在界面中删除单条记录会保留同步所需的删除标记（tombstone），不等于立即从 SQLite 物理清除。Sillage 当前没有账号重置或全量清空 API；要重新进入唯一账号初始化流程，需要重置整个实例数据目录。

重置本地开发数据前先停止后端，并在仓库根目录运行下面的保护性脚本。它只允许删除当前仓库的 `.data-dev`：

```bash
EXPECTED="$PWD/.data-dev"

if [ "${SILLAGE_DATA:-}" = "$EXPECTED" ]; then
  rm -rf -- "$EXPECTED"
  mkdir -p "$EXPECTED"
  printf '开发数据已重置：%s\n' "$EXPECTED"
else
  printf '拒绝删除：SILLAGE_DATA 不是 %s\n' "$EXPECTED"
fi
```

重新启动后端并刷新页面后即可创建新账号。浏览器中的主题、侧栏状态和未提交草稿保存在站点存储中，不属于 `SILLAGE_DATA`；需要完全清空 Web 开发状态时，还要在浏览器开发者工具中清除 `localhost:5173` 和 `localhost:5231` 的站点数据。

重置 Docker 实例时，先停止服务，再把现有目录重命名保留，而不是直接永久删除：

```bash
docker compose -f scripts/compose.yaml down

if [ -d "$HOME/.sillage" ]; then
  RESET_COPY="$HOME/.sillage.reset-$(date +%Y%m%d-%H%M%S)"
  if mv "$HOME/.sillage" "$RESET_COPY"; then
    printf '旧数据已保留在：%s\n' "$RESET_COPY"
  fi
fi

mkdir -p "$HOME/.sillage"
docker compose -f scripts/compose.yaml up -d sillage
```

`docker compose down -v` 也不会删除这个宿主机 bind mount，因此仍需按上面的步骤处理 `$HOME/.sillage`。确认新实例正常且旧数据确实不再需要后，再明确选择打印出的 `.sillage.reset-*` 目录进行永久删除。删除数据目录或备份不可撤销，而且可能同时删除记录、附件和解密 AI key 所需的密钥。

### 验证改动

常用验证命令：

```bash
go test -count=1 ./...
go vet ./...

pnpm --dir web lint
pnpm --dir web test
pnpm --dir web typecheck
pnpm --dir web build

buf lint
buf generate

cd android
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
```

Web 端到端测试使用 `pnpm --dir web test:e2e`，需要先安装 Playwright 浏览器并启动可访问的实例。更完整的维护说明见 [开发资料](docs/development/README.md)。

## 反向代理和 Tunnel

Sillage 自身只提供 HTTP 服务。对公网访问时，建议在外层使用 Caddy、Nginx、Cloudflare Tunnel 或其他代理提供 HTTPS。

Compose 文件内置了 Cloudflare Tunnel profile：

```bash
CLOUDFLARED_TOKEN=... docker compose -f scripts/compose.yaml --profile tunnel up -d
```

Tunnel 的服务地址指向：

```text
http://sillage:5231
```

这个 profile 只启动连接器，不会自动创建公网 hostname 或 ingress；需要先在 Cloudflare 中把对应路由配置到上述服务地址。

如果使用反向代理，请转发 `X-Forwarded-Proto`、`X-Forwarded-Host` 和 `X-Forwarded-For`。需要固定外部地址时设置 `SILLAGE_INSTANCE_URL`。

## 更多文档

- [使用与部署](docs/user/deployment.md)
- [数据与备份](docs/user/data.md)
- [文档目录](docs/README.md)
- [开发资料](docs/development/README.md)

## 许可证

Sillage 使用 [MIT License](LICENSE)。
