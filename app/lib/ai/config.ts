import { loadAiSettings } from "~/lib/settings/ai-settings";

export type TextProvider = "disabled" | "anthropic" | "openai";

export interface AiConfig {
  textProvider: TextProvider;
  anthropicModel: string;
  anthropicBaseUrl: string;
  openaiModel: string;
  openaiBaseUrl: string;
  // Resolved API keys from web-managed settings. Undefined when not configured.
  anthropicApiKey?: string;
  openaiApiKey?: string;
}

const DEFAULTS: AiConfig = {
  textProvider: "disabled",
  anthropicModel: "claude-opus-4-8",
  anthropicBaseUrl: "https://api.anthropic.com",
  openaiModel: "gpt-5.1-mini",
  openaiBaseUrl: "https://api.openai.com/v1",
};

/** Returns the safe default AI config. Runtime AI setup is web-managed only. */
export function getAiConfig(_env: Env): AiConfig {
  return { ...DEFAULTS };
}

/**
 * Resolves the effective AI config. Web-managed settings drive the text/chat
 * provider; when no enabled active profile exists, AI remains disabled.
 */
export async function loadAiConfig(env: Env): Promise<AiConfig> {
  const base = getAiConfig(env);
  const web = await loadAiSettings(env);
  if (!web?.enabled) {
    return base;
  }
  if (web.protocol === "anthropic") {
    return {
      ...base,
      textProvider: "anthropic",
      anthropicModel: web.model,
      anthropicBaseUrl: web.baseUrl,
      anthropicApiKey: web.apiKey || undefined,
    };
  }
  return {
    ...base,
    textProvider: "openai",
    openaiModel: web.model,
    openaiBaseUrl: web.baseUrl,
    openaiApiKey: web.apiKey || undefined,
  };
}
