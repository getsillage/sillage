import {
  ENTRY_INSIGHT_FORM_FIELD,
  type EntryInsightActionData,
  type EntryInsightIntent,
} from "~/lib/ai/entry-insights.shared";
import { runAiPipeline } from "~/lib/ai/pipeline";
import type { Db } from "~/lib/db/client";
import { getEntry } from "~/lib/db/entries";
import type { WaitUntil } from "~/lib/request-context";

export function entryInsightRequestedByForm(form: FormData): boolean {
  return form.get(ENTRY_INSIGHT_FORM_FIELD) === "on";
}

function friendlyReason(reason?: string): string {
  if (!reason) {
    return "未能生成洞察";
  }
  if (reason.includes("disabled")) {
    return "AI 未启用，请先在「设置」中配置并启用 AI 功能";
  }
  if (reason.includes("key not configured")) {
    return "尚未配置 API Key，请到「设置」补全";
  }
  return `未能生成：${reason}`;
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
): Promise<EntryInsightActionData> {
  const entry = await getEntry(db, entryId);
  if (!entry) {
    return { intent, ok: false, message: "记录不存在" };
  }
  const result = await runAiPipeline(env, entry);
  return result.summaryUpdated
    ? { intent, ok: true, message: entry.summary ? "已重新生成洞察" : "已生成洞察" }
    : { intent, ok: false, message: friendlyReason(result.skippedReasons[0]) };
}
