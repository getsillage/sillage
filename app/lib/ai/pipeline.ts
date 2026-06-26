import { sql } from "drizzle-orm";
import { getDb } from "~/lib/db/client";
import type { EntryWithAi } from "~/lib/db/entries";
import { entryAi } from "~/lib/db/schema";
import { type AiConfig, loadAiConfig } from "./config";
import { generateText } from "./text";

export interface AiPipelineResult {
  summaryUpdated: boolean;
  skippedReasons: string[];
  // Provenance + wall-clock of this run, surfaced by the manual-generation UI. The
  // model is the configured one even when the run is skipped; duration is the time
  // actually spent calling the provider.
  model: string | null;
  durationMs: number;
}

const SUMMARY_MAX_TOKENS = 320;
const SUMMARY_RETRY_MAX_TOKENS = 640;

/** The model name backing the currently selected text provider, for audit. */
export function activeModel(config: AiConfig): string | null {
  if (config.textProvider === "anthropic") {
    return config.anthropicModel;
  }
  if (config.textProvider === "openai") {
    return config.openaiModel;
  }
  return null;
}

function entryText(entry: EntryWithAi): string {
  return [`日期：${entry.entryDate}`, entry.body].filter(Boolean).join("\n\n");
}

/**
 * Runs post-write AI enrichment. Errors are captured as skipped reasons so entry
 * saves never fail merely because an AI provider is unavailable or misconfigured.
 *
 * The result is written to the `entry_ai` side table (upsert), never to `entries`,
 * so regenerating a summary does not bump `entries.updatedAt` or re-index FTS —
 * keeping the sync feed and search index quiet for a purely machine-derived change.
 */
export async function runAiPipeline(env: Env, entry: EntryWithAi): Promise<AiPipelineResult> {
  const config = await loadAiConfig(env);
  const skippedReasons: string[] = [];
  const text = entryText(entry);

  const prompt = text;
  const startedAt = Date.now();
  let summary = await generateText(config, {
    system:
      "你是 Sillage 的总结功能。请用中文写一句清楚、具体、可追溯的短摘要，直接概括这条记录的主要内容，不要诊断，不要替用户下结论。",
    prompt,
    maxTokens: SUMMARY_MAX_TOKENS,
  });

  if (!summary.skipped && summary.truncated) {
    summary = await generateText(config, {
      system:
        "你是 Sillage 的总结功能。请用中文写一句清楚、具体、可追溯的短摘要，直接概括这条记录的主要内容，不要诊断，不要替用户下结论。",
      prompt: `${prompt}\n\n【生成要求】\n上一次输出达到长度上限。请重新生成一句完整短摘要，确保句子结束。`,
      maxTokens: SUMMARY_RETRY_MAX_TOKENS,
    });
  }

  if (summary.skipped && summary.reason) {
    skippedReasons.push(summary.reason);
  }
  if (!summary.skipped && !summary.text) {
    skippedReasons.push(summary.reason ?? "AI 未返回内容");
  }
  if (!summary.skipped && summary.text && summary.truncated) {
    skippedReasons.push(summary.reason ?? "AI 输出达到长度上限");
  }

  const durationMs = Date.now() - startedAt;
  const model = activeModel(config);

  if (summary.text && !summary.truncated) {
    const db = getDb(env.DB);
    const now = new Date();
    await db
      .insert(entryAi)
      .values({
        entryId: entry.id,
        summary: summary.text,
        model,
        durationMs,
        generationCount: 1,
        generatedAt: now,
      })
      .onConflictDoUpdate({
        target: entryAi.entryId,
        set: {
          summary: summary.text,
          model,
          durationMs,
          // Each successful (re)generation bumps the counter for the history line.
          generationCount: sql`${entryAi.generationCount} + 1`,
          generatedAt: now,
        },
      });
  }

  return {
    summaryUpdated: Boolean(summary.text && !summary.truncated),
    skippedReasons,
    model,
    durationMs,
  };
}
