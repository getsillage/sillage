import { describe, expect, it } from "vitest";
import { getAiConfig } from "../app/lib/ai/config";

function envOf(values: Partial<Env>): Env {
  return values as Env;
}

describe("AI config", () => {
  it("defaults providers to disabled for dev-safe behavior", () => {
    const config = getAiConfig(envOf({}));

    expect(config.textProvider).toBe("disabled");
    expect(config.embeddingProvider).toBe("disabled");
    expect(config.anthropicModel).toBe("claude-opus-4-8");
    expect(config.anthropicBaseUrl).toBe("https://api.anthropic.com");
  });

  it("accepts configurable Anthropic and OpenAI settings", () => {
    const config = getAiConfig(
      envOf({
        AI_TEXT_PROVIDER: "anthropic",
        AI_EMBEDDING_PROVIDER: "openai",
        ANTHROPIC_MODEL: "claude-sonnet-4-6",
        ANTHROPIC_BASE_URL: "https://anthropic.example.test",
        OPENAI_MODEL: "custom-chat-model",
        OPENAI_EMBEDDING_MODEL: "custom-embedding-model",
        OPENAI_BASE_URL: "https://openai.example.test/v1",
      }),
    );

    expect(config.textProvider).toBe("anthropic");
    expect(config.embeddingProvider).toBe("openai");
    expect(config.anthropicModel).toBe("claude-sonnet-4-6");
    expect(config.anthropicBaseUrl).toBe("https://anthropic.example.test");
    expect(config.openaiModel).toBe("custom-chat-model");
    expect(config.openaiEmbeddingModel).toBe("custom-embedding-model");
    expect(config.openaiBaseUrl).toBe("https://openai.example.test/v1");
  });

  it("falls back to disabled for invalid provider values", () => {
    const config = getAiConfig(
      envOf({
        AI_TEXT_PROVIDER: "unknown",
        AI_EMBEDDING_PROVIDER: "anthropic",
      }),
    );

    expect(config.textProvider).toBe("disabled");
    expect(config.embeddingProvider).toBe("disabled");
  });
});
