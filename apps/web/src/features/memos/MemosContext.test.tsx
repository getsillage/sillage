import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, type Memo, type MemoAI } from "../../lib/api";
import { MemosProvider, useMemos } from "./MemosContext";

vi.mock("../../lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../lib/api")>();
  return {
    ...actual,
    createMemo: vi.fn(),
    getMemo: vi.fn(),
    updateMemo: vi.fn(),
    deleteMemo: vi.fn(),
    purgeMemo: vi.fn(),
    listMemos: vi.fn(),
    setMemoFavorited: vi.fn(),
    generateMemoSummary: vi.fn(),
  };
});

import {
  createMemo,
  deleteMemo,
  generateMemoSummary,
  getMemo,
  listMemos,
  purgeMemo,
  setMemoFavorited,
  updateMemo,
} from "../../lib/api";

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

function summary(content: string): MemoAI {
  return {
    memoId: "m1",
    summary: content,
    sentiment: null,
    provider: "openai",
    model: "test",
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
    createdAt: "2026-07-10T08:00:00Z",
    updatedAt: "2026-07-10T08:00:00Z",
  };
}

type MemoContext = ReturnType<typeof useMemos>;

function ObserveCache({
  onContext,
}: {
  onContext: (context: MemoContext) => void;
}) {
  const context = useMemos();
  onContext(context);
  return (
    <>
      <p data-testid="memo-list">
        {context.memos
          .map((item) => `${item.content}@${item.version}`)
          .join("|")}
      </p>
      <p data-testid="memo-summary">{context.summaries.m1?.summary ?? ""}</p>
    </>
  );
}

function renderCache() {
  let context: MemoContext;
  render(
    <MemosProvider token="t">
      <ObserveCache
        onContext={(current) => {
          context = current;
        }}
      />
    </MemosProvider>,
  );
  return () => context;
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

describe("MemosProvider detail cache", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(listMemos).mockResolvedValue({ memos: [memo("m1", "原始正文")] });
  });

  it("keeps the newer detail and summary when an earlier request returns last", async () => {
    let finishFirst: ((value: { memo: Memo; ai: MemoAI }) => void) | undefined;
    vi.mocked(getMemo)
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            finishFirst = resolve;
          }),
      )
      .mockResolvedValueOnce({
        memo: { ...memo("m1", "最新正文"), version: 2 },
        ai: summary("最新总结"),
      });
    const context = renderCache();
    await screen.findByText("原始正文@1");
    let first: Promise<Memo>;

    await act(async () => {
      first = context().fetchMemo("m1");
      await context().fetchMemo("m1");
    });
    expect(screen.getByTestId("memo-list")).toHaveTextContent("最新正文@2");

    await act(async () => {
      finishFirst?.({ memo: memo("m1", "过期正文"), ai: summary("过期总结") });
      expect(await first).toMatchObject({ content: "最新正文", version: 2 });
    });

    expect(screen.getByTestId("memo-list")).toHaveTextContent("最新正文@2");
    expect(screen.getByTestId("memo-summary")).toHaveTextContent("最新总结");
  });

  it("does not restore an older record or summary after an edit completes", async () => {
    let finishDetail: ((value: { memo: Memo; ai: MemoAI }) => void) | undefined;
    vi.mocked(getMemo).mockImplementation(
      () =>
        new Promise((resolve) => {
          finishDetail = resolve;
        }),
    );
    vi.mocked(updateMemo).mockResolvedValue({
      memo: { ...memo("m1", "编辑后的正文"), version: 2 },
    });
    const context = renderCache();
    await screen.findByText("原始正文@1");
    let pending: Promise<Memo>;

    await act(async () => {
      pending = context().fetchMemo("m1");
      await context().update(context().memos[0], { content: "编辑后的正文" });
    });
    await act(async () => {
      finishDetail?.({
        memo: memo("m1", "原始正文"),
        ai: summary("编辑前总结"),
      });
      await pending;
    });

    expect(screen.getByTestId("memo-list")).toHaveTextContent("编辑后的正文@2");
    expect(screen.getByTestId("memo-summary")).toBeEmptyDOMElement();
  });

  it.each([
    "remove",
    "purge",
  ] as const)("does not revive a record after %s completes", async (operation) => {
    let finishDetail: ((value: { memo: Memo; ai: MemoAI }) => void) | undefined;
    vi.mocked(getMemo).mockImplementation(
      () =>
        new Promise((resolve) => {
          finishDetail = resolve;
        }),
    );
    const deleted = {
      ...memo("m1", "已删除正文"),
      version: 2,
      deletedAt: "2026-07-10T09:00:00Z",
    };
    vi.mocked(deleteMemo).mockResolvedValue({ memo: deleted });
    vi.mocked(purgeMemo).mockResolvedValue({
      memo: { ...deleted, content: "" },
    });
    const context = renderCache();
    await screen.findByText("原始正文@1");
    let pending: Promise<Memo>;

    await act(async () => {
      pending = context().fetchMemo("m1");
      await context()[operation](context().memos[0]);
    });
    await act(async () => {
      finishDetail?.({ memo: memo("m1", "原始正文"), ai: summary("旧总结") });
      await expect(pending).rejects.toBeInstanceOf(ApiError);
    });

    expect(screen.getByTestId("memo-list")).toBeEmptyDOMElement();
    expect(screen.getByTestId("memo-summary")).toBeEmptyDOMElement();
  });

  it("rejects an older detail version already superseded by the loaded list", async () => {
    vi.mocked(listMemos).mockResolvedValue({
      memos: [{ ...memo("m1", "列表最新正文"), version: 3 }],
    });
    vi.mocked(getMemo).mockResolvedValue({
      memo: { ...memo("m1", "旧详情正文"), version: 2 },
      ai: summary("旧详情总结"),
    });
    const context = renderCache();
    await screen.findByText("列表最新正文@3");

    await act(async () => {
      await context().fetchMemo("m1");
    });

    expect(screen.getByTestId("memo-list")).toHaveTextContent("列表最新正文@3");
    expect(screen.getByTestId("memo-summary")).toBeEmptyDOMElement();
  });

  it("clears a stored summary when the current detail no longer has one", async () => {
    vi.mocked(getMemo)
      .mockResolvedValueOnce({
        memo: memo("m1", "原始正文"),
        ai: summary("原始总结"),
      })
      .mockResolvedValueOnce({
        memo: { ...memo("m1", "最新正文"), version: 2 },
      });
    const context = renderCache();
    await screen.findByText("原始正文@1");

    await act(async () => {
      await context().fetchMemo("m1");
    });
    expect(screen.getByTestId("memo-summary")).toHaveTextContent("原始总结");
    await act(async () => {
      await context().fetchMemo("m1");
    });

    expect(screen.getByTestId("memo-list")).toHaveTextContent("最新正文@2");
    expect(screen.getByTestId("memo-summary")).toBeEmptyDOMElement();
  });

  it.each([
    null,
    summary("旧详情总结"),
  ])("keeps a generated summary when a late detail returns %s", async (ai) => {
    let finishDetail:
      | ((value: { memo: Memo; ai: MemoAI | null }) => void)
      | undefined;
    vi.mocked(getMemo).mockImplementation(
      () =>
        new Promise((resolve) => {
          finishDetail = resolve;
        }),
    );
    const generated = summary("新生成的总结");
    vi.mocked(generateMemoSummary).mockResolvedValue({ ai: generated });
    const context = renderCache();
    await screen.findByText("原始正文@1");
    let pending: Promise<Memo>;

    await act(async () => {
      pending = context().fetchMemo("m1");
      expect(await context().summarize(context().memos[0])).toEqual(generated);
    });
    await act(async () => {
      finishDetail?.({ memo: memo("m1", "原始正文"), ai });
      await pending;
    });

    expect(screen.getByTestId("memo-summary")).toHaveTextContent(
      "新生成的总结",
    );
  });

  it.each([
    "update",
    "remove",
    "purge",
  ] as const)("does not cache a late summary after %s changes the record", async (operation) => {
    let finishSummary: ((value: { ai: MemoAI }) => void) | undefined;
    vi.mocked(generateMemoSummary).mockImplementation(
      () =>
        new Promise((resolve) => {
          finishSummary = resolve;
        }),
    );
    vi.mocked(updateMemo).mockResolvedValue({
      memo: { ...memo("m1", "编辑后的正文"), version: 2 },
    });
    const deleted = {
      ...memo("m1", ""),
      version: 2,
      deletedAt: "2026-07-10T09:00:00Z",
    };
    vi.mocked(deleteMemo).mockResolvedValue({ memo: deleted });
    vi.mocked(purgeMemo).mockResolvedValue({ memo: deleted });
    const context = renderCache();
    await screen.findByText("原始正文@1");
    let pending: Promise<MemoAI>;

    await act(async () => {
      pending = context().summarize(context().memos[0]);
      if (operation === "update") {
        await context().update(context().memos[0], { content: "编辑后的正文" });
      } else {
        await context()[operation](context().memos[0]);
      }
    });
    const generated = summary("过期正文生成的总结");
    await act(async () => {
      finishSummary?.({ ai: generated });
      expect(await pending).toEqual(generated);
    });

    expect(screen.getByTestId("memo-summary")).toBeEmptyDOMElement();
    if (operation === "update") {
      expect(screen.getByTestId("memo-list")).toHaveTextContent(
        "编辑后的正文@2",
      );
    } else {
      expect(screen.getByTestId("memo-list")).toBeEmptyDOMElement();
    }
  });

  it("accepts a generated summary after a same-version detail completes while it is pending", async () => {
    let finishSummary: ((value: { ai: MemoAI }) => void) | undefined;
    vi.mocked(generateMemoSummary).mockImplementation(
      () =>
        new Promise((resolve) => {
          finishSummary = resolve;
        }),
    );
    vi.mocked(getMemo).mockResolvedValue({
      memo: memo("m1", "原始正文"),
      ai: summary("生成前的总结"),
    });
    const context = renderCache();
    await screen.findByText("原始正文@1");
    let pending: Promise<MemoAI>;

    await act(async () => {
      pending = context().summarize(context().memos[0]);
      await context().fetchMemo("m1");
    });
    expect(screen.getByTestId("memo-summary")).toHaveTextContent(
      "生成前的总结",
    );
    await act(async () => {
      finishSummary?.({ ai: summary("新生成的总结") });
      await pending;
    });

    expect(screen.getByTestId("memo-summary")).toHaveTextContent(
      "新生成的总结",
    );
  });

  it("keeps the latest generation when an earlier summary request finishes last", async () => {
    let finishFirst: ((value: { ai: MemoAI }) => void) | undefined;
    vi.mocked(generateMemoSummary)
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            finishFirst = resolve;
          }),
      )
      .mockResolvedValueOnce({ ai: summary("后发请求的新总结") });
    const context = renderCache();
    await screen.findByText("原始正文@1");
    let first: Promise<MemoAI>;

    await act(async () => {
      first = context().summarize(context().memos[0]);
      await context().summarize(context().memos[0]);
    });
    await act(async () => {
      finishFirst?.({ ai: summary("先发请求的旧总结") });
      await first;
    });

    expect(screen.getByTestId("memo-summary")).toHaveTextContent(
      "后发请求的新总结",
    );
  });
});
