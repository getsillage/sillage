import type { AiConfig } from "./config";

export interface GenerateTextInput {
  system: string;
  prompt: string;
  maxTokens?: number;
  purpose: "summary" | "sentiment";
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
  env: Env,
  config: AiConfig,
  input: GenerateTextInput,
): Promise<GenerateTextResult> {
  if (!env.ANTHROPIC_API_KEY) {
    return { text: null, skipped: true, reason: "ANTHROPIC_API_KEY not configured" };
  }

  const response = await fetch(`${config.anthropicBaseUrl}/v1/messages`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-api-key": env.ANTHROPIC_API_KEY,
      "anthropic-version": "2023-06-01",
    },
    body: JSON.stringify({
      model: config.anthropicModel,
      max_tokens: input.maxTokens ?? 512,
      system: input.system,
      messages: [{ role: "user", content: input.prompt }],
    }),
  });

  if (!response.ok) {
    return { text: null, skipped: true, reason: `Anthropic API returned ${response.status}` };
  }

  const data = (await response.json()) as AnthropicMessageResponse;
  return { text: textFromAnthropic(data), skipped: false };
}

async function generateWithOpenAi(
  env: Env,
  config: AiConfig,
  input: GenerateTextInput,
): Promise<GenerateTextResult> {
  if (!env.OPENAI_API_KEY) {
    return { text: null, skipped: true, reason: "OPENAI_API_KEY not configured" };
  }

  const response = await fetch(`${config.openaiBaseUrl}/chat/completions`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      authorization: `Bearer ${env.OPENAI_API_KEY}`,
    },
    body: JSON.stringify({
      model: config.openaiModel,
      messages: [
        { role: "system", content: input.system },
        { role: "user", content: input.prompt },
      ],
      max_completion_tokens: input.maxTokens ?? 512,
    }),
  });

  if (!response.ok) {
    return { text: null, skipped: true, reason: `OpenAI API returned ${response.status}` };
  }

  const data = (await response.json()) as OpenAiChatResponse;
  return {
    text: data.choices?.[0]?.message?.content?.trim() || null,
    skipped: false,
  };
}

async function generateWithWorkersAi(
  env: Env,
  config: AiConfig,
  input: GenerateTextInput,
): Promise<GenerateTextResult> {
  const model = input.purpose === "sentiment" ? config.sentimentModel : config.summaryModel;
  const output = (await env.AI.run(
    model as never,
    {
      messages: [
        { role: "system", content: input.system },
        { role: "user", content: input.prompt },
      ],
      max_tokens: input.maxTokens ?? 512,
    } as never,
  )) as unknown;

  const text =
    typeof output === "object" && output !== null && "response" in output
      ? String(output.response).trim()
      : typeof output === "string"
        ? output.trim()
        : null;
  return { text, skipped: false };
}

export async function generateText(
  env: Env,
  config: AiConfig,
  input: GenerateTextInput,
): Promise<GenerateTextResult> {
  try {
    switch (config.textProvider) {
      case "anthropic":
        return await generateWithAnthropic(env, config, input);
      case "openai":
        return await generateWithOpenAi(env, config, input);
      case "workers-ai":
        return await generateWithWorkersAi(env, config, input);
      case "disabled":
        return { text: null, skipped: true, reason: "AI_TEXT_PROVIDER disabled" };
    }
  } catch (error: unknown) {
    return { text: null, skipped: true, reason: getErrorMessage(error) };
  }
}
