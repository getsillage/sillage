import { env } from "cloudflare:test";
import { describe, expect, it } from "vitest";
import {
  clearLoginAttempts,
  isLoginRateLimited,
  MAX_LOGIN_ATTEMPTS,
  recordFailedLogin,
} from "../app/lib/auth/rate-limit";

function reqFrom(ip: string | null): Request {
  const headers = new Headers();
  if (ip !== null) {
    headers.set("CF-Connecting-IP", ip);
  }
  return new Request("https://diary.example/login", { method: "POST", headers });
}

describe("login rate limiting", () => {
  it("allows attempts below the threshold", async () => {
    const req = reqFrom("10.0.0.1");
    for (let i = 0; i < MAX_LOGIN_ATTEMPTS - 1; i++) {
      await recordFailedLogin(env, req);
    }
    expect(await isLoginRateLimited(env, req)).toBe(false);
  });

  it("blocks once failures reach the threshold", async () => {
    const req = reqFrom("10.0.0.2");
    for (let i = 0; i < MAX_LOGIN_ATTEMPTS; i++) {
      await recordFailedLogin(env, req);
    }
    expect(await isLoginRateLimited(env, req)).toBe(true);
  });

  it("clears the counter after a successful login", async () => {
    const req = reqFrom("10.0.0.3");
    for (let i = 0; i < MAX_LOGIN_ATTEMPTS; i++) {
      await recordFailedLogin(env, req);
    }
    await clearLoginAttempts(env, req);
    expect(await isLoginRateLimited(env, req)).toBe(false);
  });

  it("tracks clients independently by IP", async () => {
    const attacker = reqFrom("10.0.0.4");
    for (let i = 0; i < MAX_LOGIN_ATTEMPTS; i++) {
      await recordFailedLogin(env, attacker);
    }
    expect(await isLoginRateLimited(env, reqFrom("10.0.0.5"))).toBe(false);
  });

  it("buckets requests with no client IP together under a shared key", async () => {
    const anonymous = reqFrom(null);
    for (let i = 0; i < MAX_LOGIN_ATTEMPTS; i++) {
      await recordFailedLogin(env, anonymous);
    }
    expect(await isLoginRateLimited(env, reqFrom(null))).toBe(true);
  });
});
