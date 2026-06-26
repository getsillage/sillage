# Sillage UI 整体重设计 — 方案总览

> 本文件夹是 Sillage 界面整体重设计的**实施方案**,供实施 agent 按图施工。
> 阅读顺序:本文(总览/决策/约束)→ [`design-system.md`](./design-system.md)(设计令牌)→ [`implementation-plan.md`](./implementation-plan.md)(逐文件改动 + 阶段 + 验证)。
> 配套:[`checklist.md`](./checklist.md)(逐阶段验收清单)· [`mockups/`](./mockups/)(可直接用浏览器打开的两张效果图:[记录](./mockups/home-now.html) / [问答](./mockups/ask-chat.html))。
> 产品语义以 [`../product/sillage.md`](../product/sillage.md) 为准;本方案只改"外观与外壳",不改数据模型与产品功能。

> 状态说明:本设计方案写于旧 React Router / Workers 前端阶段。当前主前端已迁移到 `web/` Vite SPA，旧 `app/`、`public/` 和根 npm 入口已移除。本目录仅作为视觉方向与历史决策参考；重新实施时需要先把路径映射到 `web/src/`，并按当前 Go/Web 验证命令执行。

## 背景与动机(Context)

用户对现有 UI 不满,核心评价是「像后台管理系统 / CRM,而不是私人记忆空间」。逐条诊断:

- **过宽**:`max-w-[1680px]` 让内容铺满整屏,没有阅读的聚拢感。日记应当窄而专注。
- **盒子套盒子**:几乎每块内容都是 `border + bg + shadow` 卡片,首页一个大表单旁叠 4 个面板,视觉拥挤、焦虑,与「记录 / 总结」想要的安静相反。
- **纯 Tailwind 灰**:没有识别度与温度,是最"模板化"的部分。
- **系统默认字体**:没有文字性格 —— 而一个叫 Sillage 的个人记录产品,文字本该清楚易读。

目标:借 Claude / ChatGPT web 的**安静、专注、清楚**气质,但翻译成贴合「私人记忆空间」的样子 —— 不是把每页都做成聊天,而是借它的**外壳**(左侧栏、暖纸底、好字体、留白)。

## 已与用户确认锁定的设计决策

这 5 条是**硬约束**,实施时不要偏离:

1. **配色 —— 青·纸感**:带一点冷意的纸白底(刻意避开当前 AI 设计最套路的「米黄 + 陶土橙」),点缀色为一抹**青瓷绿**(呼应「总结」的沉静);只有「那年今日」等记忆回望用一点**暖陶土**。
2. **字体 —— 宋体做内容,无衬线做外壳**:界面外壳(导航、标签、按钮、表单 label、元信息)用无衬线;**页面标题与记忆正文用宋体**(macOS 原生 Songti,零网络加载)。这是与纯 Claude/ChatGPT 最不同、也最贴合本项目的一笔。
3. **布局 —— 左侧栏 + 居中窄阅读栏**:导航从顶部移到左侧;内容收进居中阅读栏(替代 1680px 宽屏多栏)。
4. **签名元素 —— 历史线**:一条竖向细线,最近的记录作为节点挂在线上,往下滚像沿着自己保存的记录走。呼应 Sillage(记录)与「历史」。这是全套设计里**唯一**一处"小心机",其余都保持安静。
5. **聊天的边界**:**问答(Ask)是真聊天**,完美契合参照;**记录 / 历史 / 设置 借的是外壳气质,不做成聊天气泡**。

## 历史技术基线(已过期)

- **Tailwind v4**(无 `tailwind.config`)。主题集中在 [`app/app.css`](../../app/app.css):`@import "tailwindcss"` + `@plugin "@tailwindcss/typography"` + `@custom-variant dark (&:where(.dark, .dark *))` + `@theme { --font-sans: … }`。**配色与字体的总开关就在这里。**
- **暗色模式**:class 策略(`.dark`)。[`public/theme-init.js`](../../public/theme-init.js) 在首屏前应用,localStorage key 为 `sillage-theme`,支持 `light/dark/system`;[`app/components/ThemeToggle.tsx`](../../app/components/ThemeToggle.tsx) 负责切换。**每个新令牌都必须给暗色值。**
- **宽度令牌只有两处**:`max-w-[1680px]` 仅出现在 [`app/components/ui.ts`](../../app/components/ui.ts) 与 [`app/routes/app-layout.tsx`](../../app/routes/app-layout.tsx)。改窄是集中操作。
- **共享样式集中在** [`app/components/ui.ts`](../../app/components/ui.ts)(导出 `pageShellClass`、`panelClass`、`primaryButtonClass` 等);但页面也**大量内联** `gray-*` 工具类 —— 这决定了换色策略(见下)。
- `@tailwindcss/typography` **已安装**,正文宋体可直接复用 `prose` 体系,不要手搓。
- 入口:[`app/root.tsx`](../../app/root.tsx)(`<html lang="zh-Hans">`、`theme-color` meta、引 `app.css` 与 `theme-init.js`)。

## 换色总策略(关键决策,先读再动手)

因为全站**大量内联 `gray-*`**,逐文件替换上百处类名既费力又易错。Tailwind v4 的正确做法是**在 `@theme` 里重新调校中性 ramp**:把 `--color-gray-50 … --color-gray-950` 整条改成「纸—墨」家族的值,让现有所有 `gray-*` 工具类**一次性全局换肤**;同时**新增** `celadon`(青瓷)与 `clay`(陶土)两条 ramp 和 `--font-serif`。

- 这样做的收益:**约 80% 的重新着色在中枢一次完成**,逐文件改动集中在**结构**(侧栏、宽度、宋体、去盒子、历史线),而非颜色。
- 暗色"免费"得到:应用现有的 `dark:bg-gray-900`、`dark:text-gray-100` 等会自动解析到重调后的墨色值(因为页面已按明/暗选取不同 stop)。
- 取舍:`gray-*` 令牌从此承载"纸墨"语义而非中性灰 —— 在 `app.css` 注释里写清,后续维护者才不困惑。

完整令牌值见 [`design-system.md`](./design-system.md)。

## 实施阶段(供分工 / 排期)

| 阶段 | 内容 | 风险 |
|---|---|---|
| 1 基础 | `app.css` 令牌 + `ui.ts` + 宽度 | 低(全局换肤,无结构变化) |
| 2 外壳 | 左侧栏 + `Sidebar` + `ThemeToggle` 迁入 + 移动抽屉 | 中 |
| 3 签名+核心 | `TraceThread` + 记录 + 历史 + `EntryCard` | 中 |
| 4 聊天+阅读 | 问答/`AskPanel` + entry 详情 + `EntryForm` + `QuickCapture` + `Markdown` | 中 |
| 5 收尾 | 设置 + 登录 + 日历 + `ai/*`、`insights/*`;暗色走查 + 无障碍 + 打磨 | 低—中 |

每阶段可独立交付与验证;详见 [`implementation-plan.md`](./implementation-plan.md)。

## 不在本次范围内(Non-goals)

- 不改数据模型、同步契约、AI 流水线、路由结构与导航命名(主导航仍是 记录/历史/问答;设置在用户菜单中)。
- 不引入新的 UI 框架 / 组件库 / CSS 方案(继续 Tailwind v4 + `ui.ts` 令牌)。
- 不做端到端的信息架构重排;只做视觉与外壳。
- 问答的"最近对话"侧栏列表可作为加分项(标注为可选),不是必须。

## 当前验证基线(每阶段都要过)

- `pnpm --dir web typecheck` 且 `pnpm --dir web lint`(Biome,非 ESLint/Prettier)。
- `go test ./...` 保持绿；纯样式不新增逻辑测试，若改了交互逻辑如抽屉开合 / active 态判断，补轻量测试。
- `pnpm --dir web dev` 配合本地 Go 服务后人工 / 截图核对:**明 + 暗 × 桌面 + 移动**，覆盖 记录 / 历史 / 问答 / 设置 / 初始化 / 登录。
- 文档同步(CLAUDE.md 要求):涉及产品形态 / 导航 / 命名的改动,对照并按需更新 [`../product/sillage.md`](../product/sillage.md)。
