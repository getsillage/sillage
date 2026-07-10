import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AppShell } from "./AppShell";
import { useUnsavedChangesRegistration } from "./UnsavedNavigationGuard";

vi.mock("../state/AskContext", () => ({
  useAsk: () => ({
    conversations: [],
    activeId: "",
    startNew: vi.fn(),
  }),
}));

vi.mock("../state/MemosContext", () => ({
  useMemos: () => ({ create: vi.fn() }),
}));

const account = {
  id: "a1",
  username: "felix",
  displayName: "Felix",
  createdAt: "2026-06-27T08:00:00Z",
  updatedAt: "2026-06-27T08:00:00Z",
};

function DirtyPage() {
  useUnsavedChangesRegistration(true);
  return <main>有未保存内容</main>;
}

function renderShell({ dirty = false }: { dirty?: boolean } = {}) {
  const user = userEvent.setup();
  render(
    <MemoryRouter initialEntries={["/"]}>
      <Routes>
        <Route element={<AppShell account={account} onSignOut={vi.fn()} />}>
          <Route
            index
            element={dirty ? <DirtyPage /> : <main>记录页面</main>}
          />
          <Route path="timeline" element={<main>历史页面</main>} />
          <Route path="ask" element={<main>问答页面</main>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
  return user;
}

beforeEach(() => {
  window.localStorage.clear();
  document.body.style.overflow = "";
});

describe("AppShell mobile navigation", () => {
  it("focuses the close button, traps focus, locks scroll, and restores focus", async () => {
    const user = renderShell();
    const menuButton = screen.getByRole("button", { name: "打开导航" });

    await user.click(menuButton);

    const dialog = screen.getByRole("dialog", { name: "导航" });
    const closeButton = within(dialog).getByRole("button", {
      name: "关闭导航",
    });
    await waitFor(() => expect(closeButton).toHaveFocus());
    expect(document.body).toHaveStyle({ overflow: "hidden" });
    expect(within(dialog).getByRole("complementary")).toHaveClass(
      "bg-gray-100",
    );

    const firstLink = within(dialog).getByRole("link", { name: /Sillage/ });
    const themeButton = within(dialog).getByRole("button", {
      name: /切换主题/,
    });
    firstLink.focus();
    await user.tab({ shift: true });
    expect(themeButton).toHaveFocus();
    await user.tab();
    expect(firstLink).toHaveFocus();

    await user.click(closeButton);
    await waitFor(() =>
      expect(
        screen.queryByRole("dialog", { name: "导航" }),
      ).not.toBeInTheDocument(),
    );
    expect(document.body).not.toHaveStyle({ overflow: "hidden" });
    expect(menuButton).toHaveFocus();
  });

  it("closes on Escape and backdrop click", async () => {
    const user = renderShell();
    const menuButton = screen.getByRole("button", { name: "打开导航" });

    await user.click(menuButton);
    await user.keyboard("{Escape}");
    await waitFor(() => expect(menuButton).toHaveFocus());

    await user.click(menuButton);
    await user.click(screen.getByRole("button", { name: "关闭导航遮罩" }));
    await waitFor(() => expect(menuButton).toHaveFocus());
  });

  it("closes on navigation and leaves a single main landmark", async () => {
    const user = renderShell();
    expect(screen.getAllByRole("main")).toHaveLength(1);

    const menuButton = screen.getByRole("button", { name: "打开导航" });
    await user.click(menuButton);
    await user.click(
      within(screen.getByRole("dialog", { name: "导航" })).getByRole("link", {
        name: "历史",
      }),
    );

    expect(await screen.findByText("历史页面")).toBeInTheDocument();
    expect(
      screen.queryByRole("dialog", { name: "导航" }),
    ).not.toBeInTheDocument();
    expect(screen.getAllByRole("main")).toHaveLength(1);
    expect(menuButton).toHaveFocus();
  });

  it("keeps a quick-capture draft mounted across the Ask route", async () => {
    const user = renderShell();
    await user.click(screen.getByRole("button", { name: "速记" }));
    const quickDialog = screen.getByRole("dialog", { name: "速记" });
    await user.type(
      within(quickDialog).getByPlaceholderText("想记录什么？"),
      "跨页面速记",
    );
    await user.click(
      within(quickDialog).getByRole("button", { name: "关闭速记" }),
    );

    await user.click(screen.getByRole("link", { name: "新问答" }));
    expect(await screen.findByText("问答页面")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "速记" })).toBeNull();

    await user.click(screen.getByRole("link", { name: "记录" }));
    await user.click(screen.getByRole("button", { name: "速记" }));
    expect(screen.getByPlaceholderText("想记录什么？")).toHaveValue(
      "跨页面速记",
    );
  });

  it("dismisses sign-out confirmation without closing the mobile drawer", async () => {
    const user = renderShell({ dirty: true });
    await user.click(screen.getByRole("button", { name: "打开导航" }));
    const drawer = screen.getByRole("dialog", { name: "导航" });
    await user.click(within(drawer).getByText("Felix"));
    const signOut = within(drawer).getByRole("button", { name: "退出登录" });
    await user.click(signOut);
    expect(screen.getByRole("alertdialog")).toBeInTheDocument();

    await user.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByRole("alertdialog")).toBeNull());
    expect(screen.getByRole("dialog", { name: "导航" })).toBeInTheDocument();
    expect(signOut).toHaveFocus();
  });
});
