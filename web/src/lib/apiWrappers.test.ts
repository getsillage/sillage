import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Memo } from "./api";
import {
  createAskConversation,
  createAskMessage,
  createMemo,
  deleteMemo,
  generateMemoSummary,
  getAISettings,
  getMe,
  initializeAccount,
  listAIModels,
  listAskConversations,
  listAskMessages,
  listMemos,
  patchAISettings,
  searchMemos,
  setAskHead,
  setMemoArchived,
  setMemoPinned,
  signOut,
  testAIConnection,
  updateMemo,
  uploadAttachment,
} from "./api";

vi.mock("./auth", () => ({
  clearAccessToken: () => {},
  setAccessToken: () => {},
  getAccessToken: () => "",
}));

let fetchMock: ReturnType<typeof vi.fn>;

function ok(body: unknown = {}): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
}

function lastCall() {
  const calls = fetchMock.mock.calls;
  const [path, init] = calls[calls.length - 1];
  return { path: path as string, init: (init ?? {}) as RequestInit };
}

const memo: Memo = {
  id: "m1",
  content: "x",
  entryDate: "2026-06-27",
  version: 3,
  pinnedAt: null,
  archivedAt: null,
  createdAt: "1",
  updatedAt: "1",
  deletedAt: null,
};

beforeEach(() => {
  // A fresh Response per call: a Response body can only be read once.
  fetchMock = vi.fn(() => Promise.resolve(ok()));
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("memo api wrappers", () => {
  it("builds the expected requests", async () => {
    await listMemos("t", 10);
    expect(lastCall().path).toBe("/api/v1/memos?limit=10");

    await searchMemos("t", "爬 山", 5);
    expect(lastCall().path).toBe(
      "/api/v1/memos?query=%E7%88%AC%20%E5%B1%B1&limit=5",
    );

    await createMemo("t", { content: "hi", entryDate: "2026-06-27" });
    expect(lastCall().init.method).toBe("POST");

    await updateMemo("t", memo, { content: "new" });
    const upd = lastCall();
    expect(upd.path).toBe("/api/v1/memos/m1");
    expect(JSON.parse(upd.init.body as string).expectedVersion).toBe(3);

    await setMemoPinned("t", memo, true);
    expect(lastCall().path).toContain(":pin?expectedVersion=3");
    await setMemoPinned("t", memo, false);
    expect(lastCall().path).toContain(":unpin?");
    await setMemoArchived("t", memo, true);
    expect(lastCall().path).toContain(":archive?");
    await setMemoArchived("t", memo, false);
    expect(lastCall().path).toContain(":unarchive?");

    await deleteMemo("t", memo);
    expect(lastCall().init.method).toBe("DELETE");

    await generateMemoSummary("t", memo);
    expect(lastCall().path).toBe("/api/v1/memos/m1:generate-summary");
  });

  it("uploads attachments as multipart", async () => {
    vi.stubGlobal("crypto", { randomUUID: () => "uuid-1" });
    fetchMock.mockResolvedValue(ok({ attachment: {} }));
    await uploadAttachment("t", new File(["x"], "a.txt"));
    expect(lastCall().init.body).toBeInstanceOf(FormData);
  });
});

describe("ask + settings + auth api wrappers", () => {
  it("builds the expected requests", async () => {
    await listAskConversations("t");
    expect(lastCall().path).toBe("/api/v1/ask/conversations?limit=50");

    await createAskConversation("t", { contextScope: "all" });
    expect(lastCall().init.method).toBe("POST");

    await listAskMessages("t", "c1");
    expect(lastCall().path).toBe("/api/v1/ask/conversations/c1/messages");

    await createAskMessage("t", "c1", {
      content: "q",
      contextScope: "all",
      sourceKind: "summaries",
    });
    const created = lastCall();
    expect(created.path).toBe("/api/v1/ask/conversations/c1/messages");
    expect(JSON.parse(created.init.body as string).sourceKind).toBe(
      "summaries",
    );

    await setAskHead("t", "c1", "a1");
    expect(lastCall().path).toBe("/api/v1/ask/conversations/c1/head");

    await getAISettings("t");
    expect(lastCall().path).toBe("/api/v1/settings/ai");

    await patchAISettings("t", { profiles: [], autoSummary: true });
    expect(lastCall().init.method).toBe("PATCH");
    expect(JSON.parse(lastCall().init.body as string).autoSummary).toBe(true);

    await testAIConnection("t", {
      id: "p1",
      provider: "openai",
      baseUrl: "https://api.openai.com/v1",
      model: "gpt-test",
      temperature: 0.3,
      maxTokens: 1000,
    });
    expect(lastCall().path).toBe("/api/v1/settings/ai:test");

    await listAIModels("t", {
      id: "p1",
      provider: "openai",
      baseUrl: "https://api.openai.com/v1",
    });
    expect(lastCall().path).toBe("/api/v1/settings/ai:models");

    await getMe("t");
    expect(lastCall().path).toBe("/api/v1/auth/me");

    await signOut();
    expect(lastCall().path).toBe("/api/v1/auth/signout");

    await initializeAccount({
      username: "a",
      displayName: "A",
      password: "p",
    });
    expect(lastCall().path).toBe("/api/v1/auth/initialize");
  });
});
