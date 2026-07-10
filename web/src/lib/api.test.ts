import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, getBootstrap, listMemos, signIn } from "./api";

// auth side-effects are observed via these spies.
const clearAccessToken = vi.fn();
const setAccessToken = vi.fn();
vi.mock("./auth", () => ({
  clearAccessToken: () => clearAccessToken(),
  setAccessToken: (t: string) => setAccessToken(t),
  getAccessToken: () => "",
}));

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

beforeEach(() => {
  clearAccessToken.mockClear();
  setAccessToken.mockClear();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("request 401 refresh-and-retry", () => {
  it("refreshes once on 401 then retries the original request with the new token", async () => {
    const fetchMock = vi
      .fn()
      // 1. original request -> 401
      .mockResolvedValueOnce(new Response("", { status: 401 }))
      // 2. refresh -> new token
      .mockResolvedValueOnce(jsonResponse({ accessToken: "fresh" }))
      // 3. retried original -> ok
      .mockResolvedValueOnce(jsonResponse({ memos: [] }));
    vi.stubGlobal("fetch", fetchMock);

    const res = await listMemos("stale");
    expect(res).toEqual({ memos: [] });
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(setAccessToken).toHaveBeenCalledWith("fresh");

    // The retry carries the refreshed bearer token.
    const retryInit = fetchMock.mock.calls[2][1] as RequestInit;
    const headers = new Headers(retryInit.headers);
    expect(headers.get("Authorization")).toBe("Bearer fresh");
  });

  it("clears the token and throws when refresh fails", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response("", { status: 401 }))
      .mockResolvedValueOnce(new Response("", { status: 401 })); // refresh fails
    vi.stubGlobal("fetch", fetchMock);

    await expect(listMemos("stale")).rejects.toThrow();
    expect(clearAccessToken).toHaveBeenCalled();
  });

  it("does not attempt refresh for pre-auth endpoints", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response("", { status: 401 }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(getBootstrap()).rejects.toThrow();
    // Only the original call; no refresh round-trip.
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("surfaces the server error message envelope", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        jsonResponse({ error: { message: "用户名或密码错误" } }, 400),
      );
    vi.stubGlobal("fetch", fetchMock);

    const request = signIn({ username: "a", password: "b" });
    await expect(request).rejects.toThrow("用户名或密码错误");
    await request.catch((error: unknown) => {
      expect(error).toBeInstanceOf(ApiError);
      expect((error as ApiError).status).toBe(400);
    });
  });
});
