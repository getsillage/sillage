import { env } from "cloudflare:test";
import { beforeEach, describe, expect, it } from "vitest";
import { loadAiConfig } from "../app/lib/ai/config";
import { saveAiSettings } from "../app/lib/settings/ai-settings";

describe("loadAiConfig resolution", () => {
  beforeEach(async () => {
    await env.SESSIONS.delete("ai-settings");
  });

  it("falls back to env config when no web settings are saved", async () => {
    const config = await loadAiConfig(env);
    expect(config.textProvider).toBe("disabled");
  });

  it("lets enabled Anthropic web settings drive the text provider", async () => {
    await saveAiSettings(env, {
      enabled: true,
      protocol: "anthropic",
      baseUrl: "https://anthropic.example/test",
      model: "claude-test-model",
      apiKey: "sk-ant-web",
    });

    const config = await loadAiConfig(env);
    expect(config.textProvider).toBe("anthropic");
    expect(config.anthropicModel).toBe("claude-test-model");
    expect(config.anthropicBaseUrl).toBe("https://anthropic.example/test");
    expect(config.anthropicApiKey).toBe("sk-ant-web");
  });

  it("lets enabled OpenAI web settings drive the text provider", async () => {
    await saveAiSettings(env, {
      enabled: true,
      protocol: "openai",
      baseUrl: "https://gateway.example/v1",
      model: "gpt-web-model",
      apiKey: "sk-openai-web",
    });

    const config = await loadAiConfig(env);
    expect(config.textProvider).toBe("openai");
    expect(config.openaiModel).toBe("gpt-web-model");
    expect(config.openaiBaseUrl).toBe("https://gateway.example/v1");
    expect(config.openaiApiKey).toBe("sk-openai-web");
  });

  it("ignores web settings when disabled", async () => {
    await saveAiSettings(env, {
      enabled: false,
      protocol: "anthropic",
      baseUrl: "https://anthropic.example/test",
      model: "claude-test-model",
      apiKey: "sk-ant-web",
    });

    const config = await loadAiConfig(env);
    expect(config.textProvider).toBe("disabled");
  });
});
