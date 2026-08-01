import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { type Account, ApiError } from "../lib/api";
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

  it("signs out only when the session is definitely rejected with 401", async () => {
    setAccessToken("tok");
    vi.mocked(getBootstrap).mockResolvedValue({ initialized: true });
    vi.mocked(getMe).mockRejectedValue(
      new ApiError("unauthenticated", 401, "unauthenticated"),
    );
    render(
      <MemoryRouter initialEntries={["/"]}>
        <App />
      </MemoryRouter>,
    );
    expect(await screen.findByText("登录 Sillage")).toBeInTheDocument();
  });

  it("does not replay bootstrap or drop the session on a token refresh", async () => {
    setAccessToken("tok");
    vi.mocked(getBootstrap).mockResolvedValue({ initialized: true });
    vi.mocked(getMe)
      .mockResolvedValueOnce({ account })
      .mockRejectedValue(new Error("network down"));
    render(
      <MemoryRouter initialEntries={["/"]}>
        <App />
      </MemoryRouter>,
    );
    expect(await screen.findByText("今天想记录什么？")).toBeInTheDocument();
    const callsAfterMount = vi.mocked(getMe).mock.calls.length;

    // A background access-token refresh must not re-run bootstrap/getMe nor
    // kick the signed-in user back to the login page.
    await act(async () => {
      setAccessToken("tok-refreshed");
    });

    expect(screen.getByText("今天想记录什么？")).toBeInTheDocument();
    expect(vi.mocked(getMe).mock.calls.length).toBe(callsAfterMount);
    expect(screen.queryByText("登录 Sillage")).not.toBeInTheDocument();
  });

  it("shows a retry state instead of the login page when bootstrap fails", async () => {
    const user = userEvent.setup();
    vi.mocked(getBootstrap)
      .mockRejectedValueOnce(new Error("offline"))
      .mockResolvedValue({ initialized: true });
    vi.mocked(getMe).mockRejectedValue(
      new ApiError("unauthenticated", 401, "unauthenticated"),
    );
    render(
      <MemoryRouter initialEntries={["/"]}>
        <App />
      </MemoryRouter>,
    );

    expect(
      await screen.findByText("无法连接到 Sillage 服务，请检查网络后重试。"),
    ).toBeInTheDocument();
    expect(screen.queryByText("登录 Sillage")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "重试" }));
    expect(await screen.findByText("登录 Sillage")).toBeInTheDocument();
  });
});
