import { env } from "cloudflare:workers";
import { requireSession } from "~/lib/auth/session";
import { interruptAskAssistantMessage } from "~/lib/db/ask-conversations";
import { getDb } from "~/lib/db/client";
import type { Route } from "./+types/api.ask-stop";

export async function action({ request }: Route.ActionArgs) {
  await requireSession(request, env);
  const form = await request.formData();
  const messageId = String(form.get("messageId") ?? "").trim();
  const content = String(form.get("content") ?? "");
  if (!messageId) {
    return Response.json({ ok: false, message: "缺少消息 ID" }, { status: 400 });
  }

  await interruptAskAssistantMessage(getDb(env.DB), messageId, content);
  return Response.json({ ok: true, message: "已停止生成" });
}
