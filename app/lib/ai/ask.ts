import type { EntryWithTags } from "~/lib/db/entries";
import { loadAiConfig } from "./config";
import { activeModel } from "./pipeline";
import { buildEntriesDigest } from "./summarize";
import { generateText } from "./text";

export interface AskTurn {
  question: string;
  answer: string;
}

export interface AskRequest {
  question: string;
  entries: EntryWithTags[];
  history?: AskTurn[];
}

export interface AskResult {
  ok: boolean;
  answer: string;
  model: string | null;
  skippedReason?: string;
}

const SYSTEM_PROMPT =
  "你是 Sillage 的记忆助手。请只依据下面提供的记录回答用户的问题，用中文，简洁、具体、可追溯，可引用记录中的日期。如果提供的记录里没有相关信息，就如实说「记录里没有相关内容」，不要编造或臆测。";

const MAX_HISTORY_TURNS = 4;

function historyText(history: AskTurn[]): string {
  return history
    .slice(-MAX_HISTORY_TURNS)
    .map((turn) => `问：${turn.question}\n答：${turn.answer}`)
    .join("\n\n");
}

/**
 * Answers a free-form question grounded in the user's own records. Mirrors the
 * pipeline contract: provider/config problems come back as `skippedReason`
 * rather than throwing, so the route can show a friendly message.
 */
export async function answerQuestion(env: Env, request: AskRequest): Promise<AskResult> {
  const config = await loadAiConfig(env);
  const model = activeModel(config);

  const context = request.entries.length > 0 ? buildEntriesDigest(request.entries) : "";
  const history = request.history ? historyText(request.history) : "";
  const prompt = [
    history ? `【对话历史】\n${history}` : "",
    `【相关记录】\n${context || "（没有检索到相关记录）"}`,
    `【问题】\n${request.question}`,
  ]
    .filter(Boolean)
    .join("\n\n");

  const result = await generateText(config, {
    system: SYSTEM_PROMPT,
    prompt,
    maxTokens: 700,
  });

  if (result.skipped) {
    return { ok: false, answer: "", model, skippedReason: result.reason ?? "AI 已跳过" };
  }
  if (!result.text) {
    return { ok: false, answer: "", model, skippedReason: "AI 未返回内容" };
  }
  return { ok: true, answer: result.text, model };
}
