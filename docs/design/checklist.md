# Sillage 重设计 — 逐阶段验收清单

> 实施 agent 每完成一个阶段,对照勾选;全部为**可观察 / 可验证**项。命令见每阶段末。
> 配合 [`implementation-plan.md`](./implementation-plan.md) 使用;令牌正确性以 [`design-system.md`](./design-system.md) 为准。

## 通用门槛(每阶段都必须过)

- [ ] `npm run typecheck` 通过
- [ ] `npm run lint` 通过(Biome)
- [ ] `npm test` 全绿(未因样式改动引入逻辑回归)
- [ ] 改动的每一行都能追溯到本方案(无顺手"改进"无关代码)
- [ ] 新增的每个颜色 / 表面都同时给了 `dark:` 值

---

## 阶段 1 — 基础(令牌 + 宽度)

- [ ] `app/app.css` 的 `@theme` 含 `--font-serif`、重调后的 `--color-gray-*`、新增的 `--color-celadon-*` 与 `--color-clay-*`
- [ ] `gray` ramp 注释已说明其承载 paper/ink 语义
- [ ] `body` 背景为 `bg-gray-50 dark:bg-gray-950`(纸底铺满),`::selection` 为 celadon
- [ ] `ui.ts` 导出 `readingShellClass`、`wideShellClass`、`serifTitleClass`、`bareRowClass`;`max-w-[1680px]` 不再出现
- [ ] `pageTitleClass` 含 `font-serif`;`primaryButtonClass` 为 celadon;表单聚焦为 celadon 环
- [ ] `app/root.tsx` 的 `theme-color` 明色为纸色(可选暗色 media)
- [ ] 目测:全站变为纸墨配色、标题转宋体、暗色为"墨夜"(布局此时未变属正常)

`npm run typecheck && npm run lint && npm test`

## 阶段 2 — 外壳(左侧栏)

- [ ] 桌面:左侧栏常驻(`w-56`),主区 `lg:pl-56` 让位;五室齐全且命名顺序不变(此刻/痕迹/照见/探寻/设置)
- [ ] 侧栏含 wordmark + tagline、内联 SVG 图标(**未引入图标库**)、底部 `ThemeToggle` + 退出
- [ ] 选中态为 celadon 软底;闲置态为 muted + hover
- [ ] 移动端(`<lg`):顶部条 + 汉堡 → 抽屉可开;点遮罩 / 按 Esc / 路由跳转 均能关闭
- [ ] 键盘可达:Tab 能进入侧栏与抽屉,焦点环可见
- [ ] `ThemeToggle` 切换逻辑未变(localStorage / system 跟随仍生效)
- [ ] `QuickCapture`(⌘/Ctrl+J)仍挂载可用

`npm run typecheck && npm run lint && npm test`

## 阶段 3 — 签名 + 核心页

- [ ] `TraceThread` 组件存在,竖线 + 节点对齐;记忆回望节点为 clay 环且略大
- [ ] `EntryCard` 去盒子(hairline / 线索行),标题宋体;`openOnCardClick`、键盘打开、chip 等行为不回归
- [ ] 此刻(home):窄栏;宋体大标题 + 安静捕获区;副字段用 `SuggestedInput` 轻量触发(无大 `<select>`/宽按钮);"最近记录"走 `TraceThread`
- [ ] 痕迹(timeline):宽栏;列表用痕迹线 + `EntryCard`;筛选 / ViewToggle 为轻量 + celadon active;"那年今日"为 clay
- [ ] 明 + 暗 × 桌面 + 移动 下两页均正确

`npm run typecheck && npm run lint && npm test`

## 阶段 4 — 聊天 + 阅读

- [ ] 探寻(ask):用户气泡 sans(右)、Sillage 回答正文宋体(左)、引用来源为 celadon chip 且可跳转、底部贴底输入 + celadon 发送
- [ ] 探寻**逻辑零回归**:发送 / SSE 流式 / 重生成 / 停止 / 分支 切换均正常
- [ ] entry 详情:窄栏,宋体标题 + `prose font-serif` 正文;编辑态(`EntryForm`)正常,CAS / 修订 / 附件不受影响
- [ ] `EntryForm` / `QuickCapture` / `SuggestedInput` 换新令牌且交互不变
- [ ] `Markdown` 复用 typography `prose`(未手写排版),暗色 `dark:prose-invert` 正常

`npm run typecheck && npm run lint && npm test`

## 阶段 5 — 收尾 + 走查

- [ ] 照见 / 设置 / 登录 / 日历 / capture / new / notes 全部套用新外壳与宽度
- [ ] 设置页多面板收敛为安静分组;AI 档案 / 测试连接等**逻辑不变**
- [ ] `CalendarView` / `TimelineFilters` / `BackupSection` / `ai/*` / `insights/*` / `memory/*` 均已换肤
- [ ] 全站暗色走查:无残留纯白 / 纯黑硬编码
- [ ] 无障碍:celadon 焦点环全程可见;`gray-400` 不承载正文;关键文字对比 ≥ AA;浮层 / 抽屉可 Esc 关闭
- [ ] 响应式:窄屏不溢出,触控目标 ≥ 40px
- [ ] 按 CLAUDE.md 同步 `docs/product/sillage.md`(若动到产品形态 / 导航 / 命名)

`npm run typecheck && npm run lint && npm test` + `npm run dev` 全路由明暗 × 桌面移动截图核对

---

## 最终验收(交付前)

- [ ] 五条锁定决策全部体现(青·纸感 / 宋体内容 + 无衬线外壳 / 左侧栏窄栏 / 痕迹线 / 探寻才是聊天)
- [ ] 与两张效果图([`mockups/`](./mockups/))观感一致
- [ ] 无 `console.log` / 调试代码;无硬编码密钥
- [ ] 全部通用门槛 + 各阶段项已勾选
