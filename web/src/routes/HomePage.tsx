import { Paperclip, Send } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { LocalDateTime } from "../components/LocalDateTime";
import { Markdown } from "../components/Markdown";
import type { Memo } from "../lib/api";
import { todayISO, yearsBetween } from "../lib/date";
import { excerpt, isActive, onThisDay } from "../lib/memos";
import { useMemos } from "../state/MemosContext";

function StreamItem({ memo }: { memo: Memo }) {
  const detail = `/entries/${memo.id}`;
  return (
    <article className="rounded-2xl border border-gray-200 bg-white px-4 py-3 dark:border-gray-700/70 dark:bg-gray-800">
      <div className="mb-1 flex items-center gap-2 text-gray-400 text-xs">
        <LocalDateTime value={memo.createdAt} />
        {memo.entryDate !== memo.createdAt.slice(0, 10) ? (
          <span>· 归属 {memo.entryDate}</span>
        ) : null}
        {memo.pinnedAt ? <span>· 置顶</span> : null}
        <Link
          to={detail}
          className="ml-auto rounded px-1.5 text-gray-400 transition hover:text-gray-900 dark:hover:text-gray-100"
        >
          打开
        </Link>
      </div>
      {memo.content.trim() ? (
        <Markdown content={memo.content} variant="chat" />
      ) : (
        <p className="text-gray-400">空白记录</p>
      )}
    </article>
  );
}

export function HomePage() {
  const { memos, create, upload } = useMemos();
  const today = todayISO();
  const memories = onThisDay(memos, today);
  const stream = memos.filter(isActive).slice(0, 20).reverse();

  const [content, setContent] = useState("");
  const [entryDate, setEntryDate] = useState(today);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [uploading, setUploading] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);
  const bottomRef = useRef<HTMLDivElement>(null);

  // biome-ignore lint/correctness/useExhaustiveDependencies: scroll to newest when the stream grows
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: "end" });
  }, [stream.length]);

  async function send() {
    if (!content.trim()) {
      setError("先写下要记录的内容");
      return;
    }
    setBusy(true);
    setError("");
    try {
      await create({ content, entryDate });
      setContent("");
      setEntryDate(todayISO());
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "保存失败");
    } finally {
      setBusy(false);
    }
  }

  async function attach(files: FileList | null) {
    if (!files?.length) {
      return;
    }
    setUploading(true);
    setError("");
    try {
      for (const file of Array.from(files)) {
        const uploaded = await upload(file);
        const md = uploaded.isImage
          ? `\n![${uploaded.filename}](${uploaded.url})\n`
          : `\n[${uploaded.filename}](${uploaded.url})\n`;
        setContent((current) => current + md);
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

  return (
    <div className="flex h-[calc(100dvh-3.5rem)] flex-col lg:h-[100dvh]">
      <div className="min-h-0 flex-1 overflow-y-auto">
        <div className="mx-auto w-full max-w-3xl px-4 py-8 sm:px-6">
          <header className="mb-6">
            <p className="text-gray-400 text-xs">{today}</p>
            <h1 className="mt-1 font-semibold text-2xl text-gray-900 tracking-tight dark:text-gray-50">
              今天想记录什么？
            </h1>
          </header>

          {memories.length > 0 ? (
            <section className="mb-6 rounded-xl border border-gray-200 bg-gray-50 p-4 dark:border-gray-700/70 dark:bg-gray-800/60">
              <h2 className="font-medium text-gray-500 text-xs dark:text-gray-400">
                那年今日
              </h2>
              <ul className="mt-2 space-y-1.5">
                {memories.map((memo) => (
                  <li key={memo.id}>
                    <Link
                      to={`/entries/${memo.id}`}
                      className="block rounded-lg px-2 py-1.5 text-gray-600 text-sm transition hover:bg-gray-100 hover:text-gray-900 dark:text-gray-300 dark:hover:bg-gray-800"
                    >
                      <span className="text-gray-400">
                        {yearsBetween(memo.entryDate, today)}年前
                      </span>
                      <span className="mx-1.5 text-gray-300 dark:text-gray-600">
                        ·
                      </span>
                      {excerpt(memo.content, 48) || "空白记录"}
                    </Link>
                  </li>
                ))}
              </ul>
            </section>
          ) : null}

          {stream.length === 0 ? (
            <div className="rounded-2xl border border-gray-200 border-dashed px-4 py-12 text-center text-gray-400 text-sm dark:border-gray-700">
              还没有记录。在下面写下第一条吧。
            </div>
          ) : (
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <h2 className="font-medium text-gray-500 text-xs dark:text-gray-400">
                  最近
                </h2>
                <Link
                  to="/timeline"
                  className="text-gray-500 text-xs hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100"
                >
                  查看全部
                </Link>
              </div>
              {stream.map((memo) => (
                <StreamItem key={memo.id} memo={memo} />
              ))}
            </div>
          )}
          <div ref={bottomRef} />
        </div>
      </div>

      <div className="shrink-0 border-gray-200 border-t bg-white/95 backdrop-blur dark:border-gray-800 dark:bg-gray-900/95">
        <div className="mx-auto w-full max-w-3xl px-4 py-3 sm:px-6">
          <div className="rounded-2xl border border-gray-300 bg-white p-2 shadow-sm shadow-gray-900/5 focus-within:border-gray-400 dark:border-gray-600 dark:bg-gray-800 dark:shadow-black/20">
            <textarea
              value={content}
              onChange={(event) => setContent(event.target.value)}
              onKeyDown={(event) => {
                if ((event.metaKey || event.ctrlKey) && event.key === "Enter") {
                  event.preventDefault();
                  void send();
                }
              }}
              rows={2}
              placeholder="写下此刻…（⌘/Ctrl + Enter 发送，支持 Markdown）"
              className="block max-h-48 w-full resize-none border-0 bg-transparent px-2 py-1.5 text-[15px] text-gray-900 leading-7 outline-none placeholder:text-gray-400 dark:text-gray-50"
            />
            <div className="flex items-center justify-between gap-2 pt-1">
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => fileRef.current?.click()}
                  disabled={uploading}
                  title="附件"
                  className="flex h-8 w-8 items-center justify-center rounded-lg text-gray-500 transition hover:bg-gray-100 hover:text-gray-900 disabled:opacity-50 dark:text-gray-400 dark:hover:bg-gray-700 dark:hover:text-gray-50"
                >
                  <Paperclip className="h-4 w-4" />
                </button>
                <input
                  ref={fileRef}
                  type="file"
                  multiple
                  className="hidden"
                  onChange={(event) => attach(event.target.files)}
                />
                <input
                  type="date"
                  value={entryDate}
                  onChange={(event) => setEntryDate(event.target.value)}
                  title="归属日期"
                  className="rounded-lg border border-gray-200 bg-transparent px-2 py-1 text-gray-500 text-xs dark:border-gray-700 dark:text-gray-400"
                />
                {uploading ? (
                  <span className="text-gray-400 text-xs">上传中…</span>
                ) : null}
              </div>
              <button
                type="button"
                onClick={send}
                disabled={busy || !content.trim()}
                className="flex h-8 items-center gap-1.5 rounded-lg bg-gray-900 px-3 font-medium text-sm text-white transition hover:bg-gray-800 disabled:opacity-40 dark:bg-gray-100 dark:text-gray-900 dark:hover:bg-white"
              >
                <Send className="h-3.5 w-3.5" />
                {busy ? "发送中" : "发送"}
              </button>
            </div>
          </div>
          {error ? (
            <p className="mt-1.5 text-red-600 text-xs dark:text-red-400">
              {error}
            </p>
          ) : null}
        </div>
      </div>
    </div>
  );
}
