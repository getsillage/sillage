<p align="center">
  <img src="./public/sillage-icon.svg" alt="Sillage" width="96" height="96">
</p>

# Sillage

> 迁移中：仓库正在按 [`docs/migration/memos-style-self-hosted-plan.md`](docs/migration/memos-style-self-hosted-plan.md)
> 从 Cloudflare Workers 架构迁移到 memos 风格的 Go 自托管单体。旧 Workers 运行路径暂时保留，新的 Go
> 运行路径会按里程碑逐步替换它。

## Go 自托管迁移开发

当前已建立 Go 单体骨架：`cmd/sillage`、SQLite store/migration、Echo server、`/healthz`、`/readyz`
以及唯一账号初始化 / 登录 / refresh / 退出的基础 REST 端点。默认数据目录为 `/var/opt/sillage`；本地没有该目录时会使用当前目录，也可以显式指定 `SILLAGE_DATA`。

```bash
go test ./...
go vet ./...
go build ./cmd/sillage

SILLAGE_DATA="$(mktemp -d)" go run ./cmd/sillage
curl http://localhost:5231/healthz
curl http://localhost:5231/readyz
curl http://localhost:5231/api/v1/auth/bootstrap
```

生成的本地数据目录包含 `sillage.db`、`assets/attachments/`、`.thumbnail_cache/` 和 `runtime/`。
未显式配置 `SESSION_SECRET` / `ENCRYPTION_SECRET` 时，Sillage 会自动生成并持久化到
`runtime/secrets.json`。
本迁移不导入旧 Cloudflare 数据；新 SQLite 数据库从空库初始化。

# Sillage · Cloudflare Workers（旧运行路径）

Sillage 是一个完全运行在 Cloudflare 边缘平台上的**单用户个人记录空间**。它用于保存每天发生的事、想法和感受，也可以根据记录生成可追溯的总结。

技术栈：React Router v8（SSR）运行在 Worker 上，数据存储在 D1 / R2 / KV。服务端存储 + 静态加密（非端到端），因此支持全文搜索、记忆检索与 AI 总结。

产品指导文件见 [`docs/product/sillage.md`](docs/product/sillage.md)。

## 功能

- Markdown 写作：统一的个人记录
- 记录入口：统一编辑器，下面展示今日记录和最近历史
- 问答入口：关键词搜索、记录问答（多轮流式对话，可分支重生成与导出）
- 总结：保存记录后可生成单条总结，也可按时段或主题生成多条记录的总结
- 图片附件：上传前应用层 AES-256-GCM 加密后存入 R2，读取需登录
- 关键词搜索：D1 FTS5（trigram 分词，支持中文），覆盖记录正文
- 那年今日、月历、详情/编辑
- 编辑历史：每次修改留存版本快照，可回看改动
- 快速捕获：全局 ⌘/Ctrl+J 随手记一条，不打断当前页面
- 可选 AI：保存记录后异步生成单条总结（网页端配置，默认关闭）
- 备份：每日定时整库导出为 JSON + Markdown 到 R2，也可在网页端手动导出下载
- 单口令登录：PBKDF2 + KV 会话，登录失败按 IP 限流
- 版本标识：本地开发显示「开发版」，测试版部署显示「β版」

## 快速开始（本地开发）

```bash
npm install

# 1) 生成密钥与口令哈希（见下方「配置」）后，复制环境变量模板
cp .dev.vars.example .dev.vars   # 然后填入生成的值

# 2) 对本地 D1 应用迁移
npm run db:migrate:local

# 3) 可选：清空本地业务数据并填充当前设计样本数据
npm run db:seed:local

# 4) 启动开发服务器
npm run dev                      # http://localhost:5173

# 或允许同一局域网设备访问
npm run dev:lan                  # http://<你的局域网 IP>:5173
```

> 本地用 Miniflare 模拟 D1/R2/KV；搜索功能使用 D1 FTS5 关键词搜索。
> 重置本地样本数据前请先停止 `npm run dev`，避免 Miniflare D1 被并发访问后出现 SQLite 锁。

局域网调试时可用 `ipconfig getifaddr en0` 查看当前 Mac 的 Wi-Fi IP，例如
`http://192.168.1.23:5173`。本地 HTTP 访问会使用非 Secure 会话 cookie，以便手机
或其他设备完成登录；线上 HTTPS 访问仍会使用 Secure cookie。

## 配置

所有密钥放在 `.dev.vars`（本地，已 gitignore）或通过 `wrangler secret put`（远程）。仓库只提交 `.dev.vars.example` 作为模板。

### 发布通道

`APP_RELEASE_CHANNEL` 是非密钥运行时变量，放在 `wrangler.jsonc vars` 中。当前配置为 `beta`：部署到 Cloudflare Workers 后显示「β版」标签，并跳过登录守卫，适合公开测试。未设置或设置为 `production` 时保留单口令登录。本地开发始终显示「开发版」标签，且仍按登录逻辑运行。

### 必需密钥（secrets）

| 变量 | 用途 | 生成方式 |
|---|---|---|
| `APP_PASSWORD_HASH` | 登录口令的 PBKDF2 哈希 | `node scripts/hash-password.mjs '你的密码'` |
| `SESSION_SECRET` | 签名会话 cookie | `node -e "console.log(Buffer.from(crypto.getRandomValues(new Uint8Array(32))).toString('base64'))"` |
| `ATTACH_ENCRYPTION_KEY` | 附件 AES-256-GCM 密钥（base64，32 字节） | 同上 |

> `ATTACH_ENCRYPTION_KEY` 一旦用于加密附件就不能更换，否则已上传的图片无法解密。`SESSION_SECRET` 更换会使所有登录会话失效。

### AI 提供商（可选，默认关闭）

AI 配置全部在网页端管理，不再通过 `.dev.vars`、`wrangler.jsonc vars` 或 `wrangler secret put` 设置模型、Base URL、Provider 或 API Key。

登录后进入 `/settings`，可以保存多个 AI 配置档案，选择 **Anthropic** 或 **OpenAI 兼容**协议，填写 Base URL / API Key 后获取模型列表，或手动输入模型名称。保存时 API Key 会经 AES-256-GCM 加密后存于 KV，不会回传浏览器；只有当前活动配置启用且具备 API Key 时，保存记录后的自动总结才会发起外部请求。

## 部署到 Cloudflare

`wrangler.jsonc` 里的 `database_id` / KV `id` 当前是本地占位符。远程部署前需创建真实资源并替换：

```bash
# 1) 登录
npx wrangler login

# 2) 创建资源
npx wrangler d1 create sillage-db
npx wrangler kv namespace create SESSIONS
npx wrangler r2 bucket create sillage-blobs

# 3) 把上面命令返回的 ID 填入 wrangler.jsonc：
#    - d1_databases[0].database_id
#    - kv_namespaces[0].id
#    （R2 按名称绑定，无需 ID）

# 4) 对远程 D1 应用迁移
npm run db:migrate:remote

# 5) 设置密钥
npx wrangler secret put SESSION_SECRET
npx wrangler secret put APP_PASSWORD_HASH
npx wrangler secret put ATTACH_ENCRYPTION_KEY

# 6) 部署
npm run deploy
```

当前仓库默认部署为 beta 通道，线上访问不需要登录。部署后访问 `https://<name>.<account>.workers.dev` 冒烟测试：确认界面显示「β版」→ 保存一个带图片的记录 → 在问答页搜索 → 打开附件 URL 可正常查看。

若切换为正式私有部署，请先把 `wrangler.jsonc` 中 `APP_RELEASE_CHANNEL` 改为 `production` 或移除该变量，再按登录链路冒烟测试：登录 → 保存一条记录 → 退出登录 → 未登录访问受保护页面应重定向到登录。

## 使用说明

| 页面 | 路径 | 说明 |
|---|---|---|
| 登录 | `/login` | 输入你设置的口令；连续失败会被临时限流 |
| 记录 | `/` | 写记录 |
| 历史 | `/timeline` | 按时间查看所有记录 |
| 旧记录入口 | `/notes` | 旧链接，自动跳转到历史页 |
| 问答 | `/ask` | 搜索、问答、整理记录 |
| 详情 / 编辑 | `/entries/:id` | 查看、编辑、删除 |
| 月历 | `/calendar` | 按月查看有记录的日期 |
| 设置 | `/settings` | 管理多个 AI 配置（Anthropic / OpenAI 兼容协议），含「获取模型」与「测试连接」 |
| 退出 | `/logout` | 清除会话 |

- 启用 AI 后，保存记录会异步生成总结并写回；这不阻塞保存，失败也不会影响写入。
- 每日备份：定时任务（默认每天 19:00 UTC）把整库导出为 JSON + Markdown，写入 R2 桶 `sillage-blobs` 的 `backups/<日期>/` 前缀下。JSON 包含全部记录、标签、关系、附件元数据与 AI 总结，便于离线恢复。

## 开发参考

更详细的架构、约定与测试说明见 [`CLAUDE.md`](./CLAUDE.md)。常用命令：

```bash
npm run typecheck      # 类型检查
npm test               # 全部测试（真实 workerd 运行时）
npm run test:coverage  # 覆盖率（门槛 80%）
npm run lint           # Biome 检查
npm run format         # Biome 格式化
npm run db:seed:local  # 清空本地 D1 业务表并填充当前设计样本数据
npm run db:seed:remote # 对 wrangler.jsonc 指向的远程测试版 D1 执行同一套样本数据
```
