import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {
  type InitialEntry,
  MemoryRouter,
  Route,
  Routes,
} from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, type Memo, type MemoAI } from "../lib/api";
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

vi.mock("../components/UnsavedNavigationGuard", () => ({
  UnsavedNavigationGuard: () => null,
  useUnsavedChangesRegistration: () => undefined,
}));

import {
  createMemo,
  deleteMemo,
  generateMemoSummary,
  getMemo,
  listMemos,
  searchMemos,
  updateMemo,
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

function renderWithMemos(ui: React.ReactNode, initialPath: InitialEntry = "/") {
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
  window.localStorage.clear();
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

  it("retries after the initial record load fails", async () => {
    const user = userEvent.setup();
    vi.mocked(listMemos)
      .mockRejectedValueOnce(new Error("首页记录读取失败"))
      .mockResolvedValueOnce({
        memos: [memo({ content: "重试后出现的记录" })],
      });
    renderWithMemos(<HomePage />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "首页记录读取失败",
    );
    expect(
      screen.queryByText("今天还没有记录。可以先写下第一条。"),
    ).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "重新加载记录" }));

    expect(await screen.findByText("重试后出现的记录")).toBeInTheDocument();
    expect(listMemos).toHaveBeenCalledTimes(2);
  });
});

describe("TimelinePage", () => {
  it("does not turn an initial load failure into an empty timeline", async () => {
    const user = userEvent.setup();
    vi.mocked(listMemos)
      .mockRejectedValueOnce(new Error("历史记录读取失败"))
      .mockResolvedValueOnce({
        memos: [memo({ content: "重试后恢复的历史记录" })],
      });
    renderWithMemos(<TimelinePage />, "/timeline");

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "历史记录读取失败",
    );
    expect(
      screen.queryByText("还没有记录。可以先写一条记录。"),
    ).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "重新加载记录" }));

    expect(await screen.findByText("重试后恢复的历史记录")).toBeInTheDocument();
    expect(listMemos).toHaveBeenCalledTimes(2);
  });

  it("keeps loaded records and manually retries a failed older page", async () => {
    const user = userEvent.setup();
    vi.mocked(listMemos)
      .mockResolvedValueOnce({
        memos: [memo({ id: "recent", content: "已经读取的记录" })],
        nextCursor: "older",
      })
      .mockRejectedValueOnce(new Error("更多记录读取失败"))
      .mockResolvedValueOnce({
        memos: [
          memo({
            id: "older",
            content: "重试后读取的旧记录",
            entryDate: "2025-01-02",
          }),
        ],
      });
    renderWithMemos(<TimelinePage />, "/timeline");

    expect(await screen.findByText("已经读取的记录")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "加载更多" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "更多记录读取失败",
    );
    expect(screen.getByText("已经读取的记录")).toBeInTheDocument();
    expect(listMemos).toHaveBeenCalledTimes(2);

    await user.click(screen.getByRole("button", { name: "重试加载更多" }));

    expect(await screen.findByText("重试后读取的旧记录")).toBeInTheDocument();
    expect(listMemos).toHaveBeenCalledTimes(3);
    expect(vi.mocked(listMemos).mock.calls[1][2]).toBe("older");
    expect(vi.mocked(listMemos).mock.calls[2][2]).toBe("older");
  });

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
    expect(searchMemos).toHaveBeenCalledWith("t", "命中", 100, false);
    expect(await screen.findByText("搜索命中的记录")).toBeInTheDocument();
  });

  it("passes the archived state to server-side search", async () => {
    const user = userEvent.setup();
    vi.mocked(searchMemos).mockResolvedValue({
      memos: [
        memo({
          id: "archived-found",
          content: "归档搜索命中的记录",
          archivedAt: "2026-06-28T08:00:00Z",
        }),
      ],
    });
    renderWithMemos(<TimelinePage />, "/timeline?filter=archived");

    await user.type(screen.getByPlaceholderText("搜索记录…"), "归档命中");
    await waitFor(
      () =>
        expect(searchMemos).toHaveBeenCalledWith("t", "归档命中", 100, true),
      { timeout: 2000 },
    );
    expect(await screen.findByText("归档搜索命中的记录")).toBeInTheDocument();
  });

  it("keeps archived records discoverable", async () => {
    const user = userEvent.setup();
    vi.mocked(listMemos).mockResolvedValue({
      memos: [
        memo(),
        memo({
          id: "archived",
          content: "已经归档的记录",
          archivedAt: "2026-06-28T08:00:00Z",
        }),
      ],
    });
    renderWithMemos(<TimelinePage />, "/timeline");

    await screen.findByText("今天的记录内容");
    expect(screen.queryByText("已经归档的记录")).not.toBeInTheDocument();
    await user.click(screen.getByRole("link", { name: /已归档 1/ }));
    expect(await screen.findByText("已经归档的记录")).toBeInTheDocument();
  });

  it("renders older pinned records in a dedicated first group", async () => {
    vi.mocked(listMemos).mockResolvedValue({
      memos: [
        memo({ id: "recent", content: "最近普通记录" }),
        memo({
          id: "old-pinned",
          content: "旧日期置顶记录",
          entryDate: "2025-01-02",
          pinnedAt: "2026-06-28T08:00:00Z",
        }),
      ],
    });
    renderWithMemos(<TimelinePage />, "/timeline");

    const pinned = await screen.findByText("旧日期置顶记录");
    const recent = screen.getByText("最近普通记录");
    expect(screen.getByRole("heading", { name: "置顶" })).toBeInTheDocument();
    expect(
      pinned.compareDocumentPosition(recent) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
    expect(pinned.closest("article")).toHaveTextContent("归属");
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
    expect(await screen.findByText("6月27日 周六")).toBeInTheDocument();
    expect(screen.getByText("今天的记录内容")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: /2026年6月27日，1 条记录/ }),
    ).toBeInTheDocument();
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

  it("loads all pages before rendering an older selected day", async () => {
    let finishOlder:
      | ((value: { memos: Memo[]; nextCursor?: string }) => void)
      | undefined;
    vi.mocked(listMemos)
      .mockResolvedValueOnce({
        memos: [memo({ id: "recent", content: "最新一页" })],
        nextCursor: "older",
      })
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            finishOlder = resolve;
          }),
      );
    renderWithMemos(
      <TimelinePage />,
      "/timeline?view=calendar&y=2025&m=1&date=2025-01-02",
    );

    expect(await screen.findByRole("status")).toHaveTextContent(
      "已读取 1 条，正在继续",
    );
    expect(screen.queryByText("2025年1月")).not.toBeInTheDocument();
    await waitFor(() => expect(listMemos).toHaveBeenCalledTimes(2));

    await act(async () => {
      finishOlder?.({
        memos: [
          memo({
            id: "older",
            content: "第二页旧记录",
            entryDate: "2025-01-02",
          }),
        ],
      });
    });
    expect(await screen.findByText("2025年1月")).toBeInTheDocument();
    expect(screen.getByText("第二页旧记录")).toBeInTheDocument();
    expect(listMemos).toHaveBeenCalledTimes(2);
  });

  it("stops after a page failure and retries only on request", async () => {
    const user = userEvent.setup();
    vi.mocked(listMemos)
      .mockResolvedValueOnce({
        memos: [memo({ id: "recent", content: "最新一页" })],
        nextCursor: "older",
      })
      .mockRejectedValueOnce(new Error("读取旧页失败"))
      .mockResolvedValueOnce({
        memos: [memo({ id: "recent", content: "重试后的最新一页" })],
        nextCursor: "older",
      })
      .mockResolvedValueOnce({
        memos: [
          memo({
            id: "older",
            content: "重试后的旧记录",
            entryDate: "2025-01-02",
          }),
        ],
      });
    renderWithMemos(
      <TimelinePage />,
      "/timeline?view=calendar&y=2025&m=1&date=2025-01-02",
    );

    expect(await screen.findByRole("alert")).toHaveTextContent("读取旧页失败");
    expect(listMemos).toHaveBeenCalledTimes(2);
    await user.click(screen.getByRole("button", { name: "重新加载完整历史" }));

    expect(await screen.findByText("重试后的旧记录")).toBeInTheDocument();
    expect(listMemos).toHaveBeenCalledTimes(4);
    expect(vi.mocked(listMemos).mock.calls[2][2]).toBeUndefined();
    expect(vi.mocked(listMemos).mock.calls[3][2]).toBe("older");
  });

  it("shows an initial page error and retries refresh before loading all", async () => {
    const user = userEvent.setup();
    vi.mocked(listMemos)
      .mockRejectedValueOnce(new Error("首页读取失败"))
      .mockResolvedValueOnce({
        memos: [memo({ id: "recent", content: "重试后的最新一页" })],
        nextCursor: "older",
      })
      .mockResolvedValueOnce({
        memos: [
          memo({
            id: "older",
            content: "重试后的完整历史",
            entryDate: "2025-01-02",
          }),
        ],
      });
    renderWithMemos(
      <TimelinePage />,
      "/timeline?view=calendar&y=2025&m=1&date=2025-01-02",
    );

    expect(await screen.findByRole("alert")).toHaveTextContent("首页读取失败");
    expect(screen.queryByText("2025年1月")).not.toBeInTheDocument();
    expect(listMemos).toHaveBeenCalledTimes(1);

    await user.click(screen.getByRole("button", { name: "重新加载完整历史" }));

    expect(await screen.findByText("重试后的完整历史")).toBeInTheDocument();
    expect(listMemos).toHaveBeenCalledTimes(3);
    expect(vi.mocked(listMemos).mock.calls[1][2]).toBeUndefined();
    expect(vi.mocked(listMemos).mock.calls[2][2]).toBe("older");
  });
});

describe("EntryPage", () => {
  it("returns to the exact history view that opened the record", async () => {
    vi.mocked(getMemo).mockResolvedValue({ memo: memo() });
    renderWithMemos(<EntryPage />, {
      pathname: "/entries/m1",
      state: { returnTo: "/timeline?filter=archived" },
    });

    const back = await screen.findByRole("link", { name: "历史" });
    expect(back).toHaveAttribute("href", "/timeline?filter=archived");
  });

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
    vi.mocked(getMemo).mockRejectedValue(
      new ApiError("记录不存在", 404, "not_found"),
    );
    renderWithMemos(<EntryPage />, "/entries/missing");
    expect(
      await screen.findByText("这条记录不存在或已被删除。"),
    ).toBeInTheDocument();
  });

  it("waits for the fresh detail snapshot before editing", async () => {
    const user = userEvent.setup();
    let finishDetail: ((value: { memo: Memo }) => void) | undefined;
    vi.mocked(getMemo).mockImplementation(
      () =>
        new Promise((resolve) => {
          finishDetail = resolve;
        }),
    );
    vi.mocked(updateMemo).mockResolvedValue({
      memo: memo({ content: "最新正文 + 修改", version: 3 }),
    });
    renderWithMemos(<EntryPage />, "/entries/m1");

    expect(await screen.findByText("今天的记录内容")).toBeInTheDocument();
    const edit = screen.getByRole("button", { name: "编辑" });
    expect(edit).toBeDisabled();

    finishDetail?.({ memo: memo({ content: "服务器最新正文", version: 2 }) });
    await waitFor(() => expect(edit).toBeEnabled());
    await user.click(edit);
    const editor = screen.getByRole("textbox");
    expect(editor).toHaveValue("服务器最新正文");
    await user.type(editor, " + 修改");
    await user.click(screen.getByRole("button", { name: "更新" }));

    await waitFor(() => expect(updateMemo).toHaveBeenCalledTimes(1));
    expect(vi.mocked(updateMemo).mock.calls[0][1]).toMatchObject({
      content: "服务器最新正文",
      version: 2,
    });
    expect(vi.mocked(updateMemo).mock.calls[0][2].content).toBe(
      "服务器最新正文 + 修改",
    );
  });

  it("keeps cached content and offers retry for a transient detail failure", async () => {
    const user = userEvent.setup();
    vi.mocked(getMemo)
      .mockRejectedValueOnce(new ApiError("网络暂时不可用", 503, "unavailable"))
      .mockResolvedValueOnce({ memo: memo({ content: "重试后的正文" }) });
    renderWithMemos(<EntryPage />, "/entries/m1");

    expect(await screen.findByText("今天的记录内容")).toBeInTheDocument();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "最新内容读取失败：网络暂时不可用",
    );
    expect(
      screen.queryByText("这条记录不存在或已被删除。"),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "编辑" })).toBeDisabled();

    await user.click(screen.getByRole("button", { name: "重新加载" }));
    expect(await screen.findByText("重试后的正文")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "编辑" })).toBeEnabled();
  });

  it("requires a second click before deleting a record", async () => {
    const user = userEvent.setup();
    vi.mocked(getMemo).mockResolvedValue({ memo: memo() });
    vi.mocked(deleteMemo).mockResolvedValue({ memo: memo({ deletedAt: "x" }) });
    renderWithMemos(<EntryPage />, "/entries/m1");

    await screen.findByText("今天的记录内容");
    await user.click(screen.getByRole("button", { name: "删除" }));
    expect(deleteMemo).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "确认删除" }));
    await waitFor(() => expect(deleteMemo).toHaveBeenCalledTimes(1));
  });

  it("restores focus to the delete trigger after every cancel path", async () => {
    const user = userEvent.setup();
    vi.mocked(getMemo).mockResolvedValue({ memo: memo() });
    renderWithMemos(<EntryPage />, "/entries/m1");

    await screen.findByText("今天的记录内容");
    const deleteTrigger = screen.getByRole("button", { name: "删除" });

    await user.click(deleteTrigger);
    await user.keyboard("{Escape}");
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    expect(deleteTrigger).toHaveFocus();

    await user.click(deleteTrigger);
    await user.click(screen.getByRole("button", { name: "取消" }));
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    expect(deleteTrigger).toHaveFocus();

    await user.click(deleteTrigger);
    await user.click(screen.getByRole("button", { name: "取消删除" }));
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    expect(deleteTrigger).toHaveFocus();
    expect(deleteMemo).not.toHaveBeenCalled();
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
