import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { Markdown } from "../components/Markdown";
import {
  pageTitleClass,
  primaryButtonClass,
  readingShellClass,
  selectClass,
  textareaClass,
} from "../components/ui";
import type { AskContextScope, AskMessage } from "../lib/api";
import { useAsk } from "../state/AskContext";

const SCOPE_LABELS: Record<AskContextScope, string> = {
  recent_7_days: "最近 7 天",
  recent_30_days: "最近 30 天",
  all: "全部记录",
};

export function AskPage() {
  const [searchParams] = useSearchParams();
  const conversationParam = searchParams.get("conversation");
  const {
    activeConversation,
    activeId,
    messages,
    scope,
    busy,
    error,
    setScope,
    selectConversation,
    send,
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
        {busy ? (
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
        <div className="flex justify-end">
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
  if (message.role === "user") {
    return (
      <div className="ml-auto max-w-[85%] rounded-2xl bg-gray-100 px-4 py-2.5 text-gray-900 dark:bg-gray-800 dark:text-gray-50">
        <p className="whitespace-pre-wrap text-[15px] leading-7">
          {message.content}
        </p>
      </div>
    );
  }
  return (
    <div className="max-w-[92%]">
      <Markdown content={message.content} variant="chat" />
      {message.sourceRefs.length > 0 ? (
        <div className="mt-3 flex flex-wrap gap-2">
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
        </div>
      ) : null}
    </div>
  );
}
