import { render, screen } from "@testing-library/react";
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
