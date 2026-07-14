import { Paperclip } from "lucide-react";
import { type DragEvent, useEffect, useId, useRef, useState } from "react";
import { Markdown } from "../../components/Markdown";
import { useToast } from "../../components/Toast";
import { subtleButtonClass, textareaClass } from "../../components/ui";
import { useI18n } from "../../i18n/I18nProvider";
import type { UploadedAttachment } from "./MemosContext";

interface MarkdownEditorProps {
  value: string;
  onChange: (value: string) => void;
  onUpload: (file: File) => Promise<UploadedAttachment>;
  onUploadingChange?: (uploading: boolean) => void;
  placeholder?: string;
  disabled?: boolean;
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
  onUploadingChange,
  placeholder,
  disabled = false,
}: MarkdownEditorProps) {
  const { locale, t } = useI18n();
  const toast = useToast();
  const [preview, setPreview] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [dragging, setDragging] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const textareaId = useId();
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const latestValueRef = useRef(value);
  const uploadingRef = useRef(false);
  const feedbackLocaleRef = useRef(locale);
  latestValueRef.current = value;

  useEffect(() => {
    if (feedbackLocaleRef.current === locale) {
      return;
    }
    feedbackLocaleRef.current = locale;
    setError(null);
  }, [locale]);

  function insertAtCursor(snippet: string) {
    const textarea = textareaRef.current;
    const current = latestValueRef.current;
    const start = textarea?.selectionStart ?? current.length;
    const end = textarea?.selectionEnd ?? current.length;
    onChange(current.slice(0, start) + snippet + current.slice(end));
  }

  async function uploadFiles(files: FileList | File[]) {
    const list = Array.from(files);
    if (disabled || list.length === 0 || uploadingRef.current) {
      return;
    }
    uploadingRef.current = true;
    setUploading(true);
    onUploadingChange?.(true);
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
      toast.showToast({
        kind: "success",
        message: t(
          list.length === 1 ? "editor.uploadedOne" : "editor.uploadedMany",
          { count: list.length },
        ),
      });
    } catch (cause) {
      const message =
        cause instanceof Error ? cause.message : t("editor.uploadFailed");
      setError(message);
      toast.showToast({ kind: "error", message });
    } finally {
      uploadingRef.current = false;
      setUploading(false);
      onUploadingChange?.(false);
      if (fileRef.current) {
        fileRef.current.value = "";
      }
    }
  }

  function handleDrop(event: DragEvent<HTMLTextAreaElement>) {
    setDragging(false);
    if (event.dataTransfer.files.length === 0) {
      return;
    }
    event.preventDefault();
    if (disabled) {
      return;
    }
    void uploadFiles(event.dataTransfer.files);
  }

  return (
    <div
      className={`overflow-hidden rounded-lg border bg-white transition-colors dark:bg-gray-950 ${
        dragging
          ? "border-gray-500 ring-2 ring-gray-300/50 dark:border-gray-400 dark:ring-gray-600/50"
          : "border-gray-200 dark:border-gray-700"
      }`}
    >
      <div className="flex min-h-11 items-center gap-1 border-gray-200 border-b bg-gray-100/45 p-1 text-sm dark:border-gray-800 dark:bg-gray-900">
        <TabButton
          active={!preview}
          disabled={disabled}
          onClick={() => setPreview(false)}
        >
          {t("editor.edit")}
        </TabButton>
        <TabButton
          active={preview}
          disabled={disabled}
          onClick={() => setPreview(true)}
        >
          {t("editor.preview")}
        </TabButton>
        <div className="ml-auto pr-0.5">
          <button
            type="button"
            onClick={() => fileRef.current?.click()}
            disabled={disabled || uploading}
            className={subtleButtonClass}
            aria-label={t(
              uploading ? "editor.uploading" : "editor.addAttachment",
            )}
            title={t(uploading ? "editor.uploading" : "editor.addAttachment")}
          >
            <Paperclip className="h-4 w-4" />
            <span className="hidden sm:inline">
              {t(uploading ? "editor.uploadingEllipsis" : "editor.attachment")}
            </span>
          </button>
          <input
            ref={fileRef}
            type="file"
            multiple
            disabled={disabled || uploading}
            className="hidden"
            onChange={(event) => {
              if (event.target.files) {
                void uploadFiles(event.target.files);
              }
            }}
          />
        </div>
      </div>

      <label className="sr-only" htmlFor={textareaId} hidden={preview}>
        {t("editor.contentLabel")}
      </label>
      <textarea
        id={textareaId}
        ref={textareaRef}
        hidden={preview}
        value={value}
        disabled={disabled}
        onChange={(event) => {
          if (!disabled) {
            onChange(event.target.value);
          }
        }}
        onDrop={handleDrop}
        onDragEnter={(event) => {
          if (!disabled && event.dataTransfer.types.includes("Files")) {
            setDragging(true);
          }
        }}
        onDragLeave={() => setDragging(false)}
        onDragOver={(event) => event.preventDefault()}
        placeholder={placeholder ?? t("editor.placeholder")}
        rows={8}
        className={`${textareaClass} min-h-44 rounded-t-none border-0 bg-white text-[15px] leading-7 sm:min-h-56 dark:bg-gray-950`}
      />
      {preview ? (
        <div className="min-h-44 bg-white p-3 sm:min-h-56 dark:bg-gray-950">
          {value.trim() ? (
            <Markdown content={value} />
          ) : (
            <p className="text-gray-400 text-sm dark:text-gray-500">
              {t("editor.noPreview")}
            </p>
          )}
        </div>
      ) : null}

      {error && !toast.available ? (
        <p className="border-gray-200 border-t px-3 py-2 text-red-600 text-sm dark:border-gray-800 dark:text-red-400">
          {error}
        </p>
      ) : null}
    </div>
  );
}

interface TabButtonProps {
  active: boolean;
  disabled: boolean;
  onClick: () => void;
  children: React.ReactNode;
}

function TabButton({ active, disabled, onClick, children }: TabButtonProps) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onClick}
      aria-pressed={active}
      className={`h-10 rounded-lg px-3 text-sm transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${
        active
          ? "bg-white font-medium text-gray-900 shadow-sm shadow-gray-900/[0.03] dark:bg-gray-800 dark:text-gray-50"
          : "text-gray-500 hover:bg-gray-100 hover:text-gray-950 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-gray-100"
      }`}
    >
      {children}
    </button>
  );
}
