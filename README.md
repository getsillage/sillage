<p align="center">
  <img src="web/public/sillage-icon.svg" alt="Sillage" width="96" height="96" />
</p>

<h1 align="center">Sillage</h1>

Sillage 是一个自托管的个人记录空间，用来保存日常片段、查看历史，并基于自己的记录做 AI 总结与问答。

它面向单人使用。首次打开时创建唯一账号，之后需要登录才能访问记录、附件、总结和问答。

## 你可以用它做什么

- 写下每天发生的事、想法、感受或照片。
- 按列表或日历回看历史记录。
- 上传附件，图片和文件会保存在自己的数据目录里。
- 配置自己的 AI 服务，用记录生成总结。
- 根据已有记录提问，并回到回答引用的原始记录。
- 在浏览器使用完整功能，也可以用 Android 客户端记录和回看。

Sillage 不提供公开主页、多人协作、社交分享、公开探索或内置云同步。你的数据保存在自托管实例的数据目录中。

## 快速开始

最简单的方式是使用 Docker：

```bash
docker build -t sillage:latest -f scripts/Dockerfile .
docker run --rm -p 5231:5231 -v "$HOME/.sillage:/var/opt/sillage" sillage:latest
```

然后打开：

```text
http://localhost:5231
```

首次访问会进入初始化页面。创建账号后，这个实例就只允许这个账号登录。

也可以使用 Compose：

```bash
docker compose -f scripts/compose.yaml up -d --build sillage
docker compose -f scripts/compose.yaml logs -f sillage
```

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

## 常用配置

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SILLAGE_PORT` | `5231` | HTTP 监听端口 |
| `SILLAGE_DATA` | `/var/opt/sillage` | 持久数据目录 |
| `SILLAGE_DSN` | `$SILLAGE_DATA/sillage.db` | SQLite 数据库路径 |
| `SILLAGE_MAX_UPLOAD_MB` | `30` | 单个附件上传上限 |
| `SILLAGE_INSTANCE_URL` | 空 | 外部访问地址，反向代理或 Tunnel 场景可设置 |
| `SILLAGE_LOG_FORMAT` | `json` | `json` 或 `text` |
| `SILLAGE_LOG_LEVEL` | `info` | `debug`、`info`、`warn`、`error` |
| `SESSION_SECRET` | 自动生成 | 登录会话签名密钥 |
| `ENCRYPTION_SECRET` | 自动生成 | AI API key 加密密钥 |

`SESSION_SECRET` 和 `ENCRYPTION_SECRET` 可以省略；Sillage 会在首次启动时生成并保存到数据目录。也支持 `SILLAGE_DSN_FILE`、`SESSION_SECRET_FILE`、`ENCRYPTION_SECRET_FILE` 这类文件注入方式。

## 使用 AI

登录 Web 端后，在设置里添加 AI 档案。API key 会用 `ENCRYPTION_SECRET` 加密后保存，页面不会再次显示明文 key。

AI 功能只基于你的记录生成总结或回答问题。回答中会展示来源记录，方便回到原文确认。

## Android 客户端

Android 客户端位于 [android/](android/)，适合在手机上连接自己的 Sillage 实例。

- Android 模拟器访问本机 Docker 服务时，服务器地址填 `http://10.0.2.2:5231`。
- 真机需要填写手机可以访问到的局域网或公网地址。
- 生产使用建议通过 HTTPS 反向代理或 Cloudflare Tunnel 暴露服务。

更多说明见 [Android 使用说明](android/README.md)。

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

如果使用反向代理，请转发 `X-Forwarded-Proto`、`X-Forwarded-Host` 和 `X-Forwarded-For`。需要固定外部地址时设置 `SILLAGE_INSTANCE_URL`。

## 更多文档

- [使用与部署](docs/user/deployment.md)
- [数据与备份](docs/user/data.md)
- [文档目录](docs/README.md)
- [开发资料](docs/development/README.md)
