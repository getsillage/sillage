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

  it("cycles light, dark, then back to following the system", async () => {
    const user = userEvent.setup();
    window.localStorage.setItem("sillage-theme", "light");
    render(<ThemeToggle />);
    const button = screen.getByRole("button", {
      name: "切换主题，当前为浅色",
    });
    expect(document.documentElement.classList.contains("dark")).toBe(false);

    await user.click(button);
    expect(document.documentElement.classList.contains("dark")).toBe(true);
    expect(window.localStorage.getItem("sillage-theme")).toBe("dark");
    expect(
      screen.getByRole("button", { name: "切换主题，当前为深色" }),
    ).toBeInTheDocument();

    await user.click(button);
    // The system preference clears the stored override; the mocked matchMedia
    // reports light, so the effective mode falls back to light.
    expect(window.localStorage.getItem("sillage-theme")).toBeNull();
    expect(document.documentElement.dataset.theme).toBe("system");
    expect(document.documentElement.classList.contains("dark")).toBe(false);
    expect(
      screen.getByRole("button", { name: "切换主题，当前为跟随系统" }),
    ).toBeInTheDocument();

    await user.click(button);
    expect(window.localStorage.getItem("sillage-theme")).toBe("light");
    expect(document.documentElement.classList.contains("dark")).toBe(false);
    expect(
      screen.getByRole("button", { name: "切换主题，当前为浅色" }),
    ).toBeInTheDocument();
  });

  it("starts in system mode without a stored preference", () => {
    render(<ThemeToggle />);
    expect(
      screen.getByRole("button", { name: "切换主题，当前为跟随系统" }),
    ).toBeInTheDocument();
    expect(document.documentElement.dataset.theme).toBe("system");
  });

  it("keeps every mounted toggle's icon and label synchronized", async () => {
    const user = userEvent.setup();
    window.localStorage.setItem("sillage-theme", "light");
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

    await user.click(darkButtons[0]);

    const systemButtons = screen.getAllByRole("button", {
      name: "切换主题，当前为跟随系统",
    });
    expect(systemButtons).toHaveLength(2);
    for (const button of systemButtons) {
      expect(button.querySelector(".lucide-monitor")).toBeInTheDocument();
    }
  });
});
