import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { LanguageSwitcher } from "../../components/LanguageSwitcher";
import { I18nProvider } from "../../i18n/I18nProvider";
import { MarkdownEditor } from "./MarkdownEditor";

describe("MarkdownEditor locale feedback", () => {
  it("keeps the labeled editor stable across preview mode", async () => {
    const user = userEvent.setup();
    render(
      <I18nProvider>
        <MarkdownEditor
          value="预览正文"
          onChange={vi.fn()}
          onUpload={vi.fn()}
        />
      </I18nProvider>,
    );

    const editor = screen.getByRole("textbox", {
      name: "记录内容",
    }) as HTMLTextAreaElement;
    await user.click(screen.getByRole("button", { name: "预览" }));

    expect(editor).not.toBeVisible();
    expect(editor.labels?.[0]).not.toBeVisible();
    expect(screen.queryByRole("textbox", { name: "记录内容" })).toBeNull();
    expect(screen.getByText("预览正文", { selector: "p" })).toBeVisible();

    await user.click(screen.getByRole("button", { name: "编辑" }));

    expect(screen.getByRole("textbox", { name: "记录内容" })).toHaveValue(
      "预览正文",
    );
  });

  it("shows a toast after inserting an attachment", async () => {
    const user = userEvent.setup();
    const onUpload = vi.fn().mockResolvedValue({
      url: "/file/attachments/a1",
      filename: "note.txt",
      isImage: false,
    });
    const onChange = vi.fn();
    const { container } = render(
      <I18nProvider>
        <MarkdownEditor value="正文" onChange={onChange} onUpload={onUpload} />
      </I18nProvider>,
    );
    const editor = screen.getByRole("textbox", { name: "记录内容" });
    expect(editor).toHaveAttribute("placeholder", "写下想记录的内容…");
    expect(editor).toHaveClass(
      "focus-visible:ring-2",
      "focus-visible:ring-inset",
    );
    const fileInput =
      container.querySelector<HTMLInputElement>('input[type="file"]');

    await user.upload(
      fileInput as HTMLInputElement,
      new File(["content"], "note.txt", { type: "text/plain" }),
    );

    expect(await screen.findByRole("status")).toHaveTextContent("附件已插入");
    expect(onChange).toHaveBeenCalledWith(
      expect.stringContaining("[note.txt](/file/attachments/a1)"),
    );
  });

  it("preserves text when an upload starts before a controlled rerender", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const onUpload = vi.fn().mockResolvedValue({
      url: "/file/attachments/race",
      filename: "race.txt",
      isImage: false,
    });
    const { container } = render(
      <I18nProvider>
        <MarkdownEditor value="" onChange={onChange} onUpload={onUpload} />
      </I18nProvider>,
    );
    const editor = screen.getByRole("textbox", {
      name: "记录内容",
    }) as HTMLTextAreaElement;
    fireEvent.change(editor, { target: { value: "不能丢失的正文" } });
    onChange.mockClear();

    const fileInput =
      container.querySelector<HTMLInputElement>('input[type="file"]');
    await user.upload(
      fileInput as HTMLInputElement,
      new File(["content"], "race.txt", { type: "text/plain" }),
    );

    expect(onChange).toHaveBeenLastCalledWith(
      expect.stringContaining("不能丢失的正文"),
    );
    expect(onChange).toHaveBeenLastCalledWith(
      expect.stringContaining("[race.txt](/file/attachments/race)"),
    );
  });

  it("uses the last editor selection when file input focus mutates the DOM selection", async () => {
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
    const onChange = vi.fn();
    const { container } = render(
      <I18nProvider>
        <MarkdownEditor
          value="不能被附件替换的正文"
          onChange={onChange}
          onUpload={onUpload}
        />
      </I18nProvider>,
    );
    const editor = screen.getByRole("textbox", {
      name: "记录内容",
    }) as HTMLTextAreaElement;
    editor.setSelectionRange(editor.value.length, editor.value.length);
    fireEvent.select(editor);

    const fileInput =
      container.querySelector<HTMLInputElement>('input[type="file"]');
    fireEvent.change(fileInput as HTMLInputElement, {
      target: {
        files: [new File(["content"], "focus.txt", { type: "text/plain" })],
      },
    });
    expect(onUpload).toHaveBeenCalledTimes(1);

    // Firefox can report the blurred textarea as fully selected while the
    // hidden file input owns focus. That DOM-only mutation must not replace
    // the user's content when the asynchronous upload completes.
    editor.setSelectionRange(0, editor.value.length);
    finishUpload?.({
      url: "/file/attachments/focus",
      filename: "focus.txt",
      isImage: false,
    });

    await screen.findByRole("status");
    expect(onChange).toHaveBeenLastCalledWith(
      "不能被附件替换的正文\n[focus.txt](/file/attachments/focus)\n",
    );
  });

  it("clears an upload error without changing editor content", async () => {
    const user = userEvent.setup();
    const onUpload = vi.fn().mockRejectedValue(new Error("上传失败"));
    const onChange = vi.fn();
    const { container } = render(
      <I18nProvider>
        <LanguageSwitcher compact />
        <MarkdownEditor
          value="保留的正文"
          onChange={onChange}
          onUpload={onUpload}
        />
      </I18nProvider>,
    );
    const fileInput =
      container.querySelector<HTMLInputElement>('input[type="file"]');
    expect(fileInput).not.toBeNull();

    await user.upload(
      fileInput as HTMLInputElement,
      new File(["content"], "note.txt", { type: "text/plain" }),
    );
    expect(await screen.findByText("上传失败")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "English" }));

    expect(screen.queryByText("上传失败")).not.toBeInTheDocument();
    const editor = screen.getByRole("textbox", { name: "Record content" });
    expect(editor).toHaveValue("保留的正文");
    expect(editor).toHaveAttribute(
      "placeholder",
      "Write what you want to remember...",
    );
    expect(onChange).not.toHaveBeenCalled();
    expect(onUpload).toHaveBeenCalledTimes(1);
  });
});
