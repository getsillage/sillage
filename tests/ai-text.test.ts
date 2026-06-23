import { afterEach, describe, expect, it, vi } from "vitest";
import type { AiConfig } from "../app/lib/ai/config";
import { generateText } from "../app/lib/ai/text";

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

describe("AI text generation", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("skips when text generation is disabled", async () => {
    const result = await generateText(envOf({}), baseConfig, {
      purpose: "summary",
      system: "s",
      prompt: "p",
    });

    expect(result).toEqual({
      text: null,
      skipped: true,
      reason: "AI_TEXT_PROVIDER disabled",
    });
  });

  it("calls Anthropic Messages API with required headers and configured model", async () => {
    const fetchMock = vi.fn(async () =>
      Response.json({
        content: [{ type: "text", text: " 摘要 " }],
        stop_reason: "end_turn",
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const result = await generateText(
      envOf({ ANTHROPIC_API_KEY: "secret-key" }),
      { ...baseConfig, textProvider: "anthropic" },
      { purpose: "summary", system: "system prompt", prompt: "正文", maxTokens: 123 },
    );

    expect(result).toEqual({ text: "摘要", skipped: false });
    expect(fetchMock).toHaveBeenCalledOnce();
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("https://api.anthropic.com/v1/messages");
    expect(init.method).toBe("POST");
    expect(init.headers).toMatchObject({
      "content-type": "application/json",
      "x-api-key": "secret-key",
      "anthropic-version": "2023-06-01",
    });
    expect(JSON.parse(String(init.body))).toEqual({
      model: "claude-opus-4-8",
      max_tokens: 123,
      system: "system prompt",
      messages: [{ role: "user", content: "正文" }],
    });
  });

  it("calls OpenAI-compatible chat completions with configured base URL", async () => {
    const fetchMock = vi.fn(async () =>
      Response.json({ choices: [{ message: { content: " 平静 " } }] }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const result = await generateText(
      envOf({ OPENAI_API_KEY: "openai-key" }),
      { ...baseConfig, textProvider: "openai", openaiBaseUrl: "https://gateway.example/v1" },
      { purpose: "sentiment", system: "classify", prompt: "今天很好", maxTokens: 20 },
    );

    expect(result).toEqual({ text: "平静", skipped: false });
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("https://gateway.example/v1/chat/completions");
    expect(init.headers).toMatchObject({
      "content-type": "application/json",
      authorization: "Bearer openai-key",
    });
    expect(JSON.parse(String(init.body))).toMatchObject({
      model: "gpt-5.1-mini",
      max_completion_tokens: 20,
    });
  });

  it("captures provider failures as skipped results", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        throw new Error("network down");
      }),
    );

    const result = await generateText(
      envOf({ ANTHROPIC_API_KEY: "secret-key" }),
      { ...baseConfig, textProvider: "anthropic" },
      { purpose: "summary", system: "s", prompt: "p" },
    );

    expect(result).toEqual({ text: null, skipped: true, reason: "network down" });
  });
});
