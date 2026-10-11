import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {
  createMemoryRouter,
  MemoryRouter,
  Outlet,
  RouterProvider,
} from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { hasUnsavedChanges } from "../../components/UnsavedNavigationGuard";
import { I18nProvider } from "../../i18n/I18nProvider";
import { listMemos, uploadAttachment } from "../../lib/api";
import { todayISO } from "../../lib/date";
import { HomePage } from "./HomePage";
import { MemosProvider } from "./MemosContext";
import { QuickCapture } from "./QuickCapture";

vi.mock("../../lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../lib/api")>();
  return { ...actual, listMemos: vi.fn(), uploadAttachment: vi.fn() };
});

function renderQuickCaptureWithHome(initialPath = "/") {
  const user = userEvent.setup();
  const router = createMemoryRouter(
    [
      {
        element: (
          <>
            <Outlet />
            <QuickCapture onCapture={vi.fn()} />
          </>
        ),
        children: [
          { path: "/", element: <HomePage /> },
          { path: "/timeline", element: <main>全部记录</main> },
        ],
      },
    ],
    { initialEntries: [initialPath] },
  );
  const view = render(
    <I18nProvider>
      <MemosProvider token="t">
        <RouterProvider router={router} />
      </MemosProvider>
    </I18nProvider>,
  );
  return { user, router, ...view };
}

function renderQuickCapture(onCapture = vi.fn().mockResolvedValue(undefined)) {
  const user = userEvent.setup();
  const view = render(
    <I18nProvider>
      <MemoryRouter>
        <QuickCapture onCapture={onCapture} />
      </MemoryRouter>
    </I18nProvider>,
  );
  return { user, onCapture, ...view };
}

describe("QuickCapture", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    window.localStorage.clear();
    vi.mocked(listMemos).mockResolvedValue({ memos: [] });
  });

  it("opens the dialog from the floating button and exposes dialog semantics", async () => {
    const { user } = renderQuickCapture();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "速记" }));
    const dialog = screen.getByRole("dialog", { name: "速记" });
    expect(dialog).toHaveAttribute("aria-modal", "true");
    const editor = within(dialog).getByRole("textbox", { name: "速记内容" });
    expect(editor).toHaveAttribute("placeholder", "想记录什么？");
    expect(editor).toHaveClass(
      "focus-visible:ring-2",
      "focus-visible:ring-inset",
    );
  });

  it("closes on Escape", async () => {
    const { user } = renderQuickCapture();
    await user.click(screen.getByRole("button", { name: "速记" }));
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    await user.keyboard("{Escape}");
    await waitFor(() =>
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
    );
  });

  it("validates empty input and saves trimmed content", async () => {
    const { user, onCapture } = renderQuickCapture();
    await user.click(screen.getByRole("button", { name: "速记" }));

    await user.click(screen.getByRole("button", { name: "保存" }));
    expect(screen.getByText("想记录什么？")).toBeInTheDocument();
    expect(onCapture).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: "关闭通知" }));

    await user.type(
      screen.getByRole("textbox", { name: "速记内容" }),
      "新的一条",
    );
    await user.click(screen.getByRole("button", { name: "保存" }));
    await waitFor(() => expect(onCapture).toHaveBeenCalledWith("新的一条"));
    expect(
      (await screen.findByText("速记已保存")).closest('[role="status"]'),
    ).toBeInTheDocument();
  });

  it("ignores repeated save shortcuts while a capture is pending", async () => {
    let finishCapture: (() => void) | undefined;
    const onCapture = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          finishCapture = resolve;
        }),
    );
    const { user } = renderQuickCapture(onCapture);
    await user.click(screen.getByRole("button", { name: "速记" }));
    await user.type(
      screen.getByRole("textbox", { name: "速记内容" }),
      "  只保存一次  ",
    );

    await user.keyboard("{Control>}{Enter}{/Control}");
    await user.keyboard("{Control>}{Enter}{/Control}");

    expect(onCapture).toHaveBeenCalledTimes(1);
    expect(onCapture).toHaveBeenCalledWith("只保存一次");
    expect(screen.getByRole("textbox", { name: "速记内容" })).toBeDisabled();
    const trigger = screen.getByRole("button", { name: "速记" });
    expect(trigger).toBeDisabled();
    await user.click(trigger);
    expect(screen.getByRole("dialog", { name: "速记" })).toBeInTheDocument();
    finishCapture?.();
    await waitFor(() =>
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
    );
  });

  it("restores its global draft after remounting", async () => {
    const first = renderQuickCapture();
    await first.user.click(screen.getByRole("button", { name: "速记" }));
    await first.user.type(
      screen.getByRole("textbox", { name: "速记内容" }),
      "刷新后继续写",
    );
    await waitFor(() =>
      expect(window.localStorage.getItem("sillage.quick-capture-draft")).toBe(
        "刷新后继续写",
      ),
    );
    first.unmount();

    const second = renderQuickCapture();
    await second.user.click(screen.getByRole("button", { name: "速记" }));
    expect(screen.getByRole("textbox", { name: "速记内容" })).toHaveValue(
      "刷新后继续写",
    );
  });

  it("registers a non-empty draft for unload and clears it only after save", async () => {
    const { user } = renderQuickCapture();
    await user.click(screen.getByRole("button", { name: "速记" }));
    await user.type(
      screen.getByRole("textbox", { name: "速记内容" }),
      "待保存速记",
    );

    await waitFor(() => expect(hasUnsavedChanges()).toBe(true));
    const dirtyUnload = new Event("beforeunload", { cancelable: true });
    expect(window.dispatchEvent(dirtyUnload)).toBe(false);
    expect(window.localStorage.getItem("sillage.quick-capture-draft")).toBe(
      "待保存速记",
    );

    await user.click(screen.getByRole("button", { name: "保存" }));
    await waitFor(() => expect(hasUnsavedChanges()).toBe(false));
    expect(
      window.localStorage.getItem("sillage.quick-capture-draft"),
    ).toBeNull();
    const savedUnload = new Event("beforeunload", { cancelable: true });
    expect(window.dispatchEvent(savedUnload)).toBe(true);
  });

  it("hands quick text to the already mounted home editor and focuses it", async () => {
    const { user } = renderQuickCaptureWithHome();
    const originalEditor = screen.getByRole("textbox", { name: "记录内容" });
    await user.click(screen.getByRole("button", { name: "速记" }));
    await user.type(
      screen.getByRole("textbox", { name: "速记内容" }),
      "继续写这条速记",
    );

    await user.click(screen.getByRole("link", { name: "写得更完整" }));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    const homeEditor = screen.getByRole("textbox", { name: "记录内容" });
    expect(homeEditor).not.toBe(originalEditor);
    expect(homeEditor).toHaveValue("继续写这条速记");
    await waitFor(() => expect(homeEditor).toHaveFocus());
    expect(
      window.localStorage.getItem("sillage.quick-capture-draft"),
    ).toBeNull();
    await user.type(homeEditor, "，再补充一些");
    expect(
      JSON.parse(
        window.localStorage.getItem("sillage.entry-draft.memo:new") ?? "{}",
      ).content,
    ).toBe("继续写这条速记，再补充一些");
  });

  it("hands quick text to home when navigating from another page", async () => {
    const { user, router } = renderQuickCaptureWithHome("/timeline");
    await user.click(screen.getByRole("button", { name: "速记" }));
    await user.type(
      screen.getByRole("textbox", { name: "速记内容" }),
      "从历史继续写",
    );

    await user.click(screen.getByRole("link", { name: "写得更完整" }));

    expect(
      await screen.findByRole("textbox", { name: "记录内容" }),
    ).toHaveValue("从历史继续写");
    expect(router.state.location.pathname).toBe("/");
    expect(
      window.localStorage.getItem("sillage.quick-capture-draft"),
    ).toBeNull();
  });

  it("keeps both drafts and explains why an existing home draft blocks the handoff", async () => {
    const storedDraft = {
      version: 2,
      content: "原有完整草稿",
      entryDate: todayISO(),
      baseVersion: null,
    };
    window.localStorage.setItem(
      "sillage.entry-draft.memo:new",
      JSON.stringify(storedDraft),
    );
    const { user } = renderQuickCaptureWithHome();
    const homeEditor = screen.getByRole("textbox", { name: "记录内容" });
    await user.click(screen.getByRole("button", { name: "速记" }));
    await user.type(
      screen.getByRole("textbox", { name: "速记内容" }),
      "另一个速记",
    );

    await user.click(screen.getByRole("link", { name: "写得更完整" }));

    const dialog = screen.getByRole("dialog", { name: "速记" });
    expect(within(dialog).getByRole("alert")).toHaveTextContent(
      "写记录中已有未保存草稿",
    );
    expect(
      within(dialog).getByRole("textbox", { name: "速记内容" }),
    ).toHaveValue("另一个速记");
    expect(homeEditor).toHaveValue("原有完整草稿");
    expect(
      JSON.parse(
        window.localStorage.getItem("sillage.entry-draft.memo:new") ?? "{}",
      ),
    ).toEqual(storedDraft);
    expect(window.localStorage.getItem("sillage.quick-capture-draft")).toBe(
      "另一个速记",
    );
  });

  it("preserves an active home upload when quick capture attempts a handoff", async () => {
    let finishUpload:
      | ((result: Awaited<ReturnType<typeof uploadAttachment>>) => void)
      | undefined;
    vi.mocked(uploadAttachment).mockImplementation(
      () =>
        new Promise((resolve) => {
          finishUpload = resolve;
        }),
    );
    const { user, container } = renderQuickCaptureWithHome();
    const homeEditor = screen.getByRole("textbox", { name: "记录内容" });
    await user.upload(
      container.querySelector<HTMLInputElement>(
        'input[type="file"]',
      ) as HTMLInputElement,
      new File(["image"], "photo.png", { type: "image/png" }),
    );
    await user.click(screen.getByRole("button", { name: "速记" }));
    await user.type(
      screen.getByRole("textbox", { name: "速记内容" }),
      "等待附件后再写",
    );

    await user.click(screen.getByRole("link", { name: "写得更完整" }));

    expect(
      within(screen.getByRole("dialog")).getByRole("alert"),
    ).toHaveTextContent("写记录正在保存或上传附件");
    expect(screen.getByRole("textbox", { name: "记录内容" })).toBe(homeEditor);
    expect(
      window.localStorage.getItem("sillage.entry-draft.memo:new"),
    ).toBeNull();
    await act(async () => {
      finishUpload?.({
        attachment: {
          uid: "a1",
          filename: "photo.png",
          contentType: "image/png",
          size: 5,
          url: "/file/attachments/a1",
          sha256: null,
        },
      });
    });
    expect(homeEditor).toHaveValue("\n![photo.png](/file/attachments/a1)\n");
    expect(screen.getByRole("textbox", { name: "速记内容" })).toHaveValue(
      "等待附件后再写",
    );
  });

  it("protects an in-memory home draft even when its stored copy is missing", async () => {
    const { user } = renderQuickCaptureWithHome();
    const homeEditor = screen.getByRole("textbox", { name: "记录内容" });
    await user.type(homeEditor, "只保存在编辑器内存中的正文");
    window.localStorage.removeItem("sillage.entry-draft.memo:new");
    await user.click(screen.getByRole("button", { name: "速记" }));
    await user.type(
      screen.getByRole("textbox", { name: "速记内容" }),
      "另外的速记",
    );

    await user.click(screen.getByRole("link", { name: "写得更完整" }));

    expect(
      within(screen.getByRole("dialog")).getByRole("alert"),
    ).toHaveTextContent("写记录中已有未保存草稿");
    expect(screen.getByRole("textbox", { name: "记录内容" })).toBe(homeEditor);
    expect(homeEditor).toHaveValue("只保存在编辑器内存中的正文");
    expect(screen.getByRole("textbox", { name: "速记内容" })).toHaveValue(
      "另外的速记",
    );
    expect(
      window.localStorage.getItem("sillage.entry-draft.memo:new"),
    ).toBeNull();
    expect(window.localStorage.getItem("sillage.quick-capture-draft")).toBe(
      "另外的速记",
    );
  });

  it("keeps quick text visible when storing the full draft fails", async () => {
    const setItem = window.localStorage.setItem;
    const { user } = renderQuickCaptureWithHome();
    await user.click(screen.getByRole("button", { name: "速记" }));
    await user.type(
      screen.getByRole("textbox", { name: "速记内容" }),
      "不能丢失的速记",
    );
    const storageSpy = vi
      .spyOn(window.localStorage, "setItem")
      .mockImplementation((key, value) => {
        if (key === "sillage.entry-draft.memo:new") {
          throw new Error("Storage unavailable");
        }
        setItem(key, value);
      });

    await user.click(screen.getByRole("link", { name: "写得更完整" }));

    expect(
      within(screen.getByRole("dialog")).getByRole("alert"),
    ).toHaveTextContent("暂时无法将速记移到写记录");
    expect(screen.getByRole("textbox", { name: "速记内容" })).toHaveValue(
      "不能丢失的速记",
    );
    expect(screen.getByRole("textbox", { name: "记录内容" })).toHaveValue("");
    expect(window.localStorage.getItem("sillage.quick-capture-draft")).toBe(
      "不能丢失的速记",
    );
    storageSpy.mockRestore();
  });
});
