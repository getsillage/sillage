/**
 * Brute-force protection for the single password that guards Sillage.
 * Failed login attempts are counted per client IP in the SESSIONS KV namespace
 * with a rolling expiry, so an attacker is locked out after too many misses
 * while a legitimate user's successful login clears the counter immediately.
 */

const WINDOW_SECONDS = 15 * 60;
export const MAX_LOGIN_ATTEMPTS = 10;

function clientKey(request: Request): string {
  const ip = request.headers.get("CF-Connecting-IP") ?? "unknown";
  return `rl:login:${ip}`;
}

function parseCount(raw: string | null): number {
  if (!raw) {
    return 0;
  }
  const value = Number.parseInt(raw, 10);
  return Number.isNaN(value) ? 0 : value;
}

/** True when the client has reached the failed-attempt ceiling for the window. */
export async function isLoginRateLimited(env: Env, request: Request): Promise<boolean> {
  const count = parseCount(await env.SESSIONS.get(clientKey(request)));
  return count >= MAX_LOGIN_ATTEMPTS;
}

/** Records one failed attempt, (re)setting the lockout window. */
export async function recordFailedLogin(env: Env, request: Request): Promise<void> {
  const key = clientKey(request);
  const count = parseCount(await env.SESSIONS.get(key));
  await env.SESSIONS.put(key, String(count + 1), { expirationTtl: WINDOW_SECONDS });
}

/** Clears the failed-attempt counter after a successful login. */
export async function clearLoginAttempts(env: Env, request: Request): Promise<void> {
  await env.SESSIONS.delete(clientKey(request));
}
