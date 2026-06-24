import type { EntryWithTags } from "~/lib/db/entries";
import { parseTextList } from "~/lib/product/entry-fields";
import type { SummaryPeriodType, SummaryScope, SummaryStyle } from "~/lib/product/summary-fields";
import { loadAiConfig } from "./config";
import { activeModel } from "./pipeline";
import { generateText } from "./text";

/** Caps that keep the aggregated prompt within a sane token budget. */
const MAX_ENTRIES = 60;
const MAX_BODY_CHARS = 400;

const SYSTEM_PROMPTS: Record<SummaryStyle, string> = {
  brief:
    "你是 Sillage 的回顾层。请用中文为这段记录写一段克制、具体、可追溯的简短回顾，抓住真正重要的几件事与情绪基调，忠于记录、不诊断、不替用户下结论。第一行用「# 」开头给一个不超过 16 字的标题，随后用 2 至 4 句话写正文。",
  structured:
    "你是 Sillage 的回顾层。请用中文写一份结构化回顾，使用 Markdown 小标题，按需包含：## 主要的事、## 情绪曲线、## 出现的人、## 未尽事宜、## 温柔的建议（某节无内容则省略该节）。克制、具体、可追溯，忠于记录、不诊断、不编造。第一行用「# 」开头给一个不超过 16 字的标题。",
  narrative:
    "你是 Sillage 的回顾层。请用中文写一篇有温度的叙述式回顾，以第二人称「你」称呼记录者，把这些片段织成一篇连贯、有情绪、有细节的文章。忠于记录、不虚构事实、不说教。第一行用「# 」开头给一个不超过 16 字的标题，随后是文章正文。",
};

const MAX_TOKENS: Record<SummaryStyle, number> = {
  brief: 400,
  structured: 800,
  narrative: 1200,
};

export interface SummaryRequest {
  scope: SummaryScope;
  periodType: SummaryPeriodType | null;
  startDate: string;
  endDate: string;
  style: SummaryStyle;
  entries: EntryWithTags[];
  /** Human-readable description of the topic filter, for the prompt + fallback title. */
  topicLabel?: string;
}

export interface SummaryDraft {
  ok: boolean;
  title: string;
  content: string;
  model: string | null;
  skippedReason?: string;
}

function truncateBody(body: string): string {
  const trimmed = body.trim();
  return trimmed.length > MAX_BODY_CHARS ? `${trimmed.slice(0, MAX_BODY_CHARS)}…` : trimmed;
}

function entryBlock(entry: EntryWithTags): string {
  const people = parseTextList(entry.people);
  const relationships = parseTextList(entry.relationships);
  return [
    entry.title ? `【${entry.entryDate}】${entry.title}` : `【${entry.entryDate}】`,
    entry.moodText ? `心情：${entry.moodText}` : "",
    people.length > 0 ? `人物：${people.join("、")}` : "",
    relationships.length > 0 ? `关系：${relationships.join("、")}` : "",
    truncateBody(entry.body),
    entry.tags.length > 0 ? entry.tags.map((tag) => `#${tag}`).join(" ") : "",
  ]
    .filter(Boolean)
    .join("\n");
}

/** Serializes many entries into a compact, bounded prompt input. */
export function buildEntriesDigest(entries: EntryWithTags[]): string {
  const slice = entries.slice(0, MAX_ENTRIES);
  const blocks = slice.map(entryBlock).join("\n\n---\n\n");
  const omitted = entries.length - slice.length;
  return omitted > 0 ? `${blocks}\n\n（另有 ${omitted} 条更早的记录未纳入）` : blocks;
}

function promptHeader(request: SummaryRequest): string {
  const count = request.entries.length;
  if (request.scope === "topic") {
    const label = request.topicLabel?.trim() || "主题";
    return `主题回顾：${label}，共 ${count} 条记录，时间跨度 ${request.startDate} 至 ${request.endDate}。`;
  }
  return `回顾范围：${request.startDate} 至 ${request.endDate}，共 ${count} 条记录。`;
}

function fallbackTitle(request: SummaryRequest): string {
  if (request.scope === "topic") {
    return `${request.topicLabel?.trim() || "主题"}回顾`;
  }
  return request.startDate === request.endDate
    ? `${request.startDate} 回顾`
    : `${request.startDate}–${request.endDate} 回顾`;
}

/** Splits the model output into a title (leading `# ` line) and the body below it. */
function splitTitle(text: string, fallback: string): { title: string; content: string } {
  const trimmed = text.trim();
  const newlineIndex = trimmed.indexOf("\n");
  const firstLine = newlineIndex === -1 ? trimmed : trimmed.slice(0, newlineIndex);
  if (firstLine.startsWith("# ")) {
    const title = firstLine.slice(2).trim().slice(0, 40) || fallback;
    const content = newlineIndex === -1 ? "" : trimmed.slice(newlineIndex + 1).trim();
    return { title, content: content || trimmed };
  }
  return { title: fallback, content: trimmed };
}

/**
 * Generates a multi-entry review with the configured AI provider. Mirrors the
 * single-entry pipeline's contract: provider/config problems are returned as a
 * `skippedReason` rather than thrown, so the route can show a friendly message.
 */
export async function generateSummary(env: Env, request: SummaryRequest): Promise<SummaryDraft> {
  const config = await loadAiConfig(env);
  const model = activeModel(config);

  if (request.entries.length === 0) {
    return { ok: false, title: "", content: "", model, skippedReason: "所选范围内没有记录" };
  }

  const result = await generateText(config, {
    system: SYSTEM_PROMPTS[request.style],
    prompt: `${promptHeader(request)}\n\n${buildEntriesDigest(request.entries)}`,
    maxTokens: MAX_TOKENS[request.style],
  });

  if (result.skipped) {
    return {
      ok: false,
      title: "",
      content: "",
      model,
      skippedReason: result.reason ?? "AI 已跳过",
    };
  }
  if (!result.text) {
    return { ok: false, title: "", content: "", model, skippedReason: "AI 未返回内容" };
  }

  const { title, content } = splitTitle(result.text, fallbackTitle(request));
  return { ok: true, title, content, model };
}
