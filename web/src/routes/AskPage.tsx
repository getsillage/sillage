import { useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { Markdown } from "../components/Markdown";
import {
  ghostLinkClass,
  pageTitleClass,
  primaryButtonClass,
  readingShellClass,
  secondaryButtonClass,
  selectClass,
  textareaClass,
} from "../components/ui";
import type { AskContextScope, AskMessage, AskSourceKind } from "../lib/api";
import { todayISO } from "../lib/date";
import { useAsk } from "../state/AskContext";
import { useMemos } from "../state/MemosContext";

const SCOPE_LABELS: Record<AskContextScope, string> = {
  recent_7_days: "最近 7 天",
  recent_30_days: "最近 30 天",
  all: "全部记录",
};

const SOURCE_KIND_OPTIONS: { value: AskSourceKind; label: string }[] = [
  { value: "records", label: "原始记录" },
  { value: "summaries", label: "记录总结" },
];

export function AskPage() {
  const [searchParams] = useSearchParams();
  const conversationParam = searchParams.get("conversation");
  const {
    activeConversation,
    activeId,
    messages,
    scope,
    sourceKind,
    busy,
    streaming,
    error,
    setScope,
    setSourceKind,
    selectConversation,
    send,
    stop,
  } = useAsk();
  const [question, setQuestion] = useState("");

  // biome-ignore lint/correctness/useExhaustiveDependencies: react only to the URL param
  useEffect(() => {
    if (conversationParam && conversationParam !== activeId) {
      selectConversation(conversationParam);
    }
  }, [conversationParam]);

  async function submit() {
    const text = question.trim();
    if (!text) {
      return;
    }
    await send(text);
    setQuestion("");
  }

  return (
    <main
      className={`${readingShellClass} flex min-h-[calc(100vh-3.5rem)] flex-col lg:min-h-screen`}
    >
      <header className="flex flex-wrap items-end justify-between gap-3 border-gray-200 border-b pb-4 dark:border-gray-800">
        <div>
          <h1 className={pageTitleClass}>
            {activeConversation?.title || "根据记录提问"}
          </h1>
          <p className="mt-1 text-gray-500 text-sm dark:text-gray-400">
            基于你的记录回答，范围：{SCOPE_LABELS[scope]}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <label className="text-gray-500 text-sm dark:text-gray-400">
            <span className="mr-2">来源</span>
            <select
              value={sourceKind}
              onChange={(event) =>
                setSourceKind(event.target.value as AskSourceKind)
              }
              className={`${selectClass} mt-0 inline-block w-auto`}
            >
              {SOURCE_KIND_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>
          <label className="text-gray-500 text-sm dark:text-gray-400">
            <span className="mr-2">范围</span>
            <select
              value={scope}
              onChange={(event) =>
                setScope(event.target.value as AskContextScope)
              }
              className={`${selectClass} mt-0 inline-block w-auto`}
            >
              <option value="recent_7_days">最近 7 天</option>
              <option value="recent_30_days">最近 30 天</option>
              <option value="all">全部记录</option>
            </select>
          </label>
        </div>
      </header>

      <div className="flex-1 space-y-6 py-6">
        {messages.length === 0 ? (
          <div className="rounded-lg bg-gray-100/60 px-4 py-12 text-center text-gray-500 text-sm dark:bg-gray-900/50 dark:text-gray-400">
            可以根据记录提问，例如「我最近在反复想些什么？」
          </div>
        ) : (
          messages.map((message) => (
            <MessageBubble key={message.id} message={message} />
          ))
        )}
        {busy && !streaming ? (
          <p className="text-gray-400 text-sm dark:text-gray-500">正在思考…</p>
        ) : null}
      </div>

      <div className="sticky bottom-0 space-y-2 border-gray-200 border-t bg-gray-50/90 py-4 backdrop-blur dark:border-gray-800 dark:bg-gray-950/90">
        <textarea
          value={question}
          onChange={(event) => setQuestion(event.target.value)}
          onKeyDown={(event) => {
            if ((event.metaKey || event.ctrlKey) && event.key === "Enter") {
              event.preventDefault();
              void submit();
            }
          }}
          rows={3}
          placeholder="根据记录提问…（⌘/Ctrl + Enter 发送）"
          className={textareaClass}
        />
        {error ? (
          <p className="text-red-600 text-sm dark:text-red-400">{error}</p>
        ) : null}
        <div className="flex justify-end gap-2">
          {streaming ? (
            <button
              type="button"
              onClick={stop}
              className={`${secondaryButtonClass} w-full sm:w-auto`}
            >
              停止
            </button>
          ) : null}
          <button
            type="button"
            onClick={submit}
            disabled={busy}
            className={`${primaryButtonClass} w-full sm:w-auto`}
          >
            {busy ? "生成中…" : "发送"}
          </button>
        </div>
      </div>
    </main>
  );
}

function MessageBubble({ message }: { message: AskMessage }) {
  const { create } = useMemos();
  const navigate = useNavigate();
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  if (message.role === "user") {
    return (
      <div className="ml-auto max-w-[85%] rounded-lg bg-gray-100 px-4 py-2.5 text-gray-900 dark:bg-gray-800 dark:text-gray-50">
        <p className="whitespace-pre-wrap text-[15px] leading-7">
          {message.content}
        </p>
      </div>
    );
  }

  async function saveAsRecord() {
    if (!message.content.trim()) {
      return;
    }
    setSaving(true);
    try {
      const memo = await create({
        content: message.content,
        entryDate: todayISO(),
      });
      setSaved(true);
      navigate(`/entries/${memo.id}`);
    } catch {
      setSaving(false);
    }
  }

  return (
    <div className="max-w-[92%]">
      <Markdown content={message.content} variant="chat" />
      <div className="mt-3 flex flex-wrap items-center gap-2">
        {message.sourceRefs.map((source) => (
          <Link
            key={`${message.id}-${source.memoId}-${source.rank}`}
            to={`/entries/${source.memoId}`}
            title={source.excerpt}
            className="inline-flex items-center gap-1 rounded-lg bg-gray-100 px-2.5 py-1 text-gray-700 text-xs transition hover:bg-gray-200 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700"
          >
            <span>{source.entryDate}</span>
          </Link>
        ))}
        {message.content.trim() ? (
          <button
            type="button"
            onClick={saveAsRecord}
            disabled={saving || saved}
            className={`${ghostLinkClass} text-xs`}
          >
            {saved ? "已存为记录" : saving ? "保存中…" : "存为记录"}
          </button>
        ) : null}
      </div>
    </div>
  );
}
