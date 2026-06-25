<p align="center">
  <img src="./public/sillage-icon.svg" alt="Sillage" width="96" height="96">
</p>

# Sillage · Cloudflare Workers

Sillage 是一个完全运行在 Cloudflare 边缘平台上的**单用户私人记忆空间**。它记录事件、情绪、想法，以及它们留下的余韵：白天可以留下片段，事后可以写笔记，AI 则生成可追溯的洞察。

技术栈：React Router v8（SSR）运行在 Worker 上，数据存储在 D1 / R2 / KV。服务端存储 + 静态加密（非端到端），因此支持全文搜索、记忆检索与 AI 洞察。

产品指导文件见 [`docs/product/sillage.md`](docs/product/sillage.md)。

## 功能

- Markdown 写作：片段、笔记、草稿三种写作形态
- Today 双栏书桌：左侧统一编辑器，右侧今日片段、今日笔记、今日洞察
- 情绪系统：1-5 预设心情 + 自由文本细腻感受
- 人物与关系：作为一等字段记录，进入记忆回看
- 轻量建议输入：地点、人物、关系、标签和模型可自由输入，也可从已有值或获取结果中选择，减少命名不一致
- 探寻入口：关键词搜索、记忆问答（多轮流式对话，可分支重生成与导出）、人物关系回看
- 照见入口：AI 短摘要、今日余韵、最近洞察、萦绕主题，以及按时段或主题生成的回顾总结
- 图片附件：上传前应用层 AES-256-GCM 加密后存入 R2，读取需登录
- 关键词搜索：D1 FTS5（trigram 分词，支持中文），并覆盖地点、人物、关系等 Sillage 字段
- 那年今日、月历、详情/编辑
- 编辑历史：每次修改留存版本快照，可回看改动
- 快速捕获：全局 ⌘/Ctrl+J 随手记一条，不打断当前页面
- 可选 AI：保存记录后异步生成单条洞察（网页端配置，默认关闭）
- 备份：每日定时整库导出为 JSON + Markdown 到 R2，也可在网页端手动导出下载
- 单口令登录：PBKDF2 + KV 会话，登录失败按 IP 限流

## 快速开始（本地开发）

```bash
npm install

# 1) 生成密钥与口令哈希（见下方「配置」）后，复制环境变量模板
cp .dev.vars.example .dev.vars   # 然后填入生成的值

# 2) 对本地 D1 应用迁移
npm run db:migrate:local

# 3) 启动开发服务器
npm run dev                      # http://localhost:5173

# 或允许同一局域网设备访问
npm run dev:lan                  # http://<你的局域网 IP>:5173
```

> 本地用 Miniflare 模拟 D1/R2/KV；搜索功能使用 D1 FTS5 关键词搜索。

局域网调试时可用 `ipconfig getifaddr en0` 查看当前 Mac 的 Wi-Fi IP，例如
`http://192.168.1.23:5173`。本地 HTTP 访问会使用非 Secure 会话 cookie，以便手机
或其他设备完成登录；线上 HTTPS 访问仍会使用 Secure cookie。

## 配置

所有密钥放在 `.dev.vars`（本地，已 gitignore）或通过 `wrangler secret put`（远程）。仓库只提交 `.dev.vars.example` 作为模板。

### 必需密钥（secrets）

| 变量 | 用途 | 生成方式 |
|---|---|---|
| `APP_PASSWORD_HASH` | 登录口令的 PBKDF2 哈希 | `node scripts/hash-password.mjs '你的密码'` |
| `SESSION_SECRET` | 签名会话 cookie | `node -e "console.log(Buffer.from(crypto.getRandomValues(new Uint8Array(32))).toString('base64'))"` |
| `ATTACH_ENCRYPTION_KEY` | 附件 AES-256-GCM 密钥（base64，32 字节） | 同上 |

> `ATTACH_ENCRYPTION_KEY` 一旦用于加密附件就不能更换，否则已上传的图片无法解密。`SESSION_SECRET` 更换会使所有登录会话失效。

### AI 提供商（可选，默认关闭）

AI 配置全部在网页端管理，不再通过 `.dev.vars`、`wrangler.jsonc vars` 或 `wrangler secret put` 设置模型、Base URL、Provider 或 API Key。

登录后进入 `/settings`，可以保存多个 AI 配置档案，选择 **Anthropic** 或 **OpenAI 兼容**协议，填写 Base URL / API Key 后获取模型列表，或手动输入模型名称。保存时 API Key 会经 AES-256-GCM 加密后存于 KV，不会回传浏览器；只有当前活动配置启用且具备 API Key 时，保存记录后的自动洞察才会发起外部请求。

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

部署后访问 `https://<name>.<account>.workers.dev` 冒烟测试：登录 → 留下一个带图片的片段 → 在探寻页搜索 → 未登录访问 `/attachments/<id>` 应被拒绝（重定向到登录）。

## 使用说明

| 页面 | 路径 | 说明 |
|---|---|---|
| 登录 | `/login` | 输入你设置的口令；连续失败会被临时限流 |
| 此刻 | `/` | Today 双栏书桌，写片段/笔记/草稿 |
| 痕迹 | `/timeline` | 片段和笔记混排 |
| 笔记 | `/notes` | 今日笔记、周笔记、月笔记、主题笔记 |
| 照见 | `/review` | 今日余韵、最近洞察、萦绕主题 |
| 探寻 | `/ask` | 搜索、问答入口、人物关系回看 |
| 详情 / 编辑 | `/entries/:id` | 查看、编辑、删除 |
| 月历 | `/calendar` | 按月查看有记录的日期 |
| 设置 | `/settings` | 管理多个 AI 配置（Anthropic / OpenAI 兼容协议），含「获取模型」与「测试连接」 |
| 退出 | `/logout` | 清除会话 |

- 启用 AI 后，保存记录会异步生成洞察并写回；这不阻塞保存，失败也不会影响写入。
- 每日备份：定时任务（默认每天 19:00 UTC）把整库导出为 JSON + Markdown，写入 R2 桶 `sillage-blobs` 的 `backups/<日期>/` 前缀下。JSON 包含全部记录、标签、关系、附件元数据与 AI 洞察，便于离线恢复。

## 开发参考

更详细的架构、约定与测试说明见 [`CLAUDE.md`](./CLAUDE.md)。常用命令：

```bash
npm run typecheck      # 类型检查
npm test               # 全部测试（真实 workerd 运行时）
npm run test:coverage  # 覆盖率（门槛 80%）
npm run lint           # Biome 检查
npm run format         # Biome 格式化
```
