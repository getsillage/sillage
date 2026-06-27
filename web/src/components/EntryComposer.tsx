import { Check, Save } from "lucide-react";
import { useState } from "react";
import { todayISO } from "../lib/date";
import type { UploadedAttachment } from "../state/MemosContext";
import { MarkdownEditor } from "./MarkdownEditor";
import { inputClass, primaryButtonClass, subtleButtonClass } from "./ui";

interface EntryComposerProps {
  mode?: "create" | "edit";
  initialContent?: string;
  initialEntryDate?: string;
  submitLabel?: string;
  onSubmit: (input: { content: string; entryDate: string }) => Promise<void>;
  onUpload: (file: File) => Promise<UploadedAttachment>;
  onCancel?: () => void;
}

/** Unified record editor: an entry date, a Markdown body, and a save action. */
export function EntryComposer({
  mode = "create",
  initialContent = "",
  initialEntryDate,
  submitLabel = "保存",
  onSubmit,
  onUpload,
  onCancel,
}: EntryComposerProps) {
  const [content, setContent] = useState(initialContent);
  const [entryDate, setEntryDate] = useState(initialEntryDate ?? todayISO());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  async function handleSubmit() {
    if (!content.trim()) {
      setError("先写下要保存的内容");
      return;
    }
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await onSubmit({ content, entryDate });
      if (mode === "create") {
        setContent("");
        setEntryDate(todayISO());
        setNotice("已保存");
      }
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "保存失败");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="space-y-3">
      <label className="block text-sm text-gray-500 dark:text-gray-400">
        <span className="font-medium text-gray-700 dark:text-gray-300">
          日期
        </span>
        <input
          type="date"
          value={entryDate}
          onChange={(event) => setEntryDate(event.target.value)}
          className={`${inputClass} max-w-44`}
        />
      </label>

      <MarkdownEditor
        value={content}
        onChange={setContent}
        onUpload={onUpload}
      />

      {error ? (
        <p className="text-red-600 text-sm dark:text-red-400">{error}</p>
      ) : null}
      {notice ? (
        <p className="inline-flex items-center gap-1.5 text-gray-500 text-sm dark:text-gray-400">
          <Check className="h-4 w-4" />
          {notice}
        </p>
      ) : null}

      <div className="flex justify-end gap-2">
        {onCancel ? (
          <button
            type="button"
            onClick={onCancel}
            className={subtleButtonClass}
          >
            取消
          </button>
        ) : null}
        <button
          type="button"
          onClick={handleSubmit}
          disabled={busy}
          className={`${primaryButtonClass} w-full sm:w-auto`}
        >
          <Save className="h-4 w-4" />
          {busy ? "保存中…" : submitLabel}
        </button>
      </div>
    </div>
  );
}
