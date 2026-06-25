import { z } from "zod";
import {
  DEFAULT_ENTRY_INSIGHT_AUTO_MODE,
  ENTRY_INSIGHT_AUTO_MODES,
  type EntryInsightAutoMode,
} from "~/lib/ai/entry-insights.shared";
import { decryptBytes, encryptBytes } from "~/lib/crypto/aes";
import { base64ToBytes, bytesToBase64 } from "~/lib/crypto/encoding";

/**
 * Web-managed AI provider configuration. Stored in KV (single-user app); API
 * keys are AES-256-GCM encrypted at rest with ATTACH_ENCRYPTION_KEY and are
 * never returned to the browser.
 */

const KV_KEY = "ai-settings";
const STORE_VERSION = 2;

export const AI_PROTOCOLS = ["anthropic", "openai"] as const;
export type AiProtocol = (typeof AI_PROTOCOLS)[number];

/** Full settings including the decrypted key — server-side use only. */
export interface AiSettings {
  id: string;
  name: string;
  enabled: boolean;
  protocol: AiProtocol;
  baseUrl: string;
  model: string;
  apiKey: string;
}

/** Browser-safe projection: reports whether a key exists, never the key. */
export interface AiSettingsView {
  id: string;
  name: string;
  enabled: boolean;
  protocol: AiProtocol;
  baseUrl: string;
  model: string;
  hasApiKey: boolean;
}

export interface AiSettingsStoreView {
  activeProfileId: string | null;
  profiles: AiSettingsView[];
  entryInsightAutoMode: EntryInsightAutoMode;
}

interface StoredAiSettingsProfile {
  id: string;
  name: string;
  enabled: boolean;
  protocol: AiProtocol;
  baseUrl: string;
  model: string;
  apiKeyCipher: string;
  createdAt: number;
  updatedAt: number;
}

interface StoredAiSettingsV2 {
  version: typeof STORE_VERSION;
  activeProfileId: string | null;
  profiles: StoredAiSettingsProfile[];
  entryInsightAutoMode: EntryInsightAutoMode;
}

interface StoredAiSettingsV1 {
  enabled: boolean;
  protocol: AiProtocol;
  baseUrl: string;
  model: string;
  apiKeyCipher: string;
}

export const aiSettingsInputSchema = z.object({
  id: z.string().trim().optional(),
  name: z.string().trim().min(1, "请输入配置名称").max(80, "配置名称太长"),
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

export const aiProviderCredentialsSchema = z.object({
  id: z.string().trim().optional(),
  protocol: z.enum(AI_PROTOCOLS),
  baseUrl: z
    .string()
    .trim()
    .regex(/^https?:\/\/.+/, "请输入有效的 URL（以 http:// 或 https:// 开头）"),
  // Empty string means "reuse the selected profile's stored key".
  apiKey: z.string(),
});
export type AiProviderCredentialsInput = z.infer<typeof aiProviderCredentialsSchema>;

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

function defaultNameFor(protocol: AiProtocol): string {
  return protocol === "anthropic" ? "Claude" : "OpenAI";
}

function normalizeEntryInsightAutoMode(value: unknown): EntryInsightAutoMode {
  return ENTRY_INSIGHT_AUTO_MODES.includes(value as EntryInsightAutoMode)
    ? (value as EntryInsightAutoMode)
    : DEFAULT_ENTRY_INSIGHT_AUTO_MODE;
}

function normalizeId(id: string | undefined): string | undefined {
  const trimmed = id?.trim();
  return trimmed || undefined;
}

function isStoredV2(value: unknown): value is StoredAiSettingsV2 {
  return (
    typeof value === "object" &&
    value !== null &&
    "version" in value &&
    (value as { version?: unknown }).version === STORE_VERSION &&
    Array.isArray((value as { profiles?: unknown }).profiles)
  );
}

function isStoredV1(value: unknown): value is StoredAiSettingsV1 {
  if (typeof value !== "object" || value === null) {
    return false;
  }
  const candidate = value as Partial<StoredAiSettingsV1>;
  return (
    typeof candidate.enabled === "boolean" &&
    AI_PROTOCOLS.includes(candidate.protocol as AiProtocol) &&
    typeof candidate.baseUrl === "string" &&
    typeof candidate.model === "string" &&
    typeof candidate.apiKeyCipher === "string"
  );
}

function migrateV1(stored: StoredAiSettingsV1): StoredAiSettingsV2 {
  const now = Date.now();
  const id = "legacy-default";
  return {
    version: STORE_VERSION,
    activeProfileId: id,
    profiles: [
      {
        id,
        name: defaultNameFor(stored.protocol),
        enabled: stored.enabled,
        protocol: stored.protocol,
        baseUrl: stored.baseUrl,
        model: stored.model,
        apiKeyCipher: stored.apiKeyCipher,
        createdAt: now,
        updatedAt: now,
      },
    ],
    entryInsightAutoMode: DEFAULT_ENTRY_INSIGHT_AUTO_MODE,
  };
}

async function readStored(env: Env): Promise<StoredAiSettingsV2> {
  const raw = await env.SESSIONS.get(KV_KEY);
  if (!raw) {
    return {
      version: STORE_VERSION,
      activeProfileId: null,
      profiles: [],
      entryInsightAutoMode: DEFAULT_ENTRY_INSIGHT_AUTO_MODE,
    };
  }
  try {
    const parsed = JSON.parse(raw) as unknown;
    if (isStoredV2(parsed)) {
      return {
        version: STORE_VERSION,
        activeProfileId: normalizeId(parsed.activeProfileId ?? undefined) ?? null,
        profiles: parsed.profiles.filter((profile) =>
          AI_PROTOCOLS.includes(profile.protocol as AiProtocol),
        ),
        entryInsightAutoMode: normalizeEntryInsightAutoMode(
          (parsed as { entryInsightAutoMode?: unknown }).entryInsightAutoMode,
        ),
      };
    }
    if (isStoredV1(parsed)) {
      return migrateV1(parsed);
    }
    return {
      version: STORE_VERSION,
      activeProfileId: null,
      profiles: [],
      entryInsightAutoMode: DEFAULT_ENTRY_INSIGHT_AUTO_MODE,
    };
  } catch {
    return {
      version: STORE_VERSION,
      activeProfileId: null,
      profiles: [],
      entryInsightAutoMode: DEFAULT_ENTRY_INSIGHT_AUTO_MODE,
    };
  }
}

async function writeStored(env: Env, stored: StoredAiSettingsV2): Promise<void> {
  await env.SESSIONS.put(KV_KEY, JSON.stringify(stored));
}

function activeProfile(stored: StoredAiSettingsV2): StoredAiSettingsProfile | null {
  if (!stored.activeProfileId) {
    return null;
  }
  return stored.profiles.find((profile) => profile.id === stored.activeProfileId) ?? null;
}

async function decryptProfile(env: Env, stored: StoredAiSettingsProfile): Promise<AiSettings> {
  return {
    id: stored.id,
    name: stored.name,
    enabled: stored.enabled,
    protocol: stored.protocol,
    baseUrl: stored.baseUrl,
    model: stored.model,
    apiKey: await decryptKey(env, stored.apiKeyCipher),
  };
}

function profileView(stored: StoredAiSettingsProfile): AiSettingsView {
  return {
    id: stored.id,
    name: stored.name,
    enabled: stored.enabled,
    protocol: stored.protocol,
    baseUrl: stored.baseUrl,
    model: stored.model,
    hasApiKey: Boolean(stored.apiKeyCipher),
  };
}

/** Loads the active settings with the decrypted key. Returns null when unset. */
export async function loadAiSettings(env: Env): Promise<AiSettings | null> {
  const stored = await readStored(env);
  const profile = activeProfile(stored);
  if (!profile) {
    return null;
  }
  return await decryptProfile(env, profile);
}

/** Loads one profile by id with the decrypted key. */
export async function loadAiSettingsProfile(env: Env, id: string): Promise<AiSettings | null> {
  const stored = await readStored(env);
  const profile = stored.profiles.find((item) => item.id === id);
  return profile ? await decryptProfile(env, profile) : null;
}

/** Loads the browser-safe view (no key material). */
export async function loadAiSettingsView(env: Env): Promise<AiSettingsStoreView> {
  const stored = await readStored(env);
  return {
    activeProfileId: stored.activeProfileId,
    profiles: stored.profiles.map(profileView),
    entryInsightAutoMode: stored.entryInsightAutoMode,
  };
}

/** Loads the default behavior for per-entry AI insights after saving records. */
export async function loadEntryInsightAutoMode(env: Env): Promise<EntryInsightAutoMode> {
  return (await readStored(env)).entryInsightAutoMode;
}

/** Updates the default behavior for per-entry AI insights after saving records. */
export async function saveEntryInsightAutoMode(
  env: Env,
  mode: EntryInsightAutoMode,
): Promise<void> {
  const stored = await readStored(env);
  stored.entryInsightAutoMode = normalizeEntryInsightAutoMode(mode);
  await writeStored(env, stored);
}

/**
 * Persists a profile and makes it the active profile. An empty `apiKey`
 * preserves the selected profile's previously stored key, so the browser never
 * has to round-trip the secret.
 */
export async function saveAiSettings(env: Env, input: AiSettingsInput): Promise<string> {
  const stored = await readStored(env);
  const id = normalizeId(input.id);
  const existingIndex = id ? stored.profiles.findIndex((profile) => profile.id === id) : -1;
  const existing = existingIndex >= 0 ? stored.profiles[existingIndex] : null;
  const profileId = existing?.id ?? crypto.randomUUID();
  const now = Date.now();
  const apiKeyCipher = input.apiKey
    ? await encryptKey(env, input.apiKey)
    : (existing?.apiKeyCipher ?? "");
  const profile: StoredAiSettingsProfile = {
    id: profileId,
    name: input.name.trim(),
    enabled: input.enabled,
    protocol: input.protocol,
    baseUrl: input.baseUrl.trim(),
    model: input.model.trim(),
    apiKeyCipher,
    createdAt: existing?.createdAt ?? now,
    updatedAt: now,
  };

  if (existingIndex >= 0) {
    stored.profiles[existingIndex] = profile;
  } else {
    stored.profiles.push(profile);
  }
  stored.activeProfileId = profileId;
  await writeStored(env, stored);
  return profileId;
}

/** Makes an existing profile active without changing its contents. */
export async function activateAiSettingsProfile(env: Env, id: string): Promise<boolean> {
  const stored = await readStored(env);
  if (!stored.profiles.some((profile) => profile.id === id)) {
    return false;
  }
  stored.activeProfileId = id;
  await writeStored(env, stored);
  return true;
}

/** Deletes a profile; if it was active, the first remaining profile becomes active. */
export async function deleteAiSettingsProfile(env: Env, id: string): Promise<boolean> {
  const stored = await readStored(env);
  const nextProfiles = stored.profiles.filter((profile) => profile.id !== id);
  if (nextProfiles.length === stored.profiles.length) {
    return false;
  }
  stored.profiles = nextProfiles;
  if (stored.activeProfileId === id) {
    stored.activeProfileId = nextProfiles[0]?.id ?? null;
  }
  await writeStored(env, stored);
  return true;
}
