import { env } from "cloudflare:test";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createUserSession } from "../app/lib/auth/session";
import { getAskConversation } from "../app/lib/db/ask-conversations";
import { getDb } from "../app/lib/db/client";
import { createEntry } from "../app/lib/db/entries";
import { saveAiSettings } from "../app/lib/settings/ai-settings";
import { action as askStreamAction } from "../app/routes/api.ask-stream";

const db = getDb(env.DB);

async function resetDb() {
  await env.DB.prepare("DELETE FROM ask_messages").run();
  await env.DB.prepare("DELETE FROM ask_conversations").run();
  await env.DB.prepare("DELETE FROM entry_revisions").run();
  await env.DB.prepare("DELETE FROM entry_tags").run();
  await env.DB.prepare("DELETE FROM entry_ai").run();
  await env.DB.prepare("DELETE FROM entries").run();
  await env.DB.prepare("DELETE FROM tags").run();
  await env.SESSIONS.delete("ai-settings");
}

async function configureOpenAi() {
  await saveAiSettings(env, {
    enabled: true,
    name: "OpenAI",
    protocol: "openai",
    baseUrl: "https://api.openai.test/v1",
    model: "gpt-test",
    apiKey: "sk-test",
  });
}

async function authedRequest(form: FormData): Promise<Request> {
  const response = await createUserSession(env, "/");
  const cookie = response.headers.get("Set-Cookie");
  if (!cookie) {
    throw new Error("missing session cookie");
  }
  const request = new Request("https://sillage.example/api/ask-stream", {
    method: "POST",
    body: form,
  });
  request.headers.set("Cookie", cookie.split(";")[0]);
  return request;
}

function form(fields: Record<string, string | string[]>): FormData {
  const data = new FormData();
  for (const [key, value] of Object.entries(fields)) {
    const values = Array.isArray(value) ? value : [value];
    for (const item of values) {
      data.append(key, item);
    }
  }
  return data;
}

function sse(chunks: string[]): ReadableStream<Uint8Array> {
  return new ReadableStream({
    start(controller) {
      const encoder = new TextEncoder();
      for (const chunk of chunks) {
        controller.enqueue(encoder.encode(chunk));
      }
      controller.close();
    },
  });
}

describe("ask stream route", () => {
  beforeEach(resetDb);
  afterEach(() => vi.unstubAllGlobals());

  it("streams deltas and persists the assistant message", async () => {
    await configureOpenAi();
    await createEntry(db, {
      entryDate: "2026-06-20",
      title: "散步",
      body: "傍晚散步后状态变好了。",
      kind: "fragment",
      tags: [],
    });
    vi.stubGlobal(
      "fetch",
      vi.fn(
        async () =>
          new Response(
            sse([
              'data: {"choices":[{"delta":{"content":"可以"},"finish_reason":null}]}\n\n',
              'data: {"choices":[{"delta":{"content":"去散步"},"finish_reason":"stop"}]}\n\n',
              "data: [DONE]\n\n",
            ]),
            { headers: { "content-type": "text/event-stream" } },
          ),
      ),
    );

    const response = await askStreamAction({
      request: await authedRequest(
        form({ mode: "send", question: "我该怎么调整？", sources: ["fragment"] }),
      ),
      context: undefined as never,
      params: {},
    } as never);

    const lines = (await response.text()).trim().split("\n");
    expect(lines.map((line) => JSON.parse(line).type)).toEqual([
      "created",
      "sources",
      "delta",
      "delta",
      "done",
    ]);
    const created = JSON.parse(lines[0] ?? "{}") as { conversationId: string };
    const conversation = await getAskConversation(db, created.conversationId);
    expect(conversation?.messages.at(-1)).toMatchObject({
      role: "assistant",
      content: "可以去散步",
      status: "completed",
      model: "gpt-test",
    });
  });
});
