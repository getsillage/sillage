export const ENTRY_KINDS = ["fragment", "reflection", "draft"] as const;
export type EntryKind = (typeof ENTRY_KINDS)[number];

export const REFLECTION_TYPES = ["daily", "weekly", "monthly", "topic", "freeform"] as const;
export type ReflectionType = (typeof REFLECTION_TYPES)[number];

const ENTRY_KIND_LABELS: Record<EntryKind, string> = {
  fragment: "片段",
  reflection: "回顾",
  draft: "草稿",
};

const REFLECTION_TYPE_LABELS: Record<ReflectionType, string> = {
  daily: "今日回顾",
  weekly: "周回顾",
  monthly: "月回顾",
  topic: "主题回顾",
  freeform: "自由回顾",
};

export function isEntryKind(value: unknown): value is EntryKind {
  return typeof value === "string" && ENTRY_KINDS.includes(value as EntryKind);
}

export function isReflectionType(value: unknown): value is ReflectionType {
  return typeof value === "string" && REFLECTION_TYPES.includes(value as ReflectionType);
}

export function normalizeEntryKind(value: unknown): EntryKind {
  return isEntryKind(value) ? value : "fragment";
}

export function normalizeReflectionType(value: unknown, kind: EntryKind): ReflectionType | null {
  if (kind !== "reflection") {
    return null;
  }
  return isReflectionType(value) ? value : "daily";
}

export function entryKindLabel(kind: EntryKind): string {
  return ENTRY_KIND_LABELS[kind];
}

export function reflectionTypeLabel(type: ReflectionType | null): string | null {
  return type ? REFLECTION_TYPE_LABELS[type] : null;
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
