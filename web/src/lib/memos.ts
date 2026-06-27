import type { Memo } from "./api";

/** Collapses whitespace and truncates a memo body to a single-line preview. */
export function excerpt(body: string, max = 120): string {
  const text = body.replace(/\s+/g, " ").trim();
  return text.length > max ? `${text.slice(0, max)}…` : text;
}

/** Pinned first, then by entry date desc, then created desc — matches server order. */
export function sortMemos(memos: readonly Memo[]): Memo[] {
  return [...memos].sort((a, b) => {
    if (a.pinnedAt && !b.pinnedAt) {
      return -1;
    }
    if (!a.pinnedAt && b.pinnedAt) {
      return 1;
    }
    if (a.entryDate !== b.entryDate) {
      return b.entryDate.localeCompare(a.entryDate);
    }
    return b.createdAt.localeCompare(a.createdAt);
  });
}

/** Returns a new list with `memo` inserted or replaced by id. */
export function upsertMemo(memos: readonly Memo[], memo: Memo): Memo[] {
  return [...memos.filter((item) => item.id !== memo.id), memo];
}

/** Active = not archived and not soft-deleted. */
export function isActive(memo: Memo): boolean {
  return !memo.archivedAt && !memo.deletedAt;
}

/** Memos written on this same month/day in earlier years, newest year first. */
export function onThisDay(memos: readonly Memo[], todayISO: string): Memo[] {
  const monthDay = todayISO.slice(5);
  const year = todayISO.slice(0, 4);
  return memos
    .filter(
      (memo) =>
        isActive(memo) &&
        memo.entryDate.slice(5) === monthDay &&
        memo.entryDate.slice(0, 4) < year,
    )
    .sort((a, b) => b.entryDate.localeCompare(a.entryDate));
}

/** Counts active memos per entry date (YYYY-MM-DD). */
export function entryDateCounts(
  memos: readonly Memo[],
): Record<string, number> {
  const counts: Record<string, number> = {};
  for (const memo of memos) {
    if (isActive(memo)) {
      counts[memo.entryDate] = (counts[memo.entryDate] ?? 0) + 1;
    }
  }
  return counts;
}

/** Active memos written on a specific entry date. */
export function entriesByDate(memos: readonly Memo[], date: string): Memo[] {
  return memos.filter((memo) => isActive(memo) && memo.entryDate === date);
}
