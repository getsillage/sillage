# 部署说明

Sillage 适合运行在单机 Docker 中。服务自身只提供 HTTP；公网访问必须放在 HTTPS 反向代理或 Tunnel 后面。

## Docker

构建镜像：

```bash
docker build -t sillage:latest -f scripts/Dockerfile .
```

只允许本机访问：

```bash
docker run --rm \
  -p 127.0.0.1:5231:5231 \
  -v "$HOME/.sillage:/var/opt/sillage" \
  sillage:latest
```

打开 `http://localhost:5231`，首次访问时创建唯一账号。

不要在未配置防火墙和 HTTPS 的主机上使用 `-p 5231:5231`，它会把端口发布到宿主机可用接口。

## Compose

仓库内的 Compose 默认只发布到回环地址：

```bash
docker compose -f scripts/compose.yaml up -d --build sillage
```

需要让受信任的局域网设备直连时，可显式设置 `SILLAGE_HOST_PORT=5231`，并同时配置宿主机防火墙。公网部署仍应保持回环绑定，由反向代理或 Tunnel 访问容器网络。

常用操作：

```bash
docker compose -f scripts/compose.yaml logs -f sillage
docker compose -f scripts/compose.yaml stop sillage
docker compose -f scripts/compose.yaml start sillage
```

Compose 的 `SILLAGE_HOST_PORT` 默认是 `127.0.0.1:5231`，只控制宿主机发布地址；应用环境变量在 `scripts/compose.yaml` 中显式声明，修改前先检查该文件，不能假定同名宿主机变量会自动透传。

修改端口绑定后必须再次执行 `docker compose -f scripts/compose.yaml up -d sillage` 重建现有容器，并用 `docker compose -f scripts/compose.yaml ps` 确认发布地址；只修改 YAML 不会改变正在运行的容器。

## 配置

应用同时支持命令行 flag 和 `SILLAGE_*` 环境变量。以下是当前实际生效的常用变量：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SILLAGE_ADDR` | 空 | HTTP 绑定地址；空值会监听可用接口，直接运行时建议设为 `127.0.0.1` |
| `SILLAGE_PORT` | `5231` | HTTP 端口 |
| `SILLAGE_DATA` | 见下文 | 数据目录；Docker 为 `/var/opt/sillage` |
| `SILLAGE_DSN` | `$SILLAGE_DATA/sillage.db` | SQLite 路径；相对路径按数据目录解析 |
| `SILLAGE_MAX_UPLOAD_MB` | `30` | 单个附件上限，单位 MiB |
| `SILLAGE_LOG_FORMAT` | `json` | `json` 或 `text` |
| `SILLAGE_LOG_LEVEL` | `info` | `debug`、`info`、`warn`、`error` |
| `SESSION_SECRET` | 自动生成 | 会话签名密钥 |
| `ENCRYPTION_SECRET` | 自动生成 | AI API key 加密密钥 |

本机直接运行时，如果 `/var/opt/sillage` 已存在，默认使用该目录，否则回退到当前目录。生产环境应始终显式设置 `SILLAGE_DATA`。

`SILLAGE_DSN`、`SESSION_SECRET` 和 `ENCRYPTION_SECRET` 支持对应的 `_FILE` 变量，例如 `ENCRYPTION_SECRET_FILE=/run/secrets/encryption`。普通值与 `_FILE` 不能同时设置。容器使用 `_FILE` 时还要挂载该文件并显式传入变量；宿主环境不会自动透传。外置数据库和 secret 文件不在 `SILLAGE_DATA` 内，必须纳入同一套备份与恢复流程。

容器入口还支持 `SILLAGE_UID` 和 `SILLAGE_GID`，默认均为 `10001`，用于调整挂载目录所有权并以非 root 用户运行进程。Compose 未透传这两个变量；需要自定义时应显式修改 Compose 的 `environment` 或使用 `docker run -e`。

AI Provider、模型和 API key 登录后在应用设置中配置，不使用进程环境变量。配置前先阅读[AI 使用与隐私](ai.md)。

## 本机运行

需要 Go 1.25。生产式运行前先生成 Web 嵌入产物：

```bash
pnpm --dir web install
pnpm --dir web build
go build -o sillage ./cmd/sillage
SILLAGE_ADDR=127.0.0.1 SILLAGE_DATA="$HOME/.sillage" ./sillage
```

开发环境见[贡献指南](../../CONTRIBUTING.md)。

## 反向代理与 Tunnel

代理应终止 TLS，并覆盖客户端传入的以下请求头：

```text
X-Forwarded-Proto
X-Forwarded-Host
X-Forwarded-For
```

不要简单追加不可信的转发头。Sillage 使用 `X-Forwarded-Proto` 判断 Cookie 是否标记为 Secure，并使用 `X-Forwarded-For` 参与登录限流；后端端口应只允许代理访问。

Compose 提供可选的 Cloudflare Tunnel 连接器：

```bash
CLOUDFLARED_TOKEN=... \
  docker compose -f scripts/compose.yaml --profile tunnel up -d
```

Cloudflare 侧的服务地址应指向 `http://sillage:5231`。该 profile 不会自动创建 hostname 或 ingress，并且不会移除 Sillage 的宿主机端口映射。

## 探针与升级

```bash
curl --fail http://localhost:5231/healthz
curl --fail http://localhost:5231/readyz
```

`healthz` 只检查进程，`readyz` 还检查 SQLite。升级前：

1. 用版本化 tag 保留当前镜像，例如 `docker tag sillage:latest sillage:rollback-YYYYMMDD`。
2. 按[数据与备份](data.md)停止服务并完成整目录备份。
3. 构建新镜像并启动，确认探针、登录、记录和附件正常。
4. 失败时停止新实例，恢复对应数据备份，再用保留的旧镜像启动。

启动迁移失败时服务不会进入 ready 状态。旧二进制未必兼容已升级数据库，因此不能只回滚镜像而不恢复配套数据。
