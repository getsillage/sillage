import { z } from "zod";
import { loadAiSettings } from "~/lib/settings/ai-settings";

export type TextProvider = "disabled" | "workers-ai" | "anthropic" | "openai";
export type EmbeddingProvider = "disabled" | "workers-ai" | "openai";

export interface AiConfig {
  textProvider: TextProvider;
  embeddingProvider: EmbeddingProvider;
  summaryModel: string;
  sentimentModel: string;
  embeddingModel: string;
  anthropicModel: string;
  anthropicBaseUrl: string;
  openaiModel: string;
  openaiEmbeddingModel: string;
  openaiBaseUrl: string;
  // Resolved API keys (from web settings or env). Undefined when not configured.
  anthropicApiKey?: string;
  openaiApiKey?: string;
}

const DEFAULTS: AiConfig = {
  textProvider: "disabled",
  embeddingProvider: "disabled",
  summaryModel: "@cf/qwen/qwen2.5-coder-32b-instruct",
  sentimentModel: "@cf/qwen/qwen2.5-coder-32b-instruct",
  embeddingModel: "@cf/baai/bge-m3",
  anthropicModel: "claude-opus-4-8",
  anthropicBaseUrl: "https://api.anthropic.com",
  openaiModel: "gpt-5.1-mini",
  openaiEmbeddingModel: "text-embedding-3-large",
  openaiBaseUrl: "https://api.openai.com/v1",
};

const textProviderSchema = z
  .enum(["disabled", "workers-ai", "anthropic", "openai"])
  .catch(DEFAULTS.textProvider);
const embeddingProviderSchema = z
  .enum(["disabled", "workers-ai", "openai"])
  .catch(DEFAULTS.embeddingProvider);

function envString(env: Env, key: keyof Env, fallback: string): string {
  const value = env[key];
  return typeof value === "string" && value.trim() ? value.trim() : fallback;
}

/** Reads AI provider/model configuration from Worker env vars/secrets. */
export function getAiConfig(env: Env): AiConfig {
  return {
    textProvider: textProviderSchema.parse(
      envString(env, "AI_TEXT_PROVIDER", DEFAULTS.textProvider),
    ),
    embeddingProvider: embeddingProviderSchema.parse(
      envString(env, "AI_EMBEDDING_PROVIDER", DEFAULTS.embeddingProvider),
    ),
    summaryModel: envString(env, "AI_SUMMARY_MODEL", DEFAULTS.summaryModel),
    sentimentModel: envString(env, "AI_SENTIMENT_MODEL", DEFAULTS.sentimentModel),
    embeddingModel: envString(env, "AI_EMBEDDING_MODEL", DEFAULTS.embeddingModel),
    anthropicModel: envString(env, "ANTHROPIC_MODEL", DEFAULTS.anthropicModel),
    anthropicBaseUrl: envString(env, "ANTHROPIC_BASE_URL", DEFAULTS.anthropicBaseUrl),
    openaiModel: envString(env, "OPENAI_MODEL", DEFAULTS.openaiModel),
    openaiEmbeddingModel: envString(env, "OPENAI_EMBEDDING_MODEL", DEFAULTS.openaiEmbeddingModel),
    openaiBaseUrl: envString(env, "OPENAI_BASE_URL", DEFAULTS.openaiBaseUrl),
    anthropicApiKey: env.ANTHROPIC_API_KEY,
    openaiApiKey: env.OPENAI_API_KEY,
  };
}

/**
 * Resolves the effective AI config: web-managed settings (when present and
 * enabled) drive the text/chat provider and override its model, base URL, and
 * key; everything else falls back to env config. Used by the AI pipeline.
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
      anthropicApiKey: web.apiKey || base.anthropicApiKey,
    };
  }
  return {
    ...base,
    textProvider: "openai",
    openaiModel: web.model,
    openaiBaseUrl: web.baseUrl,
    openaiApiKey: web.apiKey || base.openaiApiKey,
  };
}
