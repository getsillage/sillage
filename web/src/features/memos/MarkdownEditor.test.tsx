import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { LanguageSwitcher } from "../../components/LanguageSwitcher";
import { I18nProvider } from "../../i18n/I18nProvider";
import { MarkdownEditor } from "./MarkdownEditor";

describe("MarkdownEditor locale feedback", () => {
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
    expect(screen.getByRole("textbox")).toHaveValue("保留的正文");
    expect(onChange).not.toHaveBeenCalled();
    expect(onUpload).toHaveBeenCalledTimes(1);
  });
});
