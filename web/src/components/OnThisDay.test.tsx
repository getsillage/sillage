import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";
import type { Memo } from "../lib/api";
import { OnThisDay } from "./OnThisDay";

function memo(overrides: Partial<Memo> = {}): Memo {
  return {
    id: "m1",
    content: "去年的记录内容",
    entryDate: "2025-06-27",
    version: 1,
    favoritedAt: null,
    archivedAt: null,
    createdAt: "2025-06-27T08:00:00Z",
    updatedAt: "2025-06-27T08:00:00Z",
    deletedAt: null,
    ...overrides,
  };
}

function renderOnThisDay(entries: Memo[]) {
  return render(
    <MemoryRouter>
      <OnThisDay entries={entries} today="2026-06-27" />
    </MemoryRouter>,
  );
}

describe("OnThisDay", () => {
  it("renders nothing when there are no entries", () => {
    const { container } = renderOnThisDay([]);
    expect(container).toBeEmptyDOMElement();
  });

  it("shows the years-ago label and links back to the memo", () => {
    renderOnThisDay([memo({ id: "abc" })]);
    expect(screen.getByText("那年今日")).toBeInTheDocument();
    expect(screen.getByText("1年前")).toBeInTheDocument();
    const link = screen.getByRole("link");
    expect(link).toHaveAttribute("href", "/entries/abc");
  });
});
