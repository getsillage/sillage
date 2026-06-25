import { env } from "cloudflare:workers";
import { runEntryInsightAction } from "~/lib/ai/entry-insights";
import { isEntryInsightIntent } from "~/lib/ai/entry-insights.shared";
import type { AiGenerationResult } from "~/lib/ai/generation-result";
import { requireSession } from "~/lib/auth/session";
import { getDb } from "~/lib/db/client";
import type { Route } from "./+types/api.entry-insight";

/**
 * JSON endpoint backing the live, cancellable single-entry insight generation.
 * Returns a plain `AiGenerationResult` (not React Router's data encoding) so the
 * client can drive it with a raw `fetch` + `AbortController`.
 */
export async function action({ request }: Route.ActionArgs) {
  await requireSession(request, env);
  const form = await request.formData();
  const entryId = String(form.get("entryId") ?? "").trim();
  const intent = String(form.get("intent") ?? "");

  if (!entryId || !isEntryInsightIntent(intent)) {
    const result: AiGenerationResult = { ok: false, message: "无效请求", category: "unknown" };
    return Response.json(result, { status: 400 });
  }

  const db = getDb(env.DB);
  const result = await runEntryInsightAction(env, db, entryId, intent);
  return Response.json(result);
}
