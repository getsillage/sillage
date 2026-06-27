import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { QuickCapture } from "./QuickCapture";

function renderQuickCapture(onCapture = vi.fn().mockResolvedValue(undefined)) {
  const user = userEvent.setup();
  render(
    <MemoryRouter>
      <QuickCapture onCapture={onCapture} />
    </MemoryRouter>,
  );
  return { user, onCapture };
}

describe("QuickCapture", () => {
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
});
