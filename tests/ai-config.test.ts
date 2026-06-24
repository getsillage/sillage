import { describe, expect, it } from "vitest";
import { getAiConfig } from "../app/lib/ai/config";

function envOf(values: Partial<Env>): Env {
  return values as Env;
}

describe("AI config", () => {
  it("defaults providers to disabled for dev-safe behavior", () => {
    const config = getAiConfig(envOf({}));

    expect(config.textProvider).toBe("disabled");
    expect(config.anthropicModel).toBe("claude-opus-4-8");
    expect(config.anthropicBaseUrl).toBe("https://api.anthropic.com");
  });

  it("ignores legacy AI environment configuration", () => {
    const config = getAiConfig({
      AI_TEXT_PROVIDER: "anthropic",
      ANTHROPIC_MODEL: "claude-sonnet-4-6",
      ANTHROPIC_BASE_URL: "https://anthropic.example.test",
      OPENAI_MODEL: "custom-chat-model",
      OPENAI_BASE_URL: "https://openai.example.test/v1",
    } as unknown as Env);

    expect(config.textProvider).toBe("disabled");
    expect(config.anthropicModel).toBe("claude-opus-4-8");
    expect(config.anthropicBaseUrl).toBe("https://api.anthropic.com");
    expect(config.openaiModel).toBe("gpt-5.1-mini");
    expect(config.openaiBaseUrl).toBe("https://api.openai.com/v1");
  });
});
