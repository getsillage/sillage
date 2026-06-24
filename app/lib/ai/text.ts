import type { AiConfig } from "./config";
import {
  anthropicEndpointCandidates,
  fetchWithEndpointFallback,
  openAiEndpointCandidates,
  responseErrorDetail,
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
  truncated?: boolean;
}

interface AnthropicTextBlock {
  type: "text";
  text: string;
}

interface AnthropicMessageResponse {
  content: Array<AnthropicTextBlock | { type: string }>;
  stop_reason?: string | null;
}

interface OpenAiTextPart {
  type?: string;
  text?: string | { value?: string };
  content?: string;
  value?: string;
}

interface OpenAiChatResponse {
  choices?: Array<{
    message?: { content?: string | OpenAiTextPart[] | null; refusal?: string | null };
    text?: string | null;
    finish_reason?: string | null;
  }>;
  output_text?: string;
  output?: Array<{
    type?: string;
    content?: Array<string | OpenAiTextPart> | string | null;
    text?: string | OpenAiTextPart | null;
  }>;
  status?: string;
  incomplete_details?: { reason?: string | null } | null;
}

type AiProviderName = "Anthropic" | "OpenAI";

const DEFAULT_MAX_TOKENS = 2048;

function getErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "AI provider request failed";
}

function logAiProviderWarning(
  message: string,
  detail: {
    provider: AiProviderName;
    model: string;
    baseUrl: string;
    status?: number;
    reason?: string | null;
    stopReason?: string | null;
  },
) {
  console.warn(`[ai] ${message}`, detail);
}

function logAiProviderError(
  message: string,
  detail: {
    provider: AiProviderName;
    model: string;
    baseUrl: string;
    reason: string;
  },
) {
  console.error(`[ai] ${message}`, detail);
}

function textFromAnthropic(response: AnthropicMessageResponse): string | null {
  if (response.stop_reason === "refusal") {
    return null;
  }
  const text = response.content
    .filter((item): item is AnthropicTextBlock => item.type === "text")
    .map((block) => block.text)
    .join("")
    .trim();
  return text || null;
}

function textFromOpenAiPart(part: string | OpenAiTextPart | null | undefined): string {
  if (typeof part === "string") {
    return part;
  }
  if (!part) {
    return "";
  }
  if (typeof part.text === "string") {
    return part.text;
  }
  if (typeof part.text?.value === "string") {
    return part.text.value;
  }
  return part.content ?? part.value ?? "";
}

function textFromOpenAiContent(content: string | OpenAiTextPart[] | null | undefined): string {
  if (typeof content === "string") {
    return content;
  }
  if (!Array.isArray(content)) {
    return "";
  }
  return content.map(textFromOpenAiPart).join("");
}

function textFromOpenAiOutput(response: OpenAiChatResponse): string {
  if (!Array.isArray(response.output)) {
    return "";
  }
  return response.output
    .map((item) => {
      const contentText = Array.isArray(item.content)
        ? item.content.map(textFromOpenAiPart).join("")
        : typeof item.content === "string"
          ? item.content
          : "";
      return `${contentText}${textFromOpenAiPart(item.text)}`;
    })
    .join("");
}

function textFromOpenAi(response: OpenAiChatResponse): string | null {
  const choiceText =
    response.choices
      ?.map((choice) => `${textFromOpenAiContent(choice.message?.content)}${choice.text ?? ""}`)
      .join("")
      .trim() ?? "";
  if (choiceText) {
    return choiceText;
  }
  const outputText = response.output_text?.trim() ?? "";
  if (outputText) {
    return outputText;
  }
  return textFromOpenAiOutput(response).trim() || null;
}

function isOpenAiTruncated(response: OpenAiChatResponse): boolean {
  return (
    response.choices?.some(
      (choice) => choice.finish_reason === "length" || choice.finish_reason === "max_tokens",
    ) === true ||
    response.status === "incomplete" ||
    response.incomplete_details?.reason === "max_output_tokens"
  );
}

function openAiFinishReason(response: OpenAiChatResponse): string | null {
  return (
    response.choices?.find((choice) => choice.finish_reason)?.finish_reason ??
    response.incomplete_details?.reason ??
    response.status ??
    null
  );
}

function noTextReason(provider: AiProviderName, reason: string | null | undefined): string {
  return reason ? `${provider} 未返回可用文本（${reason}）` : `${provider} 未返回可用文本`;
}

function truncatedReason(provider: AiProviderName, reason: string | null | undefined): string {
  return reason ? `${provider} 输出达到长度上限（${reason}）` : `${provider} 输出达到长度上限`;
}

function supportsReasoningEffort(model: string): boolean {
  const normalized = model.trim().toLowerCase();
  return /^(gpt-5|o[1-9])/.test(normalized);
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
        max_tokens: input.maxTokens ?? DEFAULT_MAX_TOKENS,
        system: input.system,
        messages: [{ role: "user", content: input.prompt }],
      }),
    },
  );

  if (!response.ok) {
    const detail = await responseErrorDetail(response);
    const reason = `Anthropic API returned ${response.status}${detail}`;
    logAiProviderWarning("Anthropic request failed", {
      provider: "Anthropic",
      model: config.anthropicModel,
      baseUrl: config.anthropicBaseUrl,
      status: response.status,
      reason,
    });
    return { text: null, skipped: true, reason };
  }

  const data = (await response.json()) as AnthropicMessageResponse;
  const text = textFromAnthropic(data);
  const truncated = data.stop_reason === "max_tokens";
  if (!text || truncated) {
    logAiProviderWarning(
      !text ? "Anthropic returned no usable text" : "Anthropic response truncated",
      {
        provider: "Anthropic",
        model: config.anthropicModel,
        baseUrl: config.anthropicBaseUrl,
        stopReason: data.stop_reason,
      },
    );
  }
  return {
    text,
    skipped: false,
    reason: truncated
      ? truncatedReason("Anthropic", data.stop_reason)
      : !text
        ? noTextReason("Anthropic", data.stop_reason)
        : undefined,
    truncated: truncated || undefined,
  };
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
        max_completion_tokens: input.maxTokens ?? DEFAULT_MAX_TOKENS,
        ...(supportsReasoningEffort(config.openaiModel) ? { reasoning_effort: "low" } : {}),
      }),
    },
  );

  if (!response.ok) {
    const detail = await responseErrorDetail(response);
    const reason = `OpenAI API returned ${response.status}${detail}`;
    logAiProviderWarning("OpenAI request failed", {
      provider: "OpenAI",
      model: config.openaiModel,
      baseUrl: config.openaiBaseUrl,
      status: response.status,
      reason,
    });
    return { text: null, skipped: true, reason };
  }

  const data = (await response.json()) as OpenAiChatResponse;
  const text = textFromOpenAi(data);
  const truncated = isOpenAiTruncated(data);
  if (!text || truncated) {
    logAiProviderWarning(!text ? "OpenAI returned no usable text" : "OpenAI response truncated", {
      provider: "OpenAI",
      model: config.openaiModel,
      baseUrl: config.openaiBaseUrl,
      stopReason: openAiFinishReason(data),
    });
  }
  return {
    text,
    skipped: false,
    reason: truncated
      ? truncatedReason("OpenAI", openAiFinishReason(data))
      : !text
        ? noTextReason("OpenAI", openAiFinishReason(data))
        : undefined,
    truncated: truncated || undefined,
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
    const reason = getErrorMessage(error);
    if (config.textProvider === "anthropic") {
      logAiProviderError("Anthropic request errored", {
        provider: "Anthropic",
        model: config.anthropicModel,
        baseUrl: config.anthropicBaseUrl,
        reason,
      });
    } else if (config.textProvider === "openai") {
      logAiProviderError("OpenAI request errored", {
        provider: "OpenAI",
        model: config.openaiModel,
        baseUrl: config.openaiBaseUrl,
        reason,
      });
    }
    return { text: null, skipped: true, reason };
  }
}
