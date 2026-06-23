import { z } from "zod";
import { decryptBytes, encryptBytes } from "~/lib/crypto/aes";
import { base64ToBytes, bytesToBase64 } from "~/lib/crypto/encoding";

/**
 * Web-managed AI provider configuration. Stored in KV (single-user app); the
 * API key is AES-256-GCM encrypted at rest with ATTACH_ENCRYPTION_KEY and is
 * never returned to the browser. Falls back to env config when unset.
 */

const KV_KEY = "ai-settings";

export const AI_PROTOCOLS = ["anthropic", "openai"] as const;
export type AiProtocol = (typeof AI_PROTOCOLS)[number];

/** Full settings including the decrypted key — server-side use only. */
export interface AiSettings {
  enabled: boolean;
  protocol: AiProtocol;
  baseUrl: string;
  model: string;
  apiKey: string;
}

/** Browser-safe projection: reports whether a key exists, never the key. */
export interface AiSettingsView {
  enabled: boolean;
  protocol: AiProtocol;
  baseUrl: string;
  model: string;
  hasApiKey: boolean;
}

interface StoredAiSettings {
  enabled: boolean;
  protocol: AiProtocol;
  baseUrl: string;
  model: string;
  apiKeyCipher: string;
}

export const aiSettingsInputSchema = z.object({
  enabled: z.boolean(),
  protocol: z.enum(AI_PROTOCOLS),
  baseUrl: z
    .string()
    .trim()
    .regex(/^https?:\/\/.+/, "请输入有效的 URL（以 http:// 或 https:// 开头）"),
  model: z.string().trim().min(1, "请输入模型名称"),
  // Empty string means "keep the previously stored key".
  apiKey: z.string(),
});
export type AiSettingsInput = z.infer<typeof aiSettingsInputSchema>;

function toBytes(text: string): Uint8Array<ArrayBuffer> {
  return new TextEncoder().encode(text) as Uint8Array<ArrayBuffer>;
}

async function encryptKey(env: Env, plaintext: string): Promise<string> {
  if (!plaintext) {
    return "";
  }
  const cipher = await encryptBytes(toBytes(plaintext), env.ATTACH_ENCRYPTION_KEY);
  return bytesToBase64(cipher);
}

async function decryptKey(env: Env, cipher: string): Promise<string> {
  if (!cipher) {
    return "";
  }
  const bytes = await decryptBytes(base64ToBytes(cipher), env.ATTACH_ENCRYPTION_KEY);
  return new TextDecoder().decode(bytes);
}

async function readStored(env: Env): Promise<StoredAiSettings | null> {
  const raw = await env.SESSIONS.get(KV_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as StoredAiSettings;
  } catch {
    return null;
  }
}

/** Loads settings with the decrypted key. Returns null when never configured. */
export async function loadAiSettings(env: Env): Promise<AiSettings | null> {
  const stored = await readStored(env);
  if (!stored) {
    return null;
  }
  return {
    enabled: stored.enabled,
    protocol: stored.protocol,
    baseUrl: stored.baseUrl,
    model: stored.model,
    apiKey: await decryptKey(env, stored.apiKeyCipher),
  };
}

/** Loads the browser-safe view (no key material). */
export async function loadAiSettingsView(env: Env): Promise<AiSettingsView | null> {
  const stored = await readStored(env);
  if (!stored) {
    return null;
  }
  return {
    enabled: stored.enabled,
    protocol: stored.protocol,
    baseUrl: stored.baseUrl,
    model: stored.model,
    hasApiKey: Boolean(stored.apiKeyCipher),
  };
}

/**
 * Persists settings. An empty `apiKey` preserves the previously stored key, so
 * the browser never has to round-trip the secret.
 */
export async function saveAiSettings(env: Env, input: AiSettingsInput): Promise<void> {
  const existing = await readStored(env);
  const apiKeyCipher = input.apiKey
    ? await encryptKey(env, input.apiKey)
    : (existing?.apiKeyCipher ?? "");
  const stored: StoredAiSettings = {
    enabled: input.enabled,
    protocol: input.protocol,
    baseUrl: input.baseUrl.trim(),
    model: input.model.trim(),
    apiKeyCipher,
  };
  await env.SESSIONS.put(KV_KEY, JSON.stringify(stored));
}
