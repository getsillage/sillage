# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在本仓库工作时提供指导。

## 协作约定

- **直接提交到 `main` 分支,不要创建新的分支**,也不要走 PR 流程。
- 与用户交流一律使用**简体中文**。
- 本文件除下方 `# RTK (Rust Token Killer)` 部分(保留英文原文)外,统一使用简体中文。

## Commit 规范

提交代码或资料时遵循 [Conventional Commits](https://www.conventionalcommits.org/) 规范。

格式:

```text
<type>(<scope>): <subject>
```

- `type` 必填,使用下列类型之一:
  - `feat`: 新功能或新增资料
  - `fix`: 修复问题
  - `docs`: 文档变更
  - `style`: 格式调整(不影响内容语义)
  - `refactor`: 结构整理或重构(不修复问题也不新增功能)
  - `perf`: 性能优化
  - `test`: 添加或修改测试
  - `chore`: 构建过程、辅助工具或仓库维护变更
  - `ci`: CI 配置变更
  - `revert`: 回滚之前的 commit
- `scope` 可选,用小写短词标识影响范围,如 `auth`、`ui`、`db`、`docs`;无明确范围时省略括号。
- `subject` 必填,使用简洁的动宾短语描述本次变更,不以句号结尾。
- 如有破坏性变更,在 `type` 或 `scope` 后加 `!`,并在正文或 footer 中说明 `BREAKING CHANGE:`。
- 需要说明动机、迁移步骤或影响范围时,在空行后添加正文;关联 issue 可在 footer 写 `Refs #123` 或 `Closes #123`。

示例:

```text
feat(entries): 支持按人物筛选记忆
fix(auth): 修复登录失败计数过期问题
docs(sync): 补充游标分页说明
chore: 更新依赖锁文件
```

## 项目

Sillage 是单用户私人记忆空间,完全运行在 Cloudflare Workers 上。Worker 上采用 React Router v8 框架模式(SSR),搭配 D1(SQL)、R2(附件)、KV(会话)。存储在服务端,带静态加密(非端到端加密)——服务端读取明文,以便运行全文搜索、记忆检索与 AI 洞察。界面文案为中文。产品指导文件见 `docs/product/sillage.md`。

## 命令

```bash
npm run dev              # wrangler dev (Miniflare): http://localhost:5173
npm run typecheck        # wrangler types && react-router typegen && tsc -b(改动 wrangler.jsonc 后运行)
npm test                 # vitest run(真实 workerd 运行时)
npm test -- --run tests/backup.test.ts   # 单个测试文件
npm run test:watch       # vitest 监听模式
npm run test:coverage    # 覆盖率;CI 门槛为所有指标 80%(vitest.config.ts)
npm run lint             # biome check .(用 Biome,不是 ESLint/Prettier)
npm run format           # biome format --write .
npm run db:generate      # drizzle-kit generate —— 从 schema.ts 生成 SQL 迁移
npm run db:migrate:local # wrangler d1 migrations apply sillage-db --local
npm run deploy           # build && wrangler deploy(需先准备真实资源 ID 与密钥)
```

迁移由 drizzle-kit **生成**,但由 `wrangler d1 migrations apply` **应用**(drizzle.config.ts 不含数据库凭据)。测试会自行应用迁移(见下),因此运行测试前无需手动迁移。

## 架构

**Worker 入口 —— `workers/app.ts`。** `fetch` 处理器创建 React Router 的 `RouterContextProvider`,通过 `waitUntilContext`(`app/lib/request-context.ts`)把 `ctx.waitUntil` 注入其中,再委托给 RR 请求处理器。`scheduled` 处理器运行每日备份。路由通过这个注入的 context 触发后台工作——见下方 AI 流水线。

**绑定与 `Env`。** 绑定为 `DB`(D1)、`BLOBS`(R2)、`SESSIONS`(KV)。`Env` 类型由 `wrangler types` **生成**到 `worker-configuration.d.ts`——切勿手写,且该文件已 gitignore(由 `postinstall`/`typecheck` 重新生成)。在路由中通过 `import { env } from "cloudflare:workers"`(模块全局)获取 env,而不是从 loader/action 参数获取。

**数据层(`app/lib/db/`)。** Drizzle ORM;schema 在 `schema.ts`。访问通过按聚合划分的小型仓储式模块(`entries.ts`、`tags.ts`、`calendar.ts`),每个都接收来自 `getDb(env.DB)` 的 `Db`。时间戳是 `timestamp_ms` 的 Date 列。关键词搜索使用 D1 FTS5 虚拟表(trigram 分词器以支持 CJK),由迁移中的触发器保持同步。

该 schema 面向**同步 / 多端**设计:id 为可按时间排序的 **UUIDv7**(`id.ts`,可在客户端生成,也可当游标);每个聚合都带 `updatedAt`(有索引)+ 软删除墓碑 `deletedAt`;`entries` 另有乐观并发 `version`、`utcOffsetMinutes`,以及向前兼容的 JSON `metadata`。Sillage 产品字段是 entries 的一等列: `kind`(`fragment|note|draft`)、`noteType`、`moodText`、`location`、`people`、`relationships`。人物/关系以 JSON 字符串数组存储,在边界用 `app/lib/product/entry-fields.ts` 解析。`deleteEntry` 执行**软删除**(打墓碑,保留标签关联/附件以便撤销;`restoreEntry`/`purgeEntry` 完成生命周期);读取一律过滤 `deletedAt IS NULL`,FTS 触发器按墓碑增删行;`updateEntry` 以**“比较并交换(CAS)”**方式更新——`UPDATE` 以读到的 `version` 作为条件,影响 0 行即返回 `{status: conflict}`,从而杜绝并发丢更新,且绝不覆盖 input 未提供的 `metadata`/offset。**机器派生的 AI 输出存放在 `entry_ai` 侧表**(1:1),通过 `composeEntries`/`fts.ts`/备份里的左连接读回——因此重新生成洞察不会 bump `entries.updatedAt`,也不会扰动 FTS。`sync.ts`(`getChangesSince`)是同步 API 背后的增量读模型。

**鉴权(`app/lib/auth/`)。** 单一密码守护一切:PBKDF2 哈希与 `APP_PASSWORD_HASH` 比对,会话经 RR `createSessionStorage` 存于 KV(cookie 仅存不透明 id;HttpOnly/Secure/SameSite)。`requireSession(request, env)` 守护 loader/action。登录 action 按客户端 IP 限流(`rate-limit.ts`,基于 KV,15 分钟内 10 次失败)。`safeRedirect` 阻止 `redirectTo` 上的开放重定向。

**附件(`app/lib/storage/`)。** 字节在应用层用 `ATTACH_ENCRYPTION_KEY` 做 AES-256-GCM 加密后再写入 R2。读取路由(`routes/attachment.tsx`)受会话守护,解密并流式返回。上传在边界处校验类型/大小。

**AI 流水线(`app/lib/ai/`)。** 保存后,单条记录洞察生成通过 `context.get(waitUntilContext)(runAiPipeline(env, entry))` **在请求关键路径之外**运行——绝不在 action 中 `await`。流水线绝不让保存失败:provider 错误被记录为 `skippedReasons`。provider 默认 `disabled`;启用的 web 配置使用 `anthropic|openai`。Anthropic 与 OpenAI 兼容端点通过原始 `fetch` 调用。仅当选定 provider **且**其 API key 已解析时才发起远程调用。结果**upsert 进 `entry_ai`**(带来源 `model`),绝不回写 `entries`,从而让同步流与 FTS 索引对纯派生改动保持安静。

配置解析:流水线调用 `loadAiConfig(env)`(异步),从 KV 读取 **web 管理的设置**。当 web 设置存在且启用时,由其驱动摘要 provider(协议 `anthropic|openai`、base URL、模型、key)。web 的 API key **静态加密存储**(KV,`ATTACH_ENCRYPTION_KEY`),且绝不发送到浏览器——loader 只返回 `hasApiKey` 视图。`/settings` 路由保存多个配置,并提供模型列出与实时“测试连接”(`test-connection.ts`)。

**搜索(`app/lib/search/`)。** `fts.ts` 实现记忆搜索:标题/正文走 D1 FTS5,同时补充匹配 `moodText`、`location`、`people`、`relationships` 等 Sillage 字段。

**UI 组件。** 基础样式常量集中在 `app/components/ui.ts`。需要“自由输入 + 已有值/远端结果建议”的字段统一使用 `app/components/SuggestedInput.tsx`:单值字段用默认替换模式,多值字段用 `selectionMode="append"` 追加为逗号分隔值。不要再为这类场景额外放置占布局的 `<select>` 或宽按钮;建议入口应是输入框内的轻量触发区,建议列表作为浮层出现。

**同步 API(`routes/api.sync.tsx` + `app/lib/api/serialize.ts`)。** `GET /api/sync?cursor=<opaque>`(受会话守护)返回游标之后的全部变更——包括软删除墓碑,以便客户端镜像删除——并附带可回传的不透明 `cursor`。分页对每个流采用 `(updatedAt, id)` 的 keyset 游标,避免同一毫秒的行在翻页边界被跳过。行经 `serialize.ts` DTO 层映射(ISO 8601 时间戳、解析 `metadata`、剔除 R2 key、附件 `url`),使内部 schema 演进不破坏非 web 客户端(如移动 app)。契约见 `docs/api/sync.md`。

**备份(`app/lib/backup/export.ts`)。** 每日 cron(wrangler.jsonc 的 `triggers.crons`)将整个 Sillage 数据导出为 JSON + Markdown 到 R2 的 `backups/<date>/sillage-<timestamp>.{json,md}`。`runScheduledBackup` 记录失败并重新抛出,使失败的 cron 运行可见。

路径别名:`~/*` → `app/*`(在 tsconfig 与 vitest.config.ts 中都做了镜像)。

## 测试

测试通过 `@cloudflare/vitest-pool-workers` 在真实 workerd 运行时内运行,因此 D1/R2/KV 绑定的行为与生产一致。`tests/apply-migrations.ts` 为每个 test-worker 应用 D1 迁移;每个测试获得隔离、可回滚的存储。用 `import { env } from "cloudflare:test"` 导入测试 env。对 provider/`fetch` 逻辑,用 `vi.stubGlobal("fetch", ...)` 打桩全局。

## 部署说明

`wrangler.jsonc` 的资源 ID(`database_id`、KV `id`)是**本地开发占位符**。真实部署需创建资源(`wrangler d1 create sillage-db` / `kv namespace create SESSIONS` / `r2 bucket create sillage-blobs`),替换这些 ID,并用 `wrangler secret put` 设置密钥:`SESSION_SECRET`、`APP_PASSWORD_HASH`、`ATTACH_ENCRYPTION_KEY`。AI provider/模型/API key 设置在 web `/settings` 页面管理。本地密钥放在 `.dev.vars`(已 gitignore);只有 `.dev.vars.example` 提交入库——它说明如何生成每个值。

<!-- headroom:rtk-instructions -->
# RTK (Rust Token Killer) - Token-Optimized Commands

When running shell commands, **always prefix with `rtk`**. This reduces context
usage by 60-90% with zero behavior change. If rtk has no filter for a command,
it passes through unchanged — so it is always safe to use.

## Key Commands
```bash
# Git (59-80% savings)
rtk git status          rtk git diff            rtk git log

# Files & Search (60-75% savings)
rtk ls <path>           rtk read <file>         rtk grep <pattern>
rtk find <pattern>      rtk diff <file>

# Test (90-99% savings) — shows failures only
rtk pytest tests/       rtk cargo test          rtk test <cmd>

# Build & Lint (80-90% savings) — shows errors only
rtk tsc                 rtk lint                rtk cargo build
rtk prettier --check    rtk mypy                rtk ruff check

# Analysis (70-90% savings)
rtk err <cmd>           rtk log <file>          rtk json <file>
rtk summary <cmd>       rtk deps                rtk env

# GitHub (26-87% savings)
rtk gh pr view <n>      rtk gh run list         rtk gh issue list

# Infrastructure (85% savings)
rtk docker ps           rtk kubectl get         rtk docker logs <c>

# Package managers (70-90% savings)
rtk pip list            rtk pnpm install        rtk npm run <script>
```

## Rules
- In command chains, prefix each segment: `rtk git add . && rtk git commit -m "msg"`
- For debugging, use raw command without rtk prefix
- `rtk proxy <cmd>` runs command without filtering but tracks usage
<!-- /headroom:rtk-instructions -->
