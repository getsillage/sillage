import { env } from "cloudflare:test";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { answerQuestion } from "../app/lib/ai/ask";
import { getDb } from "../app/lib/db/client";
import { createEntry, type EntryWithTags, getEntry } from "../app/lib/db/entries";
import { saveAiSettings } from "../app/lib/settings/ai-settings";

const db = getDb(env.DB);

async function resetDb() {
  await env.DB.prepare("DELETE FROM entry_revisions").run();
  await env.DB.prepare("DELETE FROM entry_tags").run();
  await env.DB.prepare("DELETE FROM entries").run();
  await env.DB.prepare("DELETE FROM tags").run();
  await env.SESSIONS.delete("ai-settings");
}

async function seedEntry(): Promise<EntryWithTags> {
  const id = await createEntry(db, {
    entryDate: "2026-06-20",
    title: "见面",
    body: "和小明在咖啡馆聊了很久。",
    people: ["小明"],
    tags: ["朋友"],
  });
  const entry = await getEntry(db, id);
  if (!entry) {
    throw new Error("seed failed");
  }
  return entry;
}

async function configureAnthropic() {
  await saveAiSettings(env, {
    enabled: true,
    name: "Claude",
    protocol: "anthropic",
    baseUrl: "https://api.anthropic.com",
    model: "claude-test",
    apiKey: "sk-ant-web",
  });
}

function anthropicText(text: string, stopReason = "end_turn") {
  return vi.fn(async () =>
    Response.json({ content: [{ type: "text", text }], stop_reason: stopReason }),
  );
}

describe("answerQuestion", () => {
  beforeEach(resetDb);
  afterEach(() => vi.unstubAllGlobals());

  it("skips when the provider is disabled", async () => {
    const entry = await seedEntry();
    const result = await answerQuestion(env, { question: "我见了谁？", entries: [entry] });
    expect(result.ok).toBe(false);
    expect(result.skippedReason).toContain("disabled");
  });

  it("answers grounded in the supplied records and embeds them in the prompt", async () => {
    await configureAnthropic();
    const fetchMock = anthropicText("你在 6 月 20 日和小明见了面。");
    vi.stubGlobal("fetch", fetchMock);

    const entry = await seedEntry();
    const result = await answerQuestion(env, { question: "最近我见了谁？", entries: [entry] });

    expect(result.ok).toBe(true);
    expect(result.answer).toBe("你在 6 月 20 日和小明见了面。");
    expect(result.model).toBe("claude-test");

    const body = JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body));
    const prompt = body.messages[0].content as string;
    expect(prompt).toContain("最近我见了谁？");
    expect(prompt).toContain("小明");
    expect(prompt).toContain("【相关记录】");
  });

  it("includes prior turns when history is provided", async () => {
    await configureAnthropic();
    const fetchMock = anthropicText("是的，你们聊了很久。");
    vi.stubGlobal("fetch", fetchMock);

    const entry = await seedEntry();
    const result = await answerQuestion(env, {
      question: "聊了很久吗？",
      entries: [entry],
      history: [{ question: "我见了谁？", answer: "小明。" }],
    });

    expect(result.ok).toBe(true);
    const prompt = JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body)).messages[0].content;
    expect(prompt).toContain("【对话历史】");
    expect(prompt).toContain("小明。");
  });

  it("notes when there are no relevant records", async () => {
    await configureAnthropic();
    const fetchMock = anthropicText("记录里没有相关内容。");
    vi.stubGlobal("fetch", fetchMock);

    const result = await answerQuestion(env, { question: "我去过南极吗？", entries: [] });
    expect(result.ok).toBe(true);
    const prompt = JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body)).messages[0].content;
    expect(prompt).toContain("没有检索到相关记录");
  });

  it("reports a skip when the model returns no usable text", async () => {
    await configureAnthropic();
    vi.stubGlobal("fetch", anthropicText("", "refusal"));
    const entry = await seedEntry();
    const result = await answerQuestion(env, { question: "?", entries: [entry] });
    expect(result.ok).toBe(false);
    expect(result.skippedReason).toBe("AI 未返回内容");
  });
});
