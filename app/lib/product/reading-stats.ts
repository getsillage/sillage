/**
 * Lightweight reading estimate for an entry body. Tuned for mixed Chinese/Latin
 * text: CJK is counted per character, Latin per whitespace-delimited word, and the
 * two are combined into a single "characters read" figure for the minute estimate.
 */

export interface ReadingStats {
  /** Approximate visible character count (CJK chars + Latin letters). */
  chars: number;
  /** Estimated minutes to read, at least 1 when there is any content. */
  minutes: number;
}

// Comfortable reading pace for mixed CJK prose, in characters per minute.
const CHARS_PER_MINUTE = 400;
const CJK = /[㐀-鿿豈-﫿぀-ヿ]/g;
const LATIN_WORD = /[A-Za-z0-9]+/g;

export function readingStats(body: string): ReadingStats {
  const text = body ?? "";
  const cjkCount = (text.match(CJK) ?? []).length;
  const latinWords = (text.match(LATIN_WORD) ?? []).length;
  // Count each Latin word as ~5 characters' worth of reading effort.
  const chars = cjkCount + latinWords * 5;
  if (chars === 0) {
    return { chars: 0, minutes: 0 };
  }
  return { chars, minutes: Math.max(1, Math.round(chars / CHARS_PER_MINUTE)) };
}

/** Renders a calm one-liner like "约 320 字 · 1 分钟读完"; empty string when blank. */
export function formatReadingStats(stats: ReadingStats): string {
  if (stats.chars === 0) {
    return "";
  }
  return `约 ${stats.chars} 字 · ${stats.minutes} 分钟读完`;
}
