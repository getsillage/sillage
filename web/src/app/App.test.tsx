import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Account } from "../lib/api";
import { clearAccessToken, setAccessToken } from "../lib/auth";
import { App } from "./App";

vi.mock("../lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../lib/api")>();
  return {
    ...actual,
    getBootstrap: vi.fn(),
    getMe: vi.fn(),
    signOut: vi.fn().mockResolvedValue(undefined),
    initializeAccount: vi.fn(),
    signIn: vi.fn(),
    listMemos: vi.fn().mockResolvedValue({ memos: [] }),
    listAskConversations: vi.fn().mockResolvedValue({ conversations: [] }),
  };
});

vi.mock("../components/UnsavedNavigationGuard", () => ({
  UnsavedNavigationGuard: () => null,
  useUnsavedChangesRegistration: () => undefined,
}));

import { getBootstrap, getMe, listMemos } from "../lib/api";

const account: Account = {
  id: "a1",
  username: "felix",
  displayName: "Felix",
  createdAt: "1",
  updatedAt: "1",
};

beforeEach(() => {
  vi.clearAllMocks();
  clearAccessToken();
  vi.mocked(listMemos).mockResolvedValue({ memos: [] });
});

describe("App bootstrap", () => {
  it("routes to initialization on a fresh instance", async () => {
    vi.mocked(getBootstrap).mockResolvedValue({ initialized: false });
    render(
      <MemoryRouter initialEntries={["/"]}>
        <App />
      </MemoryRouter>,
    );
    expect(await screen.findByText("创建唯一账号")).toBeInTheDocument();
  });

  it("routes to login when initialized but unauthenticated", async () => {
    vi.mocked(getBootstrap).mockResolvedValue({ initialized: true });
    vi.mocked(getMe).mockRejectedValue(new Error("unauthenticated"));
    render(
      <MemoryRouter initialEntries={["/"]}>
        <App />
      </MemoryRouter>,
    );
    expect(await screen.findByText("登录 Sillage")).toBeInTheDocument();
  });

  it("renders the app shell when authenticated", async () => {
    setAccessToken("tok");
    vi.mocked(getBootstrap).mockResolvedValue({ initialized: true });
    vi.mocked(getMe).mockResolvedValue({ account });
    render(
      <MemoryRouter initialEntries={["/"]}>
        <App />
      </MemoryRouter>,
    );
    expect(await screen.findByText("今天想记录什么？")).toBeInTheDocument();
    expect(screen.getAllByText("Sillage").length).toBeGreaterThan(0);
  });
});
