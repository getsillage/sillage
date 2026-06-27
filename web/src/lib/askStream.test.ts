import { afterEach, describe, expect, it, vi } from "vitest";
import { streamAskMessage } from "./api";

vi.mock("./auth", () => ({
  clearAccessToken: () => {},
  setAccessToken: () => {},
  getAccessToken: () => "",
}));

function sseResponse(body: string): Response {
  return new Response(body, {
    status: 200,
    headers: { "Content-Type": "text/event-stream" },
  });
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe("streamAskMessage", () => {
  it("dispatches start, delta, and done events from the SSE stream", async () => {
    const sse =
      'event: start\ndata: {"userMessage":{"id":"u1"},"sources":[{"memoId":"m1"}]}\n\n' +
      'event: delta\ndata: {"text":"你好"}\n\n' +
      'event: delta\ndata: {"text":"，世界"}\n\n' +
      'event: done\ndata: {"message":{"id":"a1","content":"你好，世界"}}\n\n';
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(sseResponse(sse)),
    );

    const deltas: string[] = [];
    let started: unknown;
    let done: unknown;
    await streamAskMessage(
      "token",
      "conv1",
      { content: "hi", contextScope: "recent_30_days" },
      {
        onStart: (data) => {
          started = data;
        },
        onDelta: (text) => deltas.push(text),
        onDone: (message) => {
          done = message;
        },
      },
    );

    expect(started).toEqual({
      userMessage: { id: "u1" },
      sources: [{ memoId: "m1" }],
    });
    expect(deltas).toEqual(["你好", "，世界"]);
    expect(done).toEqual({ id: "a1", content: "你好，世界" });
  });

  it("surfaces a pre-stream error envelope", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ error: { message: "请先配置 AI" } }), {
          status: 400,
          headers: { "Content-Type": "application/json" },
        }),
      ),
    );

    await expect(
      streamAskMessage(
        "token",
        "conv1",
        { content: "hi", contextScope: "all" },
        {},
      ),
    ).rejects.toThrow("请先配置 AI");
  });

  it("delivers an error event without throwing", async () => {
    const sse = 'event: error\ndata: {"message":"生成回答失败"}\n\n';
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(sseResponse(sse)));

    let errored = "";
    await streamAskMessage(
      "token",
      "conv1",
      { content: "hi", contextScope: "all" },
      { onError: (message) => (errored = message) },
    );
    expect(errored).toBe("生成回答失败");
  });
});
