export type Account = {
  id: string;
  username: string;
  displayName: string;
  createdAt: string;
  updatedAt: string;
};

export type AuthResponse = {
  account: Account;
  accessToken: string;
  expiresAt: string;
};

export async function getBootstrap(): Promise<{ initialized: boolean }> {
  return request("/api/v1/auth/bootstrap");
}

export async function initializeAccount(input: {
  username: string;
  displayName: string;
  password: string;
}): Promise<AuthResponse> {
  return request("/api/v1/auth/initialize", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export async function signIn(input: {
  username: string;
  password: string;
}): Promise<AuthResponse> {
  return request("/api/v1/auth/signin", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export async function getMe(
  accessToken: string,
): Promise<{ account: Account }> {
  return request("/api/v1/auth/me", {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  const res = await fetch(path, {
    ...init,
    headers,
    credentials: "include",
  });
  if (!res.ok) {
    const payload = await res.json().catch(() => null);
    const message = payload?.error?.message ?? "请求失败";
    throw new Error(message);
  }
  return res.json() as Promise<T>;
}
