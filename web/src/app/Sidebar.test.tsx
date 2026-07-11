import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { Sidebar } from "./Sidebar";

const { hasUnsavedChangesMock } = vi.hoisted(() => ({
  hasUnsavedChangesMock: vi.fn(),
}));

vi.mock("../features/ask/AskContext", () => ({
  useAsk: () => ({
    conversations: [],
    activeId: "",
    startNew: vi.fn(),
  }),
}));

vi.mock("../components/UnsavedNavigationGuard", () => ({
  hasUnsavedChanges: hasUnsavedChangesMock,
}));

const account = {
  id: "a1",
  username: "felix",
  displayName: "Felix",
  createdAt: "2026-06-27T08:00:00Z",
  updatedAt: "2026-06-27T08:00:00Z",
};

describe("Sidebar", () => {
  beforeEach(() => {
    hasUnsavedChangesMock.mockReturnValue(false);
    document.body.style.overflow = "";
  });

  it("anchors the account menu to the available footer width", () => {
    render(
      <MemoryRouter>
        <Sidebar account={account} onSignOut={vi.fn()} />
      </MemoryRouter>,
    );

    const details = screen.getByText("Felix").closest("details");
    expect(details).toHaveClass("flex-1");
  });

  it("signs out immediately when there are no unsaved changes", async () => {
    const user = userEvent.setup();
    const onSignOut = vi.fn();
    render(
      <MemoryRouter>
        <Sidebar account={account} onSignOut={onSignOut} />
      </MemoryRouter>,
    );

    await user.click(screen.getByText("Felix"));
    await user.click(screen.getByRole("button", { name: "退出登录" }));
    expect(onSignOut).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });

  it("confirms sign-out while unsaved changes are active", async () => {
    const user = userEvent.setup();
    const onSignOut = vi.fn();
    hasUnsavedChangesMock.mockReturnValue(true);
    render(
      <MemoryRouter>
        <Sidebar account={account} onSignOut={onSignOut} />
      </MemoryRouter>,
    );

    await user.click(screen.getByText("Felix"));
    const signOut = screen.getByRole("button", { name: "退出登录" });
    await user.click(signOut);

    const dialog = screen.getByRole("alertdialog", {
      name: "仍要退出登录？",
    });
    expect(dialog.closest("aside")).toBeNull();
    expect(onSignOut).not.toHaveBeenCalled();
    expect(document.body).toHaveStyle({ overflow: "hidden" });
    expect(
      within(dialog).getByRole("button", { name: "继续编辑" }),
    ).toHaveFocus();
    await user.tab();
    expect(
      within(dialog).getByRole("button", { name: "仍然退出" }),
    ).toHaveFocus();
    await user.tab();
    expect(
      within(dialog).getByRole("button", { name: "继续编辑" }),
    ).toHaveFocus();

    await user.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByRole("alertdialog")).toBeNull());
    expect(signOut).toHaveFocus();
    expect(document.body).not.toHaveStyle({ overflow: "hidden" });

    await user.click(signOut);
    await user.click(screen.getByRole("button", { name: "仍然退出" }));
    expect(onSignOut).toHaveBeenCalledTimes(1);
  });
});
