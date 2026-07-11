import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactElement } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { EntryComposer } from "./EntryComposer";

vi.mock("../../components/UnsavedNavigationGuard", () => ({
  UnsavedNavigationGuard: () => null,
}));

function renderEntryComposer(element: ReactElement) {
  return render(element);
}

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
