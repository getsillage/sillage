import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactElement } from "react";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Memo } from "../lib/api";
import { EntryCard } from "./EntryCard";
import { EntryComposer } from "./EntryComposer";
import { LocalDateTime } from "./LocalDateTime";
import { Markdown } from "./Markdown";
import { Sidebar } from "./Sidebar";
import { ThemeToggle } from "./ThemeToggle";

const { hasUnsavedChangesMock } = vi.hoisted(() => ({
  hasUnsavedChangesMock: vi.fn(),
}));

vi.mock("../state/AskContext", () => ({
  useAsk: () => ({
    conversations: [],
    activeId: "",
    startNew: vi.fn(),
  }),
}));

vi.mock("./UnsavedNavigationGuard", () => ({
  UnsavedNavigationGuard: () => null,
  hasUnsavedChanges: hasUnsavedChangesMock,
  useUnsavedChangesRegistration: () => undefined,
}));

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

function renderEntryComposer(element: ReactElement) {
  return render(element);
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

describe("ThemeToggle", () => {
  beforeEach(() => {
    window.localStorage.clear();
    document.documentElement.classList.remove("dark");
    document.documentElement.removeAttribute("data-theme");
    document.documentElement.style.removeProperty("color-scheme");
  });

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

  it("keeps every mounted toggle's icon and label synchronized", async () => {
    const user = userEvent.setup();
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
  });
});

describe("Sidebar", () => {
  beforeEach(() => {
    hasUnsavedChangesMock.mockReturnValue(false);
    document.body.style.overflow = "";
  });

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

  it("signs out immediately when there are no unsaved changes", async () => {
    const user = userEvent.setup();
    const onSignOut = vi.fn();
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
          onSignOut={onSignOut}
        />
      </MemoryRouter>,
    );

    await user.click(screen.getByText("Felix"));
    await user.click(screen.getByRole("button", { name: "退出登录" }));
    expect(onSignOut).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });

  it("confirms sign-out while unsaved changes are active", async () => {
    const user = userEvent.setup();
    const onSignOut = vi.fn();
    hasUnsavedChangesMock.mockReturnValue(true);
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
          onSignOut={onSignOut}
        />
      </MemoryRouter>,
    );

    await user.click(screen.getByText("Felix"));
    const signOut = screen.getByRole("button", { name: "退出登录" });
    await user.click(signOut);

    const dialog = screen.getByRole("alertdialog", {
      name: "仍要退出登录？",
    });
    expect(dialog.closest("aside")).toBeNull();
    expect(onSignOut).not.toHaveBeenCalled();
    expect(document.body).toHaveStyle({ overflow: "hidden" });
    expect(
      within(dialog).getByRole("button", { name: "继续编辑" }),
    ).toHaveFocus();
    await user.tab();
    expect(
      within(dialog).getByRole("button", { name: "仍然退出" }),
    ).toHaveFocus();
    await user.tab();
    expect(
      within(dialog).getByRole("button", { name: "继续编辑" }),
    ).toHaveFocus();

    await user.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByRole("alertdialog")).toBeNull());
    expect(signOut).toHaveFocus();
    expect(document.body).not.toHaveStyle({ overflow: "hidden" });

    await user.click(signOut);
    await user.click(screen.getByRole("button", { name: "仍然退出" }));
    expect(onSignOut).toHaveBeenCalledTimes(1);
  });
});

describe("EntryComposer", () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it("validates empty content and submits typed content", async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const onUpload = vi.fn();
    renderEntryComposer(
      <EntryComposer
        draftKey="memo:new"
        onSubmit={onSubmit}
        onUpload={onUpload}
      />,
    );

    await user.click(screen.getByRole("button", { name: "保存" }));
    expect(screen.getByText("先写下要保存的内容")).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();

    await user.type(screen.getByRole("textbox"), "今天写点东西");
    await user.click(screen.getByRole("button", { name: "保存" }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit.mock.calls[0][0].content).toBe("今天写点东西");
  });

  it("restores only the draft stored for the same record", async () => {
    const user = userEvent.setup();
    const first = renderEntryComposer(
      <EntryComposer
        draftKey="memo:m1"
        mode="edit"
        initialContent="服务端正文一"
        initialEntryDate="2026-06-27"
        onSubmit={vi.fn()}
        onUpload={vi.fn()}
      />,
    );

    await user.clear(screen.getByRole("textbox"));
    await user.type(screen.getByRole("textbox"), "记录一的未保存修改");
    await waitFor(() => expect(window.localStorage.length).toBe(1));
    first.unmount();

    const restored = renderEntryComposer(
      <EntryComposer
        draftKey="memo:m1"
        mode="edit"
        initialContent="服务端正文一"
        initialEntryDate="2026-06-27"
        onSubmit={vi.fn()}
        onUpload={vi.fn()}
      />,
    );
    expect(screen.getByRole("textbox")).toHaveValue("记录一的未保存修改");
    expect(screen.getByText("已恢复上次未保存的草稿")).toBeInTheDocument();

    restored.unmount();
  });

  it("does not treat initial edit content as a draft or leak another record's draft", async () => {
    const user = userEvent.setup();
    const first = renderEntryComposer(
      <EntryComposer
        draftKey="memo:m1"
        mode="edit"
        initialContent="记录一正文"
        initialEntryDate="2026-06-27"
        onSubmit={vi.fn()}
        onUpload={vi.fn()}
      />,
    );

    expect(window.localStorage.length).toBe(0);
    const cleanUnload = new Event("beforeunload", { cancelable: true });
    expect(window.dispatchEvent(cleanUnload)).toBe(true);

    await user.type(screen.getByRole("textbox"), "修改");
    await waitFor(() => expect(window.localStorage.length).toBe(1));
    first.unmount();

    renderEntryComposer(
      <EntryComposer
        draftKey="memo:m2"
        mode="edit"
        initialContent="记录二正文"
        initialEntryDate="2026-06-28"
        onSubmit={vi.fn()}
        onUpload={vi.fn()}
      />,
    );
    expect(screen.getByRole("textbox")).toHaveValue("记录二正文");
    expect(
      screen.queryByText("已恢复上次未保存的草稿"),
    ).not.toBeInTheDocument();
  });

  it("clears a saved draft and removes the unload warning", async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    renderEntryComposer(
      <EntryComposer
        draftKey="memo:new"
        onSubmit={onSubmit}
        onUpload={vi.fn()}
      />,
    );

    await user.type(screen.getByRole("textbox"), "准备保存的草稿");
    await waitFor(() => expect(window.localStorage.length).toBe(1));
    const dirtyUnload = new Event("beforeunload", { cancelable: true });
    expect(window.dispatchEvent(dirtyUnload)).toBe(false);

    await user.click(screen.getByRole("button", { name: "保存" }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(window.localStorage.length).toBe(0));
    expect(screen.getByRole("textbox")).toHaveValue("");

    const savedUnload = new Event("beforeunload", { cancelable: true });
    expect(window.dispatchEvent(savedUnload)).toBe(true);
  });

  it("locks every editing control and keeps the draft while save is pending", async () => {
    const user = userEvent.setup();
    let finishSubmit: (() => void) | undefined;
    const onSubmit = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          finishSubmit = resolve;
        }),
    );
    const { container } = renderEntryComposer(
      <EntryComposer
        draftKey="memo:new"
        initialEntryDate="2026-07-10"
        onSubmit={onSubmit}
        onUpload={vi.fn()}
        onCancel={vi.fn()}
      />,
    );

    const editor = screen.getByRole("textbox");
    const date = screen.getByLabelText("日期");
    await user.type(editor, "提交时的正文");
    await waitFor(() =>
      expect(
        window.localStorage.getItem("sillage.entry-draft.memo:new"),
      ).not.toBeNull(),
    );

    await user.click(screen.getByRole("button", { name: "保存" }));
    expect(
      await screen.findByRole("button", { name: "保存中…" }),
    ).toBeDisabled();
    expect(editor).toBeDisabled();
    expect(date).toBeDisabled();
    expect(screen.getByRole("button", { name: "编辑" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "预览" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "添加附件" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "取消" })).toBeDisabled();
    expect(
      container.querySelector<HTMLInputElement>('input[type="file"]'),
    ).toBeDisabled();

    await user.type(editor, "不应写入的晚输入");
    expect(editor).toHaveValue("提交时的正文");
    expect(date).toHaveValue("2026-07-10");
    expect(
      JSON.parse(
        window.localStorage.getItem("sillage.entry-draft.memo:new") ?? "{}",
      ),
    ).toMatchObject({
      content: "提交时的正文",
      entryDate: "2026-07-10",
    });

    finishSubmit?.();
    await waitFor(() => expect(window.localStorage.length).toBe(0));
  });

  it("confirms before cancelling edits and clears the discarded draft", async () => {
    const user = userEvent.setup();
    const onCancel = vi.fn();
    renderEntryComposer(
      <EntryComposer
        draftKey="memo:m1"
        mode="edit"
        initialContent="服务端正文"
        initialEntryDate="2026-06-27"
        initialVersion={1}
        onSubmit={vi.fn()}
        onUpload={vi.fn()}
        onCancel={onCancel}
      />,
    );

    await user.type(screen.getByRole("textbox"), " 本地修改");
    await waitFor(() =>
      expect(
        window.localStorage.getItem("sillage.entry-draft.memo:m1"),
      ).not.toBeNull(),
    );
    const cancel = screen.getByRole("button", { name: "取消" });
    await user.click(cancel);

    expect(screen.getByRole("alertdialog")).toHaveTextContent(
      "放弃未保存的修改？",
    );
    expect(screen.getByRole("button", { name: "继续编辑" })).toHaveFocus();
    expect(onCancel).not.toHaveBeenCalled();
    await user.keyboard("{Escape}");
    await waitFor(() =>
      expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument(),
    );
    expect(cancel).toHaveFocus();

    await user.click(cancel);
    await user.click(screen.getByRole("button", { name: "继续编辑" }));
    expect(cancel).toHaveFocus();

    await user.click(cancel);
    await user.click(screen.getByRole("button", { name: "放弃修改" }));
    expect(onCancel).toHaveBeenCalledTimes(1);
    expect(
      window.localStorage.getItem("sillage.entry-draft.memo:m1"),
    ).toBeNull();
  });

  it("waits for an attachment upload before saving its markdown", async () => {
    const user = userEvent.setup();
    let finishUpload:
      | ((attachment: {
          url: string;
          filename: string;
          isImage: boolean;
        }) => void)
      | undefined;
    const onUpload = vi.fn(
      () =>
        new Promise<{
          url: string;
          filename: string;
          isImage: boolean;
        }>((resolve) => {
          finishUpload = resolve;
        }),
    );
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const { container } = renderEntryComposer(
      <EntryComposer
        draftKey="memo:new"
        onSubmit={onSubmit}
        onUpload={onUpload}
        onCancel={vi.fn()}
      />,
    );

    await user.type(screen.getByRole("textbox"), "附件记录");
    const fileInput =
      container.querySelector<HTMLInputElement>('input[type="file"]');
    expect(fileInput).not.toBeNull();
    await user.upload(
      fileInput as HTMLInputElement,
      new File(["a"], "说明.txt"),
    );

    expect(
      await screen.findByRole("button", { name: "上传中" }),
    ).toBeDisabled();
    expect(screen.getByRole("button", { name: "附件上传中…" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "取消" })).toBeDisabled();
    await user.click(screen.getByRole("button", { name: "附件上传中…" }));
    expect(onSubmit).not.toHaveBeenCalled();

    finishUpload?.({
      url: "/file/attachments/a/说明.txt",
      filename: "说明.txt",
      isImage: false,
    });
    await waitFor(() =>
      expect(screen.getByRole("textbox")).toHaveValue(
        "附件记录\n[说明.txt](/file/attachments/a/说明.txt)\n",
      ),
    );

    await user.click(screen.getByRole("button", { name: "保存" }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit.mock.calls[0][0].content).toContain(
      "[说明.txt](/file/attachments/a/说明.txt)",
    );
  });

  it("asks before restoring a draft based on an older server version", async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();
    window.localStorage.setItem(
      "sillage.entry-draft.memo:m1",
      JSON.stringify({
        version: 2,
        content: "旧版本上的本地草稿",
        entryDate: "2026-06-27",
        baseVersion: 1,
      }),
    );

    renderEntryComposer(
      <EntryComposer
        draftKey="memo:m1"
        mode="edit"
        initialContent="其他客户端更新后的正文"
        initialEntryDate="2026-06-28"
        initialVersion={2}
        onSubmit={onSubmit}
        onUpload={vi.fn()}
      />,
    );

    expect(screen.getByRole("textbox")).toHaveValue("其他客户端更新后的正文");
    expect(screen.getByText("这条记录已在其他位置更新")).toBeInTheDocument();
    const submit = screen.getByRole("button", { name: "保存" });
    expect(submit).toBeDisabled();
    await user.click(submit);
    expect(onSubmit).not.toHaveBeenCalled();
    expect(
      window.localStorage.getItem("sillage.entry-draft.memo:m1"),
    ).not.toBeNull();

    await user.click(screen.getByRole("button", { name: "恢复我的草稿" }));
    expect(screen.getByRole("textbox")).toHaveValue("旧版本上的本地草稿");
    expect(submit).toBeEnabled();
    expect(
      screen.getByText("已恢复本地草稿，请确认后保存"),
    ).toBeInTheDocument();
  });
});
