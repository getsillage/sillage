export const ENTRY_KINDS = ["fragment", "note", "draft"] as const;
export type EntryKind = (typeof ENTRY_KINDS)[number];

export const NOTE_TYPES = ["daily", "weekly", "monthly", "topic", "freeform"] as const;
export type NoteType = (typeof NOTE_TYPES)[number];

const ENTRY_KIND_LABELS: Record<EntryKind, string> = {
  fragment: "片段",
  note: "笔记",
  draft: "草稿",
};

const NOTE_TYPE_LABELS: Record<NoteType, string> = {
  daily: "今日笔记",
  weekly: "周笔记",
  monthly: "月笔记",
  topic: "主题笔记",
  freeform: "自由笔记",
};

export function isEntryKind(value: unknown): value is EntryKind {
  return typeof value === "string" && ENTRY_KINDS.includes(value as EntryKind);
}

export function isNoteType(value: unknown): value is NoteType {
  return typeof value === "string" && NOTE_TYPES.includes(value as NoteType);
}

export function normalizeEntryKind(value: unknown): EntryKind {
  return isEntryKind(value) ? value : "fragment";
}

export function normalizeNoteType(value: unknown, kind: EntryKind): NoteType | null {
  if (kind !== "note") {
    return null;
  }
  return isNoteType(value) ? value : "daily";
}

export function entryKindLabel(kind: EntryKind): string {
  return ENTRY_KIND_LABELS[kind];
}

export function noteTypeLabel(type: NoteType | null): string | null {
  return type ? NOTE_TYPE_LABELS[type] : null;
}

export function cleanText(value: unknown): string | null {
  if (typeof value !== "string") {
    return null;
  }
  const trimmed = value.trim();
  return trimmed || null;
}

export function cleanTextList(value: Iterable<unknown>): string[] {
  const seen = new Set<string>();
  const result: string[] = [];
  for (const item of value) {
    const text = cleanText(item);
    if (!text || seen.has(text)) {
      continue;
    }
    seen.add(text);
    result.push(text);
  }
  return result;
}

export function serializeTextList(value: string[]): string {
  return JSON.stringify(cleanTextList(value));
}

export function parseTextList(raw: string | null | undefined): string[] {
  if (!raw) {
    return [];
  }
  try {
    const parsed: unknown = JSON.parse(raw);
    return Array.isArray(parsed) ? cleanTextList(parsed) : [];
  } catch {
    return [];
  }
}
