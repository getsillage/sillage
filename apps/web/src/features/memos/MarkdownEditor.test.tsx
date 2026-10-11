import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { LanguageSwitcher } from "../../components/LanguageSwitcher";
import { I18nProvider } from "../../i18n/I18nProvider";
import { MarkdownEditor } from "./MarkdownEditor";
import type { UploadedAttachment } from "./MemosContext";

function EditableHarness({
  onUpload,
}: {
  onUpload: (file: File) => Promise<UploadedAttachment>;
}) {
  const [content, setContent] = useState("原有正文");
  return (
    <I18nProvider>
      <MarkdownEditor
        value={content}
        onChange={setContent}
        onUpload={onUpload}
      />
    </I18nProvider>
  );
}

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

  it("inserts each uploaded file immediately and preserves typing between uploads", async () => {
    const user = userEvent.setup();
    let finishSecond: ((attachment: UploadedAttachment) => void) | undefined;
    const onUpload = vi
      .fn<(file: File) => Promise<UploadedAttachment>>()
      .mockResolvedValueOnce({
        url: "/file/attachments/first",
        filename: "first.txt",
        isImage: false,
      })
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            finishSecond = resolve;
          }),
      );
    const { container } = render(<EditableHarness onUpload={onUpload} />);
    const editor = screen.getByRole("textbox", {
      name: "记录内容",
    }) as HTMLTextAreaElement;
    editor.setSelectionRange(editor.value.length, editor.value.length);
    fireEvent.select(editor);
    const fileInput = container.querySelector<HTMLInputElement>(
      'input[type="file"]',
    ) as HTMLInputElement;

    await user.upload(fileInput, [
      new File(["first"], "first.txt", { type: "text/plain" }),
      new File(["second"], "second.txt", { type: "text/plain" }),
    ]);

    await waitFor(() => expect(onUpload).toHaveBeenCalledTimes(2));
    expect(editor).toHaveValue(
      "原有正文\n[first.txt](/file/attachments/first)\n",
    );
    expect(fileInput).toBeDisabled();
    await user.type(editor, "上传时补充的正文");

    await act(async () => {
      finishSecond?.({
        url: "/file/attachments/second",
        filename: "second.txt",
        isImage: false,
      });
    });

    expect(editor).toHaveValue(
      "原有正文\n[first.txt](/file/attachments/first)\n上传时补充的正文\n[second.txt](/file/attachments/second)\n",
    );
    expect(await screen.findByRole("status")).toHaveTextContent(
      "2 个附件已插入",
    );
    expect(fileInput).toBeEnabled();
  });

  it("keeps successful attachment links after a later failure and retries only remaining files", async () => {
    const user = userEvent.setup();
    const first = new File(["first"], "first.txt", { type: "text/plain" });
    const second = new File(["second"], "second.txt", { type: "text/plain" });
    const third = new File(["third"], "third.txt", { type: "text/plain" });
    const onUpload = vi
      .fn<(file: File) => Promise<UploadedAttachment>>()
      .mockResolvedValueOnce({
        url: "/file/attachments/first",
        filename: first.name,
        isImage: false,
      })
      .mockRejectedValueOnce(new Error("第二个附件上传失败"))
      .mockResolvedValueOnce({
        url: "/file/attachments/second",
        filename: second.name,
        isImage: false,
      })
      .mockResolvedValueOnce({
        url: "/file/attachments/third",
        filename: third.name,
        isImage: false,
      });
    const { container } = render(<EditableHarness onUpload={onUpload} />);
    const editor = screen.getByRole("textbox", {
      name: "记录内容",
    }) as HTMLTextAreaElement;
    editor.setSelectionRange(editor.value.length, editor.value.length);
    fireEvent.select(editor);
    const fileInput = container.querySelector<HTMLInputElement>(
      'input[type="file"]',
    ) as HTMLInputElement;

    await user.upload(fileInput, [first, second, third]);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "第二个附件上传失败",
    );
    expect(editor).toHaveValue(
      "原有正文\n[first.txt](/file/attachments/first)\n",
    );
    expect(onUpload).toHaveBeenCalledTimes(2);
    expect(fileInput).toBeEnabled();
    expect(fileInput).toHaveValue("");

    await user.upload(fileInput, [second, third]);

    await waitFor(() => expect(fileInput).toBeEnabled());
    expect(editor).toHaveValue(
      "原有正文\n[first.txt](/file/attachments/first)\n\n[second.txt](/file/attachments/second)\n\n[third.txt](/file/attachments/third)\n",
    );
    expect(onUpload.mock.calls.map(([file]) => file.name)).toEqual([
      first.name,
      second.name,
      second.name,
      third.name,
    ]);
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
