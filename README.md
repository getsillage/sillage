# 个人日记 · Cloudflare Workers

一个完全运行在 Cloudflare 边缘平台上的**单用户**日记应用。React Router v8（SSR）跑在 Worker 上，数据存储在 D1 / R2 / KV，AI 摘要由网页端配置的 Claude / OpenAI 兼容接口提供。服务端存储 + 静态加密（非端到端），因此支持全文搜索与 AI 摘要。

## 功能

- 📝 Markdown 写作，支持标签、心情、天气
- 🖼️ 图片附件（上传前应用层 AES-256-GCM 加密后存入 R2，读取需登录）
- 🔍 关键词搜索：D1 FTS5（trigram 分词，支持中文）
- 📅 时间线、月历视图、详情/编辑
- 🕯️ “那年今日”：首页展示往年同月同日的日记
- 🤖 可选 AI：写入后异步生成摘要（网页端配置，默认关闭）
- 💾 每日定时备份：整库导出为 JSON + Markdown 到 R2
- 🔒 单口令登录（PBKDF2 + KV 会话），登录失败按 IP 限流

## 快速开始（本地开发）

```bash
npm install

# 1) 生成密钥与口令哈希（见下方「配置」）后，复制环境变量模板
cp .dev.vars.example .dev.vars   # 然后填入生成的值

# 2) 对本地 D1 应用迁移
npm run db:migrate:local

# 3) 启动开发服务器
npm run dev                      # http://localhost:5173
```

> 本地用 Miniflare 模拟 D1/R2/KV；搜索功能使用 D1 FTS5 关键词搜索。

## 配置

所有密钥放在 `.dev.vars`（本地，已 gitignore）或通过 `wrangler secret put`（远程）。仓库只提交 `.dev.vars.example` 作为模板。

### 必需密钥（secrets）

| 变量 | 用途 | 生成方式 |
|---|---|---|
| `APP_PASSWORD_HASH` | 登录口令的 PBKDF2 哈希 | `node scripts/hash-password.mjs '你的密码'` |
| `SESSION_SECRET` | 签名会话 cookie | `node -e "console.log(Buffer.from(crypto.getRandomValues(new Uint8Array(32))).toString('base64'))"` |
| `ATTACH_ENCRYPTION_KEY` | 附件 AES-256-GCM 密钥（base64，32 字节） | 同上 |

> ⚠️ `ATTACH_ENCRYPTION_KEY` 一旦用于加密附件就**不能更换**，否则已上传的图片无法解密。`SESSION_SECRET` 更换会使所有登录会话失效。

### AI 提供商（可选，默认关闭）

AI 配置全部在网页端管理，不再通过 `.dev.vars`、`wrangler.jsonc vars` 或 `wrangler secret put` 设置模型、Base URL、Provider 或 API Key。

登录后进入 `/settings`，可以保存多个 AI 配置档案，选择 **Anthropic** 或 **OpenAI 兼容**协议，填写 Base URL / API Key 后获取模型列表，或手动输入模型名称。保存时 API Key 会经 AES-256-GCM 加密后存于 KV，不会回传浏览器；只有当前活动配置启用且具备 API Key 时，写日记后的自动摘要才会发起外部请求。

## 部署到 Cloudflare

`wrangler.jsonc` 里的 `database_id` / KV `id` 当前是**本地占位符**。远程部署前需创建真实资源并替换：

```bash
# 1) 登录
npx wrangler login

# 2) 创建资源
npx wrangler d1 create diary-db
npx wrangler kv namespace create SESSIONS
npx wrangler r2 bucket create diary-blobs

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

部署后访问 `https://<name>.<account>.workers.dev` 冒烟测试：登录 → 写一篇带图片的日记 → 搜索 → 未登录访问 `/attachments/<id>` 应被拒绝（重定向到登录）。

> 部分资源（如 R2）可能需要在 Cloudflare 控制台启用或产生用量费用，请按需开启。

## 使用说明

| 页面 | 路径 | 说明 |
|---|---|---|
| 登录 | `/login` | 输入你设置的口令；连续失败会被临时限流 |
| 时间线 | `/` | 最新日记列表 + 顶部「那年今日」 |
| 写日记 | `/new` | Markdown 正文、标签、心情、天气、图片上传 |
| 详情 / 编辑 | `/entries/:id` | 查看、编辑、删除 |
| 月历 | `/calendar` | 按月查看有日记的日期 |
| 搜索 | `/search` | 关键词搜索（D1 FTS5） |
| 设置 | `/settings` | 管理多个 AI 配置（Anthropic / OpenAI 兼容协议），含「获取模型」与「测试连接」 |
| 退出 | `/logout` | 清除会话 |

- 启用 AI 后，保存日记会**异步**生成摘要并写回；这不阻塞保存，失败也不会影响写入。
- **每日备份**：定时任务（默认每天 19:00 UTC）把整库导出为 JSON + Markdown，写入 R2 桶 `diary-blobs` 的 `backups/<日期>/` 前缀下。可在 Cloudflare 控制台或用 `wrangler r2 object get` 下载留存；JSON 包含全部条目、标签、关系与附件元数据，便于离线恢复。

## 开发参考

更详细的架构、约定与测试说明见 [`CLAUDE.md`](./CLAUDE.md)。常用命令：

```bash
npm run typecheck      # 类型检查
npm test               # 全部测试（真实 workerd 运行时）
npm run test:coverage  # 覆盖率（门槛 80%）
npm run lint           # Biome 检查
npm run format         # Biome 格式化
```
