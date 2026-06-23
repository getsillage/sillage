import { createSessionStorage, redirect } from "react-router";

const SESSION_COOKIE = "__diary_session";
const SESSION_TTL_SECONDS = 60 * 60 * 24 * 30; // 30 days
const KV_MIN_TTL_SECONDS = 60;

interface SessionData {
  authenticated: boolean;
}

function ttlFromExpires(expires?: Date): number {
  if (!expires) {
    return SESSION_TTL_SECONDS;
  }
  const seconds = Math.floor((expires.getTime() - Date.now()) / 1000);
  return Math.max(KV_MIN_TTL_SECONDS, seconds);
}

/**
 * KV-backed session storage: the signed cookie holds only an opaque session id,
 * while the session payload lives in the SESSIONS KV namespace with a TTL.
 */
export function getSessionStorage(env: Env) {
  return createSessionStorage<SessionData>({
    cookie: {
      name: SESSION_COOKIE,
      httpOnly: true,
      secure: true,
      sameSite: "lax",
      path: "/",
      maxAge: SESSION_TTL_SECONDS,
      secrets: [env.SESSION_SECRET],
    },
    async createData(data, expires) {
      const id = crypto.randomUUID();
      await env.SESSIONS.put(id, JSON.stringify(data), {
        expirationTtl: ttlFromExpires(expires),
      });
      return id;
    },
    async readData(id) {
      const raw = await env.SESSIONS.get(id);
      return raw ? (JSON.parse(raw) as SessionData) : null;
    },
    async updateData(id, data, expires) {
      await env.SESSIONS.put(id, JSON.stringify(data), {
        expirationTtl: ttlFromExpires(expires),
      });
    },
    async deleteData(id) {
      await env.SESSIONS.delete(id);
    },
  });
}

/** Creates an authenticated session and returns a redirect with the cookie set. */
export async function createUserSession(env: Env, redirectTo: string): Promise<Response> {
  const storage = getSessionStorage(env);
  const session = await storage.getSession();
  session.set("authenticated", true);
  return redirect(redirectTo, {
    headers: { "Set-Cookie": await storage.commitSession(session) },
  });
}

/** Returns true when the request carries a valid authenticated session. */
export async function isAuthenticated(request: Request, env: Env): Promise<boolean> {
  const storage = getSessionStorage(env);
  const session = await storage.getSession(request.headers.get("Cookie"));
  return session.get("authenticated") === true;
}

/**
 * Guard for loaders/actions: returns when authenticated, otherwise throws a
 * redirect to /login (preserving the originally requested path).
 */
export async function requireSession(request: Request, env: Env): Promise<void> {
  if (await isAuthenticated(request, env)) {
    return;
  }
  const url = new URL(request.url);
  const params = new URLSearchParams({ redirectTo: url.pathname + url.search });
  throw redirect(`/login?${params}`);
}

/** Destroys the session and returns a redirect that clears the cookie. */
export async function logout(request: Request, env: Env, redirectTo = "/login"): Promise<Response> {
  const storage = getSessionStorage(env);
  const session = await storage.getSession(request.headers.get("Cookie"));
  return redirect(redirectTo, {
    headers: { "Set-Cookie": await storage.destroySession(session) },
  });
}
