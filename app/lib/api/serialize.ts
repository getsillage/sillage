import type { EntryWithTags } from "~/lib/db/entries";
import type { Attachment } from "~/lib/db/schema";
import { parseTextList } from "~/lib/product/entry-fields";

/**
 * Stable, client-facing representations of the domain rows. Keeping this mapping
 * layer between the database and any API (web fetcher, mobile app, future sync
 * clients) means the internal schema can evolve without breaking external
 * consumers: timestamps become ISO 8601 strings, internal-only fields (R2 keys)
 * are dropped, and `metadata` is parsed back into an object.
 */

export interface EntryDto {
  id: string;
  entryDate: string;
  title: string;
  body: string;
  kind: string;
  noteType: string | null;
  mood: number | null;
  moodText: string | null;
  weather: string | null;
  location: string | null;
  people: string[];
  relationships: string[];
  isPinned: boolean;
  utcOffsetMinutes: number | null;
  metadata: Record<string, unknown> | null;
  version: number;
  tags: string[];
  ai: { summary: string | null; sentiment: string | null };
  createdAt: string;
  updatedAt: string;
  deletedAt: string | null;
}

export interface AttachmentDto {
  id: string;
  entryId: string | null;
  url: string;
  filename: string;
  contentType: string;
  size: number;
  sha256: string | null;
  width: number | null;
  height: number | null;
  status: string;
  createdAt: string;
  updatedAt: string;
  deletedAt: string | null;
}

function iso(value: Date | null): string | null {
  return value ? value.toISOString() : null;
}

/** Parses the stored metadata JSON, tolerating malformed values. */
export function parseMetadata(raw: string | null): Record<string, unknown> | null {
  if (!raw) {
    return null;
  }
  try {
    const parsed: unknown = JSON.parse(raw);
    return parsed && typeof parsed === "object" && !Array.isArray(parsed)
      ? (parsed as Record<string, unknown>)
      : null;
  } catch {
    return null;
  }
}

export function toEntryDto(entry: EntryWithTags): EntryDto {
  return {
    id: entry.id,
    entryDate: entry.entryDate,
    title: entry.title,
    body: entry.body,
    kind: entry.kind,
    noteType: entry.noteType,
    mood: entry.mood,
    moodText: entry.moodText,
    weather: entry.weather,
    location: entry.location,
    people: parseTextList(entry.people),
    relationships: parseTextList(entry.relationships),
    isPinned: entry.isPinned,
    utcOffsetMinutes: entry.utcOffsetMinutes,
    metadata: parseMetadata(entry.metadata),
    version: entry.version,
    tags: entry.tags,
    ai: { summary: entry.summary, sentiment: entry.sentiment },
    createdAt: entry.createdAt.toISOString(),
    updatedAt: entry.updatedAt.toISOString(),
    deletedAt: iso(entry.deletedAt),
  };
}

export function toAttachmentDto(attachment: Attachment): AttachmentDto {
  return {
    id: attachment.id,
    entryId: attachment.entryId,
    url: `/attachments/${attachment.id}`,
    filename: attachment.filename,
    contentType: attachment.contentType,
    size: attachment.size,
    sha256: attachment.sha256,
    width: attachment.width,
    height: attachment.height,
    status: attachment.status,
    createdAt: attachment.createdAt.toISOString(),
    updatedAt: attachment.updatedAt.toISOString(),
    deletedAt: iso(attachment.deletedAt),
  };
}
