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
