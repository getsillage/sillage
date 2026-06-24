import { getDb } from "~/lib/db/client";
import type { EntryWithTags } from "~/lib/db/entries";
import { entryAi } from "~/lib/db/schema";
import { type AiConfig, loadAiConfig } from "./config";
import { generateText } from "./text";

export interface AiPipelineResult {
  summaryUpdated: boolean;
  skippedReasons: string[];
}

/** The model name backing the currently selected text provider, for audit. */
function activeModel(config: AiConfig): string | null {
  if (config.textProvider === "anthropic") {
    return config.anthropicModel;
  }
  if (config.textProvider === "openai") {
    return config.openaiModel;
  }
  return null;
}

function entryText(entry: EntryWithTags): string {
  return [entry.title, entry.body, entry.tags.map((tag) => `#${tag}`).join(" ")]
    .filter(Boolean)
    .join("\n\n");
}

/**
 * Runs post-write AI enrichment. Errors are captured as skipped reasons so entry
 * saves never fail merely because an AI provider is unavailable or misconfigured.
 *
 * The result is written to the `entry_ai` side table (upsert), never to `entries`,
 * so regenerating a summary does not bump `entries.updatedAt` or re-index FTS —
 * keeping the sync feed and search index quiet for a purely machine-derived change.
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
    const now = new Date();
    await db
      .insert(entryAi)
      .values({
        entryId: entry.id,
        summary: summary.text,
        model: activeModel(config),
        generatedAt: now,
      })
      .onConflictDoUpdate({
        target: entryAi.entryId,
        set: { summary: summary.text, model: activeModel(config), generatedAt: now },
      });
  }

  return {
    summaryUpdated: Boolean(summary.text),
    skippedReasons,
  };
}
