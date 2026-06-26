import { env } from "cloudflare:test";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createUserSession } from "../app/lib/auth/session";
import {
  beginAskSend,
  completeAskAssistantMessage,
  getAskConversation,
} from "../app/lib/db/ask-conversations";
import { getDb } from "../app/lib/db/client";
import { createEntry } from "../app/lib/db/entries";
import { saveAiSettings } from "../app/lib/settings/ai-settings";
import { action as askAction, loader as askLoader } from "../app/routes/ask";

const db = getDb(env.DB);

async function resetDb() {
  await env.DB.prepare("DELETE FROM summaries").run();
  await env.DB.prepare("DELETE FROM ask_messages").run();
  await env.DB.prepare("DELETE FROM ask_conversations").run();
  await env.DB.prepare("DELETE FROM entry_revisions").run();
  await env.DB.prepare("DELETE FROM entry_tags").run();
  await env.DB.prepare("DELETE FROM entry_ai").run();
  await env.DB.prepare("DELETE FROM entries").run();
  await env.DB.prepare("DELETE FROM tags").run();
  await env.SESSIONS.delete("ai-settings");
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

async function authenticatedRequest(form: Record<string, string | string[]>, url: string) {
  const response = await createUserSession(env, "/");
  const cookie = response.headers.get("Set-Cookie");
  if (!cookie) {
    throw new Error("missing session cookie");
  }
  const body = new URLSearchParams();
  for (const [key, value] of Object.entries(form)) {
    const values = Array.isArray(value) ? value : [value];
    for (const item of values) {
      body.append(key, item);
    }
  }
  const request = new Request(url, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body,
  });
  request.headers.set("Cookie", cookie.split(";")[0]);
  return request;
}

async function authenticatedGet(url: string) {
  const response = await createUserSession(env, "/");
  const cookie = response.headers.get("Set-Cookie");
  if (!cookie) {
    throw new Error("missing session cookie");
  }
  const request = new Request(url);
  request.headers.set("Cookie", cookie.split(";")[0]);
  return request;
}

describe("ask routes", () => {
  beforeEach(resetDb);
  afterEach(() => vi.unstubAllGlobals());

  it("answers ask requests from /ask with selected sources and citations", async () => {
    await configureAnthropic();
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        Response.json({
          content: [{ type: "text", text: "你在 6 月 20 日和小明见了面。" }],
          stop_reason: "end_turn",
        }),
      ),
    );
    const id = await createEntry(db, {
      entryDate: "2026-06-20",
      title: "见面",
      body: "和小明在咖啡馆聊了很久。",
      tags: [],
    });

    const result = await askAction({
      request: await authenticatedRequest(
        {
          intent: "ask",
          question: "最近我见了谁？",
          history: JSON.stringify([{ question: "之前问了什么？", answer: "见面。" }]),
          sources: ["entry"],
        },
        "https://sillage.example/ask",
      ),
      context: undefined as never,
      params: {},
    } as never);

    expect(result).toMatchObject({
      intent: "ask",
      ok: true,
      answer: "你在 6 月 20 日和小明见了面。",
    });
    expect(result.sources?.[0]).toMatchObject({
      id,
      href: `/entries/${id}`,
      kind: "entry",
    });

    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>;
    const body = JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body));
    expect(body.messages[0].content).toContain("【对话历史】");
    expect(body.messages[0].content).toContain("和小明在咖啡馆聊了很久");
  });

  it("loads saved ask conversations and saves an answer as a record", async () => {
    const run = await beginAskSend(db, {
      question: "这段要不要保存？",
      sourceTypes: ["entry"],
    });
    await completeAskAssistantMessage(db, {
      messageId: run.assistantMessage.id,
      content: "值得保存，之后可以整理成一条记录。",
      sources: [],
      model: "test",
      durationMs: 1,
    });

    const loaderData = await askLoader({
      request: await authenticatedGet(
        `https://sillage.example/ask?conversation=${run.conversation.id}`,
      ),
      context: undefined as never,
      params: {},
    } as never);
    expect(loaderData.currentConversation?.messages).toHaveLength(2);
    expect(loaderData.conversations[0]?.id).toBe(run.conversation.id);

    const result = await askAction({
      request: await authenticatedRequest(
        {
          intent: "saveAskEntry",
          conversationId: run.conversation.id,
          messageId: run.assistantMessage.id,
        },
        "https://sillage.example/ask",
      ),
      context: undefined as never,
      params: {},
    } as never);
    expect(result).toMatchObject({ ok: true, message: "已保存为记录" });

    const conversation = await getAskConversation(db, run.conversation.id);
    expect(conversation?.messages.at(-1)?.content).toContain("值得保存");
  });
});
