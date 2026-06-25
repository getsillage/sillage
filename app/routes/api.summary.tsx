import { env } from "cloudflare:workers";
import { loadAiConfig } from "~/lib/ai/config";
import type { AiGenerationResult } from "~/lib/ai/generation-result";
import { activeModel } from "~/lib/ai/pipeline";
import { requireSession } from "~/lib/auth/session";
import { getDb } from "~/lib/db/client";
import { isSummaryIntent, runSummaryAction } from "~/lib/product/summary-actions";
import type { Route } from "./+types/api.summary";

/**
 * JSON endpoint backing the live, cancellable "回顾 / 总结" generation. Wraps the
 * existing `runSummaryAction` (whose own contract and tests stay untouched) and
 * measures wall-clock here, attaching the configured model for the "用时 · 模型"
 * line. The friendly message from `runSummaryAction` already classifies failures.
 */
export async function action({ request }: Route.ActionArgs) {
  await requireSession(request, env);
  const form = await request.formData();
  const intent = String(form.get("intent") ?? "");

  if (!isSummaryIntent(intent)) {
    const result: AiGenerationResult = { ok: false, message: "无效请求", category: "unknown" };
    return Response.json(result, { status: 400 });
  }

  const db = getDb(env.DB);
  const startedAt = Date.now();
  const data = await runSummaryAction(db, form, intent);
  const durationMs = Date.now() - startedAt;

  if (!data.ok) {
    const result: AiGenerationResult = { ok: false, message: data.message };
    return Response.json(result);
  }

  const config = await loadAiConfig(env);
  const result: AiGenerationResult = {
    ok: true,
    message: data.message,
    model: activeModel(config),
    durationMs,
  };
  return Response.json(result);
}
