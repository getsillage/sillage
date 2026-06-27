import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Memo, MemoAI } from "../lib/api";
import { MemosProvider } from "../state/MemosContext";
import { EntryPage } from "./EntryPage";
import { HomePage } from "./HomePage";
import { TimelinePage } from "./TimelinePage";

vi.mock("../lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../lib/api")>();
  return {
    ...actual,
    listMemos: vi.fn(),
    searchMemos: vi.fn(),
    getMemo: vi.fn(),
    createMemo: vi.fn(),
    updateMemo: vi.fn(),
    deleteMemo: vi.fn(),
    setMemoPinned: vi.fn(),
    setMemoArchived: vi.fn(),
    generateMemoSummary: vi.fn(),
    uploadAttachment: vi.fn(),
  };
});

import {
  createMemo,
  generateMemoSummary,
  getMemo,
  listMemos,
  searchMemos,
} from "../lib/api";

function memo(overrides: Partial<Memo> = {}): Memo {
  return {
    id: "m1",
    content: "今天的记录内容",
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

function memoAI(): MemoAI {
  return {
    memoId: "m1",
    summary: "这是一段总结。",
    sentiment: null,
    provider: "openai",
    model: "gpt-test",
    profileId: "p1",
    promptVersion: "v2",
    sourceMemoIds: '["m1"]',
    status: "complete",
    errorCode: null,
    startedAt: null,
    finishedAt: null,
    inputTokens: 1,
    outputTokens: 1,
    totalTokens: 2,
    createdAt: "2026-06-27T08:00:00Z",
    updatedAt: "2026-06-27T08:00:00Z",
  };
}

function renderWithMemos(ui: React.ReactNode, initialPath = "/") {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <MemosProvider token="t">
        <Routes>
          <Route path="/" element={ui} />
          <Route path="/timeline" element={ui} />
          <Route path="/entries/:id" element={ui} />
        </Routes>
      </MemosProvider>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(listMemos).mockResolvedValue({ memos: [memo()] });
  vi.mocked(searchMemos).mockResolvedValue({ memos: [] });
  vi.mocked(getMemo).mockResolvedValue({ memo: memo() });
});

describe("HomePage", () => {
  it("lists loaded records and creates a new one", async () => {
    const user = userEvent.setup();
    vi.mocked(createMemo).mockResolvedValue({
      memo: memo({ id: "m2", content: "新建的记录" }),
    });
    renderWithMemos(<HomePage />);

    await screen.findByText("今天的记录内容");
    expect(screen.getByText("今天想记录什么？")).toBeInTheDocument();

    await user.type(screen.getByRole("textbox"), "新建的记录");
    await user.click(screen.getByRole("button", { name: "保存" }));
    await waitFor(() => expect(createMemo).toHaveBeenCalledTimes(1));
    expect(vi.mocked(createMemo).mock.calls[0][1].content).toBe("新建的记录");
  });
});

describe("TimelinePage", () => {
  it("debounces a server-side search", async () => {
    const user = userEvent.setup();
    vi.mocked(searchMemos).mockResolvedValue({
      memos: [memo({ id: "found", content: "搜索命中的记录" })],
    });
    renderWithMemos(<TimelinePage />, "/timeline");

    await screen.findByText("今天的记录内容");
    await user.type(screen.getByPlaceholderText("搜索记录…"), "命中");
    await waitFor(() => expect(searchMemos).toHaveBeenCalled(), {
      timeout: 2000,
    });
    expect(await screen.findByText("搜索命中的记录")).toBeInTheDocument();
  });
});

describe("TimelinePage calendar view", () => {
  it("renders the month grid and the selected day's records", async () => {
    renderWithMemos(
      <TimelinePage />,
      "/timeline?view=calendar&y=2026&m=6&date=2026-06-27",
    );
    expect(await screen.findByText("2026年6月")).toBeInTheDocument();
    // The selected day panel lists the seeded record (entry date 2026-06-27).
    expect(await screen.findByText("2026-06-27")).toBeInTheDocument();
    expect(screen.getByText("今天的记录内容")).toBeInTheDocument();
  });

  it("normalizes out-of-range month params", async () => {
    renderWithMemos(<TimelinePage />, "/timeline?view=calendar&y=2026&m=13");

    expect(await screen.findByText("2027年1月")).toBeInTheDocument();
    expect(screen.queryByText("2026年13月")).not.toBeInTheDocument();
  });

  it("normalizes zero month params instead of falling back to today", async () => {
    renderWithMemos(<TimelinePage />, "/timeline?view=calendar&y=2026&m=0");

    expect(await screen.findByText("2025年12月")).toBeInTheDocument();
    expect(screen.queryByText("2026年0月")).not.toBeInTheDocument();
  });
});

describe("EntryPage", () => {
  it("shows the record with its stored summary and regenerates it", async () => {
    const user = userEvent.setup();
    vi.mocked(getMemo).mockResolvedValue({ memo: memo(), ai: memoAI() });
    vi.mocked(generateMemoSummary).mockResolvedValue({ ai: memoAI() });
    renderWithMemos(<EntryPage />, "/entries/m1");

    expect(await screen.findByText("这是一段总结。")).toBeInTheDocument();
    expect(screen.getByText(/基于 1 条记录/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /重新总结/ }));
    await waitFor(() => expect(generateMemoSummary).toHaveBeenCalledTimes(1));
  });

  it("shows a not-found message when the record is missing", async () => {
    vi.mocked(getMemo).mockRejectedValue(new Error("not found"));
    renderWithMemos(<EntryPage />, "/entries/missing");
    expect(
      await screen.findByText("这条记录不存在或已被删除。"),
    ).toBeInTheDocument();
  });
});

describe("EntryCard meta via HomePage", () => {
  it("renders attribution date for records with a differing entry date", async () => {
    vi.mocked(listMemos).mockResolvedValue({
      memos: [memo({ entryDate: "2020-01-01", content: "旧日子的记录" })],
    });
    renderWithMemos(<HomePage />);
    const card = await screen.findByText("旧日子的记录");
    expect(
      within(card.closest("article") as HTMLElement).getByText(/归属/),
    ).toBeInTheDocument();
  });
});
