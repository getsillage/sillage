import { eq } from "drizzle-orm";
import { decryptBytes, encryptBytes } from "~/lib/crypto/aes";
import type { Db } from "~/lib/db/client";
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

/** Encrypts and stores an attachment in R2, recording its metadata in D1. */
export async function putAttachment(
  db: Db,
  bucket: R2Bucket,
  encryptionKey: string,
  input: UploadInput,
): Promise<Attachment> {
  const id = crypto.randomUUID();
  const r2Key = `${R2_PREFIX}${id}`;
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
    })
    .returning();
  return row;
}

/** Loads and decrypts an attachment, or null if metadata/object is missing. */
export async function getAttachment(
  db: Db,
  bucket: R2Bucket,
  encryptionKey: string,
  id: string,
): Promise<LoadedAttachment | null> {
  const [row] = await db.select().from(attachments).where(eq(attachments.id, id));
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

export async function deleteAttachment(db: Db, bucket: R2Bucket, id: string): Promise<void> {
  const [row] = await db.select().from(attachments).where(eq(attachments.id, id));
  if (!row) {
    return;
  }
  await bucket.delete(row.r2Key);
  await db.delete(attachments).where(eq(attachments.id, id));
}
