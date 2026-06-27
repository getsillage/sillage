import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Account } from "../lib/api";
import { clearAccessToken, setAccessToken } from "../lib/auth";
import { App } from "./App";
import { InitializePage, LoginPage } from "./AuthPages";

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

import {
  getBootstrap,
  getMe,
  initializeAccount,
  listMemos,
  signIn,
} from "../lib/api";

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

describe("InitializePage", () => {
  it("submits the new account and calls onDone", async () => {
    const user = userEvent.setup();
    const onDone = vi.fn();
    vi.mocked(initializeAccount).mockResolvedValue({
      account,
      accessToken: "tok",
      expiresAt: "later",
    });
    render(
      <MemoryRouter>
        <InitializePage onDone={onDone} />
      </MemoryRouter>,
    );
    await user.type(screen.getByLabelText("账号"), "felix");
    await user.type(screen.getByLabelText("密码"), "secret-pass");
    await user.click(screen.getByRole("button", { name: "创建并进入" }));
    await waitFor(() => expect(onDone).toHaveBeenCalledWith("tok", account));
  });
});

describe("LoginPage", () => {
  it("shows an error when sign-in fails", async () => {
    const user = userEvent.setup();
    vi.mocked(signIn).mockRejectedValue(new Error("用户名或密码错误"));
    render(
      <MemoryRouter>
        <LoginPage onDone={vi.fn()} />
      </MemoryRouter>,
    );
    await user.type(screen.getByLabelText("账号"), "felix");
    await user.type(screen.getByLabelText("密码"), "wrong");
    await user.click(screen.getByRole("button", { name: "登录" }));
    expect(await screen.findByText("用户名或密码错误")).toBeInTheDocument();
  });
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
    // Sidebar wordmark + home composer prove the authed shell mounted.
    expect(await screen.findByText("今天想记录什么？")).toBeInTheDocument();
    expect(screen.getAllByText("Sillage").length).toBeGreaterThan(0);
  });
});
