import { describe, expect, it } from "vitest";
import { decryptBytes, encryptBytes } from "../app/lib/crypto/aes";

// 32-byte keys, base64-encoded.
const KEY_A = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
const KEY_B = "ZmVkY2JhOTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTA=";

function bytes(text: string): Uint8Array<ArrayBuffer> {
  return new TextEncoder().encode(text) as Uint8Array<ArrayBuffer>;
}

describe("AES-256-GCM attachment encryption", () => {
  it("round-trips plaintext", async () => {
    const plaintext = bytes("机密日记附件内容 📎");
    const encrypted = await encryptBytes(plaintext, KEY_A);
    const decrypted = await decryptBytes(encrypted, KEY_A);
    expect(new TextDecoder().decode(decrypted)).toBe("机密日记附件内容 📎");
  });

  it("produces different ciphertext each time (unique IV)", async () => {
    const plaintext = bytes("same input");
    const a = await encryptBytes(plaintext, KEY_A);
    const b = await encryptBytes(plaintext, KEY_A);
    expect(Array.from(a)).not.toEqual(Array.from(b));
  });

  it("fails to decrypt with the wrong key", async () => {
    const encrypted = await encryptBytes(bytes("secret"), KEY_A);
    await expect(decryptBytes(encrypted, KEY_B)).rejects.toThrow();
  });

  it("fails to decrypt tampered ciphertext", async () => {
    const encrypted = await encryptBytes(bytes("secret"), KEY_A);
    encrypted[encrypted.length - 1] ^= 0xff;
    await expect(decryptBytes(encrypted, KEY_A)).rejects.toThrow();
  });

  it("rejects a key of the wrong length", async () => {
    await expect(encryptBytes(bytes("x"), "c2hvcnQ=")).rejects.toThrow(/32 bytes/);
  });
});
