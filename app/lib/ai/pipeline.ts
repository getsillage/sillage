import { eq } from "drizzle-orm";
import { getDb } from "~/lib/db/client";
import type { EntryWithTags } from "~/lib/db/entries";
import { entries } from "~/lib/db/schema";
import { getAiConfig } from "./config";
import { embedText } from "./embedding";
import { generateText } from "./text";

export interface AiPipelineResult {
  summaryUpdated: boolean;
  sentimentUpdated: boolean;
  vectorUpdated: boolean;
  skippedReasons: string[];
}

function entryText(entry: EntryWithTags): string {
  return [entry.title, entry.body, entry.tags.map((tag) => `#${tag}`).join(" ")]
    .filter(Boolean)
    .join("\n\n");
}

async function upsertVector(
  env: Env,
  entry: EntryWithTags,
  vector: number[] | null,
): Promise<boolean> {
  if (!vector) {
    return false;
  }
  await env.VEC.upsert([
    {
      id: entry.id,
      values: vector,
      metadata: { entryDate: entry.entryDate },
    },
  ]);
  return true;
}

/**
 * Runs post-write AI enrichment. Errors are captured as skipped reasons so entry
 * saves never fail merely because an AI provider is unavailable or misconfigured.
 */
export async function runAiPipeline(env: Env, entry: EntryWithTags): Promise<AiPipelineResult> {
  const config = getAiConfig(env);
  const skippedReasons: string[] = [];
  const text = entryText(entry);

  const [summary, sentiment, embedding] = await Promise.all([
    generateText(env, config, {
      purpose: "summary",
      system: "你是个人日记助手。请用中文写一句简洁摘要，不要添加解释。",
      prompt: text,
      maxTokens: 160,
    }),
    generateText(env, config, {
      purpose: "sentiment",
      system:
        "你是情绪分类器。只输出一个中文情绪词，例如：开心、平静、低落、焦虑、疲惫、感恩。不要解释。",
      prompt: text,
      maxTokens: 32,
    }),
    embedText(env, config, text),
  ]);

  if (summary.skipped && summary.reason) {
    skippedReasons.push(summary.reason);
  }
  if (sentiment.skipped && sentiment.reason) {
    skippedReasons.push(sentiment.reason);
  }
  if (embedding.skipped && embedding.reason) {
    skippedReasons.push(embedding.reason);
  }

  const db = getDb(env.DB);
  const updateValues = {
    ...(summary.text ? { summary: summary.text } : {}),
    ...(sentiment.text ? { sentiment: sentiment.text } : {}),
    updatedAt: new Date(),
  };
  if (summary.text || sentiment.text) {
    await db.update(entries).set(updateValues).where(eq(entries.id, entry.id));
  }

  let vectorUpdated = false;
  try {
    vectorUpdated = await upsertVector(env, entry, embedding.vector);
  } catch (cause) {
    skippedReasons.push(cause instanceof Error ? cause.message : "Vectorize upsert failed");
  }

  return {
    summaryUpdated: Boolean(summary.text),
    sentimentUpdated: Boolean(sentiment.text),
    vectorUpdated,
    skippedReasons,
  };
}
