import type { EntryWithAi } from "~/lib/db/entries";
import type { Attachment } from "~/lib/db/schema";

/**
 * Stable, client-facing representations of the domain rows. Keeping this mapping
 * layer between the database and any API (web fetcher, mobile app, future sync
 * clients) means the internal schema can evolve without breaking external
 * consumers: timestamps become ISO 8601 strings and internal-only fields (R2 keys)
 * are dropped.
 */

export interface EntryDto {
  id: string;
  entryDate: string;
  body: string;
  version: number;
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

export function toEntryDto(entry: EntryWithAi): EntryDto {
  return {
    id: entry.id,
    entryDate: entry.entryDate,
    body: entry.body,
    version: entry.version,
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
