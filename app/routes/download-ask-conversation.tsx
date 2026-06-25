import { env } from "cloudflare:workers";
import { requireSession } from "~/lib/auth/session";
import {
  getAskConversationExport,
  renderAskConversationMarkdown,
} from "~/lib/db/ask-conversations";
import { getDb } from "~/lib/db/client";
import type { Route } from "./+types/download-ask-conversation";

function safeFilename(title: string): string {
  const compact = title
    .trim()
    .replace(/[\\/:*?"<>|]+/g, "-")
    .replace(/\s+/g, "-")
    .slice(0, 80);
  return compact || "ask-conversation";
}

export async function loader({ request }: Route.LoaderArgs) {
  await requireSession(request, env);
  const url = new URL(request.url);
  const id = url.searchParams.get("conversation")?.trim() ?? "";
  if (!id) {
    throw new Response("Missing conversation", { status: 400 });
  }

  const exported = await getAskConversationExport(getDb(env.DB), id);
  if (!exported) {
    throw new Response("Not found", { status: 404 });
  }

  const markdown = renderAskConversationMarkdown(exported);
  const filename = `${safeFilename(exported.conversation.title)}.md`;
  return new Response(markdown, {
    headers: {
      "content-type": "text/markdown; charset=utf-8",
      "content-disposition": `attachment; filename="${filename}"`,
    },
  });
}
