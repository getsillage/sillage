import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {
  createMemoryRouter,
  MemoryRouter,
  Route,
  RouterProvider,
  Routes,
  useLocation,
} from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  UnsavedNavigationGuard,
  useUnsavedChangesRegistration,
} from "../components/UnsavedNavigationGuard";
import type { AskConversation } from "../lib/api";
import { AppShell } from "./AppShell";
import { RouteAccessibility } from "./RouteAccessibility";

const { askState } = vi.hoisted(() => ({
  askState: {
    conversations: [] as AskConversation[],
    loadingConversations: false,
    conversationsLoadError: "",
    activeId: "",
    busy: false,
    variantLoading: false,
    streaming: false,
    selectConversation: vi.fn(),
    startNew: vi.fn(),
    listConversations: vi.fn(),
    retryConversations: vi.fn(),
    setConversationArchived: vi.fn(),
  },
}));

vi.mock("../features/ask/AskContext", () => ({
  useAsk: () => askState,
}));

vi.mock("../features/memos/MemosContext", () => ({
  useMemos: () => ({ create: vi.fn() }),
}));

const account = {
  id: "a1",
  username: "felix",
  displayName: "Felix",
  createdAt: "2026-06-27T08:00:00Z",
  updatedAt: "2026-06-27T08:00:00Z",
};

function conversation(
  overrides: Partial<AskConversation> = {},
): AskConversation {
  return {
    id: "c1",
    title: "睡眠变化",
    status: "active",
    contextScope: "recent_30_days",
    headMessageId: null,
    pinnedAt: null,
    archivedAt: null,
    createdAt: "2026-07-10T08:00:00Z",
    updatedAt: "2026-07-10T08:00:00Z",
    deletedAt: null,
    ...overrides,
  };
}

function DirtyPage() {
  useUnsavedChangesRegistration(true);
  return (
    <main>
      <h1>有未保存内容</h1>
    </main>
  );
}

function GuardedDirtyPage() {
  return (
    <>
      <UnsavedNavigationGuard
        when
        title="记录尚未保存"
        description="离开后会丢失未保存的修改。"
      />
      <main>
        <h1>有未保存内容</h1>
      </main>
    </>
  );
}

function AskRoute() {
  const location = useLocation();
  return (
    <main>
      <h1>问答页面</h1>
      <span data-testid="ask-location">
        {location.pathname}
        {location.search}
      </span>
    </main>
  );
}

function renderShell({
  dirty = false,
  initialEntry = "/",
}: {
  dirty?: boolean;
  initialEntry?: string;
} = {}) {
  const user = userEvent.setup();
  render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <RouteAccessibility />
      <Routes>
        <Route element={<AppShell account={account} onSignOut={vi.fn()} />}>
          <Route
            index
            element={
              dirty ? (
                <DirtyPage />
              ) : (
                <main>
                  <h1>记录页面</h1>
                </main>
              )
            }
          />
          <Route
            path="timeline"
            element={
              <main>
                <h1>全部记录页面</h1>
              </main>
            }
          />
          <Route path="ask" element={<AskRoute />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
  return user;
}

function renderGuardedShell() {
  const user = userEvent.setup();
  const nativeRequest = globalThis.Request;
  globalThis.Request = class extends nativeRequest {
    constructor(input: RequestInfo | URL, init?: RequestInit) {
      super(input, init ? { ...init, signal: undefined } : init);
    }
  };
  const router = createMemoryRouter([
    {
      path: "/",
      element: (
        <>
          <RouteAccessibility />
          <AppShell account={account} onSignOut={vi.fn()} />
        </>
      ),
      children: [
        { index: true, element: <GuardedDirtyPage /> },
        {
          path: "timeline",
          element: (
            <main>
              <h1>全部记录页面</h1>
            </main>
          ),
        },
      ],
    },
  ]);
  const view = render(<RouterProvider router={router} />);
  return {
    user,
    dispose: () => {
      view.unmount();
      globalThis.Request = nativeRequest;
    },
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  window.localStorage.clear();
  document.body.style.overflow = "";
  askState.conversations = [];
  askState.loadingConversations = false;
  askState.conversationsLoadError = "";
  askState.activeId = "";
  askState.busy = false;
  askState.variantLoading = false;
  askState.streaming = false;
  askState.listConversations.mockResolvedValue([]);
  askState.setConversationArchived.mockResolvedValue(undefined);
});

describe("AppShell mobile navigation", () => {
  it("focuses the close button, traps focus, locks scroll, and restores focus", async () => {
    const user = renderShell();
    const menuButton = screen.getByRole("button", { name: "打开导航" });

    await user.click(menuButton);

    const dialog = screen.getByRole("dialog", { name: "导航" });
    const closeButton = within(dialog).getByRole("button", {
      name: "关闭导航",
    });
    await waitFor(() => expect(closeButton).toHaveFocus());
    expect(document.body).toHaveStyle({ overflow: "hidden" });
    expect(within(dialog).getByRole("complementary")).toHaveClass(
      "bg-gray-100",
    );

    const firstLink = within(dialog).getByRole("link", { name: /Sillage/ });
    expect(firstLink).toHaveClass("h-10", "-my-1");
    const themeButton = within(dialog).getByRole("button", {
      name: /切换主题/,
    });
    firstLink.focus();
    await user.tab({ shift: true });
    expect(themeButton).toHaveFocus();
    await user.tab();
    expect(firstLink).toHaveFocus();

    await user.click(closeButton);
    await waitFor(() =>
      expect(
        screen.queryByRole("dialog", { name: "导航" }),
      ).not.toBeInTheDocument(),
    );
    expect(document.body).not.toHaveStyle({ overflow: "hidden" });
    expect(menuButton).toHaveFocus();
  });

  it("closes on Escape and backdrop click", async () => {
    const user = renderShell();
    const menuButton = screen.getByRole("button", { name: "打开导航" });

    await user.click(menuButton);
    await user.keyboard("{Escape}");
    await waitFor(() => expect(menuButton).toHaveFocus());

    await user.click(menuButton);
    await user.click(screen.getByRole("button", { name: "关闭导航遮罩" }));
    await waitFor(() => expect(menuButton).toHaveFocus());
  });

  it("ignores quick-capture shortcuts while the mobile drawer is open", async () => {
    const user = renderShell();
    const menuButton = screen.getByRole("button", { name: "打开导航" });

    await user.click(menuButton);
    const drawer = screen.getByRole("dialog", { name: "导航" });

    await user.keyboard("{Control>}j{/Control}");
    await user.keyboard("{Meta>}j{/Meta}");

    expect(screen.getAllByRole("dialog")).toEqual([drawer]);
    expect(document.querySelectorAll('[aria-modal="true"]')).toHaveLength(1);

    await user.keyboard("{Escape}");

    await waitFor(() =>
      expect(
        screen.queryByRole("dialog", { name: "导航" }),
      ).not.toBeInTheDocument(),
    );
    expect(screen.queryByRole("dialog", { name: "速记" })).toBeNull();
    expect(menuButton).toHaveFocus();
  });

  it("closes Ask search with Escape without closing the mobile drawer", async () => {
    const user = renderShell();
    await user.click(screen.getByRole("button", { name: "打开导航" }));
    const drawer = screen.getByRole("dialog", { name: "导航" });
    await user.click(within(drawer).getByRole("button", { name: "搜索问答" }));
    expect(
      within(drawer).getByRole("searchbox", { name: "搜索问答" }),
    ).toHaveFocus();

    await user.keyboard("{Escape}");

    expect(screen.getByRole("dialog", { name: "导航" })).toBeInTheDocument();
    expect(
      within(drawer).queryByRole("searchbox", { name: "搜索问答" }),
    ).not.toBeInTheDocument();
    expect(
      within(drawer).getByRole("button", { name: "搜索问答" }),
    ).toHaveFocus();
  });

  it("closes on navigation and leaves a single main landmark", async () => {
    const user = renderShell();
    expect(screen.getAllByRole("main")).toHaveLength(1);

    const menuButton = screen.getByRole("button", { name: "打开导航" });
    await user.click(menuButton);
    await user.click(
      within(screen.getByRole("dialog", { name: "导航" })).getByRole("link", {
        name: "全部记录",
      }),
    );

    expect(await screen.findByText("全部记录页面")).toBeInTheDocument();
    expect(
      screen.queryByRole("dialog", { name: "导航" }),
    ).not.toBeInTheDocument();
    expect(screen.getAllByRole("main")).toHaveLength(1);
    await waitFor(() =>
      expect(
        screen.getByRole("heading", { name: "全部记录页面" }),
      ).toHaveFocus(),
    );
  });

  it("restores the menu trigger when the current route is selected", async () => {
    const user = renderShell({ initialEntry: "/timeline" });
    const menuButton = screen.getByRole("button", { name: "打开导航" });

    await user.click(menuButton);
    await user.click(
      within(screen.getByRole("dialog", { name: "导航" })).getByRole("link", {
        name: "全部记录",
      }),
    );

    await waitFor(() =>
      expect(
        screen.queryByRole("dialog", { name: "导航" }),
      ).not.toBeInTheDocument(),
    );
    expect(menuButton).toHaveFocus();
    expect(
      screen.getByRole("heading", { name: "全部记录页面" }),
    ).not.toHaveFocus();
  });

  it("restores the menu trigger for an in-page query change", async () => {
    const user = renderShell({ initialEntry: "/timeline?view=calendar" });
    const menuButton = screen.getByRole("button", { name: "打开导航" });

    await user.click(menuButton);
    await user.click(
      within(screen.getByRole("dialog", { name: "导航" })).getByRole("link", {
        name: "全部记录",
      }),
    );

    await waitFor(() =>
      expect(
        screen.queryByRole("dialog", { name: "导航" }),
      ).not.toBeInTheDocument(),
    );
    expect(menuButton).toHaveFocus();
    expect(
      screen.getByRole("heading", { name: "全部记录页面" }),
    ).not.toHaveFocus();
  });

  it("keeps a blocked drawer navigation open until leaving is confirmed", async () => {
    const { user, dispose } = renderGuardedShell();
    try {
      const menuButton = screen.getByRole("button", { name: "打开导航" });
      await user.click(menuButton);
      const drawer = screen.getByRole("dialog", { name: "导航" });
      const allRecords = within(drawer).getByRole("link", {
        name: "全部记录",
      });

      await user.click(allRecords);

      const confirmation = await screen.findByRole("alertdialog", {
        name: "记录尚未保存",
      });
      for (let frame = 0; frame < 2; frame += 1) {
        await act(
          () =>
            new Promise<void>((resolve) => {
              window.requestAnimationFrame(() => resolve());
            }),
        );
      }
      expect(drawer).toBeInTheDocument();
      expect(
        screen.getByRole("heading", { name: "有未保存内容" }),
      ).toBeVisible();

      await user.click(
        within(confirmation).getByRole("button", { name: "继续编辑" }),
      );
      await waitFor(() => expect(screen.queryByRole("alertdialog")).toBeNull());
      expect(drawer).toBeInTheDocument();
      expect(allRecords).toHaveFocus();

      await user.click(allRecords);
      await user.click(
        within(await screen.findByRole("alertdialog")).getByRole("button", {
          name: "离开此页",
        }),
      );

      expect(await screen.findByText("全部记录页面")).toBeInTheDocument();
      expect(screen.queryByRole("dialog", { name: "导航" })).toBeNull();
      await waitFor(() =>
        expect(
          screen.getByRole("heading", { name: "全部记录页面" }),
        ).toHaveFocus(),
      );
      expect(menuButton).not.toHaveFocus();
    } finally {
      dispose();
    }
  });

  it("keeps a quick-capture draft mounted across the Ask route", async () => {
    const user = renderShell();
    await user.click(screen.getByRole("button", { name: "速记" }));
    const quickDialog = screen.getByRole("dialog", { name: "速记" });
    await user.type(
      within(quickDialog).getByRole("textbox", { name: "速记内容" }),
      "跨页面速记",
    );
    await user.click(
      within(quickDialog).getByRole("button", { name: "关闭速记" }),
    );

    await user.click(screen.getByRole("link", { name: "开始问答" }));
    expect(await screen.findByText("问答页面")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "速记" })).toBeNull();

    await user.click(screen.getByRole("link", { name: "写记录" }));
    await user.click(screen.getByRole("button", { name: "速记" }));
    expect(screen.getByRole("textbox", { name: "速记内容" })).toHaveValue(
      "跨页面速记",
    );
  });

  it("dismisses sign-out confirmation without closing the mobile drawer", async () => {
    const user = renderShell({ dirty: true });
    await user.click(screen.getByRole("button", { name: "打开导航" }));
    const drawer = screen.getByRole("dialog", { name: "导航" });
    await user.click(within(drawer).getByText("Felix"));
    const signOut = within(drawer).getByRole("button", { name: "退出登录" });
    await user.click(signOut);
    expect(screen.getByRole("alertdialog")).toBeInTheDocument();

    await user.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByRole("alertdialog")).toBeNull());
    expect(screen.getByRole("dialog", { name: "导航" })).toBeInTheDocument();
    expect(signOut).toHaveFocus();
  });
});

describe("AppShell Ask navigation", () => {
  it("shows the initial conversation-list error instead of an empty list", async () => {
    askState.conversationsLoadError = "问答列表读取失败：网络异常";
    const user = renderShell();

    expect(screen.getByRole("alert")).toHaveTextContent(
      "问答列表读取失败：网络异常",
    );
    expect(screen.queryByText("还没有问答。")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "重试" }));
    expect(askState.retryConversations).toHaveBeenCalledTimes(1);
  });

  it("clears the search and closes it with Escape", async () => {
    const user = renderShell();

    await user.click(screen.getByRole("button", { name: "搜索问答" }));
    const input = screen.getByRole("searchbox", { name: "搜索问答" });
    await user.type(input, "睡眠");
    await user.click(screen.getByRole("button", { name: "清除问答搜索" }));
    expect(input).toHaveValue("");
    expect(input).toHaveFocus();

    await user.type(input, "状态");
    await user.keyboard("{Escape}");
    expect(
      screen.queryByRole("searchbox", { name: "搜索问答" }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "搜索问答" })).toHaveFocus();
  });

  it("debounces server search and ignores an aborted older response", async () => {
    const user = renderShell();
    let resolveFirst: ((value: AskConversation[]) => void) | undefined;
    let resolveSecond: ((value: AskConversation[]) => void) | undefined;
    askState.listConversations.mockImplementation(
      ({ query }: { query?: string }) =>
        new Promise<AskConversation[]>((resolve) => {
          if (query === "第一") {
            resolveFirst = resolve;
          } else if (query === "第二") {
            resolveSecond = resolve;
          }
        }),
    );

    await user.click(screen.getByRole("button", { name: "搜索问答" }));
    const input = screen.getByRole("searchbox", { name: "搜索问答" });
    await user.type(input, "第一");
    await waitFor(() => expect(askState.listConversations).toHaveBeenCalled(), {
      timeout: 2_000,
    });

    await user.clear(input);
    await user.type(input, "第二");
    await waitFor(
      () => expect(askState.listConversations).toHaveBeenCalledTimes(2),
      { timeout: 2_000 },
    );
    const firstSignal = askState.listConversations.mock.calls[0][1] as
      | AbortSignal
      | undefined;
    expect(firstSignal?.aborted).toBe(true);

    await act(async () => {
      resolveSecond?.([
        conversation({ id: "second", title: "第二次搜索结果" }),
      ]);
    });
    expect(await screen.findByText("第二次搜索结果")).toBeInTheDocument();

    await act(async () => {
      resolveFirst?.([
        conversation({ id: "first", title: "迟到的第一次结果" }),
      ]);
    });
    expect(screen.queryByText("迟到的第一次结果")).not.toBeInTheDocument();
  });

  it("loads archived conversations and can restore one", async () => {
    const user = renderShell();
    let finishRestore: ((value: AskConversation) => void) | undefined;
    const archived = conversation({
      id: "archived",
      title: "已经归档的问答",
      archivedAt: "2026-07-10T09:00:00Z",
    });
    askState.listConversations
      .mockResolvedValueOnce([archived])
      .mockResolvedValueOnce([]);
    askState.setConversationArchived.mockImplementation(
      () =>
        new Promise((resolve) => {
          finishRestore = resolve;
        }),
    );

    await user.click(screen.getByRole("button", { name: "查看已归档问答" }));

    expect(await screen.findByText("已经归档的问答")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "已归档问答" })).toBeVisible();
    expect(askState.listConversations).toHaveBeenCalledWith(
      { query: undefined, archived: true },
      expect.any(AbortSignal),
    );

    const restoreButton = screen.getByRole("button", {
      name: "移出归档：已经归档的问答",
    });
    await user.click(restoreButton);
    expect(restoreButton).toBeDisabled();
    expect(askState.setConversationArchived).toHaveBeenCalledWith(
      "archived",
      false,
    );
    await act(async () => {
      finishRestore?.({ ...archived, archivedAt: null });
    });
    await waitFor(() =>
      expect(screen.queryByText("已经归档的问答")).not.toBeInTheDocument(),
    );
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "返回问答" })).toHaveFocus(),
    );
  });

  it("clears the active conversation URL after archiving it", async () => {
    const active = conversation();
    askState.conversations = [active];
    askState.activeId = active.id;
    askState.setConversationArchived.mockResolvedValue({
      ...active,
      archivedAt: "2026-07-10T09:00:00Z",
    });
    const user = renderShell({ initialEntry: "/ask?conversation=c1" });

    expect(screen.getByTestId("ask-location")).toHaveTextContent(
      "/ask?conversation=c1",
    );
    await user.click(
      screen.getByRole("button", { name: "归档问答：睡眠变化" }),
    );

    await waitFor(() =>
      expect(screen.getByTestId("ask-location")).toHaveTextContent(/^\/ask$/),
    );
    expect(askState.setConversationArchived).toHaveBeenCalledWith("c1", true);
    expect(askState.startNew).toHaveBeenCalledTimes(1);
  });

  it("does not return to Ask when an archive finishes after navigation", async () => {
    const active = conversation();
    let finishArchive: ((value: AskConversation) => void) | undefined;
    askState.conversations = [active];
    askState.activeId = active.id;
    askState.setConversationArchived.mockImplementation(
      () =>
        new Promise((resolve) => {
          finishArchive = resolve;
        }),
    );
    const user = renderShell({ initialEntry: "/ask?conversation=c1" });

    const archiveButton = screen.getByRole("button", {
      name: "归档问答：睡眠变化",
    });
    await user.click(archiveButton);
    expect(archiveButton).toBeDisabled();
    await user.click(screen.getByRole("link", { name: "全部记录" }));
    expect(await screen.findByText("全部记录页面")).toBeInTheDocument();

    await act(async () => {
      finishArchive?.({
        ...active,
        archivedAt: "2026-07-10T09:00:00Z",
      });
    });

    expect(screen.getByText("全部记录页面")).toBeInTheDocument();
    expect(askState.startNew).not.toHaveBeenCalled();
  });

  it("disables search and archive controls while an answer is generating", () => {
    askState.conversations = [conversation()];
    askState.busy = true;
    renderShell();

    expect(screen.getByRole("button", { name: "搜索问答" })).toBeDisabled();
    expect(
      screen.getByRole("button", { name: "查看已归档问答" }),
    ).toBeDisabled();
    expect(
      screen.getByRole("button", { name: "归档问答：睡眠变化" }),
    ).toBeDisabled();
    expect(screen.getByRole("button", { name: "开始问答" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "睡眠变化" })).toBeDisabled();
    expect(
      screen.queryByRole("link", { name: "开始问答" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: "睡眠变化" }),
    ).not.toBeInTheDocument();
  });

  it("disables Ask navigation while an answer branch is being persisted", () => {
    askState.conversations = [conversation()];
    askState.variantLoading = true;
    renderShell();

    expect(screen.getByRole("button", { name: "搜索问答" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "开始问答" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "睡眠变化" })).toBeDisabled();
    expect(
      screen.queryByRole("link", { name: "睡眠变化" }),
    ).not.toBeInTheDocument();
  });
});
