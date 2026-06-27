# Sillage 设计系统 — 令牌规范

> 本文是配色、字体、宽度、组件配方的**单一事实来源**的文档化镜像。真正的事实来源是代码：全局令牌在 [`web/src/styles/app.css`](../../web/src/styles/app.css) 的 `@theme`，组件级类名在 [`web/src/components/ui.ts`](../../web/src/components/ui.ts)。本文与代码冲突时以代码为准，并应更新本文。
> 方向：ChatGPT-web 式的安静、专注、清楚。详见 [`README.md`](./README.md)。

## 1. 配色

策略：**纯中性灰单色阶**，不引入任何彩色强调色。强调 = 近黑（浅色模式）/ 近白（深色模式）。这刻意避开当前 AI 产品最套路的彩色点缀，与「私人记忆空间」的安静气质一致。

### 1.1 中性 ramp（`--color-gray-*`，见 `app.css`）

| Token | Hex | 主要用途 |
|---|---|---|
| gray-50 | `#fafafa` | 浅色 hover / 次级表面 |
| gray-100 | `#f4f4f4` | 轻填充、hover |
| gray-200 | `#e6e6e6` | hairline 描边、chip 底 |
| gray-300 | `#d4d4d4` | 较强描边、输入框边 |
| gray-400 | `#a3a3a3` | faint 文字（时间戳等非关键元信息） |
| gray-500 | `#737373` | muted 文字（正文以外的说明） |
| gray-600 | `#525252` | 次级文字 / 幽灵按钮文字 |
| gray-700 | `#404040` | 强调文字 / 深色描边 |
| gray-800 | `#2a2a2a` | 深色卡面、深色次级按钮 |
| gray-900 | `#1f1f1f` | 主文字（浅色）/ 深色页面表面 |
| gray-950 | `#131313` | 最深表面 |

- 页面背景：`bg-white dark:bg-gray-900`（见 `app.css` 的 `html, body`）。
- 抬升表面（真正的卡片 / 浮层）：`bg-white dark:bg-gray-900` 或 `dark:bg-gray-800`，靠 hairline 区分。
- 强调（主按钮、选中态实底）：`bg-gray-900 text-white dark:bg-gray-100 dark:text-gray-900`。
- `::selection`：浅色 `rgb(0 0 0 / 0.08)`，深色 `rgb(255 255 255 / 0.16)`（见 `app.css`）。
- **没有 celadon / clay 等彩色 ramp。** 焦点环统一用半透明灰：`focus-visible:ring-gray-400/40 dark:ring-gray-500/40`。

### 1.2 对比度底线（无障碍）

- 关键文字（正文、标题、按钮文案）对背景 ≥ WCAG AA（4.5:1）。`gray-600`/`gray-900` on white 满足；白字 on `gray-900` 满足。
- `gray-400`（faint）仅用于**非关键**元信息（时间戳、归属日期），不承载正文。
- 深色：`gray-100`/`gray-50` on `gray-900` 满足。

## 2. 字体

| 角色 | 字体栈 | 用在哪 |
|---|---|---|
| sans（全站唯一） | `--font-sans`（`ui-sans-serif` / `system-ui` / `PingFang SC` …，见 `app.css`） | 所有文字：导航、标题、正文、按钮、表单、用户输入、AI 回答 |

- **全站只有无衬线，没有宋体 / serif。** 旧方案曾计划「宋体做内容」，已废弃。
- 标题靠字重区分（`font-semibold`），不靠字体族。
- 长正文（Markdown）复用已装的 `@tailwindcss/typography` 的 `prose`（深色 `dark:prose-invert`），不手写排版。

> 注：`ui.ts` 中若仍存在名为 `serifTitleClass` 的导出，是历史误名（其值实为 `font-semibold`，无任何 serif），应在前端规范化阶段删除或改名。

## 3. 间距 / 圆角 / 描边 / 阴影

- **圆角**：`rounded-lg`（卡片 / 输入 / 按钮）、`rounded-xl`（浮层 / 输入条）、`rounded-full`（头像、pill）。
- **描边**：统一低对比 hairline —— `border-gray-200 dark:border-gray-800`（输入框用 `gray-300 dark:border-gray-600/700`）。
- **阴影**：仅浮层（抽屉、下拉、QuickCapture 弹层）用极轻阴影；常规表面不加阴影。
- **纵向节奏**：section 间 `space-y-8`~`space-y-10`；阅读区上下留白慷慨。
- **去盒子原则**：能用「留白 + hairline 分隔」表达的，不再包一层 `border + bg` 卡片。卡片只留给真正成块的对象。

## 4. 布局与宽度

在 [`web/src/components/ui.ts`](../../web/src/components/ui.ts) 定义两个外壳宽度：

```ts
// 阅读 / 表单 / 对话：居中窄栏 ~768px
export const readingShellClass = "mx-auto w-full max-w-3xl px-4 py-8 sm:px-6 sm:py-10";

// 列表 / 日历 / 设置：略宽 ~1024px
export const wideShellClass = "mx-auto w-full max-w-5xl px-4 py-8 sm:px-6 sm:py-10";
```

- 配合可折叠左侧栏（见 `AppShell` / `Sidebar`），主区再居中收窄，得到 ChatGPT 式的专注阅读列。
- 各页选用：记录 / entry 详情 / 问答 / 登录 → reading；历史列表 / 日历 / 设置 → wide。

## 5. 组件配方（`ui.ts` 实际导出）

以代码为准，下表为当前导出的语义说明：

| 导出 | 语义 |
|---|---|
| `readingShellClass` / `wideShellClass` | 两档居中外壳宽度（§4） |
| `pageTitleClass` | 页面大标题：`font-semibold` + `text-2xl sm:text-3xl` |
| `pageLeadClass` | 标题下说明文字：`text-sm text-gray-500 dark:text-gray-400` |
| `panelClass` | 抬升卡：`rounded-lg border border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-900` |
| `subtlePanelClass` | 安静面板：`rounded-lg bg-gray-100/60 dark:bg-gray-900/50` |
| `rowLinkClass` | 轻量列表行链接：`hover:bg-gray-100 dark:hover:bg-gray-800/60` + 焦点环 |
| `inputClass` / `selectClass` / `textareaClass` | 表单控件：`border-gray-300 dark:border-gray-600`，灰色聚焦环 |
| `labelClass` / `helperTextClass` | 表单 label 与辅助说明 |
| `primaryButtonClass` | 主按钮：`bg-gray-900 text-white dark:bg-gray-100 dark:text-gray-900` |
| `secondaryButtonClass` | 次按钮：`border + bg-white`，hover 灰底 |
| `subtleButtonClass` | 幽灵按钮：无边框，hover 灰底 |
| `dangerButtonClass` | 危险动作（退出 / 删除）：红色文字，hover 红色软底 |

> 前端规范化阶段会补充几个高频内联模式的令牌（如 muted 文字、幽灵链接、空状态面板），以减少各页重复内联 `gray-*`。补充后更新本表。

### 5.1 Markdown / 长正文

复用已装的 `@tailwindcss/typography`：容器加 `prose max-w-none dark:prose-invert`。落点：[`web/src/components/Markdown.tsx`](../../web/src/components/Markdown.tsx) 及其消费方。**不手写正文排版。**

### 5.2 图标

使用 `lucide-react`（已安装）。图标尺寸 `h-4 w-4`（导航 / 行内）或 `h-5 w-5`（顶栏汉堡），`currentColor`，配 `aria-label`。

## 6. 暗色策略

- class 策略：`.dark`（`app.css` 的 `@custom-variant dark`），`html.color-scheme` 同步。
- 首屏防闪：[`web/public/theme-init.js`](../../web/public/theme-init.js) 在渲染前应用偏好，storage key `sillage-theme`，支持 `light` / `dark` / `system`（system 不写 storage）。
- 切换：[`web/src/components/ThemeToggle.tsx`](../../web/src/components/ThemeToggle.tsx) 在浅 / 深间切换，并跟随系统变化。
- **每个新令牌都必须给暗色值。** 不允许残留纯白 / 纯黑硬编码。
