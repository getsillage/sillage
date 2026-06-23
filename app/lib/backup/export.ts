import { asc } from "drizzle-orm";
import { getDb } from "~/lib/db/client";
import { attachments, entries, entryTags, tags } from "~/lib/db/schema";

export interface DiaryBackupResult {
  jsonKey: string;
  markdownKey: string;
  entryCount: number;
}

interface BackupTagLink {
  entryId: string;
  tagId: string;
}

interface BackupEntry {
  id: string;
  entryDate: string;
  title: string;
  body: string;
  mood: number | null;
  weather: string | null;
  isPinned: boolean;
  summary: string | null;
  sentiment: string | null;
  createdAt: string;
  updatedAt: string;
  tags: string[];
}

interface DiaryBackupPayload {
  version: 1;
  exportedAt: string;
  entries: BackupEntry[];
  tags: Array<{ id: string; name: string; createdAt: string }>;
  entryTags: BackupTagLink[];
  attachments: Array<{
    id: string;
    entryId: string | null;
    r2Key: string;
    filename: string;
    contentType: string;
    size: number;
    createdAt: string;
  }>;
}

function iso(value: Date): string {
  return value.toISOString();
}

function backupTimestamp(date: Date): string {
  return date.toISOString().replaceAll(":", "-").replaceAll(".", "-");
}

function tagsByEntry(
  links: BackupTagLink[],
  tagRows: DiaryBackupPayload["tags"],
): Map<string, string[]> {
  const namesById = new Map(tagRows.map((tag) => [tag.id, tag.name]));
  const result = new Map<string, string[]>();
  for (const link of links) {
    const name = namesById.get(link.tagId);
    if (!name) {
      continue;
    }
    const existing = result.get(link.entryId);
    result.set(link.entryId, existing ? [...existing, name] : [name]);
  }
  return result;
}

function renderMarkdown(payload: DiaryBackupPayload): string {
  const sections = payload.entries.map((entry) => {
    const title = entry.title || entry.entryDate;
    const tagLine =
      entry.tags.length > 0 ? `\n标签：${entry.tags.map((tag) => `#${tag}`).join(" ")}` : "";
    const moodLine = entry.mood ? `\n心情：${entry.mood}/5` : "";
    const weatherLine = entry.weather ? `\n天气：${entry.weather}` : "";
    const summaryLine = entry.summary ? `\n摘要：${entry.summary}` : "";
    const sentimentLine = entry.sentiment ? `\n情绪：${entry.sentiment}` : "";
    return `## ${entry.entryDate} ${title}${tagLine}${moodLine}${weatherLine}${summaryLine}${sentimentLine}\n\n${entry.body}`;
  });
  return [`# 日记备份`, ``, `导出时间：${payload.exportedAt}`, ``, ...sections].join("\n");
}

async function buildBackupPayload(env: Env, exportedAt: Date): Promise<DiaryBackupPayload> {
  const db = getDb(env.DB);
  const [entryRows, tagRowsRaw, linkRows, attachmentRows] = await Promise.all([
    db.select().from(entries).orderBy(asc(entries.entryDate), asc(entries.createdAt)),
    db.select().from(tags).orderBy(asc(tags.name)),
    db.select().from(entryTags),
    db.select().from(attachments).orderBy(asc(attachments.createdAt)),
  ]);

  const tagRows = tagRowsRaw.map((tag) => ({
    id: tag.id,
    name: tag.name,
    createdAt: iso(tag.createdAt),
  }));
  const tagMap = tagsByEntry(linkRows, tagRows);

  return {
    version: 1,
    exportedAt: iso(exportedAt),
    entries: entryRows.map((entry) => ({
      id: entry.id,
      entryDate: entry.entryDate,
      title: entry.title,
      body: entry.body,
      mood: entry.mood,
      weather: entry.weather,
      isPinned: entry.isPinned,
      summary: entry.summary,
      sentiment: entry.sentiment,
      createdAt: iso(entry.createdAt),
      updatedAt: iso(entry.updatedAt),
      tags: tagMap.get(entry.id) ?? [],
    })),
    tags: tagRows,
    entryTags: linkRows,
    attachments: attachmentRows.map((attachment) => ({
      id: attachment.id,
      entryId: attachment.entryId,
      r2Key: attachment.r2Key,
      filename: attachment.filename,
      contentType: attachment.contentType,
      size: attachment.size,
      createdAt: iso(attachment.createdAt),
    })),
  };
}

export async function exportDiaryBackup(
  env: Env,
  exportedAt = new Date(),
): Promise<DiaryBackupResult> {
  const payload = await buildBackupPayload(env, exportedAt);
  const date = exportedAt.toISOString().slice(0, 10);
  const timestamp = backupTimestamp(exportedAt);
  const baseKey = `backups/${date}/diary-${timestamp}`;
  const jsonKey = `${baseKey}.json`;
  const markdownKey = `${baseKey}.md`;

  await Promise.all([
    env.BLOBS.put(jsonKey, JSON.stringify(payload, null, 2), {
      httpMetadata: { contentType: "application/json; charset=utf-8" },
      customMetadata: { type: "diary-backup", format: "json", version: "1" },
    }),
    env.BLOBS.put(markdownKey, renderMarkdown(payload), {
      httpMetadata: { contentType: "text/markdown; charset=utf-8" },
      customMetadata: { type: "diary-backup", format: "markdown", version: "1" },
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
    await exportDiaryBackup(env);
  } catch (error) {
    console.error(
      JSON.stringify({
        event: "diary-backup",
        status: "error",
        message: error instanceof Error ? error.message : String(error),
      }),
    );
    throw error;
  }
}
