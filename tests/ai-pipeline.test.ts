import { env } from "cloudflare:test";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { runAiPipeline } from "../app/lib/ai/pipeline";
import { getDb } from "../app/lib/db/client";
import { createEntry, getEntry } from "../app/lib/db/entries";
import { saveAiSettings } from "../app/lib/settings/ai-settings";

const db = getDb(env.DB);

describe("AI pipeline", () => {
  beforeEach(async () => {
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

    expect(result).toEqual({
      summaryUpdated: true,
      skippedReasons: [],
    });
    expect(updated?.summary).toBe("看夕阳的一天");
    expect(updated?.sentiment).toBeNull();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
