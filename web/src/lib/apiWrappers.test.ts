import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Memo } from "./api";
import {
  createAskConversation,
  createAskMessage,
  createMemo,
  deleteMemo,
  generateMemoSummary,
  getAISettings,
  getAskConversation,
  getMe,
  initializeAccount,
  listAIModels,
  listAskConversations,
  listAskMessages,
  listMemos,
  patchAISettings,
  searchMemos,
  setAIAutoSummary,
  setAskConversationArchived,
  setAskHead,
  setMemoArchived,
  setMemoFavorited,
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
  favoritedAt: null,
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

    await listMemos("t", 10, "next", {
      archived: false,
      favorited: false,
    });
    expect(lastCall().path).toBe(
      "/api/v1/memos?limit=10&cursor=next&archived=false&favorited=false",
    );

    await searchMemos("t", "爬 山", 5);
    expect(lastCall().path).toBe(
      "/api/v1/memos?query=%E7%88%AC+%E5%B1%B1&limit=5",
    );

    await searchMemos("t", "归档", 5, {
      archived: true,
      favorited: false,
    });
    expect(lastCall().path).toBe(
      "/api/v1/memos?query=%E5%BD%92%E6%A1%A3&limit=5&archived=true&favorited=false",
    );

    await searchMemos("t", "收藏", 5, { favorited: true });
    expect(lastCall().path).toBe(
      "/api/v1/memos?query=%E6%94%B6%E8%97%8F&limit=5&favorited=true",
    );

    await createMemo("t", { content: "hi", entryDate: "2026-06-27" });
    expect(lastCall().init.method).toBe("POST");

    await updateMemo("t", memo, { content: "new" });
    const upd = lastCall();
    expect(upd.path).toBe("/api/v1/memos/m1");
    expect(JSON.parse(upd.init.body as string).expectedVersion).toBe(3);

    await setMemoFavorited("t", memo, true);
    expect(lastCall().path).toBe("/api/v1/memos/m1:setFavorited");
    expect(JSON.parse(lastCall().init.body as string)).toEqual({
      expectedVersion: 3,
      favorited: true,
    });
    await setMemoFavorited("t", memo, false);
    expect(JSON.parse(lastCall().init.body as string)).toEqual({
      expectedVersion: 3,
      favorited: false,
    });
    await setMemoArchived("t", memo, true);
    expect(lastCall().path).toBe("/api/v1/memos/m1:setArchived");
    expect(JSON.parse(lastCall().init.body as string)).toEqual({
      expectedVersion: 3,
      archived: true,
    });
    await setMemoArchived("t", memo, false);
    expect(JSON.parse(lastCall().init.body as string)).toEqual({
      expectedVersion: 3,
      archived: false,
    });

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

    const searchController = new AbortController();
    await listAskConversations(
      "t",
      { query: "  睡眠  ", archived: true },
      searchController.signal,
    );
    expect(lastCall().path).toBe(
      "/api/v1/ask/conversations?limit=50&query=%E7%9D%A1%E7%9C%A0&archived=true",
    );
    expect(lastCall().init.signal).toBe(searchController.signal);

    await getAskConversation("t", "c/1", searchController.signal);
    expect(lastCall().path).toBe("/api/v1/ask/conversations/c%2F1");
    expect(lastCall().init.signal).toBe(searchController.signal);

    await setAskConversationArchived("t", "c/1", true);
    expect(lastCall().path).toBe("/api/v1/ask/conversations/c%2F1:setArchived");
    expect(lastCall().init.method).toBe("POST");
    expect(JSON.parse(lastCall().init.body as string)).toEqual({
      archived: true,
    });

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

    await patchAISettings("t", { profiles: [] });
    expect(lastCall().init.method).toBe("PATCH");
    expect(JSON.parse(lastCall().init.body as string)).toEqual({
      profiles: [],
    });

    await setAIAutoSummary("t", false);
    expect(lastCall().path).toBe("/api/v1/settings/ai:setAutoSummary");
    expect(lastCall().init.method).toBe("POST");
    expect(JSON.parse(lastCall().init.body as string)).toEqual({
      autoSummary: false,
    });

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
