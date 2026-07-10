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

先启动后端：

```bash
SILLAGE_DATA="$(mktemp -d)" go run ./cmd/sillage
```

再在另一个终端启动 Web 开发服务器：

```bash
pnpm --dir web install
pnpm --dir web dev
```

打开 `http://localhost:5173`。Vite 会把 API、附件和 Connect 请求代理到 `http://localhost:5231`。生产构建会写入 Go 的嵌入目录，因此前端改动后应按顺序执行：

```bash
pnpm --dir web build
go build ./cmd/sillage
```

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
