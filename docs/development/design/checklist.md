# Sillage UI — 验收清单

> 改动 UI 后对照勾选；全部为**可观察 / 可验证**项。令牌正确性以 [`design-system.md`](./design-system.md) 为准，方向以 [`README.md`](./README.md) 为准。

## 通用门槛（每次 UI 改动都必须过）

- [ ] `pnpm --dir web typecheck` 通过
- [ ] `pnpm --dir web lint` 通过（Biome）
- [ ] `pnpm --dir web test` 通过（改了交互逻辑要补 / 更新测试）
- [ ] `pnpm --dir web build` 通过
- [ ] `go test ./...` 全绿（未因样式改动引入逻辑回归）
- [ ] 改动的每一行都能追溯到设计方向（无顺手「改进」无关代码）
- [ ] 新增的每个颜色 / 表面都同时给了 `dark:` 值

## 配色与字体

- [ ] 全站为中性灰：无任何彩色强调色（无 celadon / clay 等残留）
- [ ] 强调（主按钮、选中实底）为近黑（浅色）/ 近白（深色）
- [ ] 全站只用无衬线字体，无 serif / 宋体
- [ ] 无残留纯白 / 纯黑硬编码；`gray-400` 不承载正文

## 外壳与导航

- [ ] 桌面左侧栏常驻（`w-72`）且可折叠；折叠后主区让位正确、展开按钮可用
- [ ] 主导航为 记录 / 历史；问答经「新问答」+「对话」列表进入；设置在用户菜单
- [ ] 移动端顶部条 + 汉堡 → 抽屉；点遮罩 / 按 Esc / 路由跳转均能关闭
- [ ] `QuickCapture`（⌘/Ctrl+J）在非 `/ask` 页可用
- [ ] `ThemeToggle` 切换逻辑未变（localStorage / system 跟随仍生效）

## 组件与一致性

- [ ] 复用 `ui.ts` 令牌；新增高频模式已提为令牌并登记到 `design-system.md`
- [ ] 长正文复用 typography `prose`（未手写排版），暗色 `dark:prose-invert` 正常
- [ ] 图标统一用 `lucide-react`，配 `aria-label`

## 无障碍与响应式

- [ ] 焦点环全程可见（`focus-visible:ring-gray-400/40` 一致）；Tab 可达侧栏 / 抽屉 / 浮层
- [ ] 浮层 / 抽屉 / 菜单可 Esc 关闭；模态浮层有 `role="dialog"` / `aria-modal` 与焦点管理
- [ ] 关键文字对比 ≥ AA
- [ ] 窄屏不溢出；触控目标 ≥ 40px

## 收尾

- [ ] 全站明暗 × 桌面移动走查：记录 / 历史 / 问答 / 设置 / 初始化 / 登录
- [ ] 涉及产品形态 / 导航 / 命名的改动，已同步 [`../product-guidance.md`](../product-guidance.md)
- [ ] 无 `console.log` / 调试代码；无硬编码密钥
