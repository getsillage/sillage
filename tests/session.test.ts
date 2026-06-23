import { env } from "cloudflare:test";
import { describe, expect, it } from "vitest";
import {
  createUserSession,
  isAuthenticated,
  logout,
  requireSession,
} from "../app/lib/auth/session";

function cookieHeaderFrom(response: Response): string {
  const setCookie = response.headers.get("Set-Cookie");
  if (!setCookie) {
    throw new Error("expected a Set-Cookie header");
  }
  // Keep only the `name=value` pair for use as a request Cookie header.
  return setCookie.split(";")[0];
}

function requestWithCookie(cookie: string): Request {
  return new Request("https://example.com/protected", {
    headers: { Cookie: cookie },
  });
}

describe("KV-backed sessions", () => {
  it("issues a session cookie on login and authenticates with it", async () => {
    const response = await createUserSession(env, "/");
    expect(response.status).toBe(302);

    const cookie = cookieHeaderFrom(response);
    expect(await isAuthenticated(requestWithCookie(cookie), env)).toBe(true);
  });

  it("treats requests without a session as unauthenticated", async () => {
    const request = new Request("https://example.com/protected");
    expect(await isAuthenticated(request, env)).toBe(false);
  });

  it("requireSession passes when authenticated", async () => {
    const cookie = cookieHeaderFrom(await createUserSession(env, "/"));
    await expect(requireSession(requestWithCookie(cookie), env)).resolves.toBeUndefined();
  });

  it("requireSession redirects to /login when unauthenticated", async () => {
    const request = new Request("https://example.com/calendar?m=6");
    try {
      await requireSession(request, env);
      throw new Error("expected requireSession to throw a redirect");
    } catch (thrown) {
      expect(thrown).toBeInstanceOf(Response);
      const response = thrown as Response;
      expect(response.status).toBe(302);
      const location = response.headers.get("Location") ?? "";
      expect(location).toContain("/login");
      expect(location).toContain("redirectTo");
    }
  });

  it("logout clears the session so the cookie no longer authenticates", async () => {
    const loginCookie = cookieHeaderFrom(await createUserSession(env, "/"));
    const logoutResponse = await logout(requestWithCookie(loginCookie), env);
    const clearedCookie = cookieHeaderFrom(logoutResponse);
    expect(await isAuthenticated(requestWithCookie(clearedCookie), env)).toBe(false);
  });
});
