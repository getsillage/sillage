# 个人日记 · Cloudflare Workers

一个完全运行在 Cloudflare 边缘平台上的**单用户**日记应用。React Router v8（SSR）跑在 Worker 上，数据存储在 D1 / R2 / KV / Vectorize，AI 能力由 Workers AI 或可选的 Claude / OpenAI 提供。服务端存储 + 静态加密（非端到端），因此支持全文搜索与 AI。

## 功能

- 📝 Markdown 写作，支持标签、心情、天气
- 🖼️ 图片附件（上传前应用层 AES-256-GCM 加密后存入 R2，读取需登录）
- 🔍 混合搜索：D1 FTS5 关键词（trigram 分词，支持中文）+ Vectorize 语义检索
- 📅 时间线、月历视图、详情/编辑
- 🕯️ “那年今日”：首页展示往年同月同日的日记
- 🤖 可选 AI：写入后异步生成摘要 / 情绪 / 向量（提供商可自由切换，默认关闭）
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

> 本地用 Miniflare 模拟 D1/R2/KV。**Vectorize 没有本地模拟**，因此本地的语义搜索会自动降级为仅 FTS 关键词搜索；要验证语义搜索需部署到远程或连接远程索引。

## 配置

所有密钥放在 `.dev.vars`（本地，已 gitignore）或通过 `wrangler secret put`（远程）。仓库只提交 `.dev.vars.example` 作为模板。

### 必需密钥（secrets）

| 变量 | 用途 | 生成方式 |
|---|---|---|
| `APP_PASSWORD_HASH` | 登录口令的 PBKDF2 哈希 | `node scripts/hash-password.mjs '你的密码'` |
| `SESSION_SECRET` | 签名会话 cookie | `node -e "console.log(Buffer.from(crypto.getRandomValues(new Uint8Array(32))).toString('base64'))"` |
| `ATTACH_ENCRYPTION_KEY` | 附件 AES-256-GCM 密钥（base64，32 字节） | 同上 |

> ⚠️ `ATTACH_ENCRYPTION_KEY` 一旦用于加密附件就**不能更换**，否则已上传的图片无法解密。`SESSION_SECRET` 更换会使所有登录会话失效。

### AI 提供商（可选，默认全部关闭）

> **推荐：文本生成（摘要 / 情绪）直接在网页端「设置」页配置。** 登录后进入 `/settings`，选择 **Anthropic** 或 **OpenAI** 协议，填写 Base URL / 模型 / API Key，点「测试连接」实时验证后保存。API Key 会经 AES-256-GCM 加密后存于 KV，不会回传浏览器；网页配置启用时会覆盖下面的环境变量。**向量嵌入（语义搜索）目前仍只能用环境变量配置。**

下面的环境变量是文本生成的**回退**方式，以及向量嵌入的唯一配置方式。两个开关独立控制「文本生成（摘要/情绪）」和「向量嵌入（语义搜索）」，只有在选择了某个提供商**并**配好对应密钥时才会发起外部调用。

| 变量 | 取值 | 说明 |
|---|---|---|
| `AI_TEXT_PROVIDER` | `disabled` \| `workers-ai` \| `anthropic` \| `openai` | 摘要 / 情绪分析 |
| `AI_EMBEDDING_PROVIDER` | `disabled` \| `workers-ai` \| `openai` | 语义搜索向量 |

各提供商对应的密钥与模型：

| 提供商 | 需要的密钥 | 相关变量（含默认值） |
|---|---|---|
| Workers AI | 无（用 `AI` 绑定） | `AI_SUMMARY_MODEL`、`AI_SENTIMENT_MODEL`=`@cf/qwen/qwen2.5-coder-32b-instruct`；`AI_EMBEDDING_MODEL`=`@cf/baai/bge-m3` |
| Anthropic (Claude) | `ANTHROPIC_API_KEY` | `ANTHROPIC_MODEL`=`claude-opus-4-8`、`ANTHROPIC_BASE_URL`=`https://api.anthropic.com` |
| OpenAI（兼容端点） | `OPENAI_API_KEY` | `OPENAI_MODEL`=`gpt-5.1-mini`、`OPENAI_EMBEDDING_MODEL`=`text-embedding-3-large`、`OPENAI_BASE_URL`=`https://api.openai.com/v1` |

- **本地**：把上述开关、模型、密钥都写进 `.dev.vars`。
- **远程**：非密钥项（`AI_*_PROVIDER`、模型、`*_BASE_URL`）写在 `wrangler.jsonc` 的 `vars` 里；API Key 用 `wrangler secret put` 设置。
- `OPENAI_BASE_URL` 可指向任意 OpenAI 兼容网关（自建代理、第三方聚合等）。

## 部署到 Cloudflare

`wrangler.jsonc` 里的 `database_id` / KV `id` 当前是**本地占位符**。远程部署前需创建真实资源并替换：

```bash
# 1) 登录
npx wrangler login

# 2) 创建资源
npx wrangler d1 create diary-db
npx wrangler kv namespace create SESSIONS
npx wrangler r2 bucket create diary-blobs
npx wrangler vectorize create diary-entries --dimensions=1024 --metric=cosine

# 3) 把上面命令返回的 ID 填入 wrangler.jsonc：
#    - d1_databases[0].database_id
#    - kv_namespaces[0].id
#    （R2 / Vectorize / Workers AI 按名称绑定，无需 ID）

# 4) 对远程 D1 应用迁移
npm run db:migrate:remote

# 5) 设置密钥
npx wrangler secret put SESSION_SECRET
npx wrangler secret put APP_PASSWORD_HASH
npx wrangler secret put ATTACH_ENCRYPTION_KEY
#    文本生成（摘要/情绪）推荐部署后在网页端「设置」页配置，无需在此设置密钥。
#    若用环境变量回退，或启用向量嵌入（语义搜索）：
npx wrangler secret put ANTHROPIC_API_KEY    # 文本生成回退用
npx wrangler secret put OPENAI_API_KEY       # 文本生成回退 / 向量嵌入用

# 6) （可选）在 wrangler.jsonc 的 vars 中设置向量嵌入提供商 AI_EMBEDDING_PROVIDER

# 7) 部署
npm run deploy
```

部署后访问 `https://<name>.<account>.workers.dev` 冒烟测试：登录 → 写一篇带图片的日记 → 搜索 → 未登录访问 `/attachments/<id>` 应被拒绝（重定向到登录）。

> 部分资源（Workers AI、Vectorize、R2）可能需要在 Cloudflare 控制台启用或产生用量费用，请按需开启。

## 使用说明

| 页面 | 路径 | 说明 |
|---|---|---|
| 登录 | `/login` | 输入你设置的口令；连续失败会被临时限流 |
| 时间线 | `/` | 最新日记列表 + 顶部「那年今日」 |
| 写日记 | `/new` | Markdown 正文、标签、心情、天气、图片上传 |
| 详情 / 编辑 | `/entries/:id` | 查看、编辑、删除 |
| 月历 | `/calendar` | 按月查看有日记的日期 |
| 搜索 | `/search` | 关键词（FTS）+ 语义（启用嵌入后）混合检索 |
| 设置 | `/settings` | 配置 AI 文本提供商（Anthropic / OpenAI 协议），含「测试连接」 |
| 退出 | `/logout` | 清除会话 |

- 启用 AI 后，保存日记会**异步**生成摘要 / 情绪并写回，且生成向量供语义搜索——这些都不阻塞保存，失败也不会影响写入。
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
