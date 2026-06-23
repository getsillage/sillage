import { z } from "zod";

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
  };
}
