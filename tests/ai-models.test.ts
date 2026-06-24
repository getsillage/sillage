import { afterEach, describe, expect, it, vi } from "vitest";
import { listAiModels } from "../app/lib/ai/models";

describe("AI model listing", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("lists OpenAI-compatible models", async () => {
    const fetchMock = vi.fn(async () =>
      Response.json({
        data: [{ id: "gpt-5.1-mini" }, { id: "gpt-5.1" }, { id: "gpt-5.1-mini" }],
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const result = await listAiModels({
      protocol: "openai",
      baseUrl: "https://gateway.example/v1/",
      apiKey: "sk-openai",
    });

    expect(result).toEqual({
      ok: true,
      status: 200,
      models: ["gpt-5.1-mini", "gpt-5.1"],
      message: "已获取 2 个模型",
    });
    expect(fetchMock).toHaveBeenCalledWith("https://gateway.example/v1/models", {
      method: "GET",
      headers: { authorization: "Bearer sk-openai" },
    });
  });

  it("lists Anthropic models", async () => {
    const fetchMock = vi.fn(async () =>
      Response.json({
        data: [{ id: "claude-opus-4-8" }, { id: "claude-sonnet-4-6" }],
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const result = await listAiModels({
      protocol: "anthropic",
      baseUrl: "https://api.anthropic.com",
      apiKey: "sk-ant",
    });

    expect(result.ok).toBe(true);
    expect(result.models).toEqual(["claude-opus-4-8", "claude-sonnet-4-6"]);
    expect(fetchMock).toHaveBeenCalledWith("https://api.anthropic.com/v1/models", {
      method: "GET",
      headers: {
        "x-api-key": "sk-ant",
        "anthropic-version": "2023-06-01",
      },
    });
  });

  it("fails fast without an API key", async () => {
    const result = await listAiModels({
      protocol: "openai",
      baseUrl: "https://api.openai.com/v1",
      apiKey: "",
    });

    expect(result).toEqual({ ok: false, models: [], message: "缺少 API Key" });
  });

  it("reports provider errors", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response("invalid key", { status: 401 })),
    );

    const result = await listAiModels({
      protocol: "anthropic",
      baseUrl: "https://api.anthropic.com",
      apiKey: "bad-key",
    });

    expect(result).toEqual({
      ok: false,
      status: 401,
      models: [],
      message: "获取模型失败（401）：invalid key",
    });
  });
});
