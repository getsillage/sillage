import { env } from "cloudflare:workers";
import { type AskTurn, answerQuestion } from "~/lib/ai/ask";
import { type AskCitation, askSourceTypesFromForm, collectAskContext } from "~/lib/ai/ask-context";
import type { Db } from "~/lib/db/client";

export interface AskActionData {
  intent: "ask";
  ok: boolean;
  message: string;
  answer?: string;
  sources?: AskCitation[];
}

function parseAskHistory(raw: string): AskTurn[] {
  try {
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed
      .filter(
        (turn): turn is AskTurn =>
          typeof turn === "object" &&
          turn !== null &&
          typeof (turn as AskTurn).question === "string" &&
          typeof (turn as AskTurn).answer === "string",
      )
      .slice(-4);
  } catch {
    return [];
  }
}

function friendlyReason(reason?: string): string {
  if (!reason) {
    return "未能回答";
  }
  if (reason.includes("disabled")) {
    return "AI 未启用，请先在「设置」中配置并启用 AI 提供商";
  }
  if (reason.includes("key not configured")) {
    return "尚未配置 API Key，请到「设置」补全";
  }
  return `未能回答：${reason}`;
}

/** Runs the "探寻" intent: builds evidence from the user's own records and answers. */
export async function runAskAction(db: Db, form: FormData): Promise<AskActionData> {
  const question = String(form.get("question") ?? "").trim();
  if (!question) {
    return { intent: "ask", ok: false, message: "请输入问题" };
  }
  if (question.length > 500) {
    return { intent: "ask", ok: false, message: "问题过长（最多 500 字）" };
  }

  const sourceTypes = askSourceTypesFromForm(form);
  const history = parseAskHistory(String(form.get("history") ?? ""));
  const context = await collectAskContext(db, question, sourceTypes);
  const result = await answerQuestion(env, { question, evidence: context.evidence, history });
  if (!result.ok) {
    return { intent: "ask", ok: false, message: friendlyReason(result.skippedReason) };
  }

  return {
    intent: "ask",
    ok: true,
    message: "",
    answer: result.answer,
    sources: context.citations,
  };
}
