import { clearAccessToken, setAccessToken } from "./auth";

export type Account = {
  id: string;
  username: string;
  displayName: string;
  createdAt: string;
  updatedAt: string;
};

export type AuthResponse = {
  account: Account;
  accessToken: string;
  expiresAt: string;
};

export type Memo = {
  id: string;
  content: string;
  entryDate: string;
  version: number;
  pinnedAt: string | null;
  archivedAt: string | null;
  createdAt: string;
  updatedAt: string;
  deletedAt: string | null;
};

export type Attachment = {
  uid: string;
  url: string;
  filename: string;
  contentType: string;
  size: number;
  sha256: string | null;
};

export type MemoAI = {
  memoId: string;
  summary: string | null;
  sentiment: string | null;
  provider: string;
  model: string;
  profileId: string;
  promptVersion: string;
  sourceMemoIds: string;
  status: string;
  errorCode: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  inputTokens: number;
  outputTokens: number;
  totalTokens: number;
  createdAt: string;
  updatedAt: string;
};

export type AskContextScope = "recent_7_days" | "recent_30_days" | "all";

export type AskConversation = {
  id: string;
  title: string;
  status: string;
  contextScope: AskContextScope;
  headMessageId: string | null;
  pinnedAt: string | null;
  archivedAt: string | null;
  createdAt: string;
  updatedAt: string;
  deletedAt: string | null;
};

export type AskSourceRef = {
  memoId: string;
  entryDate: string;
  excerpt: string;
  rank: number;
};

export type AskMessage = {
  id: string;
  conversationId: string;
  role: "user" | "assistant";
  content: string;
  parentId: string | null;
  forkOfId: string | null;
  status: string;
  sourceRefs: AskSourceRef[];
  model: string;
  createdAt: string;
  updatedAt: string;
  deletedAt: string | null;
};

export type AIProfile = {
  id: string;
  name: string;
  provider: string;
  baseUrl: string;
  model: string;
  temperature: number;
  maxTokens: number;
  enabled: boolean;
  active: boolean;
  hasApiKey: boolean;
  keyUnavailable: boolean;
  // Deprecated response compatibility; auto-summary is global on AISettings.
  autoSummary: boolean;
  createdAt: string;
  updatedAt: string;
};

export type AISettings = {
  profiles: AIProfile[];
  autoSummary?: boolean;
};

// apiKey omitted/null keeps the stored key; a string sets a new one.
// temperature/maxTokens omitted let the server apply its default; an explicit
// 0 temperature is preserved for deterministic output.
export type AIProfileInput = {
  id?: string;
  name: string;
  provider: string;
  baseUrl: string;
  model: string;
  temperature?: number;
  maxTokens?: number;
  enabled: boolean;
  active: boolean;
  apiKey?: string | null;
};

export async function getBootstrap(): Promise<{ initialized: boolean }> {
  return request("/api/v1/auth/bootstrap");
}

export async function initializeAccount(input: {
  username: string;
  displayName: string;
  password: string;
}): Promise<AuthResponse> {
  return request("/api/v1/auth/initialize", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export async function signIn(input: {
  username: string;
  password: string;
}): Promise<AuthResponse> {
  return request("/api/v1/auth/signin", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export async function signOut(): Promise<void> {
  await request("/api/v1/auth/signout", { method: "POST" });
}

export async function getMe(
  accessToken: string,
): Promise<{ account: Account }> {
  return request("/api/v1/auth/me", {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
}

export async function listMemos(
  accessToken: string,
  limit = 200,
  cursor?: string,
): Promise<{ memos: Memo[]; nextCursor?: string }> {
  const params = new URLSearchParams({ limit: String(limit) });
  if (cursor) {
    params.set("cursor", cursor);
  }
  return request(`/api/v1/memos?${params.toString()}`, {
    headers: authHeaders(accessToken),
  });
}

export async function searchMemos(
  accessToken: string,
  query: string,
  limit = 100,
): Promise<{ memos: Memo[] }> {
  return request(
    `/api/v1/memos?query=${encodeURIComponent(query)}&limit=${limit}`,
    { headers: authHeaders(accessToken) },
  );
}

export async function getMemo(
  accessToken: string,
  id: string,
): Promise<{ memo: Memo; ai?: MemoAI | null }> {
  return request(`/api/v1/memos/${id}`, {
    headers: authHeaders(accessToken),
  });
}

export async function createMemo(
  accessToken: string,
  input: { content: string; entryDate: string },
): Promise<{ memo: Memo }> {
  return request("/api/v1/memos", {
    method: "POST",
    headers: authHeaders(accessToken),
    body: JSON.stringify(input),
  });
}

export async function updateMemo(
  accessToken: string,
  memo: Memo,
  input: Partial<Pick<Memo, "content" | "entryDate">>,
): Promise<{ memo: Memo }> {
  return request(`/api/v1/memos/${memo.id}`, {
    method: "PATCH",
    headers: authHeaders(accessToken),
    body: JSON.stringify({
      ...input,
      expectedVersion: memo.version,
    }),
  });
}

export async function setMemoPinned(
  accessToken: string,
  memo: Memo,
  pinned: boolean,
): Promise<{ memo: Memo }> {
  return request(
    `/api/v1/memos/${memo.id}:${pinned ? "pin" : "unpin"}?expectedVersion=${memo.version}`,
    {
      method: "POST",
      headers: authHeaders(accessToken),
    },
  );
}

export async function setMemoArchived(
  accessToken: string,
  memo: Memo,
  archived: boolean,
): Promise<{ memo: Memo }> {
  return request(
    `/api/v1/memos/${memo.id}:${archived ? "archive" : "unarchive"}?expectedVersion=${memo.version}`,
    {
      method: "POST",
      headers: authHeaders(accessToken),
    },
  );
}

export async function deleteMemo(
  accessToken: string,
  memo: Memo,
): Promise<{ memo: Memo }> {
  return request(`/api/v1/memos/${memo.id}?expectedVersion=${memo.version}`, {
    method: "DELETE",
    headers: authHeaders(accessToken),
  });
}

export async function generateMemoSummary(
  accessToken: string,
  memo: Memo,
): Promise<{ ai: MemoAI }> {
  return request(`/api/v1/memos/${memo.id}:generate-summary`, {
    method: "POST",
    headers: authHeaders(accessToken),
  });
}

export async function uploadAttachment(
  accessToken: string,
  file: File,
): Promise<{ attachment: Attachment }> {
  const body = new FormData();
  body.set("file", file);
  body.set("mutation_id", crypto.randomUUID());
  return request("/api/v1/attachments", {
    method: "POST",
    headers: authHeaders(accessToken),
    body,
  });
}

export async function listAskConversations(
  accessToken: string,
): Promise<{ conversations: AskConversation[] }> {
  return request("/api/v1/ask/conversations?limit=50", {
    headers: authHeaders(accessToken),
  });
}

export async function createAskConversation(
  accessToken: string,
  input: { title?: string; contextScope: AskContextScope },
): Promise<{ conversation: AskConversation }> {
  return request("/api/v1/ask/conversations", {
    method: "POST",
    headers: authHeaders(accessToken),
    body: JSON.stringify(input),
  });
}

export async function listAskMessages(
  accessToken: string,
  conversationId: string,
): Promise<{ messages: AskMessage[] }> {
  return request(`/api/v1/ask/conversations/${conversationId}/messages`, {
    headers: authHeaders(accessToken),
  });
}

export type AskSourceKind = "records" | "memo_summary" | "summaries";

export async function createAskMessage(
  accessToken: string,
  conversationId: string,
  input: {
    content: string;
    contextScope: AskContextScope;
    sourceKind?: AskSourceKind;
  },
): Promise<{ messages: AskMessage[] }> {
  return request(`/api/v1/ask/conversations/${conversationId}/messages`, {
    method: "POST",
    headers: authHeaders(accessToken),
    body: JSON.stringify(input),
  });
}

export interface AskStreamHandlers {
  onStart?: (data: {
    userMessage: AskMessage;
    sources: AskSourceRef[];
    regenerate?: boolean;
  }) => void;
  onDelta?: (text: string) => void;
  onDone?: (message: AskMessage) => void;
  onError?: (message: string) => void;
}

// Switches the conversation's active branch leaf (used after picking a
// regenerated answer variant) so follow-ups attach to it.
export async function setAskHead(
  accessToken: string,
  conversationId: string,
  messageId: string,
): Promise<void> {
  await request(`/api/v1/ask/conversations/${conversationId}/head`, {
    method: "POST",
    headers: authHeaders(accessToken),
    body: JSON.stringify({ messageId }),
  });
}

export interface AskMessageInput {
  content: string;
  contextScope: AskContextScope;
  sourceKind?: AskSourceKind;
  parentId?: string;
  forkOfId?: string;
}

// Streams an answer over SSE, dispatching start/delta/done/error to handlers as
// they arrive. The AbortSignal lets callers stop generation mid-answer; an abort
// resolves normally (the server persists the partial answer).
export async function streamAskMessage(
  accessToken: string,
  conversationId: string,
  input: AskMessageInput,
  handlers: AskStreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const path = `/api/v1/ask/conversations/${conversationId}/messages:stream`;
  const body = JSON.stringify(input);
  const send = (token: string) =>
    fetch(path, {
      method: "POST",
      headers: {
        ...authHeaders(token),
        "Content-Type": "application/json",
      },
      body,
      credentials: "include",
      signal,
    });

  let res = await send(accessToken);
  if (res.status === 401) {
    const refreshed = await refreshAccessToken();
    if (refreshed) {
      res = await send(refreshed);
    }
  }
  if (!res.ok || !res.body) {
    const payload = await res.json().catch(() => null);
    throw new Error(payload?.error?.message ?? "生成回答失败");
  }
  await consumeAskStream(res.body, handlers);
}

async function consumeAskStream(
  body: ReadableStream<Uint8Array>,
  handlers: AskStreamHandlers,
): Promise<void> {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) {
        break;
      }
      buffer += decoder.decode(value, { stream: true });
      let boundary = buffer.indexOf("\n\n");
      while (boundary !== -1) {
        dispatchAskEvent(buffer.slice(0, boundary), handlers);
        buffer = buffer.slice(boundary + 2);
        boundary = buffer.indexOf("\n\n");
      }
    }
  } catch (cause) {
    // An aborted stream (stop button) surfaces as an AbortError; treat it as a
    // normal end — the server has persisted whatever streamed so far.
    if (cause instanceof DOMException && cause.name === "AbortError") {
      return;
    }
    throw cause;
  }
}

function dispatchAskEvent(block: string, handlers: AskStreamHandlers): void {
  let event = "message";
  let data = "";
  for (const line of block.split("\n")) {
    if (line.startsWith("event:")) {
      event = line.slice("event:".length).trim();
    } else if (line.startsWith("data:")) {
      data += line.slice("data:".length).trim();
    }
  }
  if (!data) {
    return;
  }
  let parsed: unknown;
  try {
    parsed = JSON.parse(data);
  } catch {
    return;
  }
  switch (event) {
    case "start":
      handlers.onStart?.(
        parsed as {
          userMessage: AskMessage;
          sources: AskSourceRef[];
          regenerate?: boolean;
        },
      );
      break;
    case "delta":
      handlers.onDelta?.((parsed as { text: string }).text);
      break;
    case "done":
      handlers.onDone?.((parsed as { message: AskMessage }).message);
      break;
    case "error":
      handlers.onError?.((parsed as { message: string }).message);
      break;
  }
}

export async function getAISettings(accessToken: string): Promise<AISettings> {
  return request("/api/v1/settings/ai", {
    headers: authHeaders(accessToken),
  });
}

export async function patchAISettings(
  accessToken: string,
  input: { profiles: AIProfileInput[]; autoSummary: boolean },
): Promise<AISettings> {
  return request("/api/v1/settings/ai", {
    method: "PATCH",
    headers: authHeaders(accessToken),
    body: JSON.stringify(input),
  });
}

// Tests a saved profile's connection by id. Throws with a readable message on
// failure (the server maps provider errors to a user-facing string).
export async function testAIConnection(
  accessToken: string,
  input: {
    id?: string;
    provider: string;
    baseUrl: string;
    model: string;
    temperature?: number;
    maxTokens?: number;
    apiKey?: string | null;
  },
  signal?: AbortSignal,
): Promise<{ ok: boolean; model: string }> {
  return request("/api/v1/settings/ai:test", {
    method: "POST",
    headers: authHeaders(accessToken),
    body: JSON.stringify(input),
    signal,
  });
}

export async function listAIModels(
  accessToken: string,
  input: {
    id?: string;
    provider: string;
    baseUrl: string;
    apiKey?: string | null;
  },
  signal?: AbortSignal,
): Promise<{ models: string[] }> {
  return request("/api/v1/settings/ai:models", {
    method: "POST",
    headers: authHeaders(accessToken),
    body: JSON.stringify(input),
    signal,
  });
}

function authHeaders(accessToken: string) {
  return { Authorization: `Bearer ${accessToken}` };
}

let refreshInFlight: Promise<string | null> | null = null;

// Refreshes the access token using the HttpOnly refresh cookie. Concurrent
// callers share one in-flight request so a burst of 401s triggers a single
// refresh. On failure the stored token is cleared so the app falls back to the
// login screen.
async function refreshAccessToken(): Promise<string | null> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      try {
        const res = await fetch("/api/v1/auth/refresh", {
          method: "POST",
          credentials: "include",
        });
        if (!res.ok) {
          clearAccessToken();
          return null;
        }
        const data = (await res.json()) as AuthResponse;
        setAccessToken(data.accessToken);
        return data.accessToken;
      } catch {
        clearAccessToken();
        return null;
      } finally {
        refreshInFlight = null;
      }
    })();
  }
  return refreshInFlight;
}

function buildRequestInit(init: RequestInit): RequestInit {
  const headers = new Headers(init.headers);
  if (
    init.body &&
    !(init.body instanceof FormData) &&
    !headers.has("Content-Type")
  ) {
    headers.set("Content-Type", "application/json");
  }
  return { ...init, headers, credentials: "include" };
}

// Endpoints that do not carry an access token, so a 401 from them must not
// trigger a refresh-and-retry (refresh itself would loop; the others are
// pre-auth). /api/v1/auth/me is intentionally absent: it needs a token and
// should refresh on expiry.
const NO_REFRESH_PREFIXES = [
  "/api/v1/auth/refresh",
  "/api/v1/auth/signin",
  "/api/v1/auth/initialize",
  "/api/v1/auth/bootstrap",
];

function canRefreshFor(path: string): boolean {
  return !NO_REFRESH_PREFIXES.some((prefix) => path.startsWith(prefix));
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  let res = await fetch(path, buildRequestInit(init));
  if (res.status === 401 && canRefreshFor(path)) {
    const refreshed = await refreshAccessToken();
    if (refreshed) {
      const retryHeaders = new Headers(init.headers);
      retryHeaders.set("Authorization", `Bearer ${refreshed}`);
      res = await fetch(
        path,
        buildRequestInit({ ...init, headers: retryHeaders }),
      );
    }
  }
  if (!res.ok) {
    const payload = await res.json().catch(() => null);
    const message = payload?.error?.message ?? "请求失败";
    throw new Error(message);
  }
  if (res.status === 204) {
    return undefined as T;
  }
  return res.json() as Promise<T>;
}
