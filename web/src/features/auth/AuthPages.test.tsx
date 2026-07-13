import { fireEvent, render, screen, waitFor } from "@testing-library/react";
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

  it("keeps an initialization error in the form until the user edits it", async () => {
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
    expect(
      screen.queryByRole("button", { name: "关闭通知" }),
    ).not.toBeInTheDocument();
    expect(screen.getByLabelText("账号")).toHaveValue("felix");
    expect(screen.getByLabelText("密码")).toHaveValue("secret-pass");

    await user.type(screen.getByLabelText("账号"), "2");

    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("locks initialization controls and ignores repeated synchronous submits", async () => {
    const user = userEvent.setup();
    const onDone = vi.fn();
    const response = {
      account,
      accessToken: "tok",
      expiresAt: "later",
    };
    let resolveRequest: (value: typeof response) => void = () => undefined;
    vi.mocked(initializeAccount).mockReturnValue(
      new Promise((resolve) => {
        resolveRequest = resolve;
      }),
    );
    render(
      <I18nProvider>
        <MemoryRouter>
          <InitializePage onDone={onDone} />
        </MemoryRouter>
      </I18nProvider>,
    );
    await user.type(screen.getByLabelText("账号"), "felix");
    await user.type(screen.getByLabelText("密码"), "secret-pass");

    const submit = screen.getByRole("button", { name: "创建并进入" });
    const form = submit.closest("form");
    expect(form).not.toBeNull();
    fireEvent.submit(form as HTMLFormElement);
    fireEvent.submit(form as HTMLFormElement);

    expect(initializeAccount).toHaveBeenCalledTimes(1);
    expect(screen.getByLabelText("账号")).toBeDisabled();
    expect(screen.getByLabelText("密码")).toBeDisabled();
    expect(screen.getByRole("button", { name: "English" })).toBeDisabled();
    resolveRequest(response);
    await waitFor(() => expect(onDone).toHaveBeenCalledWith("tok", account));
  });
});

describe("LoginPage", () => {
  it("keeps and localizes a sign-in error without clearing the inputs", async () => {
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
    expect(screen.getByLabelText("Password")).toHaveValue("wrong");
    expect(screen.getByRole("alert")).toHaveTextContent("Sign-in failed");
    expect(screen.queryByText("用户名或密码错误")).not.toBeInTheDocument();
  });

  it("locks sign-in controls and ignores repeated synchronous submits", async () => {
    const user = userEvent.setup();
    const onDone = vi.fn();
    const response = {
      account,
      accessToken: "tok",
      expiresAt: "later",
    };
    let resolveRequest: (value: typeof response) => void = () => undefined;
    vi.mocked(signIn).mockReturnValue(
      new Promise((resolve) => {
        resolveRequest = resolve;
      }),
    );
    render(
      <I18nProvider>
        <MemoryRouter>
          <LoginPage onDone={onDone} />
        </MemoryRouter>
      </I18nProvider>,
    );
    await user.type(screen.getByLabelText("账号"), "felix");
    await user.type(screen.getByLabelText("密码"), "wrong");

    const submit = screen.getByRole("button", { name: "登录" });
    const form = submit.closest("form");
    expect(form).not.toBeNull();
    fireEvent.submit(form as HTMLFormElement);
    fireEvent.submit(form as HTMLFormElement);

    expect(signIn).toHaveBeenCalledTimes(1);
    expect(screen.getByLabelText("账号")).toBeDisabled();
    expect(screen.getByLabelText("密码")).toBeDisabled();
    expect(screen.getByRole("button", { name: "English" })).toBeDisabled();
    resolveRequest(response);
    await waitFor(() => expect(onDone).toHaveBeenCalledWith("tok", account));
  });
});
