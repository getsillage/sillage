import { env } from "cloudflare:test";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { runAiPipeline } from "../app/lib/ai/pipeline";
import { getDb } from "../app/lib/db/client";
import { createEntry, getEntry } from "../app/lib/db/entries";
import { saveAiSettings } from "../app/lib/settings/ai-settings";

const db = getDb(env.DB);

describe("AI pipeline", () => {
  beforeEach(async () => {
    await env.DB.prepare("DELETE FROM entry_ai").run();
    await env.DB.prepare("DELETE FROM entries").run();
    await env.DB.prepare("DELETE FROM tags").run();
    await env.SESSIONS.delete("ai-settings");
  });

  afterEach(() => {
    vi.unstubAllGlobals();
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

    const result = await runAiPipeline(env, entry);
    const updated = await getEntry(db, id);

    expect(result.summaryUpdated).toBe(false);
    expect(result.skippedReasons).toEqual(["AI text generation disabled"]);
    expect(updated?.summary).toBeNull();
    expect(updated?.sentiment).toBeNull();
  });

  it("writes summary when a web AI profile is configured", async () => {
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

    await saveAiSettings(env, {
      enabled: true,
      name: "Claude",
      protocol: "anthropic",
      baseUrl: "https://api.anthropic.com",
      model: "claude-test-model",
      apiKey: "sk-ant-web",
    });
    const fetchMock = vi.fn(async () =>
      Response.json({
        content: [{ type: "text", text: "看夕阳的一天" }],
        stop_reason: "end_turn",
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const result = await runAiPipeline(env, entry);
    const updated = await getEntry(db, id);

    expect(result.summaryUpdated).toBe(true);
    expect(result.skippedReasons).toEqual([]);
    expect(result.model).toBe("claude-test-model");
    expect(typeof result.durationMs).toBe("number");
    expect(updated?.summary).toBe("看夕阳的一天");
    expect(updated?.sentiment).toBeNull();
    // First successful generation records provenance for the history line.
    expect(updated?.aiModel).toBe("claude-test-model");
    expect(updated?.aiGenerationCount).toBe(1);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("writes summary via an OpenAI profile and records the model", async () => {
    const id = await createEntry(db, {
      entryDate: "2026-06-23",
      title: "山里徒步",
      body: "走了很久的山路。",
      tags: [],
    });
    const entry = await getEntry(db, id);
    if (!entry) {
      throw new Error("entry not created");
    }

    await saveAiSettings(env, {
      enabled: true,
      name: "OpenAI",
      protocol: "openai",
      baseUrl: "https://api.openai.com/v1",
      model: "gpt-test-model",
      apiKey: "sk-openai-web",
    });
    const fetchMock = vi.fn(async () =>
      Response.json({ choices: [{ message: { content: "徒步的一天" } }] }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const result = await runAiPipeline(env, entry);
    const updated = await getEntry(db, id);

    expect(result.summaryUpdated).toBe(true);
    expect(updated?.summary).toBe("徒步的一天");

    // The side table records which model produced the summary.
    const { results } = await env.DB.prepare("SELECT model FROM entry_ai WHERE entry_id = ?")
      .bind(id)
      .all<{ model: string | null }>();
    expect(results[0]?.model).toBe("gpt-test-model");
  });

  it("retries truncated entry summaries and does not store a still-truncated result", async () => {
    const id = await createEntry(db, {
      entryDate: "2026-06-23",
      title: "长记录",
      body: "很多细节。",
      tags: [],
    });
    const entry = await getEntry(db, id);
    if (!entry) {
      throw new Error("entry not created");
    }

    await saveAiSettings(env, {
      enabled: true,
      name: "Claude",
      protocol: "anthropic",
      baseUrl: "https://api.anthropic.com",
      model: "claude-test-model",
      apiKey: "sk-ant-web",
    });
    const fetchMock = vi.fn(async () =>
      Response.json({
        content: [{ type: "text", text: "半截摘要" }],
        stop_reason: "max_tokens",
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const result = await runAiPipeline(env, entry);
    const updated = await getEntry(db, id);

    expect(result.summaryUpdated).toBe(false);
    expect(result.skippedReasons).toEqual(["Anthropic 输出达到长度上限（max_tokens）"]);
    expect(updated?.summary).toBeNull();
    expect(fetchMock).toHaveBeenCalledTimes(2);
    const secondBody = JSON.parse(String(fetchMock.mock.calls[1]?.[1]?.body));
    expect(secondBody.max_tokens).toBe(640);
    expect(secondBody.messages[0].content).toContain("确保句子结束");
  });

  it("increments the generation count and records duration across regenerations", async () => {
    const id = await createEntry(db, {
      entryDate: "2026-06-23",
      title: "重复生成",
      body: "再来一次。",
      tags: [],
    });
    const entry = await getEntry(db, id);
    if (!entry) {
      throw new Error("entry not created");
    }

    await saveAiSettings(env, {
      enabled: true,
      name: "Claude",
      protocol: "anthropic",
      baseUrl: "https://api.anthropic.com",
      model: "claude-test-model",
      apiKey: "sk-ant-web",
    });
    const fetchMock = vi.fn(async () =>
      Response.json({
        content: [{ type: "text", text: "一句总结" }],
        stop_reason: "end_turn",
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    await runAiPipeline(env, entry);
    const second = await runAiPipeline(env, entry);
    const updated = await getEntry(db, id);

    expect(second.summaryUpdated).toBe(true);
    // Two successful runs => the counter reflects history, not just the latest.
    expect(updated?.aiGenerationCount).toBe(2);
    expect(typeof updated?.aiDurationMs).toBe("number");
    expect(updated?.aiGeneratedAt).toBeInstanceOf(Date);
  });
});
