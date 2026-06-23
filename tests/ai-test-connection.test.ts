import { afterEach, describe, expect, it, vi } from "vitest";
import { testAiConnection } from "../app/lib/ai/test-connection";

describe("AI connection test", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("fails fast without an API key", async () => {
    const result = await testAiConnection({
      protocol: "anthropic",
      baseUrl: "https://api.anthropic.com",
      model: "claude-opus-4-8",
      apiKey: "",
    });
    expect(result).toEqual({ ok: false, message: "缺少 API Key" });
  });

  it("pings the Anthropic Messages API and reports success", async () => {
    const fetchMock = vi.fn(async () => Response.json({ content: [] }, { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    const result = await testAiConnection({
      protocol: "anthropic",
      baseUrl: "https://api.anthropic.com",
      model: "claude-opus-4-8",
      apiKey: "sk-ant-key",
    });

    expect(result).toEqual({ ok: true, status: 200, message: "连接成功" });
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("https://api.anthropic.com/v1/messages");
    expect(init.headers).toMatchObject({
      "x-api-key": "sk-ant-key",
      "anthropic-version": "2023-06-01",
    });
    expect(JSON.parse(String(init.body)).max_tokens).toBe(1);
  });

  it("pings the OpenAI chat completions endpoint and reports success", async () => {
    const fetchMock = vi.fn(async () => Response.json({ choices: [] }, { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    const result = await testAiConnection({
      protocol: "openai",
      baseUrl: "https://gateway.example/v1",
      model: "gpt-5.1-mini",
      apiKey: "sk-openai-key",
    });

    expect(result.ok).toBe(true);
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("https://gateway.example/v1/chat/completions");
    expect(init.headers).toMatchObject({ authorization: "Bearer sk-openai-key" });
  });

  it("reports the status and detail on a non-OK response", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response("invalid api key", { status: 401 })),
    );

    const result = await testAiConnection({
      protocol: "anthropic",
      baseUrl: "https://api.anthropic.com",
      model: "claude-opus-4-8",
      apiKey: "wrong",
    });

    expect(result.ok).toBe(false);
    expect(result.status).toBe(401);
    expect(result.message).toContain("请求失败（401）");
    expect(result.message).toContain("invalid api key");
  });

  it("captures network errors as a failed result", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        throw new Error("getaddrinfo ENOTFOUND");
      }),
    );

    const result = await testAiConnection({
      protocol: "openai",
      baseUrl: "https://bad.example/v1",
      model: "gpt-5.1-mini",
      apiKey: "sk-openai-key",
    });

    expect(result).toEqual({ ok: false, message: "getaddrinfo ENOTFOUND" });
  });
});
