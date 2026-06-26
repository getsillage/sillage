import { env } from "cloudflare:test";
import { beforeEach, describe, expect, it } from "vitest";
import {
  type AiSettingsInput,
  activateAiSettingsProfile,
  deleteAiSettingsProfile,
  loadAiSettings,
  loadAiSettingsProfile,
  loadAiSettingsView,
  loadEntryInsightAutoMode,
  saveAiSettings,
  saveEntryInsightAutoMode,
} from "../app/lib/settings/ai-settings";

const baseInput: AiSettingsInput = {
  name: "Claude",
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
    expect(await loadAiSettingsView(env)).toEqual({
      activeProfileId: null,
      profiles: [],
      entryInsightAutoMode: "off",
    });
  });

  it("round-trips settings and decrypts the stored key", async () => {
    const id = await saveAiSettings(env, baseInput);

    const loaded = await loadAiSettings(env);
    expect(loaded).toEqual({
      id,
      name: "Claude",
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
    expect(view.profiles).toHaveLength(1);
    expect(view.profiles[0]).toMatchObject({
      name: "Claude",
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
    const id = await saveAiSettings(env, baseInput);
    await saveAiSettings(env, {
      ...baseInput,
      id,
      protocol: "openai",
      baseUrl: "https://api.openai.com/v1",
      model: "gpt-5.1-mini",
      apiKey: "",
    });

    const loaded = await loadAiSettings(env);
    expect(loaded?.protocol).toBe("openai");
    expect(loaded?.model).toBe("gpt-5.1-mini");
    expect(loaded?.apiKey).toBe("sk-ant-secret");
    expect((await loadAiSettingsView(env)).profiles[0]?.hasApiKey).toBe(true);
  });

  it("stores multiple profiles and switches the active profile", async () => {
    const anthropicId = await saveAiSettings(env, baseInput);
    const openaiId = await saveAiSettings(env, {
      enabled: true,
      name: "OpenAI Gateway",
      protocol: "openai",
      baseUrl: "https://gateway.example/v1",
      model: "gpt-web-model",
      apiKey: "sk-openai-secret",
    });

    const view = await loadAiSettingsView(env);
    expect(view.activeProfileId).toBe(openaiId);
    expect(view.profiles.map((profile) => profile.name)).toEqual(["Claude", "OpenAI Gateway"]);

    expect(await activateAiSettingsProfile(env, anthropicId)).toBe(true);
    expect((await loadAiSettings(env))?.id).toBe(anthropicId);
    expect((await loadAiSettingsProfile(env, openaiId))?.apiKey).toBe("sk-openai-secret");
  });

  it("deletes profiles and advances the active profile", async () => {
    const firstId = await saveAiSettings(env, baseInput);
    const secondId = await saveAiSettings(env, {
      enabled: false,
      name: "备用",
      protocol: "openai",
      baseUrl: "https://api.openai.com/v1",
      model: "gpt-5.1-mini",
      apiKey: "sk-openai-secret",
    });

    expect(await deleteAiSettingsProfile(env, secondId)).toBe(true);
    expect((await loadAiSettingsView(env)).activeProfileId).toBe(firstId);
    expect(await deleteAiSettingsProfile(env, firstId)).toBe(true);
    expect(await loadAiSettingsView(env)).toEqual({
      activeProfileId: null,
      profiles: [],
      entryInsightAutoMode: "off",
    });
  });

  it("stores the entry insight auto-generation preference", async () => {
    expect(await loadEntryInsightAutoMode(env)).toBe("off");

    await saveEntryInsightAutoMode(env, "all");
    expect(await loadEntryInsightAutoMode(env)).toBe("all");
    expect((await loadAiSettingsView(env)).entryInsightAutoMode).toBe("all");
  });
});
