import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Markdown } from "./Markdown";

describe("Markdown", () => {
  it("renders markdown and opens links in a new tab", () => {
    render(<Markdown content={"# 标题\n\n[去看看](https://example.com)"} />);
    expect(screen.getByRole("heading", { name: "标题" })).toBeInTheDocument();
    const link = screen.getByRole("link", { name: "去看看" });
    expect(link).toHaveAttribute("target", "_blank");
    expect(link).toHaveAttribute("rel", "noopener noreferrer");
  });
});
