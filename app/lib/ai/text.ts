import type { AiConfig } from "./config";
import {
  anthropicEndpointCandidates,
  fetchWithEndpointFallback,
  openAiEndpointCandidates,
} from "./endpoints";

export interface GenerateTextInput {
  system: string;
  prompt: string;
  maxTokens?: number;
}

export interface GenerateTextResult {
  text: string | null;
  skipped: boolean;
  reason?: string;
}

interface AnthropicTextBlock {
  type: "text";
  text: string;
}

interface AnthropicMessageResponse {
  content: Array<AnthropicTextBlock | { type: string }>;
  stop_reason?: string | null;
}

interface OpenAiChatResponse {
  choices?: Array<{ message?: { content?: string | null } }>;
}

function getErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "AI provider request failed";
}

function textFromAnthropic(response: AnthropicMessageResponse): string | null {
  if (response.stop_reason === "refusal") {
    return null;
  }
  const block = response.content.find((item): item is AnthropicTextBlock => item.type === "text");
  return block?.text.trim() || null;
}

async function generateWithAnthropic(
  config: AiConfig,
  input: GenerateTextInput,
): Promise<GenerateTextResult> {
  const apiKey = config.anthropicApiKey;
  if (!apiKey) {
    return { text: null, skipped: true, reason: "Anthropic API key not configured" };
  }

  const response = await fetchWithEndpointFallback(
    anthropicEndpointCandidates(config.anthropicBaseUrl, "messages"),
    {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-api-key": apiKey,
        "anthropic-version": "2023-06-01",
      },
      body: JSON.stringify({
        model: config.anthropicModel,
        max_tokens: input.maxTokens ?? 512,
        system: input.system,
        messages: [{ role: "user", content: input.prompt }],
      }),
    },
  );

  if (!response.ok) {
    return { text: null, skipped: true, reason: `Anthropic API returned ${response.status}` };
  }

  const data = (await response.json()) as AnthropicMessageResponse;
  return { text: textFromAnthropic(data), skipped: false };
}

async function generateWithOpenAi(
  config: AiConfig,
  input: GenerateTextInput,
): Promise<GenerateTextResult> {
  const apiKey = config.openaiApiKey;
  if (!apiKey) {
    return { text: null, skipped: true, reason: "OpenAI API key not configured" };
  }

  const response = await fetchWithEndpointFallback(
    openAiEndpointCandidates(config.openaiBaseUrl, "chat/completions"),
    {
      method: "POST",
      headers: {
        "content-type": "application/json",
        authorization: `Bearer ${apiKey}`,
      },
      body: JSON.stringify({
        model: config.openaiModel,
        messages: [
          { role: "system", content: input.system },
          { role: "user", content: input.prompt },
        ],
        max_completion_tokens: input.maxTokens ?? 512,
      }),
    },
  );

  if (!response.ok) {
    return { text: null, skipped: true, reason: `OpenAI API returned ${response.status}` };
  }

  const data = (await response.json()) as OpenAiChatResponse;
  return {
    text: data.choices?.[0]?.message?.content?.trim() || null,
    skipped: false,
  };
}

export async function generateText(
  config: AiConfig,
  input: GenerateTextInput,
): Promise<GenerateTextResult> {
  try {
    switch (config.textProvider) {
      case "anthropic":
        return await generateWithAnthropic(config, input);
      case "openai":
        return await generateWithOpenAi(config, input);
      case "disabled":
        return { text: null, skipped: true, reason: "AI text generation disabled" };
    }
  } catch (error: unknown) {
    return { text: null, skipped: true, reason: getErrorMessage(error) };
  }
}
