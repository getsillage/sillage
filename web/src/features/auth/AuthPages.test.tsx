import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Account } from "../../lib/api";
import { InitializePage, LoginPage } from "./AuthPages";

vi.mock("../../lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../lib/api")>();
  return {
    ...actual,
    initializeAccount: vi.fn(),
    signIn: vi.fn(),
  };
});

import { initializeAccount, signIn } from "../../lib/api";

const account: Account = {
  id: "a1",
  username: "felix",
  displayName: "Felix",
  createdAt: "1",
  updatedAt: "1",
};

beforeEach(() => {
  vi.clearAllMocks();
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
