import { asc, eq, isNull } from "drizzle-orm";
import { renderAskConversationMarkdown } from "~/lib/db/ask-conversations";
import { getDb } from "~/lib/db/client";
import { askConversations, askMessages, attachments, entries, entryAi } from "~/lib/db/schema";

export interface SillageBackupResult {
  jsonKey: string;
  markdownKey: string;
  entryCount: number;
}

interface BackupEntry {
  id: string;
  entryDate: string;
  body: string;
  summary: string | null;
  sentiment: string | null;
  createdAt: string;
  updatedAt: string;
}

interface SillageBackupPayload {
  version: 1;
  exportedAt: string;
  entries: BackupEntry[];
  attachments: Array<{
    id: string;
    entryId: string | null;
    r2Key: string;
    filename: string;
    contentType: string;
    size: number;
    createdAt: string;
  }>;
  askConversations: Array<{
    id: string;
    title: string;
    sourceTypes: unknown[];
    headMessageId: string | null;
    pinnedAt: string | null;
    archivedAt: string | null;
    createdAt: string;
    updatedAt: string;
    messages: Array<{
      id: string;
      parentId: string | null;
      forkOfId: string | null;
      role: string;
      content: string;
      status: string;
      sources: unknown[];
      sourceTypes: unknown[];
      model: string | null;
      durationMs: number | null;
      createdAt: string;
      updatedAt: string;
    }>;
  }>;
}

function iso(value: Date): string;
function iso(value: Date | null): string | null;
function iso(value: Date | null): string | null {
  return value ? value.toISOString() : null;
}

function parseJsonArray(raw: string | null): unknown[] {
  if (!raw) {
    return [];
  }
  try {
    const parsed: unknown = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function backupTimestamp(date: Date): string {
  return date.toISOString().replaceAll(":", "-").replaceAll(".", "-");
}

function renderMarkdown(payload: SillageBackupPayload): string {
  const sections = payload.entries.map((entry) => {
    const summaryLine = entry.summary ? `\n摘要：${entry.summary}` : "";
    const sentimentLine = entry.sentiment ? `\n情绪：${entry.sentiment}` : "";
    return `## ${entry.entryDate}${summaryLine}${sentimentLine}\n\n${entry.body}`;
  });
  const askSections =
    payload.askConversations.length > 0
      ? [
          "",
          "# 问答会话",
          "",
          ...payload.askConversations.map((conversation) =>
            renderAskConversationMarkdown({
              conversation: {
                ...conversation,
                sourceTypes: [],
                pinnedAt: conversation.pinnedAt ? new Date(conversation.pinnedAt) : null,
                archivedAt: conversation.archivedAt ? new Date(conversation.archivedAt) : null,
                createdAt: new Date(conversation.createdAt),
                updatedAt: new Date(conversation.updatedAt),
                messages: [],
              },
              messages: conversation.messages.map((message) => ({
                ...message,
                conversationId: conversation.id,
                role: message.role === "assistant" ? "assistant" : "user",
                status:
                  message.status === "running" ||
                  message.status === "error" ||
                  message.status === "interrupted"
                    ? message.status
                    : "completed",
                sources: [],
                sourceTypes: [],
                branch: null,
                createdAt: new Date(message.createdAt),
                updatedAt: new Date(message.updatedAt),
              })),
            }),
          ),
        ]
      : [];
  return [
    `# Sillage 备份`,
    ``,
    `导出时间：${payload.exportedAt}`,
    ``,
    ...sections,
    ...askSections,
  ].join("\n");
}

async function buildBackupPayload(env: Env, exportedAt: Date): Promise<SillageBackupPayload> {
  const db = getDb(env.DB);
  const [entryRows, attachmentRows, conversationRows, messageRows] = await Promise.all([
    db
      .select()
      .from(entries)
      .leftJoin(entryAi, eq(entryAi.entryId, entries.id))
      .where(isNull(entries.deletedAt))
      .orderBy(asc(entries.entryDate), asc(entries.createdAt)),
    db
      .select()
      .from(attachments)
      .where(isNull(attachments.deletedAt))
      .orderBy(asc(attachments.createdAt)),
    db.select().from(askConversations).orderBy(asc(askConversations.createdAt)),
    db
      .select()
      .from(askMessages)
      .orderBy(asc(askMessages.conversationId), asc(askMessages.createdAt)),
  ]);

  return {
    version: 1,
    exportedAt: iso(exportedAt),
    entries: entryRows.map(({ entries: entry, entry_ai: ai }) => ({
      id: entry.id,
      entryDate: entry.entryDate,
      body: entry.body,
      summary: ai?.summary ?? null,
      sentiment: ai?.sentiment ?? null,
      createdAt: iso(entry.createdAt),
      updatedAt: iso(entry.updatedAt),
    })),
    attachments: attachmentRows.map((attachment) => ({
      id: attachment.id,
      entryId: attachment.entryId,
      r2Key: attachment.r2Key,
      filename: attachment.filename,
      contentType: attachment.contentType,
      size: attachment.size,
      createdAt: iso(attachment.createdAt),
    })),
    askConversations: conversationRows.map((conversation) => ({
      id: conversation.id,
      title: conversation.title,
      sourceTypes: parseJsonArray(conversation.sourceTypes),
      headMessageId: conversation.headMessageId,
      pinnedAt: iso(conversation.pinnedAt),
      archivedAt: iso(conversation.archivedAt),
      createdAt: iso(conversation.createdAt),
      updatedAt: iso(conversation.updatedAt),
      messages: messageRows
        .filter((message) => message.conversationId === conversation.id)
        .map((message) => ({
          id: message.id,
          parentId: message.parentId,
          forkOfId: message.forkOfId,
          role: message.role,
          content: message.content,
          status: message.status,
          sources: parseJsonArray(message.sources),
          sourceTypes: parseJsonArray(message.sourceTypes),
          model: message.model,
          durationMs: message.durationMs,
          createdAt: iso(message.createdAt),
          updatedAt: iso(message.updatedAt),
        })),
    })),
  };
}

export async function exportSillageBackup(
  env: Env,
  exportedAt = new Date(),
): Promise<SillageBackupResult> {
  const payload = await buildBackupPayload(env, exportedAt);
  const date = exportedAt.toISOString().slice(0, 10);
  const timestamp = backupTimestamp(exportedAt);
  const baseKey = `backups/${date}/sillage-${timestamp}`;
  const jsonKey = `${baseKey}.json`;
  const markdownKey = `${baseKey}.md`;

  await Promise.all([
    env.BLOBS.put(jsonKey, JSON.stringify(payload, null, 2), {
      httpMetadata: { contentType: "application/json; charset=utf-8" },
      customMetadata: { type: "sillage-backup", format: "json", version: "1" },
    }),
    env.BLOBS.put(markdownKey, renderMarkdown(payload), {
      httpMetadata: { contentType: "text/markdown; charset=utf-8" },
      customMetadata: { type: "sillage-backup", format: "markdown", version: "1" },
    }),
  ]);

  return { jsonKey, markdownKey, entryCount: payload.entries.length };
}

/**
 * Cron entrypoint: runs the backup and logs contextual failures so a failed
 * scheduled invocation is observable in Workers Logs instead of silently lost.
 * Rethrows so the platform marks the cron run as failed.
 */
export async function runScheduledBackup(env: Env): Promise<void> {
  try {
    await exportSillageBackup(env);
  } catch (error) {
    console.error(
      JSON.stringify({
        event: "sillage-backup",
        status: "error",
        message: error instanceof Error ? error.message : String(error),
      }),
    );
    throw error;
  }
}
