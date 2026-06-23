import { useRef, useState } from "react";
import { Markdown } from "./Markdown";

interface MarkdownEditorProps {
  name: string;
  defaultValue?: string;
  placeholder?: string;
}

interface UploadResult {
  url: string;
  filename: string;
}

/**
 * Markdown textarea with a live preview toggle and image upload. The textarea
 * stays mounted (just hidden) during preview so its value is always submitted.
 */
export function MarkdownEditor({
  name,
  defaultValue = "",
  placeholder = "今天发生了什么…",
}: MarkdownEditorProps) {
  const [value, setValue] = useState(defaultValue);
  const [preview, setPreview] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  function insertAtCursor(snippet: string) {
    const textarea = textareaRef.current;
    const start = textarea?.selectionStart ?? value.length;
    const end = textarea?.selectionEnd ?? value.length;
    setValue((current) => current.slice(0, start) + snippet + current.slice(end));
  }

  async function uploadFile(file: File) {
    setUploading(true);
    setError(null);
    try {
      const body = new FormData();
      body.set("file", file);
      const response = await fetch("/upload", { method: "POST", body });
      const data = (await response.json().catch(() => ({}))) as UploadResult | { error?: string };
      if (!response.ok || !("url" in data)) {
        throw new Error(("error" in data && data.error) || "上传失败");
      }
      insertAtCursor(`\n![${data.filename}](${data.url})\n`);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "上传失败");
    } finally {
      setUploading(false);
      if (fileRef.current) {
        fileRef.current.value = "";
      }
    }
  }

  return (
    <div className="rounded-lg border border-gray-300">
      <div className="flex items-center gap-1 border-gray-200 border-b bg-gray-50 p-1 text-sm">
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
            className="rounded px-3 py-1 text-gray-500 hover:text-gray-900 disabled:opacity-60"
          >
            {uploading ? "上传中…" : "🖼 图片"}
          </button>
          <input
            ref={fileRef}
            type="file"
            accept="image/png,image/jpeg,image/gif,image/webp"
            className="hidden"
            onChange={(event) => {
              const file = event.target.files?.[0];
              if (file) {
                void uploadFile(file);
              }
            }}
          />
        </div>
      </div>

      <textarea
        ref={textareaRef}
        name={name}
        value={value}
        onChange={(event) => setValue(event.target.value)}
        placeholder={placeholder}
        rows={14}
        className={`block w-full resize-y rounded-b-lg p-3 font-mono text-sm focus:outline-none ${
          preview ? "hidden" : ""
        }`}
      />
      {preview ? (
        <div className="min-h-48 p-3">
          {value.trim() ? (
            <Markdown content={value} />
          ) : (
            <p className="text-gray-400 text-sm">没有可预览的内容</p>
          )}
        </div>
      ) : null}

      {error ? (
        <p className="border-gray-200 border-t px-3 py-2 text-red-600 text-sm">{error}</p>
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
      className={`rounded px-3 py-1 ${
        active ? "bg-white font-medium text-gray-900 shadow-sm" : "text-gray-500"
      }`}
    >
      {children}
    </button>
  );
}
