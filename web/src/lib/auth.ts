const ACCESS_TOKEN_KEY = "sillage.accessToken";

type TokenListener = (token: string | null) => void;

let currentToken: string | null = sessionStorage.getItem(ACCESS_TOKEN_KEY);
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
  sessionStorage.setItem(ACCESS_TOKEN_KEY, token);
  notify(token);
}

export function clearAccessToken(): void {
  currentToken = null;
  sessionStorage.removeItem(ACCESS_TOKEN_KEY);
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
