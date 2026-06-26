import { env } from "cloudflare:workers";
import { AskTab } from "~/components/memory/AskTab";
import { type AskActionData, runAskAction } from "~/lib/ai/ask-action";
import { requireSession } from "~/lib/auth/session";
import {
  deleteAskConversation,
  getAskConversation,
  listAskConversations,
  renameAskConversation,
  saveAskMessageAsEntry,
  selectAskBranch,
  toggleAskConversationArchived,
  toggleAskConversationPinned,
} from "~/lib/db/ask-conversations";
import { getDb } from "~/lib/db/client";
import { searchEntriesByKeyword } from "~/lib/search/fts";
import type { Route } from "./+types/ask";

export function meta(_: Route.MetaArgs) {
  return [{ title: "问答 · Sillage" }];
}

export async function loader({ request }: Route.LoaderArgs) {
  await requireSession(request, env);
  const db = getDb(env.DB);
  const url = new URL(request.url);
  const query = url.searchParams.get("q")?.trim() ?? "";
  const conversationId = url.searchParams.get("conversation")?.trim() ?? "";
  const includeArchived = url.searchParams.get("archived") === "1";
  const conversationQuery = url.searchParams.get("cq")?.trim() ?? "";
  const [results, conversations, currentConversation] = await Promise.all([
    query ? searchEntriesByKeyword(db, query) : Promise.resolve([]),
    listAskConversations(db, { includeArchived, query: conversationQuery }),
    conversationId ? getAskConversation(db, conversationId) : Promise.resolve(null),
  ]);

  return {
    query,
    conversationQuery,
    includeArchived,
    conversations,
    currentConversation,
    results,
  };
}

export async function action({ request }: Route.ActionArgs): Promise<AskActionData> {
  await requireSession(request, env);
  const db = getDb(env.DB);
  const form = await request.formData();
  const intent = String(form.get("intent") ?? "");

  if (intent === "ask") {
    return runAskAction(db, form);
  }
  if (intent === "renameAskConversation") {
    await renameAskConversation(
      db,
      String(form.get("conversationId") ?? ""),
      String(form.get("title") ?? ""),
    );
    return { intent: "ask", ok: true, message: "已重命名" };
  }
  if (intent === "toggleAskPinned") {
    await toggleAskConversationPinned(db, String(form.get("conversationId") ?? ""));
    return { intent: "ask", ok: true, message: "已更新置顶状态" };
  }
  if (intent === "toggleAskArchived") {
    await toggleAskConversationArchived(db, String(form.get("conversationId") ?? ""));
    return { intent: "ask", ok: true, message: "已更新归档状态" };
  }
  if (intent === "deleteAskConversation") {
    await deleteAskConversation(db, String(form.get("conversationId") ?? ""));
    return { intent: "ask", ok: true, message: "已删除会话" };
  }
  if (intent === "selectAskBranch") {
    await selectAskBranch(
      db,
      String(form.get("conversationId") ?? ""),
      String(form.get("messageId") ?? ""),
    );
    return { intent: "ask", ok: true, message: "已切换分支" };
  }
  if (intent === "saveAskEntry") {
    const result = await saveAskMessageAsEntry(
      db,
      String(form.get("conversationId") ?? ""),
      String(form.get("messageId") ?? ""),
    );
    return { intent: "ask", ok: result.ok, message: result.message };
  }
  return { intent: "ask", ok: false, message: "未知操作" };
}

export default function Ask({ loaderData }: Route.ComponentProps) {
  return (
    <section className="h-[calc(100svh-3.5rem)] bg-gray-50 dark:bg-gray-950 lg:h-screen">
      <AskTab
        query={loaderData.query}
        results={loaderData.results}
        currentConversation={loaderData.currentConversation}
      />
    </section>
  );
}
