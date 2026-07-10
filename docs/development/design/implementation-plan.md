# Sillage 外壳与令牌 — 现状说明与维护约定

> 配合 [`README.md`](./README.md)（方向 / 决策）与 [`design-system.md`](./design-system.md)（令牌）使用。
> 本文不是「分阶段施工单」—— 中性方向已落地。它描述当前外壳的真实结构，供后续维护对照。
> 改动任何文件前先 `Read` 该文件确认现状；一律引用 `ui.ts` 令牌；每个新样式都给暗色值。

## 1. 外壳结构（已落地）

### 1.1 [`web/src/components/AppShell.tsx`](../../../web/src/components/AppShell.tsx)

- 桌面：左侧栏常驻（`18rem`），主区 `lg:pl-[18rem]` 让位。侧栏可折叠（状态存 `localStorage` key `sillage-sidebar`）；折叠后左上角浮出 `PanelLeftOpen` 展开按钮，主区变 `lg:pl-0`。
- 移动端（`<lg`）：顶部条（Wordmark + 汉堡）高度包含顶部 safe area；点击展开宽 `18rem`、最大 `88vw` 的左侧抽屉。抽屉使用 `role="dialog"` / `aria-modal`，打开后锁定正文滚动并圈定 Tab 焦点；点遮罩、按 Esc 或路由变化会关闭，关闭后焦点回到汉堡按钮。
- `QuickCapture` 常驻外壳，通过 `visible` 在 `/ask` 隐藏入口并关闭浮层；正文使用全局 localStorage 草稿，因此问答页往返不会丢失。按钮避让底部 safe area，可见时主内容预留移动端底部空间。

### 1.2 [`web/src/components/Sidebar.tsx`](../../../web/src/components/Sidebar.tsx)

- 顶部：Wordmark「Sillage」+ tagline「个人记录」（普通 sans）。
- 「+ 新问答」按钮 → `/ask` 并 `startNew()`。
- 主导航 `NavLink`：记录 `/`（`Home` 图标）、历史 `/timeline`（`History` 图标）。选中态 = 灰色实底；闲置态 = muted + hover。
- 「问答」会话历史列表（ChatGPT 式 `conversations`），active 高亮当前会话。
- 底部用户卡片 `<details>`：展开含「设置」`/settings` + 「退出登录」；旁边是紧凑 `ThemeToggle`。移动端使用同一个 Sidebar，因此主题快捷按钮也在抽屉底部。

### 1.3 [`web/src/components/ThemeToggle.tsx`](../../../web/src/components/ThemeToggle.tsx)

- 在浅 / 深间切换，跟随系统变化；与 boot 脚本 `theme-init.js` 共用 storage key `sillage-theme`。**改外观时不要动这套逻辑。**
- [`web/src/components/SettingsWorkspace.tsx`](../../../web/src/components/SettingsWorkspace.tsx) 的「外观」分类同时放置带文字的完整按钮；它与侧栏快捷按钮操作同一偏好，并通过 `sillage:theme-change` 事件即时同步所有已挂载入口的图标与标签。

### 1.4 未保存状态、编辑草稿与归档入口

- [`web/src/components/UnsavedNavigationGuard.tsx`](../../../web/src/components/UnsavedNavigationGuard.tsx) 通过 data router 的 `useBlocker` 拦截 pathname / search / hash 发生变化的站内导航；`alertdialog` 提供「继续编辑 / 离开此页」，Esc 或点遮罩留在原页。独立的全局登记 hook 也供已持久化草稿参与手动退出保护，而不强制拦截普通站内导航；退出确认通过 portal 脱离侧栏层叠上下文，移动抽屉只响应当前最上层模态之后的 Esc。
- [`web/src/components/EntryComposer.tsx`](../../../web/src/components/EntryComposer.tsx) 以 `sillage.entry-draft.<draftKey>` 保存 v2 草稿；新建记录与每条已存在记录使用不同 key。编辑草稿还保存 `baseVersion`：与当前服务器 `memo.version` 不一致时先显示服务器正文，并禁用提交，直到用户选择清除旧草稿或恢复草稿后手动确认。
- 编辑器的「取消」在草稿有变化时显示「继续编辑 / 放弃修改」确认；明确放弃会同时删除对应本地草稿。附件上传期间 `EntryComposer` 禁用保存与取消；站内导航与 `beforeunload` 同时受保护。上传完成、Markdown 已插入后才允许保存。
- [`web/src/routes/EntryPage.tsx`](../../../web/src/routes/EntryPage.tsx) 在详情请求返回前禁用编辑与版本动作；编辑器正文和 `expectedVersion` 固定来自同一个最新详情快照，避免用缓存正文覆盖新版本。只有 404 显示记录不存在；网络或服务端错误保留已缓存正文并提供重试。首页、历史筛选、日历与问答来源进入详情时通过 `returnTo` 回到原视图。
- [`web/src/components/SettingsWorkspace.tsx`](../../../web/src/components/SettingsWorkspace.tsx) 用服务端最近一次读取或保存成功的 AI 设置生成基线指纹；偏离基线时同时启用站内导航确认与 `beforeunload`，但不把设置表单持久化为可恢复草稿。保存或删除期间通过 disabled `fieldset` 锁定 AI 表单；已有档案删除需二次点击确认，存在其他 dirty 修改时先拒绝删除并提示保存。
- [`web/src/routes/TimelinePage.tsx`](../../../web/src/routes/TimelinePage.tsx) 的列表视图以分段控件提供「当前记录 / 已归档」，置顶记录放在独立首组，其余记录按日期分组。日历进入时串行读取完整分页，显示已读取数量，失败后停下并提供重试。搜索把 `archived=true/false` 传给服务端；记录详情提供归档 / 取消归档图标动作。

### 1.5 单飞提交

- [`web/src/state/AskContext.tsx`](../../../web/src/state/AskContext.tsx) 用同步 ref 让发送与重新生成共用一个进行中闸门；新会话创建同时捕获导航 generation，切换会话或再次新建后，晚到创建结果不再改写当前会话或启动流。
- [`web/src/components/QuickCapture.tsx`](../../../web/src/components/QuickCapture.tsx) 同样用同步 ref 保证一次只保存一条；正文同步到 `sillage.quick-capture-draft`，非空时参与 `beforeunload` 与全局退出保护，成功后清除，失败后保留。
- [`web/src/state/MemosContext.tsx`](../../../web/src/state/MemosContext.tsx) 用同步请求所有权保证同一游标只加载一次，并用缓存 generation 让刷新和分页重取晚于 canonical 记录变更的快照；`loadAll` 在同一共享请求链上串行补齐日历所需页面。

## 2. 令牌维护约定

- 全程引用 [`web/src/components/ui.ts`](../../../web/src/components/ui.ts) 令牌，勿散落原始类 / hex。
- 新增高频模式（出现 ≥ 3 次的内联组合）应提为 `ui.ts` 导出，并在 `design-system.md §5` 补一行。当前已包含 `iconButtonClass`、`segmentedControlClass` / `segmentedItemClass`、`skeletonClass` 等交互令牌。
- 长正文用已装的 `@tailwindcss/typography`（`prose` / `dark:prose-invert`），不手写排版。
- 表单只保留必要字段（记录编辑器：日期 + Markdown 正文）。
- 暗色沿用 `.dark` + `dark:` 变体；`ThemeToggle` / `theme-init.js` 逻辑原样保留。
- 图标统一用 `lucide-react`，不要混入内联 SVG 或第二套图标库。

## 3. 各页宽度选用

| 页面 | 路由 | 外壳宽度 |
|---|---|---|
| 记录（首页） | `/` | reading |
| 历史（列表 / 日历） | `/timeline` | wide |
| 记录详情 / 编辑 | `/entries/:id` | reading |
| 问答 | `/ask` | 自定义 `max-w-4xl` 对话栏（贴底输入条） |
| 设置 | `/settings` | wide |
| 初始化 / 登录 | `/initialize` `/login` | `max-w-sm` 居中卡片 |

## 4. 验证

- `pnpm --dir web typecheck && pnpm --dir web lint && pnpm --dir web test && pnpm --dir web build`
- `go test ./...` 保持绿（纯样式不引入逻辑回归；改交互逻辑补轻量测试）。
- `pnpm --dir web dev` 配合本地 Go 服务，明暗 × 桌面移动核对全部主路由。
