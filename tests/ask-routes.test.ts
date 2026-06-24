import { env } from "cloudflare:test";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createUserSession } from "../app/lib/auth/session";
import { getDb } from "../app/lib/db/client";
import { createEntry } from "../app/lib/db/entries";
import { saveAiSettings } from "../app/lib/settings/ai-settings";
import { action as insightsAction } from "../app/routes/insights";
import { action as memoryAction } from "../app/routes/memory";

const db = getDb(env.DB);

async function resetDb() {
  await env.DB.prepare("DELETE FROM summaries").run();
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

describe("ask routes", () => {
  beforeEach(resetDb);
  afterEach(() => vi.unstubAllGlobals());

  it("answers ask requests from /memory with selected sources and citations", async () => {
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
      kind: "fragment",
      tags: [],
    });

    const result = await memoryAction({
      request: await authenticatedRequest(
        {
          intent: "ask",
          question: "最近我见了谁？",
          history: JSON.stringify([{ question: "之前问了什么？", answer: "见面。" }]),
          sources: ["fragment", "note"],
        },
        "https://sillage.example/memory",
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

  it("does not handle ask requests from /insights", async () => {
    const result = await insightsAction({
      request: await authenticatedRequest(
        { intent: "ask", question: "最近我见了谁？" },
        "https://sillage.example/insights",
      ),
      context: undefined as never,
      params: {},
    } as never);

    expect(result.intent).toBe("generate");
    expect(result.ok).toBe(false);
  });
});
