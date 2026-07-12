import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { I18nProvider } from "../../i18n/I18nProvider";
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
  window.localStorage.clear();
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

  it("emits a toast for every repeated initialization failure", async () => {
    const user = userEvent.setup();
    vi.mocked(initializeAccount).mockRejectedValue(new Error("账号创建失败"));
    render(
      <I18nProvider>
        <MemoryRouter>
          <InitializePage onDone={vi.fn()} />
        </MemoryRouter>
      </I18nProvider>,
    );
    await user.type(screen.getByLabelText("账号"), "felix");
    await user.type(screen.getByLabelText("密码"), "secret-pass");

    await user.click(screen.getByRole("button", { name: "创建并进入" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("账号创建失败");

    await user.click(screen.getByRole("button", { name: "创建并进入" }));
    await waitFor(() => expect(initializeAccount).toHaveBeenCalledTimes(2));
    await user.click(screen.getByRole("button", { name: "关闭通知" }));

    expect(screen.getByRole("alert")).toHaveTextContent("账号创建失败");
  });
});

describe("LoginPage", () => {
  it("shows an error when sign-in fails", async () => {
    const user = userEvent.setup();
    vi.mocked(signIn).mockRejectedValue(new Error("用户名或密码错误"));
    render(
      <I18nProvider>
        <MemoryRouter>
          <LoginPage onDone={vi.fn()} />
        </MemoryRouter>
      </I18nProvider>,
    );
    await user.type(screen.getByLabelText("账号"), "felix");
    await user.type(screen.getByLabelText("密码"), "wrong");
    await user.click(screen.getByRole("button", { name: "登录" }));
    expect(await screen.findByText("用户名或密码错误")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "English" }));

    expect(screen.getByLabelText("Username")).toHaveValue("felix");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.queryByText("用户名或密码错误")).not.toBeInTheDocument();
  });

  it("emits a toast for every repeated sign-in failure", async () => {
    const user = userEvent.setup();
    vi.mocked(signIn).mockRejectedValue(new Error("用户名或密码错误"));
    render(
      <I18nProvider>
        <MemoryRouter>
          <LoginPage onDone={vi.fn()} />
        </MemoryRouter>
      </I18nProvider>,
    );
    await user.type(screen.getByLabelText("账号"), "felix");
    await user.type(screen.getByLabelText("密码"), "wrong");

    await user.click(screen.getByRole("button", { name: "登录" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "用户名或密码错误",
    );

    await user.click(screen.getByRole("button", { name: "登录" }));
    await waitFor(() => expect(signIn).toHaveBeenCalledTimes(2));
    await user.click(screen.getByRole("button", { name: "关闭通知" }));

    expect(screen.getByRole("alert")).toHaveTextContent("用户名或密码错误");
  });
});
