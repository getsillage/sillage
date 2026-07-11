import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { describe, expect, it } from "vitest";
import type { Memo } from "../../lib/api";
import { EntryCard } from "./EntryCard";

function memo(overrides: Partial<Memo> = {}): Memo {
  return {
    id: "m1",
    content: "记录正文内容",
    entryDate: "2026-06-27",
    version: 1,
    favoritedAt: null,
    archivedAt: null,
    createdAt: "2026-06-27T08:00:00Z",
    updatedAt: "2026-06-27T08:00:00Z",
    deletedAt: null,
    ...overrides,
  };
}

function EntryDetailProbe() {
  const location = useLocation();
  const state = location.state as {
    returnTo?: string;
    memoSnapshot?: Memo;
  } | null;
  return (
    <div>
      <span>详情页</span>
      <span data-testid="detail-return-to">{state?.returnTo}</span>
      <span data-testid="detail-memo-snapshot">
        {state?.memoSnapshot?.content}
      </span>
    </div>
  );
}

describe("EntryCard", () => {
  it("shows a preview and opens the detail page on row click", async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={["/"]}>
        <Routes>
          <Route
            path="/"
            element={<EntryCard memo={memo()} openOnCardClick />}
          />
          <Route path="/entries/:id" element={<EntryDetailProbe />} />
        </Routes>
      </MemoryRouter>,
    );
    expect(screen.getByText("记录正文内容")).toBeInTheDocument();
    await user.click(screen.getByRole("link", { name: /查看.*详情/ }));
    expect(screen.getByText("详情页")).toBeInTheDocument();
    expect(screen.getByTestId("detail-return-to")).toHaveTextContent("/");
    expect(screen.getByTestId("detail-memo-snapshot")).toHaveTextContent(
      "记录正文内容",
    );
  });

  it("renders a blank-record placeholder for empty content", () => {
    render(
      <MemoryRouter>
        <EntryCard memo={memo({ content: "   " })} />
      </MemoryRouter>,
    );
    expect(screen.getByText("空白记录")).toBeInTheDocument();
  });

  it("shows the favorite state without pin terminology", () => {
    render(
      <MemoryRouter>
        <EntryCard memo={memo({ favoritedAt: "2026-06-28T08:00:00Z" })} />
      </MemoryRouter>,
    );

    expect(screen.getByText("收藏")).toBeInTheDocument();
    expect(screen.queryByText("置顶")).not.toBeInTheDocument();
  });
});
