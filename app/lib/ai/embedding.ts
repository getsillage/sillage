import type { AiConfig } from "./config";

export interface EmbeddingResult {
  vector: number[] | null;
  skipped: boolean;
  reason?: string;
}

interface WorkersAiEmbeddingResponse {
  data?: number[][];
}

interface OpenAiEmbeddingResponse {
  data?: Array<{ embedding?: number[] }>;
}

function getErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "AI embedding request failed";
}

function firstVector(value: unknown): number[] | null {
  if (!Array.isArray(value) || value.length === 0) {
    return null;
  }
  const first = value[0];
  return Array.isArray(first) && first.every((n) => typeof n === "number") ? first : null;
}

async function embedWithWorkersAi(
  env: Env,
  config: AiConfig,
  text: string,
): Promise<EmbeddingResult> {
  const output = (await env.AI.run(
    config.embeddingModel as never,
    {
      text: [text],
    } as never,
  )) as WorkersAiEmbeddingResponse;
  return { vector: firstVector(output.data), skipped: false };
}

async function embedWithOpenAi(env: Env, config: AiConfig, text: string): Promise<EmbeddingResult> {
  const apiKey = config.openaiApiKey ?? env.OPENAI_API_KEY;
  if (!apiKey) {
    return { vector: null, skipped: true, reason: "OPENAI_API_KEY not configured" };
  }

  const response = await fetch(`${config.openaiBaseUrl}/embeddings`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      authorization: `Bearer ${apiKey}`,
    },
    body: JSON.stringify({ model: config.openaiEmbeddingModel, input: text }),
  });

  if (!response.ok) {
    return { vector: null, skipped: true, reason: `OpenAI embeddings returned ${response.status}` };
  }

  const data = (await response.json()) as OpenAiEmbeddingResponse;
  return { vector: data.data?.[0]?.embedding ?? null, skipped: false };
}

export async function embedText(
  env: Env,
  config: AiConfig,
  text: string,
): Promise<EmbeddingResult> {
  const trimmed = text.trim();
  if (!trimmed) {
    return { vector: null, skipped: true, reason: "empty text" };
  }

  try {
    switch (config.embeddingProvider) {
      case "workers-ai":
        return await embedWithWorkersAi(env, config, trimmed);
      case "openai":
        return await embedWithOpenAi(env, config, trimmed);
      case "disabled":
        return { vector: null, skipped: true, reason: "AI_EMBEDDING_PROVIDER disabled" };
    }
  } catch (error: unknown) {
    return { vector: null, skipped: true, reason: getErrorMessage(error) };
  }
}
