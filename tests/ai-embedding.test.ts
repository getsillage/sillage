import { afterEach, describe, expect, it, vi } from "vitest";
import type { AiConfig } from "../app/lib/ai/config";
import { embedText } from "../app/lib/ai/embedding";

const baseConfig: AiConfig = {
  textProvider: "disabled",
  embeddingProvider: "disabled",
  summaryModel: "@cf/summary",
  sentimentModel: "@cf/sentiment",
  embeddingModel: "@cf/embed",
  anthropicModel: "claude-opus-4-8",
  anthropicBaseUrl: "https://api.anthropic.com",
  openaiModel: "gpt-5.1-mini",
  openaiEmbeddingModel: "text-embedding-3-large",
  openaiBaseUrl: "https://api.openai.com/v1",
};

function envOf(values: Partial<Env>): Env {
  return values as Env;
}

describe("AI embeddings", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("skips blank input before calling a provider", async () => {
    const result = await embedText(envOf({}), baseConfig, "   ");

    expect(result).toEqual({ vector: null, skipped: true, reason: "empty text" });
  });

  it("skips when embedding provider is disabled", async () => {
    const result = await embedText(envOf({}), baseConfig, "日记正文");

    expect(result).toEqual({
      vector: null,
      skipped: true,
      reason: "AI_EMBEDDING_PROVIDER disabled",
    });
  });

  it("calls OpenAI embeddings with configured model", async () => {
    const fetchMock = vi.fn(async () => Response.json({ data: [{ embedding: [0.1, 0.2, 0.3] }] }));
    vi.stubGlobal("fetch", fetchMock);

    const result = await embedText(
      envOf({ OPENAI_API_KEY: "openai-key" }),
      {
        ...baseConfig,
        embeddingProvider: "openai",
        openaiBaseUrl: "https://gateway.example/v1",
      },
      "日记正文",
    );

    expect(result).toEqual({ vector: [0.1, 0.2, 0.3], skipped: false });
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("https://gateway.example/v1/embeddings");
    expect(init.headers).toMatchObject({
      "content-type": "application/json",
      authorization: "Bearer openai-key",
    });
    expect(JSON.parse(String(init.body))).toEqual({
      model: "text-embedding-3-large",
      input: "日记正文",
    });
  });

  it("captures embedding provider failures as skipped results", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        throw new Error("embedding offline");
      }),
    );

    const result = await embedText(
      envOf({ OPENAI_API_KEY: "openai-key" }),
      { ...baseConfig, embeddingProvider: "openai" },
      "日记正文",
    );

    expect(result).toEqual({
      vector: null,
      skipped: true,
      reason: "embedding offline",
    });
  });

  it("skips OpenAI embeddings when no API key is configured", async () => {
    const result = await embedText(
      envOf({}),
      { ...baseConfig, embeddingProvider: "openai" },
      "日记正文",
    );

    expect(result).toEqual({
      vector: null,
      skipped: true,
      reason: "OPENAI_API_KEY not configured",
    });
  });

  it("reports a skipped result when OpenAI embeddings return a non-OK status", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response("error", { status: 503 })),
    );

    const result = await embedText(
      envOf({ OPENAI_API_KEY: "openai-key" }),
      { ...baseConfig, embeddingProvider: "openai" },
      "日记正文",
    );

    expect(result).toEqual({
      vector: null,
      skipped: true,
      reason: "OpenAI embeddings returned 503",
    });
  });

  it("returns a null vector when OpenAI omits embedding data", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => Response.json({ data: [] })),
    );

    const result = await embedText(
      envOf({ OPENAI_API_KEY: "openai-key" }),
      { ...baseConfig, embeddingProvider: "openai" },
      "日记正文",
    );

    expect(result).toEqual({ vector: null, skipped: false });
  });
});
