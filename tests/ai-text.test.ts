import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { AiConfig } from "../app/lib/ai/config";
import { generateText } from "../app/lib/ai/text";

const baseConfig: AiConfig = {
  textProvider: "disabled",
  anthropicModel: "claude-opus-4-8",
  anthropicBaseUrl: "https://api.anthropic.com",
  openaiModel: "gpt-5.1-mini",
  openaiBaseUrl: "https://api.openai.com/v1",
};

describe("AI text generation", () => {
  let warnSpy: ReturnType<typeof vi.spyOn>;
  let errorSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    warnSpy = vi.spyOn(console, "warn").mockImplementation(() => {});
    errorSpy = vi.spyOn(console, "error").mockImplementation(() => {});
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("skips when text generation is disabled", async () => {
    const result = await generateText(baseConfig, {
      system: "s",
      prompt: "p",
    });

    expect(result).toEqual({
      text: null,
      skipped: true,
      reason: "AI text generation disabled",
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
      { ...baseConfig, textProvider: "anthropic", anthropicApiKey: "secret-key" },
      { system: "system prompt", prompt: "正文", maxTokens: 123 },
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

  it("extracts Anthropic text from all text blocks", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        Response.json({
          content: [
            { type: "text", text: "第一段" },
            { type: "thinking", thinking: "hidden" },
            { type: "text", text: "第二段" },
          ],
          stop_reason: "end_turn",
        }),
      ),
    );

    const result = await generateText(
      { ...baseConfig, textProvider: "anthropic", anthropicApiKey: "secret-key" },
      { system: "s", prompt: "p" },
    );

    expect(result).toEqual({ text: "第一段第二段", skipped: false });
  });

  it("calls OpenAI-compatible chat completions with configured base URL", async () => {
    const fetchMock = vi.fn(async () =>
      Response.json({ choices: [{ message: { content: " 摘要 " } }] }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const result = await generateText(
      {
        ...baseConfig,
        textProvider: "openai",
        openaiBaseUrl: "https://gateway.example/v1",
        openaiApiKey: "openai-key",
      },
      { system: "summarize", prompt: "今天很好", maxTokens: 20 },
    );

    expect(result).toEqual({ text: "摘要", skipped: false });
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("https://gateway.example/v1/chat/completions");
    expect(init.headers).toMatchObject({
      "content-type": "application/json",
      authorization: "Bearer openai-key",
    });
    expect(JSON.parse(String(init.body))).toMatchObject({
      model: "gpt-5.1-mini",
      max_completion_tokens: 20,
      reasoning_effort: "low",
    });
  });

  it("extracts OpenAI-compatible text from multipart chat content", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        Response.json({
          choices: [
            {
              message: {
                content: [
                  { type: "text", text: "第一段" },
                  { type: "output_text", text: { value: "第二段" } },
                ],
              },
            },
          ],
        }),
      ),
    );

    const result = await generateText(
      {
        ...baseConfig,
        textProvider: "openai",
        openaiModel: "custom-chat-model",
        openaiApiKey: "openai-key",
      },
      { system: "s", prompt: "p" },
    );

    expect(result).toEqual({ text: "第一段第二段", skipped: false });
  });

  it("extracts OpenAI-compatible text from legacy choice text and output_text fields", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => Response.json({ choices: [{ text: " 旧字段 " }], output_text: "" })),
    );

    const result = await generateText(
      { ...baseConfig, textProvider: "openai", openaiApiKey: "openai-key" },
      { system: "s", prompt: "p" },
    );

    expect(result).toEqual({ text: "旧字段", skipped: false });
  });

  it("extracts OpenAI Responses-style output content", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        Response.json({
          output: [
            {
              type: "message",
              content: [
                { type: "output_text", text: "第一段" },
                { type: "output_text", text: { value: "第二段" } },
              ],
            },
          ],
        }),
      ),
    );

    const result = await generateText(
      { ...baseConfig, textProvider: "openai", openaiApiKey: "openai-key" },
      { system: "s", prompt: "p" },
    );

    expect(result).toEqual({ text: "第一段第二段", skipped: false });
  });

  it("captures provider failures as skipped results", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        throw new Error("network down");
      }),
    );

    const result = await generateText(
      { ...baseConfig, textProvider: "anthropic", anthropicApiKey: "secret-key" },
      { system: "s", prompt: "p" },
    );

    expect(result).toEqual({ text: null, skipped: true, reason: "network down" });
    expect(errorSpy).toHaveBeenCalledWith(
      "[ai] Anthropic request errored",
      expect.objectContaining({ model: "claude-opus-4-8", reason: "network down" }),
    );
  });

  it("skips Anthropic when no API key is configured", async () => {
    const result = await generateText(
      { ...baseConfig, textProvider: "anthropic" },
      { system: "s", prompt: "p" },
    );

    expect(result).toEqual({
      text: null,
      skipped: true,
      reason: "Anthropic API key not configured",
    });
  });

  it("skips OpenAI when no API key is configured", async () => {
    const result = await generateText(
      { ...baseConfig, textProvider: "openai" },
      { system: "s", prompt: "p" },
    );

    expect(result).toEqual({
      text: null,
      skipped: true,
      reason: "OpenAI API key not configured",
    });
  });

  it("reports a skipped result when Anthropic returns a non-OK status", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response("error", { status: 500 })),
    );

    const result = await generateText(
      { ...baseConfig, textProvider: "anthropic", anthropicApiKey: "secret-key" },
      { system: "s", prompt: "p" },
    );

    expect(result).toEqual({
      text: null,
      skipped: true,
      reason: "Anthropic API returned 500：error",
    });
    expect(warnSpy).toHaveBeenCalledWith(
      "[ai] Anthropic request failed",
      expect.objectContaining({ status: 500, reason: "Anthropic API returned 500：error" }),
    );
  });

  it("reports a skipped result when OpenAI returns a non-OK status", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response("error", { status: 429 })),
    );

    const result = await generateText(
      { ...baseConfig, textProvider: "openai", openaiApiKey: "openai-key" },
      { system: "s", prompt: "p" },
    );

    expect(result).toEqual({
      text: null,
      skipped: true,
      reason: "OpenAI API returned 429：error",
    });
    expect(warnSpy).toHaveBeenCalledWith(
      "[ai] OpenAI request failed",
      expect.objectContaining({ status: 429, reason: "OpenAI API returned 429：error" }),
    );
  });

  it("returns null text when Anthropic responds with a refusal stop reason", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => Response.json({ content: [], stop_reason: "refusal" })),
    );

    const result = await generateText(
      { ...baseConfig, textProvider: "anthropic", anthropicApiKey: "secret-key" },
      { system: "s", prompt: "p" },
    );

    expect(result).toEqual({
      text: null,
      skipped: false,
      reason: "Anthropic 未返回可用文本（refusal）",
    });
    expect(warnSpy).toHaveBeenCalledWith(
      "[ai] Anthropic returned no usable text",
      expect.objectContaining({ stopReason: "refusal" }),
    );
  });

  it("marks Anthropic max_tokens responses as truncated", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        Response.json({
          content: [{ type: "text", text: "未完成的回答" }],
          stop_reason: "max_tokens",
        }),
      ),
    );

    const result = await generateText(
      { ...baseConfig, textProvider: "anthropic", anthropicApiKey: "secret-key" },
      { system: "s", prompt: "p" },
    );

    expect(result).toEqual({
      text: "未完成的回答",
      skipped: false,
      reason: "Anthropic 输出达到长度上限（max_tokens）",
      truncated: true,
    });
  });

  it("marks OpenAI-compatible length finish reasons as truncated", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        Response.json({
          choices: [{ message: { content: "未完成的回答" }, finish_reason: "length" }],
        }),
      ),
    );

    const result = await generateText(
      { ...baseConfig, textProvider: "openai", openaiApiKey: "openai-key" },
      { system: "s", prompt: "p" },
    );

    expect(result).toEqual({
      text: "未完成的回答",
      skipped: false,
      reason: "OpenAI 输出达到长度上限（length）",
      truncated: true,
    });
  });

  it("treats OpenAI incomplete responses without text as truncation", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        Response.json({
          status: "incomplete",
          incomplete_details: { reason: "max_output_tokens" },
        }),
      ),
    );

    const result = await generateText(
      { ...baseConfig, textProvider: "openai", openaiApiKey: "openai-key" },
      { system: "s", prompt: "p" },
    );

    expect(result).toEqual({
      text: null,
      skipped: false,
      reason: "OpenAI 输出达到长度上限（max_output_tokens）",
      truncated: true,
    });
  });
});
