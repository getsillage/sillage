/**
 * Shared shape for a single AI generation attempt, returned by the JSON resource
 * routes and consumed by the client generation hook. Kept free of any server-only
 * imports so both sides can use it.
 */

export type AiFailureCategory =
  | "disabled"
  | "no-key"
  | "rate-limited"
  | "timeout"
  | "network"
  | "truncated"
  | "refusal"
  | "empty"
  | "no-entries"
  | "unknown";

export interface AiGenerationResult {
  ok: boolean;
  /** Human-friendly, already-classified message safe to show in the UI. */
  message: string;
  /** Optional next step, e.g. "到「设置」补全 API Key". */
  hint?: string;
  /** Set on failure; lets the UI tune tone/affordances per cause. */
  category?: AiFailureCategory;
  /** Provider/model that produced the text, on success. */
  model?: string | null;
  /** Server-measured wall-clock of the provider call(s), in milliseconds. */
  durationMs?: number;
}
