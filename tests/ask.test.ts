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
    const result = await answerQuestion(env, { question: "我见了谁？", evidence: entry.body });
    expect(result.ok).toBe(false);
    expect(result.skippedReason).toContain("disabled");
  });

  it("answers grounded in the supplied records and embeds them in the prompt", async () => {
    await configureAnthropic();
    const fetchMock = anthropicText("你在 6 月 20 日和小明见了面。");
    vi.stubGlobal("fetch", fetchMock);

    const result = await answerQuestion(env, {
      question: "最近我见了谁？",
      evidence: "【2026-06-20】见面\n和小明在咖啡馆聊了很久。",
    });

    expect(result.ok).toBe(true);
    expect(result.answer).toBe("你在 6 月 20 日和小明见了面。");
    expect(result.model).toBe("claude-test");

    const body = JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body));
    const prompt = body.messages[0].content as string;
    expect(prompt).toContain("最近我见了谁？");
    expect(prompt).toContain("小明");
    expect(prompt).toContain("【记忆证据】");
  });

  it("includes prior turns when history is provided", async () => {
    await configureAnthropic();
    const fetchMock = anthropicText("是的，你们聊了很久。");
    vi.stubGlobal("fetch", fetchMock);

    const result = await answerQuestion(env, {
      question: "聊了很久吗？",
      evidence: "【2026-06-20】见面\n和小明在咖啡馆聊了很久。",
      history: [{ question: "我见了谁？", answer: "小明。" }],
    });

    expect(result.ok).toBe(true);
    const prompt = JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body)).messages[0].content;
    expect(prompt).toContain("【对话历史】");
    expect(prompt).toContain("小明。");
  });

  it("frames advice as grounded memory synthesis instead of exact-match retrieval", async () => {
    await configureAnthropic();
    const fetchMock = anthropicText("可以先减少上下文切换，并保留早晨写作。");
    vi.stubGlobal("fetch", fetchMock);

    const result = await answerQuestion(env, {
      question: "你推荐我做哪些调整？",
      evidence:
        "【2026-06-15】月中回顾\n最消耗的是反复切换上下文，最稳定的能量来自早晨写作、傍晚散步和拆分任务。",
      history: [{ question: "我最近状态怎么样？", answer: "整体稳定，但上下文切换消耗较大。" }],
    });

    expect(result.ok).toBe(true);
    const body = JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body));
    expect(body.system).toContain("总结、复盘、模式识别和温和建议");
    expect(body.system).toContain("不要要求记忆里必须已经写过明确的「建议」");
    expect(body.system).not.toContain("不要总结、复盘、诊断或扩写");
    expect(body.max_tokens).toBe(1800);
  });

  it("retries with a larger budget when the answer is truncated", async () => {
    await configureAnthropic();
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        Response.json({
          content: [{ type: "text", text: "从你最近的记录里，我推导出四条建议" }],
          stop_reason: "max_tokens",
        }),
      )
      .mockResolvedValueOnce(
        Response.json({
          content: [{ type: "text", text: "完整建议：减少切换，保留早晨写作。" }],
          stop_reason: "end_turn",
        }),
      );
    vi.stubGlobal("fetch", fetchMock);

    const result = await answerQuestion(env, {
      question: "你推荐我做哪些调整？",
      evidence: "最消耗的是反复切换上下文，最稳定的能量来自早晨写作。",
    });

    expect(result).toMatchObject({
      ok: true,
      answer: "完整建议：减少切换，保留早晨写作。",
    });
    expect(fetchMock).toHaveBeenCalledTimes(2);
    const firstBody = JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body));
    const secondBody = JSON.parse(String(fetchMock.mock.calls[1]?.[1]?.body));
    expect(firstBody.max_tokens).toBe(1800);
    expect(secondBody.max_tokens).toBe(3200);
    expect(secondBody.messages[0].content).toContain("请重新生成完整答案");
  });

  it("notes when there are no relevant records", async () => {
    await configureAnthropic();
    const fetchMock = anthropicText("记录里没有相关内容。");
    vi.stubGlobal("fetch", fetchMock);

    const result = await answerQuestion(env, { question: "我去过南极吗？", evidence: "" });
    expect(result.ok).toBe(true);
    const prompt = JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body)).messages[0].content;
    expect(prompt).toContain("所选来源里没有找到相关证据");
  });

  it("reports a skip when the model returns no usable text", async () => {
    await configureAnthropic();
    vi.stubGlobal("fetch", anthropicText("", "refusal"));
    const result = await answerQuestion(env, { question: "?", evidence: "证据" });
    expect(result.ok).toBe(false);
    expect(result.skippedReason).toBe("AI 未返回内容");
  });
});
