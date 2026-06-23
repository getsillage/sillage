import { env } from "cloudflare:test";
import { beforeEach, describe, expect, it } from "vitest";
import {
  type AiSettingsInput,
  loadAiSettings,
  loadAiSettingsView,
  saveAiSettings,
} from "../app/lib/settings/ai-settings";

const baseInput: AiSettingsInput = {
  enabled: true,
  protocol: "anthropic",
  baseUrl: "https://api.anthropic.com",
  model: "claude-opus-4-8",
  apiKey: "sk-ant-secret",
};

describe("AI settings storage", () => {
  beforeEach(async () => {
    await env.SESSIONS.delete("ai-settings");
  });

  it("returns null before anything is configured", async () => {
    expect(await loadAiSettings(env)).toBeNull();
    expect(await loadAiSettingsView(env)).toBeNull();
  });

  it("round-trips settings and decrypts the stored key", async () => {
    await saveAiSettings(env, baseInput);

    const loaded = await loadAiSettings(env);
    expect(loaded).toEqual({
      enabled: true,
      protocol: "anthropic",
      baseUrl: "https://api.anthropic.com",
      model: "claude-opus-4-8",
      apiKey: "sk-ant-secret",
    });
  });

  it("never exposes the key in the browser-safe view", async () => {
    await saveAiSettings(env, baseInput);

    const view = await loadAiSettingsView(env);
    expect(view).toEqual({
      enabled: true,
      protocol: "anthropic",
      baseUrl: "https://api.anthropic.com",
      model: "claude-opus-4-8",
      hasApiKey: true,
    });
    expect(JSON.stringify(view)).not.toContain("sk-ant-secret");
  });

  it("does not store the key in plaintext", async () => {
    await saveAiSettings(env, baseInput);

    const raw = await env.SESSIONS.get("ai-settings");
    expect(raw).not.toContain("sk-ant-secret");
  });

  it("preserves the existing key when saved with an empty apiKey", async () => {
    await saveAiSettings(env, baseInput);
    await saveAiSettings(env, {
      ...baseInput,
      protocol: "openai",
      baseUrl: "https://api.openai.com/v1",
      model: "gpt-5.1-mini",
      apiKey: "",
    });

    const loaded = await loadAiSettings(env);
    expect(loaded?.protocol).toBe("openai");
    expect(loaded?.model).toBe("gpt-5.1-mini");
    expect(loaded?.apiKey).toBe("sk-ant-secret");
    expect((await loadAiSettingsView(env))?.hasApiKey).toBe(true);
  });
});
