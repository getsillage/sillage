type TokenListener = (token: string | null) => void;

// Access tokens intentionally remain in memory. The server-issued refresh
// cookie is HttpOnly and restores a session after reload without exposing a
// reusable bearer token to Web Storage or other script-readable persistence.
let currentToken: string | null = null;
const listeners = new Set<TokenListener>();

function notify(token: string | null): void {
  for (const fn of listeners) {
    fn(token);
  }
}

export function getAccessToken(): string | null {
  return currentToken;
}

export function setAccessToken(token: string): void {
  currentToken = token;
  notify(token);
}

export function clearAccessToken(): void {
  currentToken = null;
  notify(null);
}

// Subscribers are notified when a background refresh or sign-out changes the
// token. Each call registers an independent listener so multiple consumers can
// coexist without clobbering one another.
export function subscribeAccessToken(fn: TokenListener): () => void {
  listeners.add(fn);
  return () => {
    listeners.delete(fn);
  };
}
