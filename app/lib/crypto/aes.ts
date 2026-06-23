import { base64ToBytes } from "./encoding";

// AES-256-GCM. The 12-byte random IV is prepended to the ciphertext so the same
// key can be reused safely across objects (unique IV per encryption).
const IV_BYTES = 12;

async function importKey(keyBase64: string): Promise<CryptoKey> {
  const raw = base64ToBytes(keyBase64);
  if (raw.length !== 32) {
    throw new Error("ATTACH_ENCRYPTION_KEY must be 32 bytes (base64-encoded)");
  }
  return crypto.subtle.importKey("raw", raw, "AES-GCM", false, ["encrypt", "decrypt"]);
}

/** Encrypts bytes, returning `iv || ciphertext` as a single buffer. */
export async function encryptBytes(
  plaintext: Uint8Array<ArrayBuffer>,
  keyBase64: string,
): Promise<Uint8Array<ArrayBuffer>> {
  const key = await importKey(keyBase64);
  const iv = crypto.getRandomValues(new Uint8Array(IV_BYTES));
  const ciphertext = new Uint8Array(
    await crypto.subtle.encrypt({ name: "AES-GCM", iv }, key, plaintext),
  );
  const out = new Uint8Array(IV_BYTES + ciphertext.length);
  out.set(iv, 0);
  out.set(ciphertext, IV_BYTES);
  return out;
}

/** Decrypts a buffer produced by {@link encryptBytes}. Throws if tampered. */
export async function decryptBytes(
  payload: Uint8Array<ArrayBuffer>,
  keyBase64: string,
): Promise<Uint8Array<ArrayBuffer>> {
  const key = await importKey(keyBase64);
  const iv = payload.slice(0, IV_BYTES);
  const ciphertext = payload.slice(IV_BYTES);
  const plaintext = await crypto.subtle.decrypt({ name: "AES-GCM", iv }, key, ciphertext);
  return new Uint8Array(plaintext);
}
