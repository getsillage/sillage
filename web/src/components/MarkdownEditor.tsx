import { type DragEvent, useRef, useState } from "react";
import type { UploadedAttachment } from "../state/MemosContext";
import { Markdown } from "./Markdown";
import { subtleButtonClass, textareaClass } from "./ui";

interface MarkdownEditorProps {
  value: string;
  onChange: (value: string) => void;
  onUpload: (file: File) => Promise<UploadedAttachment>;
  placeholder?: string;
}

function attachmentMarkdown({
  url,
  filename,
  isImage,
}: UploadedAttachment): string {
  return isImage ? `\n![${filename}](${url})\n` : `\n[${filename}](${url})\n`;
}

/**
 * Markdown textarea with a live preview toggle and attachment upload. The
 * textarea stays mounted (just hidden) during preview so its value is stable.
 */
export function MarkdownEditor({
  value,
  onChange,
  onUpload,
  placeholder = "写下想记录的内容…",
}: MarkdownEditorProps) {
  const [preview, setPreview] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const latestValueRef = useRef(value);
  latestValueRef.current = value;

  function insertAtCursor(snippet: string) {
    const textarea = textareaRef.current;
    const current = latestValueRef.current;
    const start = textarea?.selectionStart ?? current.length;
    const end = textarea?.selectionEnd ?? current.length;
    onChange(current.slice(0, start) + snippet + current.slice(end));
  }

  async function uploadFiles(files: FileList | File[]) {
    const list = Array.from(files);
    if (list.length === 0) {
      return;
    }
    setUploading(true);
    setError(null);
    try {
      let appended = "";
      for (const file of list) {
        const uploaded = await onUpload(file);
        if (list.length === 1) {
          insertAtCursor(attachmentMarkdown(uploaded));
        } else {
          appended += attachmentMarkdown(uploaded);
        }
      }
      if (appended) {
        insertAtCursor(appended);
      }
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "上传失败");
    } finally {
      setUploading(false);
      if (fileRef.current) {
        fileRef.current.value = "";
      }
    }
  }

  function handleDrop(event: DragEvent<HTMLTextAreaElement>) {
    if (event.dataTransfer.files.length === 0) {
      return;
    }
    event.preventDefault();
    void uploadFiles(event.dataTransfer.files);
  }

  return (
    <div className="overflow-hidden rounded-lg border border-gray-300 bg-white dark:border-gray-700 dark:bg-gray-950">
      <div className="flex items-center gap-1 border-gray-200 border-b bg-gray-100/60 p-1 text-sm dark:border-gray-800 dark:bg-gray-900">
        <TabButton active={!preview} onClick={() => setPreview(false)}>
          编辑
        </TabButton>
        <TabButton active={preview} onClick={() => setPreview(true)}>
          预览
        </TabButton>
        <div className="ml-auto pr-1">
          <button
            type="button"
            onClick={() => fileRef.current?.click()}
            disabled={uploading}
            className={subtleButtonClass}
          >
            {uploading ? "上传中…" : "附件"}
          </button>
          <input
            ref={fileRef}
            type="file"
            multiple
            className="hidden"
            onChange={(event) => {
              if (event.target.files) {
                void uploadFiles(event.target.files);
              }
            }}
          />
        </div>
      </div>

      <textarea
        ref={textareaRef}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        onDrop={handleDrop}
        onDragOver={(event) => event.preventDefault()}
        placeholder={placeholder}
        rows={12}
        className={`${textareaClass} rounded-t-none border-0 text-[15px] leading-7 focus:ring-0 ${preview ? "hidden" : ""}`}
      />
      {preview ? (
        <div className="min-h-48 bg-white p-3 dark:bg-gray-950">
          {value.trim() ? (
            <Markdown content={value} />
          ) : (
            <p className="text-gray-400 text-sm dark:text-gray-500">
              没有可预览的内容
            </p>
          )}
        </div>
      ) : null}

      {error ? (
        <p className="border-gray-200 border-t px-3 py-2 text-red-600 text-sm dark:border-gray-800 dark:text-red-400">
          {error}
        </p>
      ) : null}
    </div>
  );
}

interface TabButtonProps {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}

function TabButton({ active, onClick, children }: TabButtonProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`rounded-lg px-3 py-1.5 transition ${
        active
          ? "bg-celadon-50 font-medium text-celadon-800 dark:bg-celadon-900 dark:text-celadon-200"
          : "text-gray-500 hover:bg-gray-100 hover:text-gray-950 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-gray-100"
      }`}
    >
      {children}
    </button>
  );
}
