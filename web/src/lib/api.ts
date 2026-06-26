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

export async function getMe(
  accessToken: string,
): Promise<{ account: Account }> {
  return request("/api/v1/auth/me", {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
}

export async function listMemos(
  accessToken: string,
): Promise<{ memos: Memo[] }> {
  return request("/api/v1/memos?limit=100", {
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

export async function createAskMessage(
  accessToken: string,
  conversationId: string,
  input: { content: string; contextScope: AskContextScope },
): Promise<{ messages: AskMessage[] }> {
  return request(`/api/v1/ask/conversations/${conversationId}/messages`, {
    method: "POST",
    headers: authHeaders(accessToken),
    body: JSON.stringify(input),
  });
}

function authHeaders(accessToken: string) {
  return { Authorization: `Bearer ${accessToken}` };
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (
    init.body &&
    !(init.body instanceof FormData) &&
    !headers.has("Content-Type")
  ) {
    headers.set("Content-Type", "application/json");
  }
  const res = await fetch(path, {
    ...init,
    headers,
    credentials: "include",
  });
  if (!res.ok) {
    const payload = await res.json().catch(() => null);
    const message = payload?.error?.message ?? "请求失败";
    throw new Error(message);
  }
  return res.json() as Promise<T>;
}
