import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import type { Memo } from "../lib/api";
import { EntryCard } from "./EntryCard";
import { EntryComposer } from "./EntryComposer";
import { LocalDateTime } from "./LocalDateTime";
import { Markdown } from "./Markdown";
import { Sidebar } from "./Sidebar";
import { ThemeToggle } from "./ThemeToggle";

vi.mock("../state/AskContext", () => ({
  useAsk: () => ({
    conversations: [],
    activeId: "",
    startNew: vi.fn(),
  }),
}));

function memo(overrides: Partial<Memo> = {}): Memo {
  return {
    id: "m1",
    content: "记录正文内容",
    entryDate: "2026-06-27",
    version: 1,
    pinnedAt: null,
    archivedAt: null,
    createdAt: "2026-06-27T08:00:00Z",
    updatedAt: "2026-06-27T08:00:00Z",
    deletedAt: null,
    ...overrides,
  };
}

describe("Markdown", () => {
  it("renders markdown and opens links in a new tab", () => {
    render(<Markdown content={"# 标题\n\n[去看看](https://example.com)"} />);
    expect(screen.getByRole("heading", { name: "标题" })).toBeInTheDocument();
    const link = screen.getByRole("link", { name: "去看看" });
    expect(link).toHaveAttribute("target", "_blank");
    expect(link).toHaveAttribute("rel", "noopener noreferrer");
  });
});

describe("LocalDateTime", () => {
  it("renders a time element, or nothing for an invalid value", () => {
    const { container } = render(
      <LocalDateTime value="2026-06-27T08:30:00Z" />,
    );
    expect(container.querySelector("time")).toBeInTheDocument();

    const invalid = render(<LocalDateTime value="not-a-date" />);
    expect(invalid.container.querySelector("time")).toBeNull();
  });
});

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
          <Route path="/entries/:id" element={<div>详情页</div>} />
        </Routes>
      </MemoryRouter>,
    );
    expect(screen.getByText("记录正文内容")).toBeInTheDocument();
    await user.click(screen.getByRole("link", { name: /查看.*详情/ }));
    expect(screen.getByText("详情页")).toBeInTheDocument();
  });

  it("renders a blank-record placeholder for empty content", () => {
    render(
      <MemoryRouter>
        <EntryCard memo={memo({ content: "   " })} />
      </MemoryRouter>,
    );
    expect(screen.getByText("空白记录")).toBeInTheDocument();
  });
});

describe("ThemeToggle", () => {
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
});

describe("Sidebar", () => {
  it("anchors the account menu to the available footer width", () => {
    render(
      <MemoryRouter>
        <Sidebar
          account={{
            id: "a1",
            username: "felix",
            displayName: "Felix",
            createdAt: "2026-06-27T08:00:00Z",
            updatedAt: "2026-06-27T08:00:00Z",
          }}
          onSignOut={vi.fn()}
        />
      </MemoryRouter>,
    );

    const details = screen.getByText("Felix").closest("details");
    expect(details).toHaveClass("flex-1");
  });
});

describe("EntryComposer", () => {
  it("validates empty content and submits typed content", async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const onUpload = vi.fn();
    render(<EntryComposer onSubmit={onSubmit} onUpload={onUpload} />);

    await user.click(screen.getByRole("button", { name: "保存" }));
    expect(screen.getByText("先写下要保存的内容")).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();

    await user.type(screen.getByRole("textbox"), "今天写点东西");
    await user.click(screen.getByRole("button", { name: "保存" }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit.mock.calls[0][0].content).toBe("今天写点东西");
  });
});
