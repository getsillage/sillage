# Sillage 设计系统 — 令牌规范

> 本文是配色、字体、宽度、组件配方的**单一事实来源**的文档化镜像。真正的事实来源是代码：全局令牌在 [`web/src/styles/app.css`](../../../web/src/styles/app.css) 的 `@theme`，组件级类名在 [`web/src/components/ui.ts`](../../../web/src/components/ui.ts)。本文与代码冲突时以代码为准，并应更新本文。
> 方向：ChatGPT-web 式的安静、专注、清楚。详见 [`README.md`](./README.md)。

## 1. 配色

策略：**中性灰承担品牌与常规交互**。强调 = 近黑（浅色模式）/ 近白（深色模式）。这刻意避开当前 AI 产品最套路的彩色点缀，与「私人记忆空间」的安静气质一致。

唯一色相例外是**语义红**：错误信息、删除确认、退出登录等破坏性操作可使用 `red-*`，并同时提供深色值。语义红不能用于品牌、普通链接、常规选中态或装饰。

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

- 页面背景：`bg-gray-50 dark:bg-gray-950`（见 `app.css` 的 `html, body` 与 `AppShell`）。
- 抬升表面：`panelClass` 使用 `bg-white/80 dark:bg-gray-900/70`；菜单、输入与浮层根据层级使用不透明或半透明的 `white / gray-900`。
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

## 3. 间距 / 圆角 / 描边 / 阴影

- **圆角**：`rounded-lg`（卡片 / 输入 / 按钮）、`rounded-xl`（浮层 / 输入条）、`rounded-full`（头像、pill）。
- **描边**：统一低对比 hairline —— `border-gray-200 dark:border-gray-800`（输入框用 `gray-200 dark:border-gray-700`）。
- **阴影**：常规 `panelClass`、选中分段项与侧栏实底项可用 `shadow-sm shadow-gray-900/[0.03]` 的微弱层次；抽屉、菜单、QuickCapture 等浮层使用 `shadow-lg` / `shadow-xl`。不在普通内容块上使用明显重阴影。
- **纵向节奏**：页面 section 默认 `space-y-6 sm:space-y-8`；阅读区上下分别使用 `py-7 sm:py-9`。
- **去盒子原则**：能用「留白 + hairline 分隔」表达的，不再包一层 `border + bg` 卡片。卡片只留给真正成块的对象。

## 4. 布局与宽度

在 [`web/src/components/ui.ts`](../../../web/src/components/ui.ts) 定义两个外壳宽度：

```ts
// 阅读 / 表单 / 对话：居中窄栏 768px
export const readingShellClass = "mx-auto w-full max-w-3xl px-4 py-7 sm:px-6 sm:py-9";

// 列表 / 日历 / 设置：宽栏 1152px
export const wideShellClass = "mx-auto w-full max-w-6xl px-4 py-7 sm:px-6 sm:py-9";
```

- 配合可折叠左侧栏（见 `AppShell` / `Sidebar`），主区再居中收窄，得到 ChatGPT 式的专注阅读列。
- 记录首页与详情使用 `readingShellClass`；历史与设置使用 `wideShellClass`。
- 问答为贴底输入条保留独立的 `max-w-4xl`（896px）对话栏；初始化 / 登录使用 `max-w-sm`（384px）居中卡片。二者不是上述通用 shell。

## 5. 组件配方（`ui.ts` 实际导出）

以代码为准，下表为当前导出的语义说明：

| 导出 | 语义 |
|---|---|
| `readingShellClass` / `wideShellClass` | 768px / 1152px 两档居中外壳宽度（§4） |
| `pageSectionClass` | 页面纵向节奏：`space-y-6 sm:space-y-8` |
| `pageTitleClass` | 页面大标题：`font-semibold` + `text-2xl sm:text-[1.75rem]` |
| `pageLeadClass` | 标题下说明文字：`mt-1 text-sm text-gray-500 dark:text-gray-400` |
| `mutedTextClass` | 次级说明、时间戳与元信息文字 |
| `ghostLinkClass` | 无实底的行内链接 / 按钮，包含 hover 与焦点环 |
| `emptyStateClass` | 虚线 hairline + 安静底色的居中空状态 |
| `panelClass` | 抬升卡：半透明浅 / 深表面、hairline 与极轻 `shadow-sm` |
| `subtlePanelClass` | 安静面板：`bg-gray-100/55 dark:bg-gray-900/55` |
| `rowLinkClass` | 轻量列表行链接：`hover:bg-gray-100/80 dark:hover:bg-gray-800/70` + 焦点环 |
| `inputClass` / `selectClass` / `textareaClass` | 表单控件：单行控件高 40px，`border-gray-200 dark:border-gray-700`，灰色聚焦环 |
| `labelClass` / `helperTextClass` | 表单 label 与辅助说明 |
| `primaryButtonClass` | 主按钮：`bg-gray-900 text-white dark:bg-gray-100 dark:text-gray-900` |
| `secondaryButtonClass` | 次按钮：`border + bg-white`，hover 灰底 |
| `subtleButtonClass` | 幽灵按钮：无边框，hover 灰底 |
| `iconButtonClass` | 仅图标操作：稳定 `40 × 40px` 触控目标、hover 与焦点环 |
| `segmentedControlClass` / `segmentedItemClass(active)` | 视图、状态和设置分类的分段控件；选中项用实底与微弱阴影 |
| `skeletonClass` | 加载占位：中性灰 `animate-pulse` 骨架 |
| `dangerButtonClass` / `dangerLinkClass` | 危险动作（退出 / 删除）：语义红文字、焦点环与软底反馈 |

### 5.1 Markdown / 长正文

复用已装的 `@tailwindcss/typography`：容器加 `prose max-w-none dark:prose-invert`。落点为渲染 Markdown 的组件及其消费方。**不手写正文排版。**

### 5.2 图标

使用 `lucide-react`（已安装）。图标尺寸 `h-4 w-4`（导航 / 行内）或 `h-5 w-5`（顶栏汉堡），`currentColor`，配 `aria-label`。

### 5.3 未保存与进行中状态

- 正文或设置存在未保存修改时，站内导航统一使用 [`UnsavedNavigationGuard.tsx`](../../../web/src/components/UnsavedNavigationGuard.tsx) 的模态 `alertdialog`。初始焦点与默认安全选择是留在原页继续编辑；离开使用语义红危险动作，Esc 与遮罩等同于留下。Guard 同时登记全局未保存状态，手动退出登录必须先显示顶层确认框，不能通过卸载页面绕过保护。
- 关闭、刷新和站外离开继续使用浏览器 `beforeunload`；站内确认不能替代它。保存成功或表单回到基线后，两类保护都应解除。
- 发送、速记与记录保存等可能被点击和快捷键重复触发的入口，应在事件处理入口使用同步单飞闸门，不能只依赖下一次 React render 才更新的 `busy` 状态。所有较长异步变更都要在 UI 中禁用冲突操作，并用「保存中」「生成中」「附件上传中」等文案说明状态。
- 附件上传与记录保存是有顺序的两个动作：上传完成并将附件 Markdown 写入编辑器后，才能恢复保存。若允许用户明确离开，必须先说明附件可能未写入记录。

## 6. 暗色策略

- class 策略：`.dark`（`app.css` 的 `@custom-variant dark`），`html.color-scheme` 同步。
- 首屏防闪：[`web/public/theme-init.js`](../../../web/public/theme-init.js) 在渲染前应用偏好，storage key `sillage-theme`，支持 `light` / `dark` / `system`（system 不写 storage）。
- 切换：[`web/src/components/ThemeToggle.tsx`](../../../web/src/components/ThemeToggle.tsx) 在浅 / 深间切换，并跟随系统变化。紧凑按钮位于侧栏底部用户卡片旁，完整按钮位于「设置 → 外观」；两处共享 `sillage-theme`，并通过页面内主题事件即时同步图标与标签。
- **每个新令牌都必须给暗色值。** 不允许残留纯白 / 纯黑硬编码。

## 7. 触控与移动端底线

- 主要按钮、图标按钮、分段项、导航行与菜单项的触控高度至少 `40px`；`iconButtonClass` 固定为 `40 × 40px`。
- `button`、`a`、`summary` 全局使用 `touch-action: manipulation`，并关闭浏览器默认点击高亮；可见 hover 不能是唯一反馈。
- 移动抽屉宽 `18rem`、最大 `88vw`。打开后锁定正文滚动、把 Tab 焦点限制在对话框内；关闭后把焦点交还打开按钮。
- 移动顶栏高度在 `3.5rem` 基础上叠加 `env(safe-area-inset-top)`，避免与刘海或状态栏重叠。
- 右下角速记按钮为 `48 × 48px`，位置通过 `env(safe-area-inset-bottom)` 避让系统手势区。
