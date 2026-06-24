import { eq } from "drizzle-orm";
import { getDb } from "~/lib/db/client";
import type { EntryWithTags } from "~/lib/db/entries";
import { entries } from "~/lib/db/schema";
import { loadAiConfig } from "./config";
import { generateText } from "./text";

export interface AiPipelineResult {
  summaryUpdated: boolean;
  skippedReasons: string[];
}

function entryText(entry: EntryWithTags): string {
  return [entry.title, entry.body, entry.tags.map((tag) => `#${tag}`).join(" ")]
    .filter(Boolean)
    .join("\n\n");
}

/**
 * Runs post-write AI enrichment. Errors are captured as skipped reasons so entry
 * saves never fail merely because an AI provider is unavailable or misconfigured.
 */
export async function runAiPipeline(env: Env, entry: EntryWithTags): Promise<AiPipelineResult> {
  const config = await loadAiConfig(env);
  const skippedReasons: string[] = [];
  const text = entryText(entry);

  const summary = await generateText(config, {
    system: "你是个人日记助手。请用中文写一句简洁摘要，不要添加解释。",
    prompt: text,
    maxTokens: 160,
  });

  if (summary.skipped && summary.reason) {
    skippedReasons.push(summary.reason);
  }

  if (summary.text) {
    const db = getDb(env.DB);
    await db
      .update(entries)
      .set({ summary: summary.text, updatedAt: new Date() })
      .where(eq(entries.id, entry.id));
  }

  return {
    summaryUpdated: Boolean(summary.text),
    skippedReasons,
  };
}
