import { useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { SendHorizontal, Square } from "lucide-react";
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
import type { AskContextScope, AskSourceKind } from "../lib/api";
import type { ActiveEntry } from "../lib/askTree";
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
    entries,
    liveUser,
    liveAnswer,
    regeneratingId,
    scope,
    sourceKind,
    busy,
    streaming,
    error,
    setScope,
    setSourceKind,
    selectConversation,
    send,
    regenerate,
    selectVariant,
    stop,
  } = useAsk();
  const [question, setQuestion] = useState("");
  const liveUserMessage = shouldShowLiveUser(entries, liveUser) ? liveUser : null;
  const lastAssistantId = [...entries]
    .reverse()
    .find((entry) => entry.message.role === "assistant")?.message.id;

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
        {entries.length === 0 && !liveUser ? (
          <div className="rounded-lg bg-gray-100/60 px-4 py-12 text-center text-gray-500 text-sm dark:bg-gray-900/50 dark:text-gray-400">
            可以根据记录提问，例如「我最近在反复想些什么？」
          </div>
        ) : (
          entries.map((entry) => (
            <MessageBubble
              key={entry.message.id}
              entry={entry}
              streamingText={
                regeneratingId === entry.message.id ? liveAnswer : undefined
              }
              canRegenerate={
                entry.message.role === "assistant" &&
                entry.message.id === lastAssistantId &&
                !busy
              }
              onRegenerate={() => regenerate(entry.message.id)}
              onSelectVariant={selectVariant}
            />
          ))
        )}
        {liveUserMessage ? (
          <>
            <div className="ml-auto max-w-[85%] rounded-lg bg-gray-100 px-4 py-2.5 text-gray-900 dark:bg-gray-800 dark:text-gray-50">
              <p className="whitespace-pre-wrap text-[15px] leading-7">
                {liveUserMessage.content}
              </p>
            </div>
            <div className="max-w-[92%]">
              {liveAnswer ? (
                <Markdown content={liveAnswer} variant="chat" />
              ) : (
                <p className="text-gray-400 text-sm dark:text-gray-500">
                  正在思考…
                </p>
              )}
            </div>
          </>
        ) : null}
        {busy && !streaming ? (
          <p className="text-gray-400 text-sm dark:text-gray-500">正在思考…</p>
        ) : null}
      </div>

      <div className="sticky bottom-0 z-10 py-4">
        <div className="space-y-3 rounded-2xl border border-gray-200 bg-white/90 p-3 shadow-sm dark:border-gray-700 dark:bg-gray-900/40 dark:shadow-black/10">
          <textarea
            value={question}
            onChange={(event) => setQuestion(event.target.value)}
            onKeyDown={(event) => {
              if (
                event.key === "Enter" &&
                !event.shiftKey &&
                !event.nativeEvent.isComposing
              ) {
                event.preventDefault();
                void submit();
              }
            }}
            rows={2}
            placeholder="根据记录提问…（Enter 发送，Shift + Enter 换行）"
            className={`${textareaClass} min-h-28 bg-white dark:bg-gray-900`}
          />
          {error ? (
            <p className="text-red-600 text-sm dark:text-red-400">{error}</p>
          ) : null}
          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:items-center sm:justify-end">
            {streaming ? (
              <button
                type="button"
                onClick={stop}
                className={`${secondaryButtonClass} h-10 w-full sm:w-auto`}
              >
                <Square className="h-4 w-4" />
                停止
              </button>
            ) : null}
            <button
              type="button"
              onClick={submit}
              disabled={busy}
              className={`${primaryButtonClass} h-10 w-full sm:w-auto`}
            >
              <SendHorizontal className="h-4 w-4" />
              {busy ? "生成中…" : "发送"}
            </button>
          </div>
        </div>
      </div>
    </main>
  );
}

export function shouldShowLiveUser(
  entries: ActiveEntry[],
  liveUser: { id: string } | null,
): boolean {
  if (!liveUser) {
    return false;
  }
  return !entries.some((entry) => entry.message.id === liveUser.id);
}

interface MessageBubbleProps {
  entry: ActiveEntry;
  streamingText?: string;
  canRegenerate: boolean;
  onRegenerate: () => void;
  onSelectVariant: (messageId: string) => void;
}

function MessageBubble({
  entry,
  streamingText,
  canRegenerate,
  onRegenerate,
  onSelectVariant,
}: MessageBubbleProps) {
  const { create } = useMemos();
  const navigate = useNavigate();
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const { message, variants, index } = entry;

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

  const content = streamingText ?? message.content;
  const hasVariants = variants.length > 1;

  return (
    <div className="max-w-[92%]">
      <Markdown content={content} variant="chat" />
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
        {hasVariants ? (
          <span className="inline-flex items-center gap-1 text-gray-500 text-xs dark:text-gray-400">
            <button
              type="button"
              aria-label="上一个回答"
              disabled={index <= 0}
              onClick={() => onSelectVariant(variants[index - 1].id)}
              className={`${ghostLinkClass} px-1`}
            >
              ‹
            </button>
            <span>
              {index + 1}/{variants.length}
            </span>
            <button
              type="button"
              aria-label="下一个回答"
              disabled={index >= variants.length - 1}
              onClick={() => onSelectVariant(variants[index + 1].id)}
              className={`${ghostLinkClass} px-1`}
            >
              ›
            </button>
          </span>
        ) : null}
        {canRegenerate ? (
          <button
            type="button"
            onClick={onRegenerate}
            className={`${ghostLinkClass} text-xs`}
          >
            重新生成
          </button>
        ) : null}
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
