import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { StrictMode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  hasUnsavedChanges,
  UnsavedNavigationGuard,
} from "./UnsavedNavigationGuard";

const { useBlockerMock } = vi.hoisted(() => ({
  useBlockerMock: vi.fn(),
}));

vi.mock("react-router-dom", () => ({
  useBlocker: useBlockerMock,
}));

describe("UnsavedNavigationGuard", () => {
  const proceed = vi.fn();
  const reset = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    useBlockerMock.mockReturnValue({
      state: "blocked",
      location: { pathname: "/other" },
      proceed,
      reset,
    });
  });

  it("traps focus and resets the blocked navigation with Escape", async () => {
    const user = userEvent.setup();
    render(
      <UnsavedNavigationGuard
        when
        title="设置尚未保存"
        description="离开后会丢失。"
      />,
    );

    const stay = screen.getByRole("button", { name: "继续编辑" });
    const leave = screen.getByRole("button", { name: "离开此页" });
    expect(stay).toHaveFocus();
    await user.tab();
    expect(leave).toHaveFocus();
    await user.tab();
    expect(stay).toHaveFocus();
    await user.keyboard("{Escape}");
    expect(reset).toHaveBeenCalledTimes(1);
  });

  it("proceeds only after the destructive action is confirmed", async () => {
    const user = userEvent.setup();
    render(
      <UnsavedNavigationGuard
        when
        title="记录尚未保存"
        description="本地草稿会保留。"
      />,
    );

    await user.click(screen.getByRole("button", { name: "离开此页" }));
    expect(proceed).toHaveBeenCalledTimes(1);
    expect(reset).not.toHaveBeenCalled();
  });

  it("registers active guards without leaking through StrictMode cleanup", async () => {
    useBlockerMock.mockReturnValue({
      state: "unblocked",
      location: undefined,
      proceed,
      reset,
    });
    const view = render(
      <StrictMode>
        <UnsavedNavigationGuard
          when
          title="设置尚未保存"
          description="离开后会丢失。"
        />
      </StrictMode>,
    );

    await waitFor(() => expect(hasUnsavedChanges()).toBe(true));
    view.unmount();
    expect(hasUnsavedChanges()).toBe(false);
  });
});
