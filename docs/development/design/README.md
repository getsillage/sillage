# Sillage UI 设计方向 — 总览

> 本文件夹记录 Sillage 界面的**设计方向与决策**。
> 阅读顺序：本文（方向 / 决策 / 约束）→ [`design-system.md`](./design-system.md)（令牌：配色 / 字体 / 宽度 / 组件）→ [`implementation-plan.md`](./implementation-plan.md)（外壳与令牌现状 + 维护约定）。
> 配套：[`checklist.md`](./checklist.md)（验收清单）。
> 产品语义以 [`../product-guidance.md`](../product-guidance.md) 为准；本文只描述「外观与外壳」，不改数据模型与产品功能。
> 本目录只覆盖 Web 客户端；其中“移动端”指 Web 响应式布局。原生 Android 客户端的现状见 [`../../../android/README.md`](../../../android/README.md)，跨客户端产品原则见 [`../product-guidance.md`](../product-guidance.md)。
> 事实来源是代码：`web/src/styles/app.css` 与 `web/src/components/ui.ts`。文档与代码冲突以代码为准。

## 背景与动机

早期 UI 被评价为「像后台管理系统 / CRM，而不是私人记忆空间」：内容过宽、盒子套盒子、纯模板灰、缺乏专注感。

目标：借 ChatGPT-web 的**安静、专注、清楚**气质，翻译成贴合「私人记忆空间」的样子 —— 不是把每页都做成聊天，而是借它的**外壳**（左侧栏、好留白、清楚的层次），保持安静中性。

## 已锁定的设计决策（硬约束）

这 5 条是当前实现的方向，实施时不要偏离：

1. **配色 —— 中性灰为主**：品牌、导航、选中态与常规反馈只用中性灰；强调用近黑（浅色）/ 近白（深色）。唯一的色相例外是错误与删除、退出等破坏性操作使用语义红，红色不作为品牌强调色或装饰色。
2. **字体 —— 全站无衬线**：只用 `--font-sans`（system / PingFang），靠字重区分层次。**没有宋体 / serif。**
3. **布局 —— 可折叠左侧栏 + 居中内容栏**：导航在左侧栏（桌面可折叠、移动端抽屉）；记录详情使用 `max-w-3xl` 阅读栏，问答使用 `max-w-4xl` 对话栏，全部记录与设置使用 `max-w-6xl` 宽栏。
4. **聊天的边界**：**问答（Ask）是真聊天**（含流式回答）；**写记录 / 全部记录 / 设置 借的是外壳气质，不做成聊天气泡。**
5. **图标 —— `lucide-react`**：统一图标库，`currentColor` + `aria-label`。

> 历史说明：早期曾锁定一套「青瓷绿 + 宋体纸感」方案（celadon / clay / Songti / 历史竖线 TraceThread）。该方向已被否决并从代码中清除，本目录已据现行中性方向重写。

## 信息架构（与产品文档一致）

- 主导航：**写记录 `/`** · **全部记录 `/timeline`**。
- 问答通过左侧栏的 **「开始问答」按钮** 与 **「问答」会话区**进入（`/ask`），不作为主导航项重复；会话区支持服务端搜索，并可切换查看和恢复已归档问答。
- 设置在左侧栏底部**用户卡片的展开菜单**中（`/settings`），不作为主导航项。
- 主题切换快捷按钮位于侧栏底部、用户卡片旁；「设置 → 外观」同时提供带文字入口，两处共享同一偏好。
- 全部记录页通过「未归档 / 已归档 / 收藏」分段控件进入互斥状态视图；记录详情提供收藏 / 取消收藏与归档 / 取消归档动作。
- 全局：除问答页外，任意页面都有**速记**入口（右下角悬浮按钮 / ⌘·Ctrl+J）。
- 旧路径 `/review` 重定向到 `/ask`。

真实路由：`/`、`/timeline`、`/entries/:id`、`/ask`、`/settings`、`/initialize`、`/login`。

## 技术基线

- **Tailwind v4**（无 `tailwind.config`），主题集中在 [`web/src/styles/app.css`](../../../web/src/styles/app.css) 的 `@theme`：`--font-sans` + 重定义的 `--color-gray-*` + `@custom-variant dark` + `@plugin "@tailwindcss/typography"`。
- **暗色**：class 策略（`.dark`）。[`web/public/theme-init.js`](../../../web/public/theme-init.js) 首屏前应用，storage key `sillage-theme`，支持 `light` / `dark` / `system`。
- **共享样式**集中在 [`web/src/components/ui.ts`](../../../web/src/components/ui.ts)；页面也会内联 `gray-*` 工具类，高频模式已收敛为按钮、图标按钮、分段控件、骨架屏、空状态等令牌。
- **外壳**：[`web/src/components/AppShell.tsx`](../../../web/src/components/AppShell.tsx)（桌面可折叠侧栏 + 移动抽屉 + QuickCapture 挂载）与 [`web/src/components/Sidebar.tsx`](../../../web/src/components/Sidebar.tsx)。
- **Markdown**：`react-markdown` + `remark-gfm` + `remark-breaks` + `@tailwindcss/typography`（非富文本所见即所得编辑器）。

## 不在范围内（Non-goals）

- 不改数据模型、同步契约、AI 流水线与路由结构；侧栏保持现有层级，只使用上述已锁定名称。
- 不引入新的 UI 框架 / 组件库 / CSS 方案（继续 Tailwind v4 + `ui.ts` 令牌 + lucide-react）。
- 不做信息架构重排；只做视觉与外壳的规范与打磨。

## 验证基线（每次改动都要过）

- `pnpm --dir web typecheck` 且 `pnpm --dir web lint`（Biome）。
- 改了交互逻辑（抽屉开合 / 焦点管理 / active 态）补轻量测试：`pnpm --dir web test`。
- `pnpm --dir web build` 通过；配合本地 Go 服务人工核对：**明 + 暗 × 桌面 + 移动**，覆盖 写记录 / 全部记录 / 问答 / 设置 / 初始化 / 登录。
- 涉及产品形态 / 导航 / 命名的改动，对照并按需更新 [`../product-guidance.md`](../product-guidance.md)。
