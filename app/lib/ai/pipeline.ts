import { getDb } from "~/lib/db/client";
import type { EntryWithTags } from "~/lib/db/entries";
import { entryAi } from "~/lib/db/schema";
import {
  entryKindLabel,
  normalizeEntryKind,
  normalizeReflectionType,
  parseTextList,
  reflectionTypeLabel,
} from "~/lib/product/entry-fields";
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
  const kind = normalizeEntryKind(entry.kind);
  const reflectionType = normalizeReflectionType(entry.reflectionType, kind);
  const labels = [
    `类型：${entryKindLabel(kind)}${reflectionType ? ` / ${reflectionTypeLabel(reflectionType)}` : ""}`,
    entry.moodText ? `细腻感受：${entry.moodText}` : "",
    entry.location ? `地点：${entry.location}` : "",
    parseTextList(entry.people).length > 0 ? `人物：${parseTextList(entry.people).join("、")}` : "",
    parseTextList(entry.relationships).length > 0
      ? `关系：${parseTextList(entry.relationships).join("、")}`
      : "",
  ];
  return [entry.title, ...labels, entry.body, entry.tags.map((tag) => `#${tag}`).join(" ")]
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
    system:
      "你是 Sillage 的回声层。请用中文写一句克制、具体、可追溯的短摘要，先说记录里留下了什么，不要诊断，不要替用户下结论。",
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
