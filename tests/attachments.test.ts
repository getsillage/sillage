import { env } from "cloudflare:test";
import { beforeEach, describe, expect, it } from "vitest";
import { getDb } from "../app/lib/db/client";
import { deleteAttachment, getAttachment, putAttachment } from "../app/lib/storage/attachments";

const KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
const db = getDb(env.DB);

function bytes(text: string): Uint8Array<ArrayBuffer> {
  return new TextEncoder().encode(text) as Uint8Array<ArrayBuffer>;
}

describe("R2 attachment storage", () => {
  beforeEach(async () => {
    await env.DB.prepare("DELETE FROM attachments").run();
  });

  it("stores encrypted bytes in R2 and round-trips on read", async () => {
    const att = await putAttachment(db, env.BLOBS, KEY, {
      bytes: bytes("图片数据"),
      filename: "photo.png",
      contentType: "image/png",
    });

    // The raw R2 object must NOT equal the plaintext (it is encrypted).
    const raw = await env.BLOBS.get(`attachments/${att.id}`);
    const rawBytes = new Uint8Array((await raw?.arrayBuffer()) ?? new ArrayBuffer(0));
    expect(new TextDecoder().decode(rawBytes)).not.toBe("图片数据");

    const loaded = await getAttachment(db, env.BLOBS, KEY, att.id);
    expect(loaded?.contentType).toBe("image/png");
    expect(new TextDecoder().decode(loaded?.bytes)).toBe("图片数据");
  });

  it("returns null for a missing attachment", async () => {
    expect(await getAttachment(db, env.BLOBS, KEY, "nope")).toBeNull();
  });

  it("deletes both the R2 object and the metadata row", async () => {
    const att = await putAttachment(db, env.BLOBS, KEY, {
      bytes: bytes("x"),
      filename: "f.png",
      contentType: "image/png",
    });
    await deleteAttachment(db, env.BLOBS, att.id);

    expect(await env.BLOBS.get(`attachments/${att.id}`)).toBeNull();
    expect(await getAttachment(db, env.BLOBS, KEY, att.id)).toBeNull();
  });
});
