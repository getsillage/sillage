# Sillage 重设计 — 实施计划(逐文件 / 分阶段)

> 配合 [`README.md`](./README.md)(决策与约束)与 [`design-system.md`](./design-system.md)(令牌)使用。
> 实施前先读这三份;每个组件改动前先 `Read` 该文件确认现状(下文路径与现状描述基于重设计启动时的快照,可能已演进)。
> 约束复述:**只改外观与外壳,不改数据 / 路由 / AI / 同步逻辑**;一律引用 `ui.ts` 令牌;每个新样式都给暗色值;沿用 Tailwind v4 + Biome。

---

## 阶段 1 — 基础(令牌 + 宽度)

**目标**:全局换肤到「青·纸感」,引入宋体与窄栏令牌。无结构变化,改完应用应当整体变色但布局照旧。

### 1.1 [`app/app.css`](../../app/app.css)
- 在 `@theme` 增加 `--font-serif`(见 design-system §2)。
- 在 `@theme` **重调** `--color-gray-50 … --color-gray-950` 为「纸—墨」家族(design-system §1.1 表),并加注释说明其承载 paper/ink 语义。
- 在 `@theme` **新增** `--color-celadon-50 … 900` 与 `--color-clay-50 … 900`(§1.2 / §1.3)。
- `html, body` 由 `@apply bg-white dark:bg-gray-950` 改为 `@apply bg-gray-50 dark:bg-gray-950`(纸底铺满)。
- `::selection` 改为 celadon 低透明度(§1.4)。

### 1.2 [`app/components/ui.ts`](../../app/components/ui.ts)
- 新增 `readingShellClass`、`wideShellClass`(design-system §4),**删除** `pageShellClass` 里的 `max-w-[1680px]`(或保留 `pageShellClass` 作为 `wideShellClass` 的别名以零破坏过渡 —— 推荐直接改各页 import)。
- 按 design-system §5 配方重写:`pageTitleClass`(加 `font-serif`、去 `font-semibold`)、`panelClass`/`subtlePanelClass`/`rowLinkClass`(去边框 / 去 shadow / 改 hairline)、表单类(celadon 焦点)、三个按钮类(primary 改 celadon)。
- 新增导出:`serifTitleClass`、`bareRowClass`。

### 1.3 [`app/root.tsx`](../../app/root.tsx)
- `<meta name="theme-color">` 由 `#111827` 改为明色 `#F3F5F1`;可加一条 `media="(prefers-color-scheme: dark)"` 的 `#181A15`。
- 确认 `font-serif` 变量在 SSR 首屏可用(无需额外网络字体,Songti 为本地字体)。

> 验证:`npm run typecheck && npm run lint && npm test`;`npm run dev` 目测各页变为纸墨配色、标题转宋体、内容尚未收窄属正常(收窄在阶段 2/3 随页推进)。

---

## 阶段 2 — 外壳(左侧栏)

**目标**:顶部导航 → 左侧栏;移动端降级为顶部条 + 抽屉。

### 2.1 新增 [`app/components/Sidebar.tsx`](../../app/components/Sidebar.tsx)
- 从 [`app/routes/app-layout.tsx`](../../app/routes/app-layout.tsx) 抽出导航,做成纵向五室:此刻 `/` · 痕迹 `/timeline` · 照见 `/review` · 探寻 `/ask` · 设置 `/settings`(命名 / 顺序不变)。
- 顶部 wordmark(Palatino 斜体 "Sillage" + 宋体 tagline「记忆的余迹」)。
- 每项配**小图标**:用内联 SVG(项目当前无图标库,**勿为此引依赖**);保持 16–18px、`currentColor`。
- 选中态 / 闲置态见 [附录 A1](#a1-左侧栏外壳)。
- 底部区:`ThemeToggle` + 退出 `Form`(沿用现有 `action="/logout"`)。

### 2.2 [`app/routes/app-layout.tsx`](../../app/routes/app-layout.tsx)
- 用 `<aside>`(桌面固定 `w-56`)+ `<main className="lg:pl-56">` 替换现有 sticky 顶部 header。
- 移动端(`<lg`):顶部条(wordmark + 汉堡按钮)+ 点击展开的左侧抽屉(slide-over);复用现有移动端适配经验(近期 commit 已做手机端)。抽屉需 `useState` 开合 + 点击遮罩关闭 + 路由变化时关闭。
- 保留 `loader` 的 `requireSession` 与底部 `<QuickCapture />` 挂载,**不动**。

### 2.3 [`app/components/ThemeToggle.tsx`](../../app/components/ThemeToggle.tsx)
- **逻辑保持不变**(localStorage、`.dark`、system 跟随)。只重塑按钮外观以适配侧栏底部(更克制,hairline / celadon hover)。

> 验证:侧栏在桌面常驻、移动端抽屉可开合;键盘可达(Tab / Enter / Esc 关抽屉);明暗两色正常;`typecheck && lint && test`。

---

## 阶段 3 — 签名 + 核心页

### 3.1 新增 [`app/components/TraceThread.tsx`](../../app/components/TraceThread.tsx)
- 通用"痕迹线"容器:竖线 + 节点插槽,支持普通节点与"记忆回望"节点(clay 环)。规格见 design-system §5.3 与 [附录 A2](#a2-痕迹线)。
- 设计成接收 `children`(节点行)或一个 `entries` + 渲染函数,供 此刻 与 痕迹 复用。保持小而专。

### 3.2 [`app/components/EntryCard.tsx`](../../app/components/EntryCard.tsx)
- **去盒子**:由 `rowLinkClass` 重边框卡 → hairline 分隔 / 线索节点行。标题改 `serifTitleClass`;元信息行(时间、kind、mood、location)保持 sans + faint。
- 标签 / 人物 / 关系 chip 用 design-system §5.2;`EntryInsightControl` 洞察块改 celadon 软底。
- 保留交互逻辑(`openOnCardClick`、键盘处理、`navigate`)与 props 形态,仅换类名与结构层级。

### 3.3 [`app/routes/home.tsx`](../../app/routes/home.tsx)(此刻)
- 改 `readingShellClass`。
- 顶部:日期 eyebrow(sans/faint)+ 宋体大标题「今天留下些什么？」+ 斜体英文副题(沿用现有 What lingers today?)。
- 捕获:把大表单收成**安静输入区**(参考 [附录 A4](#a4-此刻-捕获));副字段(心情 / 地点 / 人物)用既有 `SuggestedInput` 的轻量触发(CLAUDE.md 既定模式),**不要**大 `<select>` / 宽按钮。
- 弱化右侧四面板:今日片段 / 笔记 / 草稿 / 洞察改为标题下的安静分组或合并;「最近记录」改用 `TraceThread` 渲染。
- 「那年今日」用 clay 节点融入痕迹线或独立安静块。
- 保留 `loader`/`action`/`scheduleEntryInsight` 等逻辑不变。

### 3.4 [`app/routes/timeline.tsx`](../../app/routes/timeline.tsx)(痕迹)
- 改 `wideShellClass`。列表视图主体用 `TraceThread` + `EntryCard`(线索版)。
- 左侧筛选 `aside` 与 `TimelineFilters` 改轻量(hairline、celadon active)。`ViewToggle`(列表 / 日历)改 celadon active。
- 「那年今日」`OnThisDay` 用 clay 令牌。日历视图见阶段 5。

> 验证:此刻 / 痕迹 在明暗 + 桌面移动下,痕迹线对齐、节点 / 回望节点正确、宋体标题生效;`typecheck && lint && test`。

---

## 阶段 4 — 聊天 + 阅读

### 4.1 [`app/routes/ask.tsx`](../../app/routes/ask.tsx) + [`app/components/AskPanel.tsx`](../../app/components/AskPanel.tsx)(探寻)
- AskPanel 较大(重设计时约 33K),**先 Read 通读**再改;只换外观,**不动** SSE 流式 / 分支 / 重生成 / 停止等逻辑。
- 目标形态(见 [附录 A3](#a3-探寻-聊天)):用户消息 = sans 气泡(右,`bg-white` + hairline);**Sillage 回答正文 = 宋体**(左,头像用 celadon 软底圆点);**引用来源**渲染为 celadon 软底 chip(链接到对应 entry);底部贴底输入条(`bg-white` + hairline + celadon 发送按钮)。
- 加分项(可选):把"最近对话"列表放进左侧栏的探寻区(类 ChatGPT 历史);非必须。
- 容器宽度 `readingShellClass`。

### 4.2 [`app/routes/entry.tsx`](../../app/routes/entry.tsx)(详情 / 编辑)
- `readingShellClass`。阅读态:宋体标题 + `prose font-serif`(typography 插件)正文;元信息 sans;改动历史 / 版本块用 hairline 安静呈现。
- 编辑态沿用 `EntryForm`;保留 CAS 并发、`entry_revisions`、附件等逻辑。

### 4.3 表单族
- [`app/components/EntryForm.tsx`](../../app/components/EntryForm.tsx):去多余边框,celadon 焦点 / 保存按钮;副字段用 `SuggestedInput`。
- [`app/components/QuickCapture.tsx`](../../app/components/QuickCapture.tsx):浮层(⌘/Ctrl+J)换新令牌,保留极轻阴影;逻辑不变。
- [`app/components/SuggestedInput.tsx`](../../app/components/SuggestedInput.tsx):输入框 celadon 焦点;建议浮层用 `bg-white` + hairline + 极轻阴影;选中项 celadon 软底。
- [`app/components/Markdown.tsx`](../../app/components/Markdown.tsx)(及 `LazyMarkdown` / `MarkdownEditor`):复用 `prose prose-stone font-serif dark:prose-invert`(design-system §5.1),勿手写排版。

> 验证:探寻完整走一轮(发送 / 流式 / 引用跳转 / 停止)外观正确且逻辑不回归;entry 阅读 / 编辑明暗正常;`typecheck && lint && test`。

---

## 阶段 5 — 收尾 + 走查

### 5.1 其余路由
- [`app/routes/review.tsx`](../../app/routes/review.tsx)(照见):`readingShellClass`;总结卡去盒子化,宋体标题 + 安静分组。
- [`app/routes/settings.tsx`](../../app/routes/settings.tsx):较大(约 23K),**先 Read**;把众多面板收敛为**安静分组**(hairline 分隔,少边框);AI 档案 / 测试连接等交互**逻辑不变**,只换皮。`wideShellClass`。
- [`app/routes/login.tsx`](../../app/routes/login.tsx):居中纸感卡片 + wordmark,celadon 主按钮;限流 / 安全逻辑不动。
- [`app/routes/calendar.tsx`](../../app/routes/calendar.tsx) + [`app/components/CalendarView.tsx`](../../app/components/CalendarView.tsx):纸感网格,当日 celadon、有记录的日子用低对比标记;`wideShellClass`。
- [`app/routes/capture.tsx`](../../app/routes/capture.tsx) / [`new.tsx`](../../app/routes/new.tsx) / [`notes.tsx`](../../app/routes/notes.tsx):薄页,套新外壳 / 宽度即可。

### 5.2 其余组件
- [`app/components/TimelineFilters.tsx`](../../app/components/TimelineFilters.tsx)、[`BackupSection.tsx`](../../app/components/BackupSection.tsx)、`app/components/ai/*`(如 `EntryInsightControl`)、`app/components/insights/*`、`app/components/memory/*`:逐一换到新令牌(celadon / hairline / 宋体标题)。
- [`app/components/LocalDateTime.tsx`](../../app/components/LocalDateTime.tsx) / [`RelativeTime.tsx`](../../app/components/RelativeTime.tsx):一般无需改,确认在 faint 文字色下可读。

### 5.3 跨页走查
- **响应式**:`<lg` 侧栏抽屉、各页窄栏在窄屏不溢出;触控目标 ≥ 40px。
- **暗色**:逐页核对"墨夜"模式,无残留纯白 / 纯黑硬编码。
- **无障碍**:celadon 焦点环全程可见;`gray-400` 不承载正文;对比 ≥ AA(design-system §1.5);抽屉 / 浮层可 Esc 关闭、焦点可达。

> 验证:全量 `typecheck && lint && test`;`npm run dev` 截图核对明暗 × 桌面移动覆盖全部主路由;按 CLAUDE.md 同步 [`../product/sillage.md`](../product/sillage.md)。

---

## 复用清单(避免重复造轮子)

- 全程引用 [`app/components/ui.ts`](../../app/components/ui.ts) 令牌,勿散落原始类 / hex。
- 长正文用**已装的** `@tailwindcss/typography`(`prose`)。
- 捕获 / 表单副字段用既有 [`SuggestedInput`](../../app/components/SuggestedInput.tsx)。
- [`EntryCard`](../../app/components/EntryCard.tsx) 跨 此刻 / 痕迹 共用;痕迹线用单一 `TraceThread`。
- 暗色沿用 `.dark` + `dark:` 变体;[`ThemeToggle`](../../app/components/ThemeToggle.tsx) 逻辑原样保留。
- 侧栏图标用内联 SVG,**不引图标库**。

---

## 附录 A — 参考结构片段

> 仅为结构与类名参考(Tailwind 类基于新令牌)。实施时按各组件真实 props / 数据适配;`navItem` 等为示意函数。

### A1. 左侧栏外壳

```tsx
// app-layout.tsx(桌面常驻 + main 让位)
<div className="min-h-screen bg-gray-50 text-gray-900 dark:bg-gray-950 dark:text-gray-50">
  <aside className="fixed inset-y-0 left-0 hidden w-56 flex-col border-r border-gray-200 bg-white px-3 py-5 lg:flex dark:border-gray-800 dark:bg-gray-900">
    <Link to="/" className="px-2 pb-5">
      <span className="text-xl italic" style={{ fontFamily: "Palatino, serif" }}>Sillage</span>
      <span className="mt-0.5 block font-serif text-[11px] tracking-widest text-gray-400">记忆的余迹</span>
    </Link>
    <nav className="flex flex-col gap-0.5 text-sm">
      <NavLink to="/" end className={navItem}>{/* icon */}此刻</NavLink>
      {/* 痕迹 /timeline · 照见 /review · 探寻 /ask · 设置 /settings */}
    </nav>
    <div className="mt-auto flex items-center justify-between border-t border-gray-200 pt-3 dark:border-gray-800">
      <ThemeToggle />
      <Form method="post" action="/logout"><button className={subtleButtonClass}>退出</button></Form>
    </div>
  </aside>
  {/* 移动:顶部条 + 汉堡 → 抽屉(useState 开合,遮罩点击 / 路由变化 / Esc 关闭) */}
  <main className="lg:pl-56"><Outlet /></main>
  <QuickCapture />
</div>
```

```ts
// 选中 / 闲置
const navItem = ({ isActive }) =>
  "flex items-center gap-2.5 rounded-lg px-3 py-2 transition " +
  (isActive
    ? "bg-celadon-50 text-celadon-800 dark:bg-celadon-900/40 dark:text-celadon-200"
    : "text-gray-500 hover:bg-gray-100 hover:text-gray-900 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-gray-100");
```

### A2. 痕迹线

```tsx
<ol className="relative pl-6">
  <span className="absolute left-[5px] top-2 bottom-2 w-px bg-gray-200 dark:bg-gray-800" aria-hidden />
  {entries.map((e) => (
    <li key={e.id} className="relative py-4">
      <span className={
        "absolute top-[1.4rem] h-2.5 w-2.5 rounded-full bg-gray-50 dark:bg-gray-950 " +
        (e.isMemory ? "-left-[3px] h-3 w-3 ring-[1.5px] ring-clay-400" : "-left-[1px] ring-[1.5px] ring-celadon-500")
      } aria-hidden />
      <div className="text-xs text-gray-400">{/* time */}</div>
      <h3 className="mt-0.5 font-serif text-gray-900 dark:text-gray-50">{/* title */}</h3>
      <p className="mt-1 text-sm leading-7 text-gray-500 dark:text-gray-400">{/* excerpt */}</p>
    </li>
  ))}
</ol>
```

### A3. 探寻 聊天

```tsx
{/* 用户:sans 气泡(右) */}
<div className="flex justify-end">
  <div className="max-w-[80%] rounded-2xl rounded-br-sm border border-gray-200 bg-white px-4 py-2.5 text-sm leading-7 text-gray-800 dark:border-gray-800 dark:bg-gray-900 dark:text-gray-100">…</div>
</div>

{/* Sillage:宋体回答(左)+ 引用 chips */}
<div className="flex gap-3">
  <span className="mt-1 flex h-7 w-7 flex-none items-center justify-center rounded-full bg-celadon-50 text-celadon-600 dark:bg-celadon-900/40 dark:text-celadon-300">{/* mark */}</span>
  <div className="min-w-0">
    <div className="font-serif text-[15px] leading-8 text-gray-900 dark:text-gray-50">…</div>
    <div className="mt-3 text-[11px] tracking-wide text-gray-400">引自你的记录</div>
    <div className="mt-2 flex flex-col gap-1.5">
      <Link to={`/entries/${id}`} className="w-fit rounded-lg bg-celadon-50 px-2.5 py-1 text-xs text-celadon-800 dark:bg-celadon-900/40 dark:text-celadon-200">…</Link>
    </div>
  </div>
</div>

{/* 贴底输入 */}
<div className="border-t border-gray-200 dark:border-gray-800">
  <div className="mx-auto flex max-w-3xl items-center gap-2 rounded-xl border border-gray-200 bg-white p-2 pl-4 dark:border-gray-800 dark:bg-gray-900">
    <input className="flex-1 bg-transparent text-sm outline-none" placeholder="继续问……" />
    <button className="flex h-8 w-8 items-center justify-center rounded-lg bg-celadon-600 text-white">{/* ↑ */}</button>
  </div>
</div>
```

### A4. 此刻 捕获

```tsx
<header className="mb-5">
  <p className="text-xs tracking-wide text-gray-400">{today}</p>
  <h1 className="mt-1.5 font-serif text-2xl text-gray-900 sm:text-3xl dark:text-gray-50">今天留下些什么？</h1>
  <p className="mt-0.5 text-sm italic text-gray-400" style={{ fontFamily: "Palatino, serif" }}>What lingers today?</p>
</header>

<div className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900">
  <textarea className="block w-full resize-none bg-transparent font-serif text-[15px] outline-none placeholder:text-gray-400" placeholder="记下此刻……" />
  <div className="mt-5 flex items-center gap-2">
    {/* 心情 / 地点 / 人物:SuggestedInput 轻量触发 */}
    <button className={primaryButtonClass + " ml-auto"}>留下</button>
  </div>
</div>
```
