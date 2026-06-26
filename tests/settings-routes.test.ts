import { env } from "cloudflare:test";
import { beforeEach, describe, expect, it } from "vitest";
import { createUserSession } from "../app/lib/auth/session";
import { loadAiSettingsView, saveEntryInsightAutoMode } from "../app/lib/settings/ai-settings";
import { action as settingsAction } from "../app/routes/settings";

async function resetSettings() {
  await env.SESSIONS.delete("ai-settings");
}

async function authenticatedRequest(
  form: Record<string, string>,
  url = "https://sillage.example/settings",
) {
  const response = await createUserSession(env, "/");
  const cookie = response.headers.get("Set-Cookie");
  if (!cookie) {
    throw new Error("missing session cookie");
  }
  const body = new URLSearchParams(form);
  const request = new Request(url, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body,
  });
  request.headers.set("Cookie", cookie.split(";")[0]);
  return request;
}

describe("settings route", () => {
  beforeEach(resetSettings);

  it("saves generation preferences independently from AI profiles", async () => {
    const preferences = await settingsAction({
      request: await authenticatedRequest({
        intent: "preferences",
        entryInsightAutoMode: "all",
      }),
      context: undefined as never,
      params: {},
    } as never);

    expect(preferences).toMatchObject({ intent: "preferences", ok: true });
    expect((await loadAiSettingsView(env)).entryInsightAutoMode).toBe("all");

    const saved = await settingsAction({
      request: await authenticatedRequest({
        intent: "save",
        name: "Claude",
        enabled: "on",
        protocol: "anthropic",
        baseUrl: "https://api.anthropic.com",
        model: "claude-test",
        apiKey: "sk-test",
        entryInsightAutoMode: "off",
      }),
      context: undefined as never,
      params: {},
    } as never);

    expect(saved).toMatchObject({ intent: "save", ok: true });
    const view = await loadAiSettingsView(env);
    expect(view.entryInsightAutoMode).toBe("all");
    expect(view.profiles[0]).toMatchObject({
      name: "Claude",
      protocol: "anthropic",
      model: "claude-test",
      hasApiKey: true,
    });
  });

  it("normalizes invalid generation preference values", async () => {
    await saveEntryInsightAutoMode(env, "all");

    const result = await settingsAction({
      request: await authenticatedRequest({
        intent: "preferences",
        entryInsightAutoMode: "unexpected",
      }),
      context: undefined as never,
      params: {},
    } as never);

    expect(result).toMatchObject({ intent: "preferences", ok: true });
    expect((await loadAiSettingsView(env)).entryInsightAutoMode).toBe("off");
  });
});
