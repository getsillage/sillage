import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Memo } from "../../lib/api";
import { MemosProvider, useMemos } from "./MemosContext";

vi.mock("../../lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../lib/api")>();
  return {
    ...actual,
    createMemo: vi.fn(),
    listMemos: vi.fn(),
    setMemoFavorited: vi.fn(),
  };
});

import { createMemo, listMemos, setMemoFavorited } from "../../lib/api";

function memo(id: string, content: string): Memo {
  return {
    id,
    content,
    entryDate: "2026-07-10",
    version: 1,
    favoritedAt: null,
    archivedAt: null,
    createdAt: "2026-07-10T08:00:00Z",
    updatedAt: "2026-07-10T08:00:00Z",
    deletedAt: null,
  };
}

function Harness() {
  const {
    memos,
    hasMore,
    loadingMore,
    loadMore,
    loadAll,
    refresh,
    create,
    setFavorited,
  } = useMemos();
  return (
    <div>
      <p data-testid="memo-list">
        {memos.map((item) => item.content).join("|")}
      </p>
      <p>{hasMore ? "还有更多" : "没有更多"}</p>
      <p>{loadingMore ? "加载中" : "空闲"}</p>
      <p>{memos[0]?.favoritedAt ? "首条已收藏" : "首条未收藏"}</p>
      <button
        type="button"
        onClick={() => {
          void loadMore();
          void loadMore();
        }}
      >
        连续加载
      </button>
      <button type="button" onClick={() => void refresh()}>
        刷新
      </button>
      <button type="button" onClick={() => void loadAll()}>
        加载完整历史
      </button>
      <button
        type="button"
        onClick={() =>
          void create({ content: "新建竞态记录", entryDate: "2026-07-10" })
        }
      >
        新建竞态记录
      </button>
      <button
        type="button"
        onClick={() => {
          if (memos[0]) {
            void setFavorited(memos[0], true);
          }
        }}
      >
        收藏首条
      </button>
    </div>
  );
}

describe("MemosProvider pagination", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("owns one page request synchronously and ignores it after a refresh", async () => {
    const user = userEvent.setup();
    let finishOlder:
      | ((value: { memos: Memo[]; nextCursor?: string }) => void)
      | undefined;
    vi.mocked(listMemos)
      .mockResolvedValueOnce({
        memos: [memo("initial", "初始记录")],
        nextCursor: "older-cursor",
      })
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            finishOlder = resolve;
          }),
      )
      .mockResolvedValueOnce({
        memos: [memo("fresh", "刷新后的记录")],
        nextCursor: "fresh-cursor",
      });

    render(
      <MemosProvider token="t">
        <Harness />
      </MemosProvider>,
    );
    expect(await screen.findByText("初始记录")).toBeInTheDocument();
    expect(screen.getByText("还有更多")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "连续加载" }));
    expect(listMemos).toHaveBeenCalledTimes(2);
    expect(screen.getByText("加载中")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "刷新" }));
    expect(await screen.findByText("刷新后的记录")).toBeInTheDocument();
    expect(screen.getByText("空闲")).toBeInTheDocument();

    await act(async () => {
      finishOlder?.({
        memos: [memo("stale", "过期分页记录")],
        nextCursor: "stale-cursor",
      });
    });
    await waitFor(() =>
      expect(screen.queryByText(/过期分页记录/)).not.toBeInTheDocument(),
    );
    expect(screen.getByText("刷新后的记录")).toBeInTheDocument();
  });

  it("loads every remaining page serially", async () => {
    const user = userEvent.setup();
    vi.mocked(listMemos)
      .mockResolvedValueOnce({
        memos: [memo("page-1", "第一页")],
        nextCursor: "cursor-2",
      })
      .mockResolvedValueOnce({
        memos: [memo("page-2", "第二页")],
        nextCursor: "cursor-3",
      })
      .mockResolvedValueOnce({ memos: [memo("page-3", "第三页")] });

    render(
      <MemosProvider token="t">
        <Harness />
      </MemosProvider>,
    );
    expect(await screen.findByText("第一页")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "加载完整历史" }));
    expect(await screen.findByText("第一页|第二页|第三页")).toBeInTheDocument();
    expect(listMemos).toHaveBeenNthCalledWith(2, "t", 200, "cursor-2", {
      archived: false,
      favorited: false,
    });
    expect(listMemos).toHaveBeenNthCalledWith(3, "t", 200, "cursor-3", {
      archived: false,
      favorited: false,
    });
    expect(screen.getByText("没有更多")).toBeInTheDocument();
  });

  it("restarts a stale refresh after a canonical mutation response", async () => {
    const user = userEvent.setup();
    let finishInitial:
      | ((value: { memos: Memo[]; nextCursor?: string }) => void)
      | undefined;
    const created = memo("created", "新建竞态记录");
    const old = memo("old", "旧快照记录");
    vi.mocked(listMemos)
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            finishInitial = resolve;
          }),
      )
      .mockResolvedValueOnce({ memos: [old, created] });
    vi.mocked(createMemo).mockResolvedValue({ memo: created });

    render(
      <MemosProvider token="t">
        <Harness />
      </MemosProvider>,
    );
    await user.click(screen.getByRole("button", { name: "新建竞态记录" }));
    await waitFor(() =>
      expect(screen.getByTestId("memo-list")).toHaveTextContent("新建竞态记录"),
    );

    await act(async () => {
      finishInitial?.({ memos: [old] });
    });
    await waitFor(() => expect(listMemos).toHaveBeenCalledTimes(2));
    await waitFor(() =>
      expect(screen.getByTestId("memo-list")).toHaveTextContent(
        "旧快照记录|新建竞态记录",
      ),
    );
  });

  it("restarts a stale page after a canonical favorite response", async () => {
    const user = userEvent.setup();
    let finishStalePage:
      | ((value: { memos: Memo[]; nextCursor?: string }) => void)
      | undefined;
    const initial = memo("m1", "边界记录");
    const favorited = {
      ...initial,
      version: 2,
      favoritedAt: "2026-07-10T09:00:00Z",
    };
    vi.mocked(listMemos)
      .mockResolvedValueOnce({ memos: [initial], nextCursor: "older" })
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            finishStalePage = resolve;
          }),
      )
      .mockResolvedValueOnce({
        memos: [favorited, memo("m2", "更早记录")],
      });
    vi.mocked(setMemoFavorited).mockResolvedValue({ memo: favorited });

    render(
      <MemosProvider token="t">
        <Harness />
      </MemosProvider>,
    );
    expect(await screen.findByText("边界记录")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "连续加载" }));
    await user.click(screen.getByRole("button", { name: "收藏首条" }));
    expect(await screen.findByText("首条已收藏")).toBeInTheDocument();

    await act(async () => {
      finishStalePage?.({ memos: [initial, memo("m2", "更早记录")] });
    });
    await waitFor(() => expect(listMemos).toHaveBeenCalledTimes(3));
    expect(screen.getByText("首条已收藏")).toBeInTheDocument();
    expect(screen.getByTestId("memo-list")).toHaveTextContent("更早记录");
    expect(screen.getByText("没有更多")).toBeInTheDocument();
  });
});
