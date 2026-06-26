import { ENTRY_INSIGHT_FORM_FIELD, type EntryInsightIntent } from "~/lib/ai/entry-insights.shared";
import { classifyAiFailure } from "~/lib/ai/failure";
import type { AiGenerationResult } from "~/lib/ai/generation-result";
import { runAiPipeline } from "~/lib/ai/pipeline";
import type { Db } from "~/lib/db/client";
import { getEntry } from "~/lib/db/entries";
import type { WaitUntil } from "~/lib/request-context";

export function entryInsightRequestedByForm(form: FormData): boolean {
  return form.get(ENTRY_INSIGHT_FORM_FIELD) === "on";
}

export function scheduleEntryInsight(
  env: Env,
  db: Db,
  waitUntil: WaitUntil,
  entryId: string,
): void {
  waitUntil(
    (async () => {
      try {
        const entry = await getEntry(db, entryId);
        if (entry) {
          await runAiPipeline(env, entry);
        }
      } catch (error) {
        console.warn("Entry insight generation failed", error);
      }
    })(),
  );
}

export async function runEntryInsightAction(
  env: Env,
  db: Db,
  entryId: string,
  intent: EntryInsightIntent,
): Promise<AiGenerationResult> {
  const entry = await getEntry(db, entryId);
  if (!entry) {
    return { ok: false, message: "记录不存在", category: "unknown" };
  }
  const result = await runAiPipeline(env, entry);
  if (result.summaryUpdated) {
    return {
      ok: true,
      message: intent === "regenerate-entry-insight" ? "已重新生成总结" : "已生成总结",
      model: result.model,
      durationMs: result.durationMs,
    };
  }
  const failure = classifyAiFailure(result.skippedReasons[0]);
  return { ok: false, message: failure.message, hint: failure.hint, category: failure.category };
}
