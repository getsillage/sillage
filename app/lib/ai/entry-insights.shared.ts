import { type EntryKind, normalizeEntryKind } from "~/lib/product/entry-fields";

export const ENTRY_INSIGHT_FORM_FIELD = "generateEntryInsight";
export const ENTRY_INSIGHT_INTENTS = [
  "generate-entry-insight",
  "regenerate-entry-insight",
] as const;
export type EntryInsightIntent = (typeof ENTRY_INSIGHT_INTENTS)[number];

export interface EntryInsightActionData {
  intent: EntryInsightIntent;
  ok: boolean;
  message: string;
}

export const ENTRY_INSIGHT_AUTO_MODES = ["off", "notes", "all"] as const;
export type EntryInsightAutoMode = (typeof ENTRY_INSIGHT_AUTO_MODES)[number];
export const DEFAULT_ENTRY_INSIGHT_AUTO_MODE: EntryInsightAutoMode = "notes";

export function isEntryInsightIntent(value: string): value is EntryInsightIntent {
  return (ENTRY_INSIGHT_INTENTS as readonly string[]).includes(value);
}

export function shouldGenerateEntryInsightForKind(
  mode: EntryInsightAutoMode,
  kind: EntryKind | string | null | undefined,
): boolean {
  const normalized = normalizeEntryKind(kind);
  if (mode === "all") {
    return true;
  }
  if (mode === "notes") {
    return normalized === "note";
  }
  return false;
}
