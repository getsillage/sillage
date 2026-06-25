import { loadAiConfig } from "./config";
import { activeModel } from "./pipeline";
import { generateText, streamText } from "./text";

export interface AskTurn {
  question: string;
  answer: string;
}

export interface AskRequest {
  question: string;
  evidence: string;
  history?: AskTurn[];
  signal?: AbortSignal;
}

export interface AskStreamRequest extends AskRequest {
  onDelta: (text: string) => void | Promise<void>;
}

export interface AskResult {
  ok: boolean;
  answer: string;
  model: string | null;
  durationMs?: number;
  skippedReason?: string;
}

const SYSTEM_PROMPT = [
  "你是 Sillage 的记忆对话助手。你的能力不只是检索记录，也包括基于用户自己的手记做总结、复盘、模式识别和温和建议。",
  "回答必须以【记忆证据】和【对话历史】为依据：可以从证据中归纳趋势、推导下一步建议，或提出澄清问题；不要编造不存在的事实，也不要把推测说成确定结论。",
  "当用户请求建议、调整、规划或指导时，不要要求记忆里必须已经写过明确的「建议」；请从记录里的状态、反复出现的模式、已经有效或无效的做法中推导 2-4 条具体建议，并简短说明依据。",
  "当证据不足以回答事实问题时，直接说明缺口；如果是建议类问题，先说明依据有限，再给出低风险、可选择的建议。",
  "用中文，语气像可信的个人记忆伙伴：具体、克制、有帮助。不要做医学或心理诊断；正文里不必重复链接。",
].join("\n");

const MAX_HISTORY_TURNS = 4;
const ASK_MAX_TOKENS = 3200;
const ASK_RETRY_MAX_TOKENS = 4800;

function historyText(history: AskTurn[]): string {
  return history
    .slice(-MAX_HISTORY_TURNS)
    .map((turn) => `问：${turn.question}\n答：${turn.answer}`)
    .join("\n\n");
}

function buildAskPrompt(request: AskRequest): string {
  const history = request.history ? historyText(request.history) : "";
  return [
    history ? `【对话历史】\n${history}` : "",
    `【记忆证据】\n${request.evidence.trim() || "（所选来源里没有找到相关证据）"}`,
    `【问题】\n${request.question}`,
  ]
    .filter(Boolean)
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
  const prompt = buildAskPrompt(request);

  let result = await generateText(config, {
    system: SYSTEM_PROMPT,
    prompt,
    maxTokens: ASK_MAX_TOKENS,
    signal: request.signal,
  });

  if (!result.skipped && result.truncated) {
    result = await generateText(config, {
      system: SYSTEM_PROMPT,
      prompt: `${prompt}\n\n【回答要求】\n请重新生成完整答案，直接给出完整的结论和条目，不要只写标题或开头。`,
      maxTokens: ASK_RETRY_MAX_TOKENS,
      signal: request.signal,
    });
  }

  if (result.skipped) {
    return { ok: false, answer: "", model, skippedReason: result.reason ?? "AI 已跳过" };
  }
  if (!result.text) {
    return { ok: false, answer: "", model, skippedReason: result.reason ?? "AI 未返回内容" };
  }
  if (result.truncated) {
    return {
      ok: false,
      answer: "",
      model,
      skippedReason: `${result.reason ?? "AI 回答过长被截断了"}，请缩小问题范围后重试`,
    };
  }
  return { ok: true, answer: result.text, model };
}

export async function streamQuestion(env: Env, request: AskStreamRequest): Promise<AskResult> {
  const config = await loadAiConfig(env);
  const model = activeModel(config);
  const result = await streamText(config, {
    system: SYSTEM_PROMPT,
    prompt: buildAskPrompt(request),
    maxTokens: ASK_MAX_TOKENS,
    signal: request.signal,
    onDelta: request.onDelta,
  });

  if (result.skipped) {
    return {
      ok: false,
      answer: "",
      model,
      durationMs: result.durationMs,
      skippedReason: result.reason ?? "AI 已跳过",
    };
  }
  if (!result.text) {
    return {
      ok: false,
      answer: "",
      model,
      durationMs: result.durationMs,
      skippedReason: result.reason ?? "AI 未返回内容",
    };
  }
  if (result.truncated) {
    return {
      ok: false,
      answer: "",
      model,
      durationMs: result.durationMs,
      skippedReason: `${result.reason ?? "AI 回答过长被截断了"}，请缩小问题范围后重试`,
    };
  }
  return { ok: true, answer: result.text, model, durationMs: result.durationMs };
}
