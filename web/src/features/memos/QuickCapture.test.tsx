import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { hasUnsavedChanges } from "../../components/UnsavedNavigationGuard";
import { QuickCapture } from "./QuickCapture";

function renderQuickCapture(onCapture = vi.fn().mockResolvedValue(undefined)) {
  const user = userEvent.setup();
  const view = render(
    <MemoryRouter>
      <QuickCapture onCapture={onCapture} />
    </MemoryRouter>,
  );
  return { user, onCapture, ...view };
}

describe("QuickCapture", () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it("opens the dialog from the floating button and exposes dialog semantics", async () => {
    const { user } = renderQuickCapture();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "速记" }));
    const dialog = screen.getByRole("dialog", { name: "速记" });
    expect(dialog).toHaveAttribute("aria-modal", "true");
  });

  it("closes on Escape", async () => {
    const { user } = renderQuickCapture();
    await user.click(screen.getByRole("button", { name: "速记" }));
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    await user.keyboard("{Escape}");
    await waitFor(() =>
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
    );
  });

  it("validates empty input and saves trimmed content", async () => {
    const { user, onCapture } = renderQuickCapture();
    await user.click(screen.getByRole("button", { name: "速记" }));

    await user.click(screen.getByRole("button", { name: "保存" }));
    expect(screen.getByText("想记录什么？")).toBeInTheDocument();
    expect(onCapture).not.toHaveBeenCalled();

    await user.type(screen.getByPlaceholderText("想记录什么？"), "新的一条");
    await user.click(screen.getByRole("button", { name: "保存" }));
    await waitFor(() => expect(onCapture).toHaveBeenCalledWith("新的一条"));
  });

  it("ignores repeated save shortcuts while a capture is pending", async () => {
    let finishCapture: (() => void) | undefined;
    const onCapture = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          finishCapture = resolve;
        }),
    );
    const { user } = renderQuickCapture(onCapture);
    await user.click(screen.getByRole("button", { name: "速记" }));
    await user.type(
      screen.getByPlaceholderText("想记录什么？"),
      "  只保存一次  ",
    );

    await user.keyboard("{Control>}{Enter}{/Control}");
    await user.keyboard("{Control>}{Enter}{/Control}");

    expect(onCapture).toHaveBeenCalledTimes(1);
    expect(onCapture).toHaveBeenCalledWith("只保存一次");
    expect(screen.getByPlaceholderText("想记录什么？")).toBeDisabled();
    const trigger = screen.getByRole("button", { name: "速记" });
    expect(trigger).toBeDisabled();
    await user.click(trigger);
    expect(screen.getByRole("dialog", { name: "速记" })).toBeInTheDocument();
    finishCapture?.();
    await waitFor(() =>
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
    );
  });

  it("restores its global draft after remounting", async () => {
    const first = renderQuickCapture();
    await first.user.click(screen.getByRole("button", { name: "速记" }));
    await first.user.type(
      screen.getByPlaceholderText("想记录什么？"),
      "刷新后继续写",
    );
    await waitFor(() =>
      expect(window.localStorage.getItem("sillage.quick-capture-draft")).toBe(
        "刷新后继续写",
      ),
    );
    first.unmount();

    const second = renderQuickCapture();
    await second.user.click(screen.getByRole("button", { name: "速记" }));
    expect(screen.getByPlaceholderText("想记录什么？")).toHaveValue(
      "刷新后继续写",
    );
  });

  it("registers a non-empty draft for unload and clears it only after save", async () => {
    const { user } = renderQuickCapture();
    await user.click(screen.getByRole("button", { name: "速记" }));
    await user.type(screen.getByPlaceholderText("想记录什么？"), "待保存速记");

    await waitFor(() => expect(hasUnsavedChanges()).toBe(true));
    const dirtyUnload = new Event("beforeunload", { cancelable: true });
    expect(window.dispatchEvent(dirtyUnload)).toBe(false);
    expect(window.localStorage.getItem("sillage.quick-capture-draft")).toBe(
      "待保存速记",
    );

    await user.click(screen.getByRole("button", { name: "保存" }));
    await waitFor(() => expect(hasUnsavedChanges()).toBe(false));
    expect(
      window.localStorage.getItem("sillage.quick-capture-draft"),
    ).toBeNull();
    const savedUnload = new Event("beforeunload", { cancelable: true });
    expect(window.dispatchEvent(savedUnload)).toBe(true);
  });
});
