import { env } from "cloudflare:test";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { runAiPipeline } from "../app/lib/ai/pipeline";
import { getDb } from "../app/lib/db/client";
import { createEntry, getEntry } from "../app/lib/db/entries";

const db = getDb(env.DB);

interface AiRunCall {
  model: string;
  input: unknown;
}

function testEnv(values: Partial<Env>): Env {
  return { ...env, ...values } as Env;
}

describe("AI pipeline", () => {
  beforeEach(async () => {
    await env.DB.prepare("DELETE FROM entries").run();
    await env.DB.prepare("DELETE FROM tags").run();
  });

  it("degrades safely when AI providers are disabled", async () => {
    const id = await createEntry(db, {
      entryDate: "2026-06-23",
      title: "普通一天",
      body: "今天散步，天气很好。",
      tags: ["生活"],
    });
    const entry = await getEntry(db, id);
    if (!entry) {
      throw new Error("entry not created");
    }

    const result = await runAiPipeline(testEnv({}), entry);
    const updated = await getEntry(db, id);

    expect(result.summaryUpdated).toBe(false);
    expect(result.sentimentUpdated).toBe(false);
    expect(result.vectorUpdated).toBe(false);
    expect(result.skippedReasons).toEqual([
      "AI_TEXT_PROVIDER disabled",
      "AI_TEXT_PROVIDER disabled",
      "AI_EMBEDDING_PROVIDER disabled",
    ]);
    expect(updated?.summary).toBeNull();
    expect(updated?.sentiment).toBeNull();
  });

  it("writes summary, sentiment, and Vectorize embedding when configured", async () => {
    const id = await createEntry(db, {
      entryDate: "2026-06-23",
      title: "海边散步",
      body: "今天看到了漂亮夕阳。",
      tags: ["旅行"],
    });
    const entry = await getEntry(db, id);
    if (!entry) {
      throw new Error("entry not created");
    }

    const aiCalls: AiRunCall[] = [];
    const aiRun = vi.fn(async (model: string, input: unknown) => {
      aiCalls.push({ model, input });
      if (model === "summary-model") {
        return { response: "看夕阳的一天" };
      }
      if (model === "sentiment-model") {
        return { response: "开心" };
      }
      return { data: [[0.1, 0.2, 0.3]] };
    });
    const upsert = vi.fn(async () => undefined);

    const result = await runAiPipeline(
      testEnv({
        AI_TEXT_PROVIDER: "workers-ai",
        AI_EMBEDDING_PROVIDER: "workers-ai",
        AI_SUMMARY_MODEL: "summary-model",
        AI_SENTIMENT_MODEL: "sentiment-model",
        AI_EMBEDDING_MODEL: "embedding-model",
        AI: { run: aiRun } as unknown as Env["AI"],
        VEC: { upsert } as unknown as Env["VEC"],
      }),
      entry,
    );
    const updated = await getEntry(db, id);

    expect(result).toEqual({
      summaryUpdated: true,
      sentimentUpdated: true,
      vectorUpdated: true,
      skippedReasons: [],
    });
    expect(updated?.summary).toBe("看夕阳的一天");
    expect(updated?.sentiment).toBe("开心");
    expect(aiCalls.map((call) => call.model).sort()).toEqual([
      "embedding-model",
      "sentiment-model",
      "summary-model",
    ]);
    expect(upsert).toHaveBeenCalledWith([
      {
        id,
        values: [0.1, 0.2, 0.3],
        metadata: { entryDate: "2026-06-23" },
      },
    ]);
  });
});
