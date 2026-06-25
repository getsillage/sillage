/**
 * Anthropomorphic, time-staged copy shown while an AI generation is in flight, so
 * the wait reads like the layer is *thinking* rather than hanging. Pure + data-only
 * so it can be unit-tested and shared between server-rendered and client code.
 */

export interface ProgressPhase {
  /** Show this label once elapsed time reaches at least `atMs`. */
  atMs: number;
  label: string;
}

/**
 * Picks the label for the current elapsed time. `phases` must be sorted ascending
 * by `atMs` and start at 0. Returns the last phase whose threshold has passed.
 */
export function phaseLabel(phases: readonly ProgressPhase[], elapsedMs: number): string {
  let label = phases[0]?.label ?? "";
  for (const phase of phases) {
    if (elapsedMs >= phase.atMs) {
      label = phase.label;
    } else {
      break;
    }
  }
  return label;
}

/** Single-entry "AI 洞察" generation. */
export const ENTRY_INSIGHT_PHASES: readonly ProgressPhase[] = [
  { atMs: 0, label: "正在读这条记录…" },
  { atMs: 2500, label: "正在提炼留下的东西…" },
  { atMs: 6000, label: "正在斟酌措辞…" },
  { atMs: 12000, label: "快好了，正在收尾…" },
  { atMs: 22000, label: "比平时慢一些，再等等…" },
];

/** Multi-entry "回顾 / 总结" generation. */
export const SUMMARY_PHASES: readonly ProgressPhase[] = [
  { atMs: 0, label: "正在翻阅这段时间…" },
  { atMs: 3000, label: "正在把碎片织到一起…" },
  { atMs: 8000, label: "正在落笔…" },
  { atMs: 16000, label: "快好了，正在收尾…" },
  { atMs: 28000, label: "内容有点多，再等一等…" },
];

/** Formats milliseconds as a calm "X.X 秒" for the live ticker and the final line. */
export function formatDuration(ms: number): string {
  const seconds = Math.max(0, ms) / 1000;
  if (seconds < 60) {
    return `${seconds.toFixed(1)} 秒`;
  }
  const minutes = Math.floor(seconds / 60);
  const rest = Math.round(seconds % 60);
  return rest === 0 ? `${minutes} 分钟` : `${minutes} 分 ${rest} 秒`;
}
