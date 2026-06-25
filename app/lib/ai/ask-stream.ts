import { env } from "cloudflare:workers";
import { streamQuestion } from "~/lib/ai/ask";
import { type AskCitation, askSourceTypesFromForm, collectAskContext } from "~/lib/ai/ask-context";
import {
  type AskMessageView,
  beginAskEdit,
  beginAskRegeneration,
  beginAskSend,
  completeAskAssistantMessage,
  failAskAssistantMessage,
  historyBeforeMessage,
} from "~/lib/db/ask-conversations";
import type { Db } from "~/lib/db/client";

export type AskStreamEvent =
  | {
      type: "created";
      conversationId: string;
      userMessage: AskMessageView;
      assistantMessage: AskMessageView;
    }
  | { type: "delta"; text: string }
  | { type: "sources"; sources: AskCitation[] }
  | { type: "done"; answer: string; model: string | null; durationMs: number }
  | { type: "error"; message: string; durationMs?: number };

function encodeEvent(event: AskStreamEvent): Uint8Array {
  return new TextEncoder().encode(`${JSON.stringify(event)}\n`);
}

function friendlyReason(reason?: string): string {
  if (!reason) {
    return "未能回答";
  }
  if (reason === "aborted") {
    return "已停止生成";
  }
  if (reason.includes("disabled")) {
    return "AI 未启用，请先在「设置」中配置并启用 AI 提供商";
  }
  if (reason.includes("key not configured")) {
    return "尚未配置 API Key，请到「设置」补全";
  }
  return `未能回答：${reason}`;
}

async function prepareRun(db: Db, form: FormData) {
  const mode = String(form.get("mode") ?? "send");
  const conversationId = String(form.get("conversationId") ?? "").trim() || null;
  const messageId = String(form.get("messageId") ?? "").trim();
  const question = String(form.get("question") ?? "").trim();
  const sourceTypes = askSourceTypesFromForm(form);

  if (mode === "regenerate") {
    if (!conversationId || !messageId) {
      return { ok: false as const, message: "缺少要重新生成的回答" };
    }
    const result = await beginAskRegeneration(db, conversationId, messageId);
    if (result.status === "missing") {
      return { ok: false as const, message: "未找到这条回答" };
    }
    if (result.status === "invalid") {
      return { ok: false as const, message: "这条消息不能重新生成" };
    }
    return { ok: true as const, ...result };
  }

  if (!question) {
    return { ok: false as const, message: "请输入问题" };
  }
  if (question.length > 500) {
    return { ok: false as const, message: "问题过长（最多 500 字）" };
  }

  if (mode === "edit") {
    if (!conversationId || !messageId) {
      return { ok: false as const, message: "缺少要编辑的问题" };
    }
    const result = await beginAskEdit(db, { conversationId, messageId, question, sourceTypes });
    if (result.status === "missing") {
      return { ok: false as const, message: "未找到这条问题" };
    }
    if (result.status === "invalid") {
      return { ok: false as const, message: "这条消息不能编辑" };
    }
    return { ok: true as const, ...result };
  }

  const result = await beginAskSend(db, {
    conversationId,
    question,
    sourceTypes,
  });
  return { ok: true as const, status: "created" as const, ...result };
}

export async function runAskStream(db: Db, form: FormData, signal: AbortSignal): Promise<Response> {
  const stream = new TransformStream<Uint8Array, Uint8Array>();
  const writer = stream.writable.getWriter();

  async function send(event: AskStreamEvent) {
    await writer.write(encodeEvent(event));
  }

  void (async () => {
    let assistantMessageId: string | null = null;
    try {
      const prepared = await prepareRun(db, form);
      if (!prepared.ok) {
        await send({ type: "error", message: prepared.message });
        return;
      }
      assistantMessageId = prepared.assistantMessage.id;
      await send({
        type: "created",
        conversationId: prepared.conversation.id,
        userMessage: prepared.userMessage,
        assistantMessage: prepared.assistantMessage,
      });

      const history = await historyBeforeMessage(
        db,
        prepared.conversation.id,
        prepared.userMessage.parentId,
      );
      const sourceTypes = prepared.assistantMessage.sourceTypes;
      const context = await collectAskContext(db, prepared.userMessage.content, sourceTypes);
      await send({ type: "sources", sources: context.citations });

      const result = await streamQuestion(env, {
        question: prepared.userMessage.content,
        evidence: context.evidence,
        history,
        signal,
        onDelta: async (text) => {
          await send({ type: "delta", text });
        },
      });

      if (!result.ok) {
        const message = friendlyReason(result.skippedReason);
        await failAskAssistantMessage(db, prepared.assistantMessage.id, message, result.durationMs);
        await send({ type: "error", message, durationMs: result.durationMs });
        return;
      }

      await completeAskAssistantMessage(db, {
        messageId: prepared.assistantMessage.id,
        content: result.answer,
        sources: context.citations,
        model: result.model,
        durationMs: result.durationMs ?? 0,
      });
      await send({
        type: "done",
        answer: result.answer,
        model: result.model,
        durationMs: result.durationMs ?? 0,
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : "请求失败，请稍后再试";
      if (assistantMessageId) {
        await failAskAssistantMessage(db, assistantMessageId, message);
      }
      await send({ type: "error", message });
    } finally {
      await writer.close();
    }
  })();

  return new Response(stream.readable, {
    headers: {
      "content-type": "application/x-ndjson; charset=utf-8",
      "cache-control": "no-store",
    },
  });
}
