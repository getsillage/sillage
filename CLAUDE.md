# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在本仓库工作时提供指导。

## 协作约定

- **直接提交到 `main` 分支,不要创建新的分支**,也不要走 PR 流程。
- 与用户交流一律使用**简体中文**。
- 本文件除下方 `# RTK (Rust Token Killer)` 部分(保留英文原文)外,统一使用简体中文。

## 行为规范

以下行为规范适配自 [Andrej Karpathy 关于 LLM 编码陷阱的总结](https://x.com/karpathy/status/2015883857489522876),与本项目其他约定合并使用。整体倾向**谨慎而非速度**;琐碎任务(改错别字、显而易见的一行改动)自行判断。

### 1. 编码前思考 —— 不假设、不藏困惑、呈现权衡

- 明确说明假设;不确定就问,不要猜。
- 有多种理解时摆出来,不要默默选一个。
- **用户不是技术专家,对技术与规范的认知可能有偏差**:当需求本身有问题、有风险或不符合规范 / 最佳实践时,不要默默照做——先指出问题,给出更规范的做法与设计,用平实语言讲清理由与权衡,由用户知情后决定。
- 不清楚就停下来,说清困惑再继续。

### 2. 简洁优先 —— 用最少代码解决问题,不做推测性设计

- 不加要求之外的功能;不为一次性代码造抽象;不加没要求的“灵活 / 可配置”;不为不可能发生的场景写错误处理。
- 检验:资深工程师会嫌它过度复杂吗?会就简化。沿用 `app/lib` 既有的小而专模块风格,不无故引入新层。

### 3. 精准修改 —— 只碰必须碰的,只清理自己造成的混乱

- 不顺手“改进”相邻代码 / 注释 / 格式;不重构没坏的东西;匹配现有风格(Biome、`~/*` 别名、仓储式 db 模块)。
- 看到无关死代码先提一句、别删;改动产生的孤儿(因你改动而变得无用的 import / 变量 / 函数)要清掉。
- 检验:每一行改动都能直接追溯到用户的请求。

### 4. 目标驱动执行 —— 定义可验证的成功标准,循环到通过

- 把指令式任务转成可验证目标。本项目示例:
  - “修 bug” → 先写一个重现它的测试(真实 workerd,`import { env } from "cloudflare:test"`),再让它通过。
  - “加校验 / 功能” → 先写无效输入或期望行为的测试,再实现到通过。
  - “重构 X” → 确保前后 `npm test` 都通过。
- 多步任务先给一个简短计划(步骤 → 验证)。验证手段:`npm run typecheck` / `npm run lint` / `npm test`(覆盖率门槛 80%)。

### 项目补充

- **文档同步**:每次提交前按需同步更新 `CLAUDE.md` / `README.md` / `docs/`——功能、命令、架构、API 契约的变化都要反映到文档。改同步字段就更新 `docs/api/sync.md`;改产品形态 / 导航 / 命名就对照 `docs/product/sillage.md`。

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

Sillage 是单用户个人记录空间,完全运行在 Cloudflare Workers 上。Worker 上采用 React Router v8 框架模式(SSR),搭配 D1(SQL)、R2(附件)、KV(会话)。存储在服务端,带静态加密(非端到端加密)——服务端读取明文,以便运行全文搜索、记录检索与 AI 总结。界面文案为中文且应通俗易懂。产品指导文件见 `docs/product/sillage.md`。

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

**数据层(`app/lib/db/`)。** Drizzle ORM;schema 在 `schema.ts`。访问通过按聚合划分的小型仓储式模块(`entries.ts`、`tags.ts`、`calendar.ts`、`revisions.ts`、`summaries.ts`、`ask-conversations.ts`、`sync.ts`),每个都接收来自 `getDb(env.DB)`(`client.ts`)的 `Db`。表集合:`entries` + 1:1 派生侧表 `entry_ai` + 追加式编辑历史 `entry_revisions` + `tags`/`entry_tags` + `attachments` + 回顾总结 `summaries` + 问答会话 `ask_conversations`/`ask_messages`。时间戳是 `timestamp_ms` 的 Date 列。关键词搜索使用 D1 FTS5 虚拟表(trigram 分词器以支持 CJK),由迁移中的触发器保持同步。

该 schema 面向**同步 / 多端**设计:id 为可按时间排序的 **UUIDv7**(`id.ts`,可在客户端生成,也可当游标);每个聚合都带 `updatedAt`(有索引)+ 软删除墓碑 `deletedAt`;`entries` 另有乐观并发 `version`、`utcOffsetMinutes`,以及向前兼容的 JSON `metadata`。当前 Web 记录入口只写入日期、正文和记录类型,历史上的标题、天气、地点、心情、人物、关系、标签等列仍保留在 schema/API/备份中用于兼容旧数据与外部客户端。`deleteEntry` 执行**软删除**(打墓碑,保留标签关联/附件以便撤销;`restoreEntry`/`purgeEntry` 完成生命周期);读取一律过滤 `deletedAt IS NULL`,FTS 触发器按墓碑增删行;`updateEntry` 以**“比较并交换(CAS)”**方式更新——`UPDATE` 以读到的 `version` 作为条件,影响 0 行即返回 `{status: conflict}`,从而杜绝并发丢更新,且绝不覆盖 input 未提供的 `metadata`/offset。每次成功的内容更新(及创建)都会向 `entry_revisions` 追加一份该 `version` 的快照(正文及兼容字段 JSON),供「查看改动」回看,随 entry 级联删除。**机器派生的 AI 输出存放在 `entry_ai` 侧表**(1:1),通过 `composeEntries`/`fts.ts`/备份里的左连接读回——因此重新生成总结不会 bump `entries.updatedAt`,也不会扰动 FTS。`sync.ts`(`getChangesSince`)是同步 API 背后的增量读模型。

**鉴权(`app/lib/auth/`)。** 单一密码守护正式版本:PBKDF2 哈希与 `APP_PASSWORD_HASH` 比对,会话经 RR `createSessionStorage` 存于 KV(cookie 仅存不透明 id;HttpOnly/Secure/SameSite)。`requireSession(request, env)` 守护 loader/action。当前测试版部署通过 `APP_RELEASE_CHANNEL=beta` 跳过登录守卫,并在界面标注「β版」;本地开发优先显示「开发版」标识但不跳过登录。登录 action 按客户端 IP 限流(`rate-limit.ts`,基于 KV,15 分钟内 10 次失败)。`safeRedirect` 阻止 `redirectTo` 上的开放重定向。

**附件(`app/lib/storage/`)。** 字节在应用层用 `ATTACH_ENCRYPTION_KEY` 做 AES-256-GCM 加密后再写入 R2。读取路由(`routes/attachment.tsx`)受会话守护,解密并流式返回。上传在边界处校验类型/大小。

**AI 流水线(`app/lib/ai/`)。** AI 有三条路径、共用同一份配置:①保存记录后的**单条总结**(`pipeline.ts`/`entry-insights.ts`)②多记录**回顾总结**(`summarize.ts`,见下)③**记录问答**(`ask*.ts`,见下)。单条总结通过 `context.get(waitUntilContext)(runAiPipeline(env, entry))` **在请求关键路径之外**运行——绝不在 action 中 `await`。流水线绝不让保存失败:provider 错误被记录为 `skippedReasons`。provider 默认 `disabled`;启用的 web 配置使用 `anthropic|openai`。Anthropic 与 OpenAI 兼容端点通过原始 `fetch` 调用。仅当选定 provider **且**其 API key 已解析时才发起远程调用。结果**upsert 进 `entry_ai`**(带来源 `model`),绝不回写 `entries`,从而让同步流与 FTS 索引对纯派生改动保持安静。

配置解析:流水线调用 `loadAiConfig(env)`(异步),从 KV 读取 **web 管理的设置**(`app/lib/settings/ai-settings.ts`,支持**多套配置档案**并选定**当前活动档案**)。当活动档案存在且启用时,由它驱动 provider(协议 `anthropic|openai`、base URL、模型、key);跨端点差异由 `endpoints.ts` 回退处理。web 的 API key **静态加密存储**(KV,`ATTACH_ENCRYPTION_KEY`),且绝不发送到浏览器——loader 只返回 `hasApiKey` 视图。`/settings` 路由保存多套档案,并提供模型列出(`models.ts`)与实时“测试连接”(`test-connection.ts`)。

**回顾总结(并入问答)。** `summaries` 表把**多条记录**聚合成时段或主题总结(`scope=period|topic`、`periodType=day|week|month|quarter|year|custom`、`style=brief|structured|narrative`);`sourceEntryIds` 以 JSON 记来源且不设 FK(删源记录不应级联删掉提及它的总结),`trigger` 区分手动与预留的定时(phase 2)。`summarize.ts` 生成,`app/lib/db/summaries.ts` 读写,`app/lib/product/summary-fields.ts`/`summary-actions.ts` 在边界处理字段与动作;入口在 `/ask` 的「整理记录」,`/review` 仅重定向到 `/ask`,JSON 端点仍为 `/api.summary`。沿用 entries 的同步约定(UUIDv7、`updatedAt`、软删除)。

**问答 / 记录问答(`/ask`)。** 多轮**流式**对话持久化在 `ask_conversations` + `ask_messages`:消息树以 `parentId` 串起当前可见分支,`forkOfId` 记录重生成/编辑从哪个兄弟分叉,`headMessageId` 指向可见分支头(加载时回溯祖先,渲染成一条线性路径)。`ask*.ts`(`ask-context.ts` 组织检索上下文、`ask-stream.ts` 流式产出、`ask-action.ts` 处理动作)配合 `app/lib/db/ask-conversations.ts` 读写;路由 `/ask` + `/api.ask-stream`(SSE)+ `/api.ask-stop` + `/download-ask-conversation`(导出)。

**搜索(`app/lib/search/`)。** `fts.ts` 实现记录搜索:正文走 D1 FTS5,并继续兼容历史标题及旧扩展字段。

**UI 组件。** 基础样式常量集中在 `app/components/ui.ts`。当前记录表单保持极简,只暴露日期与正文;不要在记录、时间线或总结入口重新加入天气、地点、标题、预设心情、人物、标签、关系等显性字段。

**路由(`app/routes/`)。** `app-layout.tsx` 是受会话守护的外壳,左侧导航保留 记录(`/`,`home.tsx`)→ 历史(`/timeline`)→ 问答(`/ask`);问答会话列表默认常显在左侧栏中。`设置` 不作为主导航项,放在左侧用户卡片展开菜单里。其余路由:`entry.tsx`(详情/编辑 `/entries/:id`)、`new.tsx`、`notes.tsx`、`calendar.tsx`、`capture.tsx`、`login.tsx`/`logout.tsx`、`upload.tsx`(附件上传)、`attachment.tsx`(解密读取),以及 `download-backup.tsx` 与 `api.*` 端点(`api.sync`、`api.entry-insight`、`api.summary`、`api.ask-stream`、`api.ask-stop`)。

**同步 API(`routes/api.sync.tsx` + `app/lib/api/serialize.ts`)。** `GET /api/sync?cursor=<opaque>`(受会话守护)返回游标之后的全部变更——包括软删除墓碑,以便客户端镜像删除——并附带可回传的不透明 `cursor`。分页对每个流采用 `(updatedAt, id)` 的 keyset 游标,避免同一毫秒的行在翻页边界被跳过。行经 `serialize.ts` DTO 层映射(ISO 8601 时间戳、解析 `metadata`、剔除 R2 key、附件 `url`),使内部 schema 演进不破坏非 web 客户端(如移动 app)。契约见 `docs/api/sync.md`。

**备份(`app/lib/backup/export.ts`)。** 每日 cron(wrangler.jsonc 的 `triggers.crons`)将整个 Sillage 数据导出为 JSON + Markdown 到 R2 的 `backups/<date>/sillage-<timestamp>.{json,md}`;JSON 含记录、标签、附件元数据、`entry_ai` 总结、`summaries` 回顾与 `ask` 会话。`runScheduledBackup` 记录失败并重新抛出,使失败的 cron 运行可见。

路径别名:`~/*` → `app/*`(在 tsconfig 与 vitest.config.ts 中都做了镜像)。

## 测试

测试通过 `@cloudflare/vitest-pool-workers` 在真实 workerd 运行时内运行,因此 D1/R2/KV 绑定的行为与生产一致。`tests/apply-migrations.ts` 为每个 test-worker 应用 D1 迁移;每个测试获得隔离、可回滚的存储。用 `import { env } from "cloudflare:test"` 导入测试 env。对 provider/`fetch` 逻辑,用 `vi.stubGlobal("fetch", ...)` 打桩全局。

## 部署说明

`wrangler.jsonc` 的资源 ID(`database_id`、KV `id`)是**本地开发占位符**。真实部署需创建资源(`wrangler d1 create sillage-db` / `kv namespace create SESSIONS` / `r2 bucket create sillage-blobs`),替换这些 ID,并用 `wrangler secret put` 设置密钥:`SESSION_SECRET`、`APP_PASSWORD_HASH`、`ATTACH_ENCRYPTION_KEY`。`APP_RELEASE_CHANNEL` 是非密钥发布通道变量:当前为 `beta`,部署后免登录并显示「β版」;正式私有部署要改为 `production` 或移除。AI provider/模型/API key 设置在 web `/settings` 页面管理。本地密钥放在 `.dev.vars`(已 gitignore);只有 `.dev.vars.example` 提交入库——它说明如何生成每个值。

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
