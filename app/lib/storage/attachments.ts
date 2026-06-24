import { and, eq, isNull } from "drizzle-orm";
import { decryptBytes, encryptBytes } from "~/lib/crypto/aes";
import type { Db } from "~/lib/db/client";
import { uuidv7 } from "~/lib/db/id";
import { type Attachment, attachments } from "~/lib/db/schema";

const R2_PREFIX = "attachments/";

export interface UploadInput {
  bytes: Uint8Array<ArrayBuffer>;
  filename: string;
  contentType: string;
  entryId?: string | null;
}

export interface LoadedAttachment {
  bytes: Uint8Array<ArrayBuffer>;
  contentType: string;
  filename: string;
}

/** Lowercase hex SHA-256 of the plaintext bytes (integrity + dedup). */
async function sha256Hex(bytes: Uint8Array<ArrayBuffer>): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

/** Encrypts and stores an attachment in R2, recording its metadata in D1. */
export async function putAttachment(
  db: Db,
  bucket: R2Bucket,
  encryptionKey: string,
  input: UploadInput,
): Promise<Attachment> {
  const id = uuidv7();
  const r2Key = `${R2_PREFIX}${id}`;
  const sha256 = await sha256Hex(input.bytes);
  const encrypted = await encryptBytes(input.bytes, encryptionKey);
  await bucket.put(r2Key, encrypted);

  const [row] = await db
    .insert(attachments)
    .values({
      id,
      entryId: input.entryId ?? null,
      r2Key,
      filename: input.filename,
      contentType: input.contentType,
      size: input.bytes.length,
      sha256,
    })
    .returning();
  return row;
}

/** Loads and decrypts a live attachment, or null if missing/deleted/object-gone. */
export async function getAttachment(
  db: Db,
  bucket: R2Bucket,
  encryptionKey: string,
  id: string,
): Promise<LoadedAttachment | null> {
  const [row] = await db
    .select()
    .from(attachments)
    .where(and(eq(attachments.id, id), isNull(attachments.deletedAt)));
  if (!row) {
    return null;
  }
  const object = await bucket.get(row.r2Key);
  if (!object) {
    return null;
  }
  const encrypted = new Uint8Array(await object.arrayBuffer()) as Uint8Array<ArrayBuffer>;
  const bytes = await decryptBytes(encrypted, encryptionKey);
  return { bytes, contentType: row.contentType, filename: row.filename };
}

/**
 * Soft-deletes an attachment: reclaims the R2 bytes but keeps a tombstone row so
 * delta-sync clients learn the attachment is gone. `getAttachment` then 404s.
 */
export async function deleteAttachment(db: Db, bucket: R2Bucket, id: string): Promise<void> {
  const [row] = await db.select().from(attachments).where(eq(attachments.id, id));
  if (!row) {
    return;
  }
  await bucket.delete(row.r2Key);
  const now = new Date();
  await db
    .update(attachments)
    .set({ deletedAt: now, updatedAt: now })
    .where(eq(attachments.id, id));
}
