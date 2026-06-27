# Sillage 外壳与令牌 — 现状说明与维护约定

> 配合 [`README.md`](./README.md)（方向 / 决策）与 [`design-system.md`](./design-system.md)（令牌）使用。
> 本文不是「分阶段施工单」—— 中性方向已落地。它描述当前外壳的真实结构，供后续维护对照。
> 改动任何文件前先 `Read` 该文件确认现状；一律引用 `ui.ts` 令牌；每个新样式都给暗色值。

## 1. 外壳结构（已落地）

### 1.1 [`web/src/components/AppShell.tsx`](../../web/src/components/AppShell.tsx)

- 桌面：左侧栏常驻（`w-72`），主区 `lg:pl-72` 让位。侧栏可折叠（状态存 `localStorage` key `sillage-sidebar`）；折叠后左上角浮出 `PanelLeftOpen` 展开按钮，主区变 `lg:pl-0`。
- 移动端（`<lg`）：顶部条（Wordmark + 汉堡）→ 点击展开左侧抽屉（slide-over）。抽屉关闭条件：点遮罩 / 按 Esc / 路由变化。
- `QuickCapture` 在非 `/ask` 页挂载（右下角悬浮 + ⌘/Ctrl+J）。

### 1.2 [`web/src/components/Sidebar.tsx`](../../web/src/components/Sidebar.tsx)

- 顶部：Wordmark「Sillage」+ tagline「个人记录」（普通 sans）。
- 「+ 新问答」按钮 → `/ask` 并 `startNew()`。
- 主导航 `NavLink`：记录 `/`（`Home` 图标）、历史 `/timeline`（`History` 图标）。选中态 = 灰色实底；闲置态 = muted + hover。
- 「问答」会话历史列表（ChatGPT 式 `conversations`），active 高亮当前会话。
- 底部用户卡片 `<details>`：展开含「设置」`/settings` + 「退出登录」；旁边是 `ThemeToggle`。

### 1.3 [`web/src/components/ThemeToggle.tsx`](../../web/src/components/ThemeToggle.tsx)

- 在浅 / 深间切换，跟随系统变化；与 boot 脚本 `theme-init.js` 共用 storage key `sillage-theme`。**改外观时不要动这套逻辑。**

## 2. 令牌维护约定

- 全程引用 [`web/src/components/ui.ts`](../../web/src/components/ui.ts) 令牌，勿散落原始类 / hex。
- 新增高频模式（出现 ≥ 3 次的内联组合）应提为 `ui.ts` 导出，并在 `design-system.md §5` 补一行。
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
| 问答 | `/ask` | reading（贴底输入条） |
| 设置 | `/settings` | wide |
| 初始化 / 登录 | `/initialize` `/login` | 居中卡片 |

## 4. 验证

- `pnpm --dir web typecheck && pnpm --dir web lint && pnpm --dir web test && pnpm --dir web build`
- `go test ./...` 保持绿（纯样式不引入逻辑回归；改交互逻辑补轻量测试）。
- `pnpm --dir web dev` 配合本地 Go 服务，明暗 × 桌面移动核对全部主路由。
