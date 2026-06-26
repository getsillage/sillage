const ACCESS_TOKEN_KEY = "sillage.accessToken";

type TokenListener = (token: string | null) => void;

let currentToken: string | null = sessionStorage.getItem(ACCESS_TOKEN_KEY);
let listener: TokenListener | null = null;

export function getAccessToken(): string | null {
  return currentToken;
}

export function setAccessToken(token: string): void {
  currentToken = token;
  sessionStorage.setItem(ACCESS_TOKEN_KEY, token);
  listener?.(token);
}

export function clearAccessToken(): void {
  currentToken = null;
  sessionStorage.removeItem(ACCESS_TOKEN_KEY);
  listener?.(null);
}

// The app root subscribes so token changes from a background refresh or a
// sign-out propagate into React state. A single subscriber is enough.
export function subscribeAccessToken(fn: TokenListener): () => void {
  listener = fn;
  return () => {
    if (listener === fn) {
      listener = null;
    }
  };
}
