# Sillage 设计系统 — 令牌规范(青·纸感)

> 本文是配色、字体、间距、组件配方的**单一事实来源**。实施时一律引用这里的令牌,不要散落原始 hex 或一次性魔法值。
> 落地位置:全局令牌在 [`app/app.css`](../../app/app.css) 的 `@theme`;组件级类名在 [`app/components/ui.ts`](../../app/components/ui.ts)。

## 1. 配色

策略见 [`README.md`](./README.md#换色总策略先读再动手):**重调 `gray` ramp** 成"纸—墨"家族(让现有内联 `gray-*` 全局换肤),**新增** `celadon`、`clay` 两条 ramp。下列值同时服务明 / 暗(应用按明暗选取不同 stop)。

### 1.1 中性 ramp(重调 `--color-gray-*`)

| Token | Hex | 主要用途(明 / 暗) |
|---|---|---|
| gray-50 | `#F3F5F1` | 页面纸底 / 暗色主文字 |
| gray-100 | `#E9ECE4` | 轻填充、hover / 暗色次文字 |
| gray-200 | `#DCE0D6` | 描边、chip 底 / 暗色描边 |
| gray-300 | `#C7CCBE` | 较强描边、占位 |
| gray-400 | `#A4AA9A` | faint 文字(时间戳等非关键元信息) |
| gray-500 | `#7C8276` | muted 文字(正文以外的说明) |
| gray-600 | `#646A5E` | 次级文字 |
| gray-700 | `#4B5046` | 强调文字 / 暗色卡面偏亮 |
| gray-800 | `#343A30` | 暗色卡面 |
| gray-900 | `#262B22` | 主文字(墨) / 暗色页面表面 |
| gray-950 | `#181A15` | 最深 / 暗色页面纸底 |

- 纸底语义:明色页面背景 = `bg-gray-50`(把 [`app/app.css`](../../app/app.css) 的 `body` 由 `bg-white` 改为 `bg-gray-50 dark:bg-gray-950`,让纸感铺满)。
- 抬升表面(真正的卡片 / 浮层)继续用 `bg-white dark:bg-gray-900`,在纸底上自然略亮。
- `::selection` 由当前的硬编码灰改为 celadon 低透明度(见 1.4)。

### 1.2 青瓷绿 `celadon`(新增,主点缀色)

| Token | Hex | 用途 |
|---|---|---|
| celadon-50 | `#E7EFE9` | 软底(导航选中、引用 chip、洞察条) |
| celadon-100 | `#D3E2D8` | 软底加深 |
| celadon-200 | `#B3CDBC` | 暗色文字 on 软底 |
| celadon-300 | `#8FB39C` | 暗色主点缀 |
| celadon-400 | `#6F9A7F` | hover / 暗色按钮底 |
| celadon-500 | `#5C8270` | DEFAULT 点缀 |
| celadon-600 | `#4F7A66` | 主按钮底(明)、链接 |
| celadon-700 | `#3D5E4C` | 主按钮 hover(明) |
| celadon-800 | `#33533F` | 文字 on 软底(明) |
| celadon-900 | `#24382C` | 暗色软底 |

- 焦点环统一用 celadon:`focus-visible:ring-celadon-600/30 dark:ring-celadon-400/30`。

### 1.3 暖陶土 `clay`(新增,仅记忆回望)

仅用于「那年今日 / On this day」及同类"过去的回声"。**不要**当作第二主色铺开。

| Token | Hex | 用途 |
|---|---|---|
| clay-50 | `#F2E9DD` | 软底(明) |
| clay-100 | `#E6D6C2` | 软底加深 |
| clay-300 | `#C29A6E` | 暗色点缀 |
| clay-400 | `#A8794F` | DEFAULT |
| clay-600 | `#6E4A28` | 文字 on 软底(明) |
| clay-900 | `#2A241D` | 暗色软底 |

### 1.4 `app.css` 落地要点

```css
@theme {
  --font-serif: "Songti SC", "STSong", "Source Han Serif SC", "Noto Serif SC", serif;

  /* 中性 ramp 重调为「纸—墨」(承载 paper/ink 语义,非中性灰) */
  --color-gray-50:  #F3F5F1;
  /* …gray-100 … gray-950,按 1.1 表 */

  /* 青瓷绿(主点缀) */
  --color-celadon-50:  #E7EFE9;
  /* …至 celadon-900,按 1.2 表 */

  /* 暖陶土(仅记忆回望) */
  --color-clay-50:  #F2E9DD;
  /* …按 1.3 表 */
}

/* 纸底铺满 + 选区色 */
html, body { @apply bg-gray-50 dark:bg-gray-950; }
::selection { background: rgb(92 130 112 / 0.18); } /* celadon */
```

> Tailwind v4 会据 `@theme` 自动生成 `bg-celadon-600`、`text-celadon-800`、`font-serif` 等工具类,无需额外配置。

### 1.5 对比度底线(无障碍)

- 关键文字(正文、标题、按钮文案)对背景 ≥ WCAG AA(4.5:1)。`gray-600`/`gray-900` on `gray-50` 满足;白字 on `celadon-600` 满足。
- `gray-400`(faint)仅用于**非关键**元信息(时间戳、归属日期),不承载正文。
- 暗色:`gray-100`/`gray-50` on `gray-950` 满足;`celadon-200` on `celadon-900` 用于软底文字时校验 ≥ AA。

## 2. 字体

| 角色 | 字体栈 | 用在哪 |
|---|---|---|
| sans(外壳) | 现有 `--font-sans`(system / PingFang) | 导航、标签、按钮、表单 label、元信息、用户输入气泡 |
| **serif(内容)** | `--font-serif`(Songti 系) | 页面标题、entry 标题与正文、探寻**回答**正文、那年今日标题 |
| wordmark | `"Palatino","Iowan Old Style",serif` 斜体 | 仅左侧栏 "Sillage" 标志 |

- 通过 `font-serif` 工具类施加;正文长文用 typography 的 `prose` 并叠加 `font-serif`(见 §5)。
- 行高:正文 `leading-7`/`leading-8`(宋体需要更松的行距才耐读)。
- **大小写 / 标点**:中文标题用宋体即可,不强加字重;两种字重足够(常规 / 中)。避免堆叠多级粗体。

## 3. 间距 / 圆角 / 描边 / 阴影

- **圆角**:沿用 `rounded-lg`(卡片)/ `rounded-md`(小元素)/ `rounded-full`(头像、pill)。不要更大的圆角。
- **描边**:统一**低对比 hairline** —— `border border-gray-200 dark:border-gray-800`(必要时 `gray-200/70`)。去掉重边框。
- **阴影**:**移除大部分 `shadow-sm`**;只在浮层(抽屉、下拉、QuickCapture 弹层)保留极轻阴影,以及输入焦点环。
- **纵向节奏**:section 之间 `space-y-8`~`space-y-10`;阅读区上下留白慷慨(页面顶部 `pt-10`+)。
- **去盒子原则**:能用"留白 + hairline 分隔"表达的,就不要再包一层 `border + bg` 卡片。卡片只留给"真正成块的对象"。

## 4. 布局与宽度

替换 `max-w-[1680px]`,在 [`app/components/ui.ts`](../../app/components/ui.ts) 定义两个外壳:

```ts
// 阅读 / 表单 / 对话:居中窄栏
export const readingShellClass =
  "mx-auto w-full max-w-3xl px-4 py-8 sm:px-6 sm:py-10";   // ~768px

// 列表网格 / 日历 / 设置:略宽
export const wideShellClass =
  "mx-auto w-full max-w-5xl px-4 py-8 sm:px-6 sm:py-10";    // ~1024px
```

- 配合左侧栏(§ 见 implementation-plan)。主区再居中收窄,得到 Claude/ChatGPT 式的专注阅读列。
- 各页选用:此刻 / entry / 探寻 / 照见正文 / 登录 → reading;痕迹列表 / 日历 / 设置 → wide。

## 5. 组件配方(重写 `ui.ts` 的目标形态)

下列为新令牌下的目标类串(实施 agent 据此重写 [`app/components/ui.ts`](../../app/components/ui.ts) 对应导出)。保持导出名不变以减小改动面,新增 `readingShellClass`/`wideShellClass`/`serifTitleClass`/`bareRowClass`。

| 导出 | 目标 |
|---|---|
| `pageTitleClass` | `font-serif text-2xl text-gray-900 sm:text-3xl dark:text-gray-50`(去掉 `font-semibold`,靠宋体本身的重量) |
| `pageLeadClass` | `mt-1 text-sm text-gray-500 dark:text-gray-400` |
| `serifTitleClass`(新) | `font-serif text-gray-900 dark:text-gray-50` —— 卡内 / 行内标题统一入口 |
| `panelClass` | 抬升卡:`rounded-lg border border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-900`(去 `shadow-sm`) |
| `subtlePanelClass` | `rounded-lg bg-gray-100/60 dark:bg-gray-900/50`(无边或极淡边) |
| `bareRowClass`(新) | 去盒子行:`block py-3`,靠 hairline 分隔的列表项用 |
| `rowLinkClass` | 轻量:`block rounded-lg px-3 py-3 transition hover:bg-gray-100 dark:hover:bg-gray-800/60`(去边框) |
| `inputClass`/`textareaClass`/`selectClass` | 边框 `border-gray-300 dark:border-gray-700`,聚焦 `focus:border-celadon-600 focus:ring-celadon-600/20 dark:focus:border-celadon-400` |
| `labelClass` | `block text-sm font-medium text-gray-700 dark:text-gray-300` |
| `helperTextClass` | `mt-1 text-xs text-gray-500 dark:text-gray-400` |
| `primaryButtonClass` | **celadon**:`bg-celadon-600 text-white hover:bg-celadon-700 focus-visible:ring-celadon-600/30 dark:bg-celadon-500 dark:text-gray-950 dark:hover:bg-celadon-400` |
| `secondaryButtonClass` | `border border-gray-300 bg-white text-gray-800 hover:bg-gray-100 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100 dark:hover:bg-gray-800` |
| `subtleButtonClass` | `text-gray-600 hover:bg-gray-100 hover:text-gray-900 dark:text-gray-300 dark:hover:bg-gray-800` |

### 5.1 Markdown / 长正文

复用已装的 `@tailwindcss/typography`:容器加 `prose prose-stone max-w-none font-serif dark:prose-invert`,并按需把 `prose` 的标题 / 引用调到 celadon。**不要**手写正文排版。落点:[`app/components/Markdown.tsx`](../../app/components/Markdown.tsx) 及其消费方。

### 5.2 标签 / chip

人物 / 关系 / 标签:`rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-600 dark:bg-gray-800 dark:text-gray-300`;引用 / 洞察用 celadon 软底:`bg-celadon-50 text-celadon-800 dark:bg-celadon-900/40 dark:text-celadon-200`。

### 5.3 痕迹线(签名元素)的视觉规格

- 容器:`relative pl-6`。
- 竖线:`absolute left-[5px] top-2 bottom-2 w-px bg-gray-200 dark:bg-gray-800`。
- 普通节点:`absolute -left-[1px] top-[…] h-2.5 w-2.5 rounded-full bg-gray-50 ring-[1.5px] ring-celadon-500 dark:bg-gray-950`(空心点)。
- 记忆回望节点(那年今日):同上但 `ring-clay-400`,略大(`h-3 w-3`)。
- 行内:时间(sans, faint)→ 标题(宋体)→ 摘要(sans, muted)→ 标签;**行与行之间靠留白,不再各自包卡片**。
- 详细结构见 [`implementation-plan.md` 附录 A](./implementation-plan.md#附录-a-参考结构片段)。
