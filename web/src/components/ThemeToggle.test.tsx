import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { ThemeToggle } from "./ThemeToggle";

describe("ThemeToggle", () => {
  beforeEach(() => {
    window.localStorage.clear();
    document.documentElement.classList.remove("dark");
    document.documentElement.removeAttribute("data-theme");
    document.documentElement.style.removeProperty("color-scheme");
  });

  it("toggles the document theme on click", async () => {
    const user = userEvent.setup();
    render(<ThemeToggle />);
    const button = screen.getByRole("button");
    expect(document.documentElement.classList.contains("dark")).toBe(false);
    await user.click(button);
    expect(document.documentElement.classList.contains("dark")).toBe(true);
    await user.click(button);
    expect(document.documentElement.classList.contains("dark")).toBe(false);
  });

  it("keeps every mounted toggle's icon and label synchronized", async () => {
    const user = userEvent.setup();
    render(
      <>
        <ThemeToggle compact />
        <ThemeToggle />
      </>,
    );

    const lightButtons = screen.getAllByRole("button", {
      name: "切换主题，当前为浅色",
    });
    expect(lightButtons).toHaveLength(2);
    for (const button of lightButtons) {
      expect(button.querySelector(".lucide-sun")).toBeInTheDocument();
      expect(button.querySelector(".lucide-moon")).not.toBeInTheDocument();
    }

    await user.click(lightButtons[0]);

    const darkButtons = screen.getAllByRole("button", {
      name: "切换主题，当前为深色",
    });
    expect(darkButtons).toHaveLength(2);
    for (const button of darkButtons) {
      expect(button.querySelector(".lucide-moon")).toBeInTheDocument();
      expect(button.querySelector(".lucide-sun")).not.toBeInTheDocument();
    }
  });
});
